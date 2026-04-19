package com.noslen.transaction_reader.io;

import com.noslen.transaction_reader.model.Transaction;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses Discover card CSV exports for two accounts:
 *   CC 1 — files containing "9577" in the path (e.g. Discover 9577/)
 *   CC 2 — files containing "4914" in the path (e.g. Discover 4914/)
 *
 * Expected columns (0-indexed):
 *   0: Trans. Date   (skip)
 *   1: Post Date     (M/d/yy or MM/dd/yyyy)
 *   2: Description
 *   3: Amount        (positive = charge/debit, negative = payment/credit)
 *   4: Category      (skip — Discover's taxonomy, overwritten by MerchantClassifier)
 */
public class DiscoverParser implements StatementParser {

    private static final Logger logger = LogManager.getLogger(DiscoverParser.class);

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
        DateTimeFormatter.ofPattern("M/d/yy"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("M/d/yyyy")
    );

    private String accountName;

    @Override
    public String getAccountName() {
        return accountName != null ? accountName : "Discover";
    }

    @Override
    public List<Transaction> parse(String filePath) throws IOException {
        this.accountName = inferAccount(filePath);
        logger.info("DiscoverParser: inferred account '{}' from {}", accountName, filePath);

        List<Transaction> transactions = new ArrayList<>();

        try (CSVReader reader = new CSVReader(new FileReader(filePath))) {
            String[] row;
            boolean firstRow = true;

            while ((row = reader.readNext()) != null) {
                if (firstRow) { firstRow = false; continue; }
                if (row.length < 4) continue;

                try {
                    String dateStr = row[1].trim();
                    String desc    = row[2].trim();
                    if (dateStr.isEmpty() || desc.isEmpty()) continue;

                    LocalDate postDate = parseDate(dateStr);
                    if (postDate == null) {
                        logger.warn("Discover: unrecognized date '{}'", dateStr);
                        continue;
                    }

                    double amount = parseDouble(row[3]);
                    double debit  = amount > 0 ? amount : 0.0;
                    double credit = amount < 0 ? Math.abs(amount) : 0.0;

                    transactions.add(new Transaction(postDate, desc, debit, credit, 0.0, accountName));
                } catch (Exception e) {
                    logger.warn("Skipping Discover row: {}", e.getMessage());
                }
            }
        } catch (CsvValidationException e) {
            throw new IOException("CSV validation error in " + filePath, e);
        }

        logger.info("DiscoverParser: parsed {} transactions from {}", transactions.size(), filePath);
        return transactions;
    }

    private String inferAccount(String filePath) {
        String lower = filePath.toLowerCase();
        if (lower.contains("9577")) return "CC 1";
        if (lower.contains("4914")) return "CC 2";
        logger.warn("Cannot infer Discover account from path '{}' — defaulting to CC 1", filePath);
        return "CC 1";
    }

    private LocalDate parseDate(String raw) {
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try { return LocalDate.parse(raw, fmt); }
            catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    private double parseDouble(String raw) {
        String s = raw == null ? "" : raw.trim().replace("$", "").replace(",", "");
        if (s.isEmpty()) return 0.0;
        try { return Double.parseDouble(s); }
        catch (NumberFormatException e) { return 0.0; }
    }
}
