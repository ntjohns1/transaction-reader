package com.noslen.transaction_reader.io;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.time.LocalDate;
import java.util.*;

public class ExcelFileWriter {

    private final String budgetFilePath;

    public ExcelFileWriter(String budgetFilePath) {
        this.budgetFilePath = budgetFilePath;
    }

    public void updateExcel(Map<LocalDate, Map<String, Double>> mappedTransactions, String outputFilePath) {
        try (FileInputStream fis = new FileInputStream(budgetFilePath);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheet("2025"); // Assuming we're updating the 2025 sheet

            for (Map.Entry<LocalDate, Map<String, Double>> entry : mappedTransactions.entrySet()) {
                LocalDate transactionDate = entry.getKey();
                Map<String, Double> categoryAmounts = entry.getValue();

                int rowIndex = findRowForDate(sheet, transactionDate);
                if (rowIndex == -1) {
                    System.err.println("No matching row for date: " + transactionDate);
                    continue;
                }

                for (Map.Entry<String, Double> categoryEntry : categoryAmounts.entrySet()) {
                    String category = categoryEntry.getKey();
                    double amount = categoryEntry.getValue();

                    int columnIndex = findColumnForCategory(sheet, category);
                    if (columnIndex == -1) {
                        System.err.println("No matching column for category: " + category);
                        continue;
                    }

                    updateCellWithFormula(sheet, rowIndex, columnIndex, amount);
                }
            }

            // Save updated workbook
            try (FileOutputStream fos = new FileOutputStream(outputFilePath)) {
                workbook.write(fos);
                System.out.println("Budget file updated successfully: " + outputFilePath);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private int findRowForDate(Sheet sheet, LocalDate date) {
        for (Row row : sheet) {
            Cell cell = row.getCell(1); // Column B (index 1) contains dates
            if (cell != null && cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                LocalDate rowDate = cell.getLocalDateTimeCellValue().toLocalDate();
                if (rowDate.equals(date)) {
                    return row.getRowNum();
                }
            }
        }
        return -1; // Not found
    }

    private int findColumnForCategory(Sheet sheet, String category) {
        Row headerRow = sheet.getRow(0); // Assuming categories are in the first row
        for (Cell cell : headerRow) {
            if (cell.getCellType() == CellType.STRING && cell.getStringCellValue().equalsIgnoreCase(category)) {
                return cell.getColumnIndex();
            }
        }
        return -1; // Not found
    }

    /**
     * Update the cell with an Excel formula referencing the debit/credit column.
     * - Debits use `=-W2`
     * - Credits use `=X3`
     */
    private void updateCellWithFormula(Sheet sheet, int rowIndex, int columnIndex, double amount) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) row = sheet.createRow(rowIndex);

        Cell cell = row.getCell(columnIndex, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);

        String referenceColumn = amount < 0 ? "W" : "X"; // W for debits, X for credits
        int excelRowIndex = rowIndex + 1; // Excel rows are 1-based

        String newFormula = "=" + referenceColumn + excelRowIndex;

        // If the cell already has a formula, append the new reference
        if (cell.getCellType() == CellType.FORMULA) {
            newFormula = cell.getCellFormula() + " + " + newFormula;
        }

        cell.setCellFormula(newFormula);
    }
}