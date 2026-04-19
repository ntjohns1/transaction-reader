package com.noslen.transaction_reader;

import com.noslen.transaction_reader.config.Config;
import com.noslen.transaction_reader.io.*;
import com.noslen.transaction_reader.model.Transaction;
import com.noslen.transaction_reader.service.CliService;
import com.noslen.transaction_reader.service.MerchantClassifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class TransactionReaderApplication {

    private static final Logger logger = LogManager.getLogger(TransactionReaderApplication.class);

    public static void main(String[] args) {
        logger.info("Starting Transaction Reader...");

        Config config;
        try {
            config = Config.getInstance();
        } catch (IllegalStateException e) {
            logger.error("Configuration error: {}", e.getMessage());
            logger.error("Copy config.properties.template → config.properties and fill in your paths.");
            System.exit(1);
            return;
        }

        try {
            // ── Step 1: Discover statement files ─────────────────────────────
            Map<String, List<String>> statementFiles = discoverStatements(config.getStatementsDir());
            logger.info("Found statement files: {}", statementFiles);

            // ── Step 2: Parse all statements ──────────────────────────────────
            List<Transaction> allTransactions = new ArrayList<>();
            allTransactions.addAll(parseWith(new ElfcuParser(),    statementFiles.get("ELFCU")));
            allTransactions.addAll(parseWith(new ChaseParser(),    statementFiles.get("Chase")));
            allTransactions.addAll(parseWith(new DiscoverParser(), statementFiles.get("Discover")));
            logger.info("Parsed {} total transactions.", allTransactions.size());

            if (allTransactions.isEmpty()) {
                logger.warn("No transactions found — nothing to write.");
                return;
            }

            // ── Step 3: Classify unclassified transactions ────────────────────
            CliService cliService = new CliService();
            MerchantClassifier classifier = new MerchantClassifier(
                config.getMerchantsPath(), cliService, config.getCategoryList());

            int classified = 0;
            for (Transaction t : allTransactions) {
                if (t.getClassification() == null || t.getClassification().isBlank()) {
                    t.setClassification(classifier.classify(t.getDescription()));
                    classified++;
                }
            }
            logger.info("Classified {} transactions (ELFCU pre-classified entries skipped).", classified);
            classifier.save(); // persist any new merchant entries learned from CLI

            // ── Step 4: Append to account sheets ──────────────────────────────
            ExcelFileWriter writer = new ExcelFileWriter(config);

            Map<String, List<Transaction>> byAccount = groupByAccount(allTransactions);
            for (Map.Entry<String, List<Transaction>> entry : byAccount.entrySet()) {
                writer.appendTransactionsToSheet(entry.getValue(), entry.getKey());
                logger.info("Appended {} transactions to '{}' sheet.",
                            entry.getValue().size(), entry.getKey());
            }

            // ── Step 5: Write cross-sheet formulas to Ledger ──────────────────
            writer.writeFormulasToLedger();

            // ── Step 6: Save ──────────────────────────────────────────────────
            writer.saveWorkbook();
            logger.info("Done. Output written to {}", config.getOutputPath());

        } catch (Exception e) {
            logger.error("Fatal error during processing: ", e);
            System.exit(1);
        }
    }

    // ── Statement file discovery ──────────────────────────────────────────────

    /**
     * Scans the statements directory for known account subfolders and collects
     * all statement files within each.
     *
     * Subfolder → account mapping (all .csv):
     *   Elements/       → ELFCU
     *   Chase/          → Chase
     *   Discover 4914/  → CC 2   (DiscoverParser infers from path)
     *   Discover 9577/  → CC 1   (DiscoverParser infers from path)
     */
    private static Map<String, List<String>> discoverStatements(String statementsDir) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        result.put("ELFCU", filesIn(statementsDir, "Elements", ".csv"));
        result.put("Chase", filesIn(statementsDir, "Chase",    ".csv"));
        List<String> discoverFiles = new ArrayList<>();
        discoverFiles.addAll(filesIn(statementsDir, "Discover 4914", ".csv"));
        discoverFiles.addAll(filesIn(statementsDir, "Discover 9577", ".csv"));
        result.put("Discover", discoverFiles);
        return result;
    }

    private static List<String> filesIn(String base, String subdir, String extension) {
        File dir = new File(base, subdir);
        if (!dir.exists() || !dir.isDirectory()) {
            logger.debug("Statement directory not found: {}", dir.getAbsolutePath());
            return Collections.emptyList();
        }
        File[] files = dir.listFiles(f -> f.isFile() && f.getName().toLowerCase().endsWith(extension));
        if (files == null || files.length == 0) return Collections.emptyList();
        return Arrays.stream(files).map(File::getAbsolutePath).toList();
    }

    // ── Parsing helpers ───────────────────────────────────────────────────────

    private static List<Transaction> parseWith(StatementParser parser, List<String> filePaths) {
        if (filePaths == null || filePaths.isEmpty()) return Collections.emptyList();
        List<Transaction> result = new ArrayList<>();
        for (String path : filePaths) {
            try {
                result.addAll(parser.parse(path));
            } catch (IOException e) {
                logger.error("Error parsing {}: {}", path, e.getMessage());
            }
        }
        return result;
    }

    private static Map<String, List<Transaction>> groupByAccount(List<Transaction> transactions) {
        Map<String, List<Transaction>> grouped = new LinkedHashMap<>();
        for (Transaction t : transactions) {
            grouped.computeIfAbsent(t.getAccount(), k -> new ArrayList<>()).add(t);
        }
        return grouped;
    }
}
