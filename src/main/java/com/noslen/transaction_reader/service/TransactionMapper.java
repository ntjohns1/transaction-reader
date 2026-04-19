package com.noslen.transaction_reader.service;

import com.noslen.transaction_reader.config.Config;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TransactionMapper {

    private static final Logger logger = LogManager.getLogger(TransactionMapper.class);

    public Map<LocalDate, Map<String, List<String>>> collectTransactionRefs(Sheet sheet, int lastRow) {
        Map<LocalDate, Map<String, List<String>>> transactionMappings = new HashMap<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        int columnIndex = 19;

        for (int rowIndex = 2; rowIndex <= lastRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;

            Cell dateCell = row.getCell(columnIndex);
            Cell categoryCell = row.getCell(columnIndex + 7); // Category classification column
            Cell debitCell = row.getCell(columnIndex + 3); // Debit (W)
            Cell creditCell = row.getCell(columnIndex + 4); // Credit (X)

            if (dateCell != null && dateCell.getCellType() == CellType.STRING) {
                try {
                    LocalDate postDate = LocalDate.parse(dateCell.getStringCellValue(), formatter);

                    // ✅ Check if categoryCell is null before accessing
                    if (categoryCell == null || categoryCell.getCellType() != CellType.STRING) {
                        logger.warn("Skipping row {}: Category cell is missing or not a string.", rowIndex);
                        continue;
                    }

                    String category = categoryCell.getStringCellValue().trim();

                    // ✅ Ensure we have a valid category mapping for this date
                    transactionMappings.putIfAbsent(postDate, new HashMap<>());
                    Map<String, List<String>> dateCategories = transactionMappings.get(postDate);
                    dateCategories.putIfAbsent(category, new ArrayList<>());

                    // ✅ Determine if it's a debit or credit transaction
                    boolean isDebit = debitCell != null && debitCell.getCellType() == CellType.NUMERIC && debitCell.getNumericCellValue() > 0;
                    boolean isCredit = creditCell != null && creditCell.getCellType() == CellType.NUMERIC && creditCell.getNumericCellValue() > 0;

                    String ref;
                    if (isDebit) {
                        ref = "-" + "W" + (rowIndex + 1); // ✅ Excel rows are 1-based
                    } else if (isCredit) {
                        ref = "X" + (rowIndex + 1);
                    } else {
                        logger.warn("Skipping row {}: No valid debit or credit found.", rowIndex);
                        continue;
                    }

                    // ✅ Add reference to the transactionMappings
                    dateCategories.get(category).add(ref);
                    logger.debug("Added cell reference {} for date {} in category {}", ref, postDate, category);

                } catch (Exception e) {
                    logger.warn("Skipping invalid date format in row {}: {}", rowIndex, dateCell.getStringCellValue());
                }
            }
        }


        return transactionMappings;


    }

//     for (int rowIndex = startRow; rowIndex <= lastRow; rowIndex++) {
//        Row row = sheet.getRow(rowIndex);
//        if (row == null) continue;
//
//        Cell dateCell = row.getCell(columnIndex);
//
//        if (dateCell != null && dateCell.getCellType() == CellType.STRING) {
//            try {
//                LocalDate postDate = LocalDate.parse(dateCell.getStringCellValue(),
//                                                     formatter);
//                transactionMappings.putIfAbsent(postDate,
//                                                new HashMap<>());
//
////                    for (String category : categories) {
////                        transactionMappings.get(postDate).put(category, new ArrayList<>());
////                    }
//                categories.forEach(c -> transactionMappings.get(postDate)
//                        .put(c,
//                             new ArrayList<>()));
//                logger.debug("Mapped transaction date: {} with empty categories",
//                             postDate);
//                transactionMappings.entrySet()
//                        .forEach(e -> logger.info(e.toString()));
//            } catch (Exception e) {
//                logger.warn("Skipping invalid date format in row {}: {}",
//                            rowIndex,
//                            dateCell.getStringCellValue());
//            }
//        }
//    }
//
//        for (int rowIndex = startRow; rowIndex <= lastRow; rowIndex++) {
//        Row row = sheet.getRow(rowIndex);
//        Cell dateCell = row.getCell(columnIndex);
//        Cell categoryCell = row.getCell(columnIndex + 7);
//        boolean isDebit = row.getCell(columnIndex).getNumericCellValue() > 0;
//
//        if (dateCell != null && dateCell.getCellType() == CellType.STRING) {
//            try {
//                LocalDate postDate = LocalDate.parse(dateCell.getStringCellValue(),
//                                                     formatter);
//                Map<String, List<String>> dateCategories = transactionMappings.get(postDate);
//                List<String> cellRefs = dateCategories.get(categoryCell.getStringCellValue());
//                String ref = isDebit ? "-W" + rowIndex + 1 : "X" + (rowIndex + 1);
//                cellRefs.add(ref);
//            } catch (Exception e) {
//                logger.warn("Skipping invalid date format in row {}: {}",
//                            rowIndex,
//                            dateCell.getStringCellValue());
//            }
//        }


}