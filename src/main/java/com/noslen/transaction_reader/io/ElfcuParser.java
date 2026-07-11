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
 * Parses ELFCU (Elements Financial) CSV exports.
 *
 * Expected columns (0-indexed):
 *   0: Account Number
 *   1: Post Date      (M/d/yy or M/d/yyyy — varies by export)
 *   2: Check
 *   3: Description
 *   4: Debit          (empty if none)
 *   5: Credit         (empty if none)
 *   6: Status
 *   7: Balance
 *   8: Classification (ELFCU's native taxonomy — ignored; MerchantClassifier reassigns)
 */
public class ElfcuParser implements StatementParser {

    private static final Logger logger = LogManager.getLogger(ElfcuParser.class);

    // Elements exports use 2-digit years in some downloads and 4-digit in others.
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
        DateTimeFormatter.ofPattern("M/d/yy"),
        DateTimeFormatter.ofPattern("M/d/yyyy")
    );
    private static final String ACCOUNT_NAME = "ELFCU";

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
                if (firstRow) { firstRow = false; continue; } // skip header

                if (row.length < 9) {
                    logger.warn("Skipping short row ({} cols): {}", row.length, String.join(",", row));
                    continue;
                }

                try {
                    LocalDate postDate    = parseDate(row[1].trim());
                    if (postDate == null) {
                        logger.warn("ELFCU: unrecognized date '{}'", row[1].trim());
                        continue;
                    }
                    String description    = row[3].trim();
                    double debit          = row[4].trim().isEmpty() ? 0.0 : Double.parseDouble(row[4].trim());
                    double credit         = row[5].trim().isEmpty() ? 0.0 : Double.parseDouble(row[5].trim());
                    double balance        = row[7].trim().isEmpty() ? 0.0 : Double.parseDouble(row[7].trim());

                    transactions.add(new Transaction(postDate, description, debit, credit, balance, ACCOUNT_NAME));

                } catch (Exception e) {
                    logger.warn("Skipping invalid ELFCU row: {}", String.join(",", row));
                }
            }
        } catch (CsvValidationException e) {
            throw new IOException("CSV validation error in " + filePath, e);
        }

        logger.info("ElfcuParser: parsed {} transactions from {}", transactions.size(), filePath);
        return transactions;
    }

    private LocalDate parseDate(String raw) {
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try { return LocalDate.parse(raw, fmt); }
            catch (DateTimeParseException ignored) {}
        }
        return null;
    }
}
