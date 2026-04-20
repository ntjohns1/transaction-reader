package com.noslen.transaction_reader.io;

import com.noslen.transaction_reader.config.Config;
import com.noslen.transaction_reader.model.Transaction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

/**
 * Writes categorized transactions into a multi-sheet Excel workbook:
 *
 *   "2026 Ledger"  — daily rows with category totals (formulas reference account sheets)
 *   "ELFCU"        — raw ELFCU transaction history
 *   "Chase"        — raw Chase transaction history
 *   "CC 1"         — raw Discover 9577 transaction history
 *   "CC 2"         — raw Discover 4914 transaction history
 *
 * Account history sheet layout (columns A–F):
 *   A: Post Date (ISO string)
 *   B: Description
 *   C: Debit
 *   D: Credit
 *   E: Balance
 *   F: Classification
 *
 * Ledger formulas use cross-sheet references, e.g.:
 *   Category cell:  -ELFCU!C5 + -ELFCU!C8
 *   Balance cell:   ELFCU!E12
 *
 * For sheet names containing spaces (CC 1, CC 2) the name is quoted:  'CC 1'!C5
 */
public class ExcelFileWriter {

    private static final Logger logger = LogManager.getLogger(ExcelFileWriter.class);

    /** Maps account name → column index on the Ledger sheet. */
    private static final Map<String, Integer> ACCOUNT_LEDGER_COLS = Map.of(
        "ELFCU", 2,   // C
        "Chase", 3,   // D
        "CC 1",  4,   // E
        "CC 2",  5    // F
    );

    /** Accounts without a running-balance column in the source CSV — compute it with a formula. */
    private static final Set<String> COMPUTED_BALANCE_ACCOUNTS = Set.of("CC 1", "CC 2");

    /** Account history sheet column indices. */
    private static final int COL_DATE   = 0; // A
    private static final int COL_DESC   = 1; // B
    private static final int COL_DEBIT  = 2; // C
    private static final int COL_CREDIT = 3; // D
    private static final int COL_BAL    = 4; // E
    private static final int COL_CLASS  = 5; // F

    private final Config config;
    private Workbook workbook;

    public ExcelFileWriter(Config config) {
        this.config = config;
        initializeWorkbook();
    }

    // ── Initialization ────────────────────────────────────────────────────────

    private void initializeWorkbook() {
        try {
            File file = new File(config.getInitialExcelPath());
            if (file.exists()) {
                try (FileInputStream fis = new FileInputStream(file)) {
                    workbook = new XSSFWorkbook(fis);
                }
                logger.info("Opened existing workbook: {}", config.getInitialExcelPath());
            } else {
                workbook = new XSSFWorkbook();
                logger.warn("Budget Excel not found at {} — created blank workbook.",
                            config.getInitialExcelPath());
            }
            ensureAccountSheets();
        } catch (IOException e) {
            logger.error("Error initializing workbook: ", e);
        }
    }

    /** Creates account sheets with headers + opening balance rows if they don't exist yet. */
    private void ensureAccountSheets() {
        Map<String, Double> balances = config.getOpeningBalances();
        for (String accountName : ACCOUNT_LEDGER_COLS.keySet()) {
            if (workbook.getSheet(accountName) == null) {
                Sheet sheet = workbook.createSheet(accountName);
                writeAccountSheetHeader(sheet);
                writeOpeningBalanceRow(sheet, balances.getOrDefault(accountName, 0.0));
                logger.info("Created account sheet '{}'", accountName);
            }
        }
    }

    private void writeAccountSheetHeader(Sheet sheet) {
        Row header = sheet.createRow(0);
        header.createCell(COL_DATE,  CellType.STRING).setCellValue("Post Date");
        header.createCell(COL_DESC,  CellType.STRING).setCellValue("Description");
        header.createCell(COL_DEBIT, CellType.STRING).setCellValue("Debit");
        header.createCell(COL_CREDIT,CellType.STRING).setCellValue("Credit");
        header.createCell(COL_BAL,   CellType.STRING).setCellValue("Balance");
        header.createCell(COL_CLASS, CellType.STRING).setCellValue("Classification");
    }

    private void writeOpeningBalanceRow(Sheet sheet, double openingBalance) {
        Row row = sheet.createRow(1);
        row.createCell(COL_DATE, CellType.STRING).setCellValue("Opening Balance");
        row.createCell(COL_BAL,  CellType.NUMERIC).setCellValue(openingBalance);
    }

    // ── Append transactions to account sheet ─────────────────────────────────

    /**
     * Appends transactions for the given account to its dedicated sheet.
     * Transactions are written newest-first (reversed before writing).
     * Data starts at row 2 (after header row 0 and opening balance row 1).
     */
    public void appendTransactionsToSheet(List<Transaction> transactions, String accountName) {
        Sheet sheet = workbook.getSheet(accountName);
        if (sheet == null) {
            logger.error("Account sheet '{}' not found — skipping append.", accountName);
            return;
        }

        boolean computedBalance = COMPUTED_BALANCE_ACCOUNTS.contains(accountName);

        // CC sheets: oldest-at-top so the running-balance formula chains downward
        // from the opening-balance row (E2). Bank sheets: newest-at-top (existing layout).
        List<Transaction> ordered = new ArrayList<>(transactions);
        if (!computedBalance) {
            Collections.reverse(ordered);
        }

        int nextRow = findFirstEmptyRow(sheet, 2);
        logger.info("Appending {} {} transactions starting at row {}", ordered.size(), accountName, nextRow);

        for (Transaction t : ordered) {
            Row row = sheet.getRow(nextRow);
            if (row == null) row = sheet.createRow(nextRow);

            row.createCell(COL_DATE,  CellType.STRING).setCellValue(t.getPostDate().toString());
            row.createCell(COL_DESC,  CellType.STRING).setCellValue(t.getDescription());
            row.createCell(COL_DEBIT, CellType.NUMERIC).setCellValue(t.getDebit());
            row.createCell(COL_CREDIT,CellType.NUMERIC).setCellValue(t.getCredit());

            if (computedBalance) {
                int excelRow = nextRow + 1;
                row.createCell(COL_BAL, CellType.FORMULA)
                   .setCellFormula("E" + (excelRow - 1) + "+C" + excelRow + "-D" + excelRow);
            } else {
                row.createCell(COL_BAL, CellType.NUMERIC).setCellValue(t.getBalance());
            }

            String cls = t.getClassification() != null ? t.getClassification() : "";
            row.createCell(COL_CLASS, CellType.STRING).setCellValue(cls);

            nextRow++;
        }
    }

    // ── Write cross-sheet formulas to Ledger ─────────────────────────────────

    /**
     * Reads all account sheets and writes two types of formulas into the Ledger:
     *
     *   1. Category columns (I–V): sum of debit/credit refs for that date + category
     *      e.g.  -ELFCU!C5 + -Chase!C12
     *
     *   2. Account balance columns (C–F): balance from the last transaction on that date
     *      e.g.  ELFCU!E5
     *
     * Existing Ledger formulas for a cell are appended to (not replaced) so that
     * multiple accounts can contribute to the same category cell.
     */
    public void writeFormulasToLedger() {
        Sheet ledger = workbook.getSheet("2026 Ledger");
        if (ledger == null) {
            logger.error("'2026 Ledger' sheet not found — cannot write formulas.");
            return;
        }

        for (Map.Entry<String, Integer> entry : ACCOUNT_LEDGER_COLS.entrySet()) {
            String accountName     = entry.getKey();
            int    accountLedgerCol = entry.getValue();
            Sheet  accountSheet    = workbook.getSheet(accountName);

            if (accountSheet == null) {
                logger.warn("Account sheet '{}' not found — skipping.", accountName);
                continue;
            }

            String escapedName = accountName.contains(" ")
                ? "'" + accountName + "'"
                : accountName;

            // Collect (date → category → list of cell refs) and (date → last row index)
            Map<LocalDate, Map<String, List<String>>> categoryRefs = new HashMap<>();
            Map<LocalDate, Integer>                   lastRowForDate = new HashMap<>();

            for (int rowIdx = 2; rowIdx <= accountSheet.getLastRowNum(); rowIdx++) {
                Row row = accountSheet.getRow(rowIdx);
                if (row == null) continue;

                Cell dateCell  = row.getCell(COL_DATE);
                Cell classCell = row.getCell(COL_CLASS);
                Cell debitCell = row.getCell(COL_DEBIT);
                Cell creditCell= row.getCell(COL_CREDIT);

                if (dateCell == null || dateCell.getCellType() != CellType.STRING) continue;

                LocalDate date;
                try {
                    date = LocalDate.parse(dateCell.getStringCellValue().trim());
                } catch (Exception e) {
                    continue;
                }

                // Track last row per date for the balance formula
                lastRowForDate.put(date, rowIdx);

                if (classCell == null || classCell.getCellType() != CellType.STRING) continue;
                String category = classCell.getStringCellValue().trim();
                if (category.isEmpty()) continue;

                boolean isDebit  = debitCell  != null && debitCell.getCellType()  == CellType.NUMERIC
                                   && debitCell.getNumericCellValue() > 0;
                boolean isCredit = creditCell != null && creditCell.getCellType() == CellType.NUMERIC
                                   && creditCell.getNumericCellValue() > 0;

                String ref;
                if (isDebit) {
                    ref = "-" + escapedName + "!C" + (rowIdx + 1); // negate debit
                } else if (isCredit) {
                    ref = escapedName + "!D" + (rowIdx + 1);
                } else {
                    continue;
                }

                categoryRefs
                    .computeIfAbsent(date, k -> new HashMap<>())
                    .computeIfAbsent(category, k -> new ArrayList<>())
                    .add(ref);
            }

            // Write category formulas to Ledger
            categoryRefs.forEach((date, catMap) -> {
                int ledgerRow = findRowForDate(ledger, date);
                if (ledgerRow == -1) {
                    logger.warn("No Ledger row for date {} (account {})", date, accountName);
                    return;
                }
                Row row = getOrCreateRow(ledger, ledgerRow);
                catMap.forEach((category, refs) -> {
                    int colIdx = findColumnForCategory(ledger, category);
                    if (colIdx == -1) {
                        logger.warn("No Ledger column for category '{}' — skipping.", category);
                        return;
                    }
                    Cell cell = row.getCell(colIdx, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                    String newFormula = String.join(" + ", refs);
                    // Append to any existing formula from another account
                    if (cell.getCellType() == CellType.FORMULA) {
                        newFormula = cell.getCellFormula() + " + " + newFormula;
                    }
                    cell.setCellFormula(newFormula);
                    logger.debug("Ledger[{},{}] = {}", ledgerRow, colIdx, newFormula);
                });
            });

            // Write account balance formula (last transaction balance for each date)
            lastRowForDate.forEach((date, rowIdx) -> {
                int ledgerRow = findRowForDate(ledger, date);
                if (ledgerRow == -1) return;
                Row row = getOrCreateRow(ledger, ledgerRow);
                Cell cell = row.getCell(accountLedgerCol, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                cell.setCellFormula(escapedName + "!E" + (rowIdx + 1));
            });

            logger.info("Wrote Ledger formulas for account '{}'", accountName);
        }
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    public void saveWorkbook() {
        try (FileOutputStream fos = new FileOutputStream(config.getOutputPath())) {
            workbook.write(fos);
            workbook.close();
            logger.info("Workbook saved to {}", config.getOutputPath());
        } catch (IOException e) {
            logger.error("Error saving workbook: ", e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Returns the first row index >= startRow that has no value in column A. */
    private int findFirstEmptyRow(Sheet sheet, int startRow) {
        int idx = startRow;
        while (idx <= sheet.getLastRowNum()) {
            Row row = sheet.getRow(idx);
            if (row == null) return idx;
            Cell cell = row.getCell(COL_DATE);
            if (cell == null || cell.getCellType() == CellType.BLANK
                    || cell.toString().trim().isEmpty()) return idx;
            idx++;
        }
        return idx;
    }

    /**
     * Finds the Ledger row index for a given date.
     * Expects column B (index 1) to contain Excel date serials.
     */
    private int findRowForDate(Sheet ledger, LocalDate date) {
        for (Row row : ledger) {
            Cell cell = row.getCell(1);
            if (cell != null && cell.getCellType() == CellType.NUMERIC
                    && DateUtil.isCellDateFormatted(cell)) {
                if (cell.getLocalDateTimeCellValue().toLocalDate().equals(date)) {
                    return row.getRowNum();
                }
            }
        }
        return -1;
    }

    /** Finds the Ledger column index for a given category name (case-insensitive). */
    private int findColumnForCategory(Sheet ledger, String category) {
        Row headerRow = ledger.getRow(0);
        if (headerRow == null) return -1;
        for (Cell cell : headerRow) {
            if (cell.getCellType() == CellType.STRING
                    && cell.getStringCellValue().trim().equalsIgnoreCase(category.trim())) {
                return cell.getColumnIndex();
            }
        }
        return -1;
    }

    private Row getOrCreateRow(Sheet sheet, int rowIndex) {
        Row row = sheet.getRow(rowIndex);
        return row != null ? row : sheet.createRow(rowIndex);
    }
}
