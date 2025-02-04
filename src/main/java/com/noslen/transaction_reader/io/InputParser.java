package com.noslen.transaction_reader.io;

import com.noslen.transaction_reader.config.Config;
import com.noslen.transaction_reader.model.Transaction;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class InputParser {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("M/d/yy");

    public static List<Transaction> parseTransactions() {
        List<Transaction> transactions = new ArrayList<>();
        String inputFilePath = Config.getInstance().getInputPath();

        try (CSVReader reader = new CSVReader(new FileReader(inputFilePath))) {
            String[] nextLine;
            boolean isFirstRow = true;

            while ((nextLine = reader.readNext()) != null) {
                if (isFirstRow) { // Skip header row
                    isFirstRow = false;
                    continue;
                }

                try {
                    // Parse fields
                    LocalDate postDate = LocalDate.parse(nextLine[1].trim(), DATE_FORMATTER);

                    String description = nextLine[3].trim();
                    double debit = nextLine[4].isEmpty() ? 0.0 : Double.parseDouble(nextLine[4]);
                    double credit = nextLine[5].isEmpty() ? 0.0 : Double.parseDouble(nextLine[5]);
                    double balance = nextLine[7].isEmpty() ? 0.0 : Double.parseDouble(nextLine[7]);
                    String classification = nextLine.length > 8 ? nextLine[8].trim() : "";

                    // Create Transaction object
                    transactions.add(new Transaction(postDate, description, debit, credit, balance));

                } catch (Exception e) {
                    e.printStackTrace();
                    System.err.println("Skipping invalid row: " + String.join(",", nextLine));
                }
            }
        } catch (IOException | CsvValidationException e) {
            e.printStackTrace();
        }

        return transactions;
    }
}