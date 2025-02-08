package com.noslen.transaction_reader.io;

import com.noslen.transaction_reader.config.Config;
import com.noslen.transaction_reader.model.Transaction;
import com.noslen.transaction_reader.service.CliService;
import com.noslen.transaction_reader.service.TransactionMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class ExcelFileWriter {

    private static final Logger logger = LogManager.getLogger(ExcelFileWriter.class);

    private final String outputFilePath;

    private final String initialFilePath;

    private final Config config;

    private final CliService cliService;
    private final TransactionMapper mapper;
    private Workbook workbook;
    private Sheet sheet;
    private int startRowIndex;
    private int lastRowIndex;

    public ExcelFileWriter(CliService cliService, TransactionMapper mapper) {
        this.outputFilePath = Config.getInstance()
                .getOutputPath();
        this.initialFilePath = Config.getInstance()
                .getInitialExcelPath();
        initializeWorkbook();
        this.cliService = cliService;
        this.config = Config.getInstance();
        this.mapper = mapper;
    }


    private void initializeWorkbook() {
        try {
            File file = new File(initialFilePath);
            if (file.exists()) {
                FileInputStream fis = new FileInputStream(file);
                workbook = new XSSFWorkbook(fis);
                sheet = workbook.getSheetAt(0);
                fis.close();
            } else {
                workbook = new XSSFWorkbook();
                sheet = workbook.createSheet("2025");
            }
        } catch (IOException e) {
            logger.error("Error initializing Excel file: ",
                         e);
        }
    }

    public void categorizeTransactions(List<Transaction> transactions) {
        for (Transaction transaction : transactions) {
            String category = cliService.promptForCategory(transaction,
                                                           config.getCategoryList());
            transaction.setClassification(category);
        }
    }

    public void appendTransactionsToTable(List<Transaction> transactions) {
        int startColumn = 19; // Column T
        int nextRow = findFirstEmptyCellInColumn(startColumn);
        this.startRowIndex = nextRow;
        transactions.sort(Comparator.comparing(Transaction::getPostDate));
        for (Transaction transaction : transactions) {
            Row row = sheet.getRow(nextRow);
            if (row == null) row = sheet.createRow(nextRow);
            row.createCell(startColumn,
                           CellType.STRING)
                    .setCellValue(transaction.getPostDate()
                                          .toString());
            row.createCell(startColumn + 2,
                           CellType.STRING)
                    .setCellValue(transaction.getDescription());
            row.createCell(startColumn + 3,
                           CellType.NUMERIC)
                    .setCellValue(transaction.getDebit());
            row.createCell(startColumn + 4,
                           CellType.NUMERIC)
                    .setCellValue(transaction.getCredit());
            row.createCell(startColumn + 6,
                           CellType.NUMERIC)
                    .setCellValue(transaction.getBalance());
            row.createCell(startColumn + 7,
                           CellType.STRING)
                    .setCellValue(transaction.getClassification());

            nextRow++;
        }
        this.lastRowIndex = nextRow;
    }

    public void writeTransactionMapToTable() {
        Map<LocalDate, Map<String, List<String>>> transactionUpdates =
                mapper.collectTransactionRefs(sheet, startRowIndex, lastRowIndex);

        transactionUpdates.forEach((date, categoryMap) -> {
            final int rowIndex = findRowForDate(date);

            if (rowIndex == -1) {
                logger.warn("No matching row found for date: {}", date);
                return;
            }

            Row row = sheet.getRow(rowIndex);
            if (row == null) row = sheet.createRow(rowIndex);

            final Row finalRow = row;
            categoryMap.forEach((category, references) -> {
                final int columnIndex = findColumnForCategory(category);

                if (columnIndex == -1) {
                    logger.warn("No matching column found for category: {}", category);
                    return;
                }

                Cell cell = finalRow.getCell(columnIndex, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                if (!references.isEmpty()) {
                    String refs = String.join(" + ", references);
                    cell.setCellFormula(refs);
                    logger.debug("Set formula '{}' at row {} column {}", refs, rowIndex, columnIndex);
                } else {
                    logger.warn("No references found for date {} in category {}", date, category);
                }
            });
        });
    }

    private int findFirstEmptyCellInColumn(int columnIndex) {
        int rowIndex = 1;

        while (rowIndex <= sheet.getLastRowNum()) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                return rowIndex;
            }

            Cell cell = row.getCell(columnIndex);
            if (cell == null || cell.getCellType() == CellType.BLANK || cell.toString()
                    .trim()
                    .isEmpty()) {
                break;
            }
            rowIndex++;
        }
        return rowIndex - 1;
    }

    private int findRowForDate(LocalDate date) {
        for (Row row : sheet) {
            Cell cell = row.getCell(1); // Column B (index 1) contains dates
            if (cell != null && cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                LocalDate rowDate = cell.getLocalDateTimeCellValue()
                        .toLocalDate();
                if (rowDate.equals(date)) {
                    return row.getRowNum();
                }
            }
        }
        return -1; // Not found
    }

    private int findColumnForCategory(String category) {
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) return -1;

        for (Cell cell : headerRow) {
            if (cell.getCellType() == CellType.STRING && cell.getStringCellValue()
                    .trim()
                    .equalsIgnoreCase(category.trim())) {
                return cell.getColumnIndex();
            }
        }
        return -1;
    }

    public void saveWorkbook() {
        try (FileOutputStream fos = new FileOutputStream(outputFilePath)) {
            workbook.write(fos);
            workbook.close();
            logger.info("Excel file saved successfully: {}",
                        outputFilePath);
        } catch (IOException e) {
            logger.error("Error saving Excel file: ",
                         e);
        }
    }

    public Sheet getSheet() {
        return sheet;
    }

//    public void mapTransactionsToCategories(Sheet sheet, List<Transaction> transactions) {
//        Map<LocalDate, Map<String, List<Integer>>> categoryReferences = new HashMap<>();
//
//        for (Transaction transaction : transactions) {
//            LocalDate date = transaction.getPostDate();
//            String category = transaction.getClassification();
//            int rowIndex = findRowForDate(sheet,
//                                          date);
//            int categoryColumnIndex = findColumnForCategory(sheet,
//                                                            category);
//
//            if (rowIndex == -1 || categoryColumnIndex == -1) continue;
//
//            String referenceColumn = transaction.getDebit() > 0 ? "W" : "X";
//            int excelRowIndex = rowIndex + 1;
//
//            categoryReferences.computeIfAbsent(date,
//                                               k -> new HashMap<>())
//                    .computeIfAbsent(category,
//                                     k -> new ArrayList<>())
//                    .add(excelRowIndex);
//        }
//
//        // Sum formulas per (date, category)
//        for (Map.Entry<LocalDate, Map<String, List<Integer>>> dateEntry : categoryReferences.entrySet()) {
//            for (Map.Entry<String, List<Integer>> categoryEntry : dateEntry.getValue()
//                    .entrySet()) {
//                int rowIndex = findRowForDate(sheet,
//                                              dateEntry.getKey());
//                int categoryColumnIndex = findColumnForCategory(sheet,
//                                                                categoryEntry.getKey());
//
//                String sumFormula = categoryEntry.getValue()
//                        .stream()
//                        .map(rowNum -> "-W" + rowNum)
//                        .collect(Collectors.joining(" + "));
//
//                Row row = sheet.getRow(rowIndex);
//                if (row == null) row = sheet.createRow(rowIndex);
//                Cell cell = row.createCell(categoryColumnIndex);
//                cell.setCellFormula(sumFormula);
//            }
//        }
//    }

//    private void updateCellWithFormula(Sheet, int rowIndex, int columnIndex, double amount) {
//        Row row = sheet.getRow(rowIndex);
//        if (row == null) row = sheet.createRow(rowIndex);
//
//        Cell cell = row.getCell(columnIndex,
//                                Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
//
//        String referenceColumn = amount < 0 ? "Z" : "AA"; // W for debits, X for credits
//        int excelRowIndex = rowIndex + 1; // Excel rows are 1-based
//
//        String newFormula = referenceColumn + excelRowIndex;
//
//        // If the cell already has a formula, append the new reference
//        if (cell.getCellType() == CellType.FORMULA) {
//            newFormula = cell.getCellFormula() + " + " + newFormula;
//        }
//
//        cell.setCellFormula(newFormula);
//    }
}
