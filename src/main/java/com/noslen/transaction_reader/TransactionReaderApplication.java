package com.noslen.transaction_reader;

import com.noslen.transaction_reader.config.Config;
import com.noslen.transaction_reader.io.ExcelFileWriter;
import com.noslen.transaction_reader.io.InputParser;
import com.noslen.transaction_reader.model.Transaction;
import com.noslen.transaction_reader.service.CliService;
import com.noslen.transaction_reader.service.TransactionMapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TransactionReaderApplication {

	public static void main(String[] args) {
		// Step 1: Parse transactions
		List<Transaction> transactions = InputParser.parseTransactions();

		// Step 2: Map transactions to rows/categories
		CliService CliService = new CliService();
		TransactionMapper mapper = new TransactionMapper(CliService);
		Map<LocalDate, Map<String, Double>> mappedData = mapper.mapTransactions(transactions);

		// Step 3: Update Excel file
		ExcelFileWriter excelWriter = new ExcelFileWriter(Config.getInstance().getInitialExcelPath());
		excelWriter.updateExcel(mappedData, Config.getInstance()
				.getOutputPath());
	}
}

