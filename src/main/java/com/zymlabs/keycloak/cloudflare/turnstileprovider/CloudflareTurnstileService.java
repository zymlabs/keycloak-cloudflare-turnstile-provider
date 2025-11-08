package com.zymlabs.keycloak.cloudflare.turnstileprovider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.jboss.logging.Logger;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for verifying Cloudflare Turnstile tokens.
 *
 * This service communicates with the Cloudflare Turnstile siteverify API
 * to validate CAPTCHA responses from users.
 */
public class CloudflareTurnstileService implements Closeable {

    private static final Logger logger = Logger.getLogger(CloudflareTurnstileService.class);
    private static final String SITEVERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final String secretKey;
    private final CloseableHttpClient httpClient;

    /**
     * Create a new Turnstile service with default timeouts (5 seconds).
     *
     * @param secretKey Cloudflare Turnstile secret key
     */
    public CloudflareTurnstileService(String secretKey) {
        this(secretKey, 5000, 5000);
    }

    /**
     * Create a new Turnstile service with custom timeouts.
     *
     * @param secretKey Cloudflare Turnstile secret key
     * @param connectTimeoutMs Connection timeout in milliseconds
     * @param readTimeoutMs Read timeout in milliseconds
     */
    public CloudflareTurnstileService(String secretKey, int connectTimeoutMs, int readTimeoutMs) {
        this.secretKey = secretKey;

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(connectTimeoutMs)
                .setSocketTimeout(readTimeoutMs)
                .build();

        this.httpClient = HttpClientBuilder.create()
                .setDefaultRequestConfig(requestConfig)
                .build();

        logger.debugf("CloudflareTurnstileService initialized (connect timeout: %dms, read timeout: %dms)",
                connectTimeoutMs, readTimeoutMs);
    }

    /**
     * Verify a Turnstile token.
     *
     * @param token The cf-turnstile-response token from the client
     * @param remoteIp The user's IP address (optional but recommended)
     * @return Verification result
     */
    public TurnstileVerificationResult verify(String token, String remoteIp) {
        if (token == null || token.trim().isEmpty()) {
            logger.warn("Turnstile token is null or empty");
            return new TurnstileVerificationResult(false, "missing-input-response", null, null, null);
        }

        try {
            HttpPost post = new HttpPost(SITEVERIFY_URL);

            // Build form data
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("secret", secretKey));
            params.add(new BasicNameValuePair("response", token));
            if (remoteIp != null && !remoteIp.trim().isEmpty()) {
                params.add(new BasicNameValuePair("remoteip", remoteIp));
            }

            post.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));

            logger.debugf("Verifying Turnstile token for IP: %s", remoteIp);

            HttpResponse response = httpClient.execute(post);
            String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

            logger.debugf("Turnstile API response: %s", responseBody);

            // Parse JSON response
            JsonNode jsonResponse = objectMapper.readTree(responseBody);

            boolean success = jsonResponse.has("success") && jsonResponse.get("success").asBoolean();
            String challengeTs = jsonResponse.has("challenge_ts") ? jsonResponse.get("challenge_ts").asText() : null;
            String hostname = jsonResponse.has("hostname") ? jsonResponse.get("hostname").asText() : null;

            // Extract error codes if present
            String errorCodes = null;
            if (jsonResponse.has("error-codes") && jsonResponse.get("error-codes").isArray()) {
                List<String> errors = new ArrayList<>();
                jsonResponse.get("error-codes").forEach(node -> errors.add(node.asText()));
                errorCodes = String.join(",", errors);
            }

            TurnstileVerificationResult result = new TurnstileVerificationResult(
                    success, errorCodes, challengeTs, hostname, responseBody);

            if (success) {
                logger.infof("Turnstile verification successful for IP: %s, hostname: %s", remoteIp, hostname);
            } else {
                logger.warnf("Turnstile verification failed for IP: %s, errors: %s", remoteIp, errorCodes);
            }

            return result;

        } catch (IOException e) {
            logger.errorf(e, "Failed to verify Turnstile token: %s", e.getMessage());
            return new TurnstileVerificationResult(false, "network-error", null, null,
                    String.format("Error: %s", e.getMessage()));
        }
    }

    @Override
    public void close() {
        try {
            if (httpClient != null) {
                httpClient.close();
            }
        } catch (IOException e) {
            logger.warnf(e, "Error closing HTTP client: %s", e.getMessage());
        }
    }

    /**
     * Result of a Turnstile verification.
     */
    public static class TurnstileVerificationResult {
        private final boolean success;
        private final String errorCodes;
        private final String challengeTs;
        private final String hostname;
        private final String rawResponse;

        public TurnstileVerificationResult(boolean success, String errorCodes, String challengeTs,
                                          String hostname, String rawResponse) {
            this.success = success;
            this.errorCodes = errorCodes;
            this.challengeTs = challengeTs;
            this.hostname = hostname;
            this.rawResponse = rawResponse;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getErrorCodes() {
            return errorCodes;
        }

        public String getChallengeTs() {
            return challengeTs;
        }

        public String getHostname() {
            return hostname;
        }

        public String getRawResponse() {
            return rawResponse;
        }

        @Override
        public String toString() {
            return String.format("TurnstileVerificationResult{success=%s, errorCodes=%s, hostname=%s}",
                    success, errorCodes, hostname);
        }
    }
}
