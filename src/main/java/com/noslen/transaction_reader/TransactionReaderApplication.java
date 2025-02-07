package com.noslen.transaction_reader;

import com.noslen.transaction_reader.io.ExcelFileWriter;
import com.noslen.transaction_reader.io.InputParser;
import com.noslen.transaction_reader.model.Transaction;
import com.noslen.transaction_reader.service.CliService;
import com.noslen.transaction_reader.service.TransactionMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public class TransactionReaderApplication {
    private static final Logger logger = LogManager.getLogger(TransactionReaderApplication.class);

    public static void main(String[] args) {
        logger.info("Starting Transaction Reader Application...");
        try {
            // Step 1: Parse transactions from test CSV
            List<Transaction> transactions = InputParser.parseTransactions();
            logger.info("Parsed {} transactions from test CSV.",
                        transactions.size());
            // Step 2: Map transactions to categories
			CliService cliService = new CliService();
            ExcelFileWriter excelWriter = new ExcelFileWriter(cliService);
			TransactionMapper mapper = new TransactionMapper();
			excelWriter.categorizeTransactions(transactions);
            excelWriter.appendTransactionsToTable(transactions);
            excelWriter.collectTransactionRefs();
            excelWriter.saveWorkbook();
            logger.info("Transactions written to output file successfully.");

        } catch (Exception e) {
            logger.error("An error occurred during processing: ",
                         e);
        }
        logger.info("Transaction Reader Application finished.");
    }
}

