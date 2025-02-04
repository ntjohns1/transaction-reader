package com.noslen.transaction_reader.service;

import com.noslen.transaction_reader.model.Transaction;
import com.noslen.transaction_reader.config.Config;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TransactionMapper {

    private final CliService cliService;
    private final Config config;
    private final Map<String, String> categoryMap;
    private final List<String> categoryList;

    public TransactionMapper(CliService cliService) {
        this.cliService = cliService;
        this.config = Config.getInstance();
        this.categoryMap = config.loadCategoryMappings();
        this.categoryList = config.getCategoryList();
    }

    public Map<LocalDate, Map<String, Double>> mapTransactions(List<Transaction> transactions) {
        Map<LocalDate, Map<String, Double>> mappedData = new HashMap<>();

        for (Transaction transaction : transactions) {
            LocalDate date = transaction.getPostDate();
            String description = transaction.getDescription();

            // Ignore transaction.category & only use Excel-derived categories
            String category = categoryMap.get(description);

            if (category == null || !categoryList.contains(category)) {
                category = cliService.promptForCategory(transaction, categoryList);
                categoryMap.put(description, category); // Save for future mappings
                config.saveCategoryMappings(categoryMap); // Persist mappings
            }

            // Handle debit/credit logic
            double amount = transaction.getDebit() > 0 ? -transaction.getDebit() : transaction.getCredit();

            // Insert into mapped data
            mappedData.putIfAbsent(date, new HashMap<>());
            Map<String, Double> categoryAmounts = mappedData.get(date);

            // Sum amounts for multiple transactions
            categoryAmounts.put(category, categoryAmounts.getOrDefault(category, 0.0) + amount);
        }

        return mappedData;
    }
}