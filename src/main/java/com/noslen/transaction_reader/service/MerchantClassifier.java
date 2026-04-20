package com.noslen.transaction_reader.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * Classifies transaction descriptions into budget categories.
 *
 * Lookup order (first match wins):
 *   1. Shortcut   — exact prefix patterns (ATM, etc.)
 *   2. Keywords   — substring match against merchants["keywords"] map
 *   3. Merchants  — exact key match after normalizing the description
 *   4. CLI        — prompt the user via CliService; result saved for future runs
 *
 * merchants.json format:
 * {
 *   "merchants": { "trader joes": "Groceries", ... },
 *   "keywords":  { "netflix": "Subscriptions", "planet fitness": "Health/Fitness", ... }
 * }
 *
 * The file is loaded once and updated in memory; call {@link #save()} at the end of a
 * run to persist new entries learned from the CLI.
 */
public class MerchantClassifier {

    private static final Logger logger = LogManager.getLogger(MerchantClassifier.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ── Normalization pipeline ────────────────────────────────────────────────

    private static final Pattern PAYMENT_PREFIXES = Pattern.compile(
        "^(sq \\*|tst\\*|sp \\*|paypal \\*|venmo\\s*|zelle\\s*|apple pay \\*|apple\\.com/bill\\s*)",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern LONG_NUMBERS = Pattern.compile("\\b\\d{5,}\\b");

    private static final Pattern BUSINESS_SUFFIX = Pattern.compile(
        "\\s+(llc|inc|corp|co|ltd)\\.?\\s*$", Pattern.CASE_INSENSITIVE);

    private static final Pattern TRAILING_STATE = Pattern.compile("\\s+[a-z]{2}$");

    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9 ]");

    private static final Pattern EXTRA_SPACES = Pattern.compile("\\s+");

    /** Ordered chain of transformations applied after shortcut checks. */
    private static final List<UnaryOperator<String>> PIPELINE = List.of(
        s -> PAYMENT_PREFIXES.matcher(s).replaceFirst(""),
        s -> LONG_NUMBERS.matcher(s).replaceAll(""),
        s -> BUSINESS_SUFFIX.matcher(s).replaceAll(""),
        s -> TRAILING_STATE.matcher(s).replaceAll(""),
        s -> NON_ALNUM.matcher(s).replaceAll(" "),
        s -> EXTRA_SPACES.matcher(s).replaceAll(" ").strip()
    );

    // ── State ─────────────────────────────────────────────────────────────────

    private final String merchantsFilePath;
    private final Map<String, String> merchants;  // normalized key → category
    private final Map<String, String> keywords;   // substring → category
    private final CliService cliService;
    private final List<String> categoryList;
    private boolean dirty = false;

    public MerchantClassifier(String merchantsFilePath, CliService cliService,
                               List<String> categoryList) throws IOException {
        this.merchantsFilePath = merchantsFilePath;
        this.cliService = cliService;
        this.categoryList = categoryList;

        File file = new File(merchantsFilePath);
        if (file.exists()) {
            Map<String, Map<String, String>> data =
                MAPPER.readValue(file, new TypeReference<>() {});
            this.merchants = new HashMap<>(data.getOrDefault("merchants", new HashMap<>()));
            this.keywords  = new HashMap<>(data.getOrDefault("keywords",  new HashMap<>()));
        } else {
            logger.warn("merchants.json not found at {} — starting with empty maps", merchantsFilePath);
            this.merchants = new HashMap<>();
            this.keywords  = new HashMap<>();
        }

        logger.info("MerchantClassifier: loaded {} merchant entries, {} keyword entries",
                    merchants.size(), keywords.size());
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns the category for the given transaction description.
     * Falls back to interactive CLI prompt for unknowns and persists the result.
     */
    public String classify(String description) {
        // 1. Shortcuts
        String lower = description.toLowerCase().trim();
        if (lower.startsWith("atm withdrawal")) return "Other";
        if (lower.startsWith("atm deposit"))    return "Other Credits";

        // 2. Keyword scan (substring match, longest key wins for ties)
        String kwMatch = keywordMatch(lower);
        if (kwMatch != null) {
            logger.info("keyword match → '{}' for description '{}'", kwMatch, description);
            return kwMatch;
        }

        // 3. Normalized exact lookup
        String key = normalize(lower);
        if (!key.isEmpty() && merchants.containsKey(key)) {
            return merchants.get(key);
        }

        // 4. CLI fallback — prompt user, save result
        logger.info("Unknown merchant key '{}' (raw: '{}') — prompting user", key, description);
        String category = cliService.promptForCategoryByDescription(description, categoryList);
        if (!key.isEmpty()) {
            merchants.put(key, category);
            dirty = true;
        }
        return category;
    }

    /** Persist any newly learned merchant → category entries back to merchants.json. */
    public void save() throws IOException {
        if (!dirty) return;
        Map<String, Map<String, String>> data = Map.of(
            "merchants", merchants,
            "keywords",  keywords
        );
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(new File(merchantsFilePath), data);
        logger.info("MerchantClassifier: saved updated merchants.json ({} entries)", merchants.size());
        dirty = false;
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    /**
     * Scans the keywords map against {@code lower} (raw lowercase description).
     * All matching keywords are collected; the longest match is returned to prefer
     * more specific entries (e.g. "planet fitness" over "fitness").
     */
    private String keywordMatch(String lower) {
        String bestKey = null;
        for (String keyword : keywords.keySet()) {
            if (lower.contains(keyword.toLowerCase())) {
                if (bestKey == null || keyword.length() > bestKey.length()) {
                    bestKey = keyword;
                }
            }
        }
        return bestKey != null ? keywords.get(bestKey) : null;
    }

    /** Applies the normalization pipeline to produce a stable lookup key. */
    String normalize(String lower) {
        String s = lower;
        for (UnaryOperator<String> step : PIPELINE) {
            s = step.apply(s);
        }
        return s;
    }
}
