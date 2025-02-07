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

    public Map<LocalDate, Map<String, List<String>>> collectTransactionRefs(Sheet sheet, int startRow, int lastRow) {
        Map<LocalDate, Map<String, List<String>>> transactionMappings = new HashMap<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        int columnIndex = 19;

        List<String> categories = Config.getInstance()
                .getCategoryList();

        logger.info("Reading transactions from Excel up to row {}...",
                    lastRow);

        for (int rowIndex = startRow; rowIndex <= lastRow; rowIndex++) {
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

        for (int rowIndex = startRow; rowIndex <= lastRow; rowIndex++) {
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



}