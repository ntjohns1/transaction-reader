package com.noslen.transaction_reader.io;

import com.noslen.transaction_reader.config.Config;
import com.noslen.transaction_reader.model.Transaction;
import com.plaid.client.ApiClient;
import com.plaid.client.model.TransactionsGetRequest;
import com.plaid.client.model.TransactionsGetResponse;
import com.plaid.client.request.PlaidApi;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import retrofit2.Response;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

public class PlaidClient {

    private static final Logger logger = LogManager.getLogger(PlaidClient.class);

    private final PlaidApi plaidApi;
    private final Map<String, String> accessTokens;
    private final Map<String, String> accountIdToName;

    public PlaidClient(Config config) {
        HashMap<String, String> apiKeys = new HashMap<>();
        apiKeys.put("clientId", config.getPlaidClientId());
        apiKeys.put("secret", config.getPlaidSecret());

        ApiClient apiClient = new ApiClient(apiKeys);
        apiClient.setPlaidAdapter(resolveEnvironment(config.getPlaidEnvironment()));
        this.plaidApi = apiClient.createService(PlaidApi.class);

        this.accessTokens    = config.getPlaidAccessTokens();
        this.accountIdToName = config.getPlaidAccountIdMap();
    }

    public List<Transaction> fetchTransactions(LocalDate startDate, LocalDate endDate) throws IOException {
        List<Transaction> all = new ArrayList<>();
        for (Map.Entry<String, String> entry : accessTokens.entrySet()) {
            String institutionLabel = entry.getKey();
            String accessToken = entry.getValue();
            logger.info("Fetching Plaid transactions for {} ({} to {})", institutionLabel, startDate, endDate);
            all.addAll(fetchForToken(accessToken, institutionLabel, startDate, endDate));
        }
        return all;
    }

    private List<Transaction> fetchForToken(String accessToken, String institutionLabel,
                                             LocalDate startDate, LocalDate endDate) throws IOException {
        TransactionsGetRequest request = new TransactionsGetRequest()
                .accessToken(accessToken)
                .startDate(startDate)
                .endDate(endDate);

        Response<TransactionsGetResponse> response = plaidApi.transactionsGet(request).execute();
        if (!response.isSuccessful() || response.body() == null) {
            logger.error("Plaid API error for {}: HTTP {}", institutionLabel, response.code());
            return Collections.emptyList();
        }

        List<com.plaid.client.model.Transaction> plaidTxns = response.body().getTransactions();
        logger.info("Received {} transactions from Plaid for {}", plaidTxns.size(), institutionLabel);
        return mapTransactions(plaidTxns, institutionLabel);
    }

    private List<Transaction> mapTransactions(
            List<com.plaid.client.model.Transaction> plaidTxns, String institutionLabel) {

        List<Transaction> result = new ArrayList<>();
        for (com.plaid.client.model.Transaction t : plaidTxns) {
            // Discover: both cards share one access token; account ID disambiguates CC 1 vs CC 2.
            // For single-card institutions (ELFCU, Chase) the map is empty so institutionLabel is used.
            String accountName = accountIdToName.getOrDefault(t.getAccountId(), institutionLabel);

            // Plaid convention: positive amount = money leaving the account (debit),
            //                   negative amount = money entering the account (credit).
            double amount = t.getAmount() != null ? t.getAmount() : 0.0;
            double debit  = amount > 0 ?  amount : 0.0;
            double credit = amount < 0 ? -amount : 0.0;

            result.add(new Transaction(
                    t.getDate(),
                    t.getName(),
                    debit,
                    credit,
                    0.0,
                    accountName
            ));
        }
        return result;
    }

    private static String resolveEnvironment(String env) {
        if ("production".equalsIgnoreCase(env)) return ApiClient.Production;
        return ApiClient.Sandbox;
    }
}
