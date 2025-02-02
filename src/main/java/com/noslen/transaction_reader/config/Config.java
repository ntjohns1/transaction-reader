package com.noslen.transaction_reader.config;

public class Config {
//    *** Example Usage: ***
//    String inputFile = Config.getInstance().getInputPath();

    private static Config instance;
    private final String inputPath;
    private final String outputPath;
    private final String initialExcelPath;

    private Config() {
        this.inputPath = System.getenv("INPUT_FILE");
        this.outputPath = System.getenv("OUTPUT_FILE");
        this.initialExcelPath = System.getenv("BUDGET_EXCEL_FILE");

        if (inputPath == null || outputPath == null || initialExcelPath == null) {
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
}