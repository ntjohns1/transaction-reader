package com.noslen.transaction_reader.config;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class Config {
//    *** Example Usage: ***
//    String inputFile = Config.getInstance().getInputPath();

    private static Config instance;
    private final String inputPath;
    private final String outputPath;
    private final String categoryFile;
    private final String initialExcelPath;


    private Config() {
        this.inputPath = System.getenv("INPUT_FILE");
        this.outputPath = System.getenv("OUTPUT_FILE");
        this.initialExcelPath = System.getenv("BUDGET_EXCEL_FILE");
        this.categoryFile = System.getenv("CATEGORY_FILE");

        if (inputPath == null || outputPath == null || initialExcelPath == null || categoryFile ==null) {
            throw new IllegalStateException("Missing required environment variables!");
        }
    }

    public static Config getInstance() {
        if (instance == null) {
            instance = new Config();
        }
        return instance;
    }

    public String getInputPath() {
        return inputPath;
    }

    public String getOutputPath() {
        return outputPath;
    }

    public String getInitialExcelPath() {
        return initialExcelPath;
    }


    public Map<String, String> loadCategoryMappings() {
        Map<String, String> categoryMap = new HashMap<>();
        Properties properties = new Properties();

        // Try loading from category file
        try (FileInputStream fis = new FileInputStream(categoryFile)) {
            properties.load(fis);
            for (String key : properties.stringPropertyNames()) {
                categoryMap.put(key, properties.getProperty(key));
            }
        } catch (IOException ignored) {}

        // If properties are empty, read from Excel
        if (categoryMap.isEmpty()) {
            categoryMap = loadCategoriesFromExcel();
            saveCategoryMappings(categoryMap);
        }

        return categoryMap;
    }

    private Map<String, String> loadCategoriesFromExcel() {
        Map<String, String> categoryMap = new HashMap<>();

        try (FileInputStream fis = new FileInputStream(initialExcelPath);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0); // Assuming first sheet is used
            Row headerRow = sheet.getRow(0); // Row 1 (Index 0)

            if (headerRow != null) {
                for (int i = 1; i <= 17; i++) { // Columns B to R
                    Cell cell = headerRow.getCell(i);
                    if (cell != null && cell.getCellType() == CellType.STRING) {
                        String category = cell.getStringCellValue().trim();
                        categoryMap.put(category, category); // Store category itself
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Error loading categories from Excel.");
        }

        return categoryMap;
    }

    public void saveCategoryMappings(Map<String, String> categoryMap) {
        Properties properties = new Properties();
        properties.putAll(categoryMap);

        try (FileOutputStream fos = new FileOutputStream(categoryFile)) {
            properties.store(fos, "Transaction Category Mappings");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public List<String> getCategoryList() {
        return loadCategoryMappings().keySet().stream().toList();
    }
}