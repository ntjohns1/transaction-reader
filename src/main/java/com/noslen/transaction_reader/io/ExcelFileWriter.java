package com.noslen.transaction_reader.io;

import com.noslen.transaction_reader.config.Config;
import com.noslen.transaction_reader.model.Transaction;
import com.noslen.transaction_reader.service.CliService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ExcelFileWriter {

    private static final Logger logger = LogManager.getLogger(ExcelFileWriter.class);

    private final String outputFilePath;

    private final String initialFilePath;

    private final Config config;

    private final CliService cliService;
    private Workbook workbook;
    private Sheet sheet;
    private int startRowIndex;

    public ExcelFileWriter(CliService cliService) {
        this.outputFilePath = Config.getInstance()
                .getOutputPath();
        this.initialFilePath = Config.getInstance()
                .getInitialExcelPath();
        initializeWorkbook();
        this.cliService = cliService;
        this.config = Config.getInstance();
    }


    private void initializeWorkbook() {
        try {
            File file = new File(initialFilePath);
            if (file.exists()) {
                FileInputStream fis = new FileInputStream(file);
                workbook = new XSSFWorkbook(fis);
                sheet = workbook.getSheetAt(0); // Adjust index if needed
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
            String category = cliService.promptForCategory(transaction, config.getCategoryList());
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

            row.createCell(startColumn)
                    .setCellValue(transaction.getPostDate()
                                          .toString());
            row.createCell(startColumn + 2)
                    .setCellValue(transaction.getDescription());
            row.createCell(startColumn + 3)
                    .setCellValue(transaction.getDebit());
            row.createCell(startColumn + 4)
                    .setCellValue(transaction.getCredit());
            row.createCell(startColumn + 6)
                    .setCellValue(transaction.getBalance());
            row.createCell(startColumn + 7)
                    .setCellValue(transaction.getClassification());

            nextRow++;
        }
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

    public Map<LocalDate, Map<String, List<String>>> collectTransactionRefs() {
        Map<LocalDate, Map<String, List<String>>> transactionMappings = new HashMap<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        int columnIndex = 19;
        int lastRow = findFirstEmptyCellInColumn(columnIndex);

        List<String> categories = Config.getInstance()
                .getCategoryList();

        logger.info("Reading transactions from Excel up to row {}...",
                    lastRow);

        for (int rowIndex = startRowIndex; rowIndex <= lastRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;

            Cell dateCell = row.getCell(columnIndex);

            if (dateCell != null && dateCell.getCellType() == CellType.STRING) {
                try {
                    LocalDate postDate = LocalDate.parse(dateCell.getStringCellValue(),
                                                         formatter);
                    transactionMappings.putIfAbsent(postDate,
                                                    new HashMap<>());

//                    for (String category : categories) {
//                        transactionMappings.get(postDate).put(category, new ArrayList<>());
//                    }
                    categories.forEach(c -> transactionMappings.get(postDate)
                            .put(c,
                                 new ArrayList<>()));
                    logger.debug("Mapped transaction date: {} with empty categories",
                                 postDate);
                    transactionMappings.entrySet()
                            .forEach(e -> logger.info(e.toString()));
                } catch (Exception e) {
                    logger.warn("Skipping invalid date format in row {}: {}",
                                rowIndex,
                                dateCell.getStringCellValue());
                }
            }
        }

        for (int rowIndex = startRowIndex; rowIndex <= lastRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            Cell dateCell = row.getCell(columnIndex);
            Cell categoryCell = row.getCell(columnIndex + 7);
            boolean isDebit = row.getCell(columnIndex).getNumericCellValue() > 0;

            if (dateCell != null && dateCell.getCellType() == CellType.STRING) {
                try {
                    LocalDate postDate = LocalDate.parse(dateCell.getStringCellValue(),
                                                         formatter);
                    Map<String, List<String>> dateCategories = transactionMappings.get(postDate);
                    List<String> cellRefs = dateCategories.get(categoryCell.getStringCellValue());
                    String ref = isDebit ? "-W" + rowIndex + 1 : "X" + (rowIndex + 1);
                    cellRefs.add(ref);
                } catch (Exception e) {
                    logger.warn("Skipping invalid date format in row {}: {}",
                                rowIndex,
                                dateCell.getStringCellValue());
                }
            }

        }

        return transactionMappings;


    }




    private int findRowForDate(Sheet sheet, LocalDate date) {
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

    /**
     * Update the cell with an Excel formula referencing the debit/credit column.
     * - Debits use `=-W2`
     * - Credits use `=X3`
     */
//    private void updateCellWithFormula(Sheet sheet, int rowIndex, int columnIndex, double amount) {
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
