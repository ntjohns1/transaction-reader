package com.noslen.transaction_reader.service;

import com.noslen.transaction_reader.model.Transaction;
import com.noslen.transaction_reader.config.Config;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TransactionMapper {

    private final CliService cliService;
    private final Config config;
    private final Map<String, String> categoryMap;

    public TransactionMapper(CliService cliService) {
        this.cliService = cliService;
        this.config = Config.getInstance();

        this.categoryMap = config.loadCategoryMappings();

    }

    public Map<LocalDate, Map<String, Double>> mapTransactions(List<Transaction> transactions) {
        Map<LocalDate, Map<String, Double>> mappedData = new HashMap<>();

        for (Transaction transaction : transactions) {
            LocalDate date = transaction.postDate();
            String category = transaction.classification();

            // Check if category exists or needs user input
            if (category == null || category.isBlank()) {
                category = categoryMap.getOrDefault(transaction.description(),
                                                    cliService.promptForCategory(transaction.description(), config.getCategoryList()));
                categoryMap.put(transaction.description(), category); // Save mapping
            }

            // Save updated category mapping to Config
            config.saveCategoryMappings(categoryMap);

            // Handle debit/credit logic
            double amount = transaction.debit() > 0 ? -transaction.debit() : transaction.credit();

            // Insert into mapped data
            mappedData.putIfAbsent(date, new HashMap<>());
            Map<String, Double> categoryAmounts = mappedData.get(date);

            // Sum amounts for multiple transactions
            categoryAmounts.put(category, categoryAmounts.getOrDefault(category, 0.0) + amount);
        }

        return mappedData;
    }
}