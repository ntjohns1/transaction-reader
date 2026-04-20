package com.noslen.transaction_reader.io;

import com.noslen.transaction_reader.model.Transaction;

import java.io.IOException;
import java.util.List;

/**
 * Common contract for all bank statement parsers.
 * Each implementation handles one statement format (ELFCU CSV, Chase XLSX, Discover XLSX/HTML).
 * Every returned Transaction has its {@code account} field set to {@link #getAccountName()}.
 */
public interface StatementParser {
    List<Transaction> parse(String filePath) throws IOException;
    String getAccountName();
}
