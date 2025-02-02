package com.noslen.transaction_reader;

import com.noslen.transaction_reader.io.InputParser;
import com.noslen.transaction_reader.model.Transaction;

import java.util.ArrayList;
import java.util.List;

public class TransactionReaderApplication {

	public static void main(String[] args) {
		System.out.println("test");
		List<Transaction> transactions = InputParser.parseTransactions();
		System.out.println(transactions);
	}

}
