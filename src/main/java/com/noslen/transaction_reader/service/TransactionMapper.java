package com.noslen.transaction_reader.service;

import com.noslen.transaction_reader.model.Transaction;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class TransactionMapper {

    private final Map<String, String> categoryMap = new HashMap<>();

    public Map<LocalDate, Map<String, Double>> mapTransactions(List<Transaction> transactions) {
        Map<LocalDate, Map<String, Double>> mappedData = new HashMap<>();

        for (Transaction transaction : transactions) {
            LocalDate date = transaction.postDate();
            String category = transaction.classification();
            double amount = transaction.debit() > 0 ? -transaction.debit() : transaction.credit();

            // If classification is missing, prompt user
            if (category == null || category.isBlank()) {
                category = promptForCategory(transaction.description());
                categoryMap.put(transaction.description(),
                                category); // Save for future use
            }

            // Insert into mapped data
            mappedData.putIfAbsent(date,
                                   new HashMap<>());
            Map<String, Double> categoryAmounts = mappedData.get(date);

            // Add amount to existing category sum
            categoryAmounts.put(category,
                                categoryAmounts.getOrDefault(category,
                                                             0.0) + amount);
        }

        return mappedData;
    }

    private String promptForCategory(String description) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Enter category for transaction: " + description);
        return scanner.nextLine()
                .trim();
    }
}
