package com.noslen.transaction_reader.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;;

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

    // Plaid
    private final boolean plaidEnabled;
    private final String plaidClientId;
    private final String plaidSecret;
    private final String plaidEnvironment;
    private final String plaidAccessTokenElfcu;
    private final String plaidAccessTokenChase;
    private final String plaidAccessTokenDiscover;
    private final String plaidAccountIdCc1;
    private final String plaidAccountIdCc2;
    private final int plaidDays;

    private Config() {
        Properties props = loadPropertiesFile();

        String plaidEnabledRaw = get(props, "PLAID_ENABLED", "plaid.enabled", "false");
        this.plaidEnabled = "true".equalsIgnoreCase(plaidEnabledRaw);

        this.initialExcelPath = require(props, "BUDGET_EXCEL_FILE",    "budget.excel.path");
        this.outputPath       = get(props,     "OUTPUT_FILE",           "budget.output.path", initialExcelPath);
        // statementsDir not required when Plaid is the data source
        this.statementsDir    = plaidEnabled
                ? get(props,     "STATEMENTS_DIR", "budget.statements.dir", null)
                : require(props, "STATEMENTS_DIR", "budget.statements.dir");
        this.merchantsPath    = require(props, "MERCHANTS_FILE",        "budget.merchants.path");

        this.openingBalanceElfcu  = getDouble(props, "OPENING_BALANCE_ELFCU",  "budget.opening.balance.elfcu",  178.33);
        this.openingBalanceChase  = getDouble(props, "OPENING_BALANCE_CHASE",  "budget.opening.balance.chase",  0.00);
        this.openingBalanceCc1    = getDouble(props, "OPENING_BALANCE_CC1",    "budget.opening.balance.cc1",    3412.49);
        this.openingBalanceCc2    = getDouble(props, "OPENING_BALANCE_CC2",    "budget.opening.balance.cc2",    165.35);

        String cats = get(props, "BUDGET_CATEGORIES", "budget.categories",
                "Bills/Rent,Groceries,Restaurants,Car/Gas,Subscriptions,Entertainment," +
                "Tech,Health/Fitness,Medical,Dispensary,Pets,Investments,Merchandise,Other");
        this.categoryList = Arrays.asList(cats.split(","));

        this.plaidClientId          = get(props, "PLAID_CLIENT_ID",           "plaid.client.id",               null);
        this.plaidSecret            = get(props, "PLAID_SECRET",              "plaid.secret",                  null);
        this.plaidEnvironment       = get(props, "PLAID_ENVIRONMENT",         "plaid.environment",             "sandbox");
        this.plaidAccessTokenElfcu  = get(props, "PLAID_ACCESS_TOKEN_ELFCU",  "plaid.access.token.elfcu",      null);
        this.plaidAccessTokenChase  = get(props, "PLAID_ACCESS_TOKEN_CHASE",  "plaid.access.token.chase",      null);
        this.plaidAccessTokenDiscover = get(props, "PLAID_ACCESS_TOKEN_DISCOVER", "plaid.access.token.discover", null);
        this.plaidAccountIdCc1      = get(props, "PLAID_ACCOUNT_ID_CC1",      "plaid.account.id.cc1",          null);
        this.plaidAccountIdCc2      = get(props, "PLAID_ACCOUNT_ID_CC2",      "plaid.account.id.cc2",          null);
        this.plaidDays              = (int) getDouble(props, "PLAID_DAYS",    "plaid.days",                    30.0);

        if (plaidEnabled) {
            requireNotNull(plaidClientId, "PLAID_CLIENT_ID",  "plaid.client.id");
            requireNotNull(plaidSecret,   "PLAID_SECRET",     "plaid.secret");
        }
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

    public boolean isPlaidEnabled()      { return plaidEnabled; }
    public String getPlaidClientId()     { return plaidClientId; }
    public String getPlaidSecret()       { return plaidSecret; }
    public String getPlaidEnvironment()  { return plaidEnvironment; }
    public int getPlaidDays()            { return plaidDays; }

    /** Returns a label→accessToken map for each configured institution. */
    public Map<String, String> getPlaidAccessTokens() {
        Map<String, String> tokens = new LinkedHashMap<>();
        if (plaidAccessTokenElfcu     != null) tokens.put("ELFCU",    plaidAccessTokenElfcu);
        if (plaidAccessTokenChase     != null) tokens.put("Chase",    plaidAccessTokenChase);
        if (plaidAccessTokenDiscover  != null) tokens.put("Discover", plaidAccessTokenDiscover);
        return tokens;
    }

    /** Returns a Plaid accountId→our account name map for disambiguation (e.g. two Discover cards). */
    public Map<String, String> getPlaidAccountIdMap() {
        Map<String, String> map = new HashMap<>();
        if (plaidAccountIdCc1 != null) map.put(plaidAccountIdCc1, "CC 1");
        if (plaidAccountIdCc2 != null) map.put(plaidAccountIdCc2, "CC 2");
        return map;
    }

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

    private void requireNotNull(String value, String envKey, String propKey) {
        if (value == null) {
            throw new IllegalStateException(
                "Missing required Plaid config: set env var '" + envKey +
                "' or add '" + propKey + "' to config.properties");
        }
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
