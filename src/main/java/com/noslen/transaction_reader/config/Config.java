package com.noslen.transaction_reader.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Reads configuration from {@code config.properties} in the working directory,
 * with environment variables taking precedence over file values.
 *
 * Copy {@code config.properties.template} → {@code config.properties} and fill in
 * your real paths. The {@code config.properties} file is gitignored — never commit
 * it as it contains paths to personal financial data.
 */
public class Config {

    private static final Logger logger = LogManager.getLogger(Config.class);
    private static Config instance;

    // Paths
    private final String initialExcelPath;
    private final String outputPath;
    private final String statementsDir;
    private final String merchantsPath;

    // Opening balances
    private final double openingBalanceElfcu;
    private final double openingBalanceChase;
    private final double openingBalanceCc1;
    private final double openingBalanceCc2;

    // Category list (expense categories only — matches Ledger header columns I–V)
    private final List<String> categoryList;

    private Config() {
        Properties props = loadPropertiesFile();

        this.initialExcelPath = require(props, "BUDGET_EXCEL_FILE",    "budget.excel.path");
        this.outputPath       = get(props,     "OUTPUT_FILE",           "budget.output.path", initialExcelPath);
        this.statementsDir    = require(props, "STATEMENTS_DIR",        "budget.statements.dir");
        this.merchantsPath    = require(props, "MERCHANTS_FILE",        "budget.merchants.path");

        this.openingBalanceElfcu  = getDouble(props, "OPENING_BALANCE_ELFCU",  "budget.opening.balance.elfcu",  178.33);
        this.openingBalanceChase  = getDouble(props, "OPENING_BALANCE_CHASE",  "budget.opening.balance.chase",  0.00);
        this.openingBalanceCc1    = getDouble(props, "OPENING_BALANCE_CC1",    "budget.opening.balance.cc1",    3412.49);
        this.openingBalanceCc2    = getDouble(props, "OPENING_BALANCE_CC2",    "budget.opening.balance.cc2",    165.35);

        String cats = get(props, "BUDGET_CATEGORIES", "budget.categories",
                "Bills/Rent,Groceries,Restaurants,Car/Gas,Subscriptions,Entertainment," +
                "Tech,Health/Fitness,Medical,Dispensary,Pets,Investments,Merchandise,Other");
        this.categoryList = Arrays.asList(cats.split(","));
    }

    public static Config getInstance() {
        if (instance == null) instance = new Config();
        return instance;
    }

    // ── accessors ────────────────────────────────────────────────────────────

    public String getInitialExcelPath()  { return initialExcelPath; }
    public String getOutputPath()        { return outputPath; }
    public String getStatementsDir()     { return statementsDir; }
    public String getMerchantsPath()     { return merchantsPath; }

    public Map<String, Double> getOpeningBalances() {
        return Map.of(
            "ELFCU", openingBalanceElfcu,
            "Chase", openingBalanceChase,
            "CC 1",  openingBalanceCc1,
            "CC 2",  openingBalanceCc2
        );
    }

    public List<String> getCategoryList() { return categoryList; }

    // Keep for backward compat with CliService
    public String getInputPath() { return statementsDir; }

    // ── internals ────────────────────────────────────────────────────────────

    private Properties loadPropertiesFile() {
        Properties props = new Properties();
        File configFile = new File("config.properties");
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                props.load(fis);
                logger.info("Loaded config.properties from working directory.");
            } catch (IOException e) {
                logger.warn("Could not read config.properties: {}", e.getMessage());
            }
        } else {
            logger.info("No config.properties found — relying on environment variables.");
        }
        return props;
    }

    /** env var takes precedence; then props file; then throws if missing. */
    private String require(Properties props, String envKey, String propKey) {
        String val = get(props, envKey, propKey, null);
        if (val == null) {
            throw new IllegalStateException(
                "Missing required config: set env var '" + envKey +
                "' or add '" + propKey + "' to config.properties");
        }
        return val;
    }

    private String get(Properties props, String envKey, String propKey, String defaultValue) {
        String envVal = System.getenv(envKey);
        if (envVal != null && !envVal.isBlank()) return envVal;
        String propVal = props.getProperty(propKey);
        if (propVal != null && !propVal.isBlank()) return propVal;
        return defaultValue;
    }

    private double getDouble(Properties props, String envKey, String propKey, double defaultValue) {
        String raw = get(props, envKey, propKey, null);
        if (raw == null) return defaultValue;
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            logger.warn("Invalid double for {} / {}: '{}', using default {}", envKey, propKey, raw, defaultValue);
            return defaultValue;
        }
    }
}
