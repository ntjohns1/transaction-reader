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
 * Parses Chase bank statement CSV exports.
 *
 * Expected columns (0-indexed):
 *   0: Details        (skip)
 *   1: Posting Date   (MM/dd/yyyy)
 *   2: Description
 *   3: Amount         (negative = debit, positive = credit)
 *   4: Type           (skip)
 *   5: Balance
 *   6: Check or Slip # (skip)
 */
public class ChaseParser implements StatementParser {

    private static final Logger logger = LogManager.getLogger(ChaseParser.class);
    private static final String ACCOUNT_NAME = "Chase";

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("M/d/yyyy"),
        DateTimeFormatter.ofPattern("M/d/yy")
    );

    @Override
    public String getAccountName() {
        return ACCOUNT_NAME;
    }

    @Override
    public List<Transaction> parse(String filePath) throws IOException {
        List<Transaction> transactions = new ArrayList<>();

        try (CSVReader reader = new CSVReader(new FileReader(filePath))) {
            String[] row;
            boolean firstRow = true;

            while ((row = reader.readNext()) != null) {
                if (firstRow) { firstRow = false; continue; }
                if (row.length < 6) continue;

                try {
                    String dateStr = row[1].trim();
                    String desc    = row[2].trim();
                    if (dateStr.isEmpty() || desc.isEmpty()) continue;

                    LocalDate postDate = parseDate(dateStr);
                    if (postDate == null) {
                        logger.warn("Chase: unrecognized date '{}'", dateStr);
                        continue;
                    }

                    double amount  = parseDouble(row[3]);
                    double balance = parseDouble(row[5]);

                    double debit  = amount < 0 ? Math.abs(amount) : 0.0;
                    double credit = amount > 0 ? amount : 0.0;

                    transactions.add(new Transaction(postDate, desc, debit, credit, balance, ACCOUNT_NAME));
                } catch (Exception e) {
                    logger.warn("Skipping Chase row: {}", e.getMessage());
                }
            }
        } catch (CsvValidationException e) {
            throw new IOException("CSV validation error in " + filePath, e);
        }

        logger.info("ChaseParser: parsed {} transactions from {}", transactions.size(), filePath);
        return transactions;
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
