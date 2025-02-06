package com.noslen.transaction_reader.service;

import com.noslen.transaction_reader.model.Transaction;
import com.noslen.transaction_reader.config.Config;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.Sheet;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TransactionMapper {

    private static final Logger logger = LogManager.getLogger(TransactionMapper.class);

    private final CliService cliService;
    private final Config config;


    public TransactionMapper(CliService cliService) {
        this.cliService = cliService;
        this.config = Config.getInstance();
    }

    public void categorizeTransactions(List<Transaction> transactions) {
        for (Transaction transaction : transactions) {
            String category = cliService.promptForCategory(transaction, config.getCategoryList());
            transaction.setClassification(category);
        }
    }

    public Map<LocalDate, Map<String, String>> collectTransactionRefs(Sheet sheet) {
         Map<LocalDate, Map<String, String>> transactionMappings = new HashMap<>();
         DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
         //  Read transactions
         return transactionMappings;
    }
}