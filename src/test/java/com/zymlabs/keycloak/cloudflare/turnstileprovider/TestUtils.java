package com.zymlabs.keycloak.cloudflare.turnstileprovider;

import org.keycloak.connections.jpa.JpaConnectionProvider;
import org.keycloak.events.EventBuilder;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import jakarta.persistence.EntityManager;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.mockito.Answers.RETURNS_SELF;

/**
 * Shared test utilities for creating mock objects and test data.
 */
public class TestUtils {

    // ===== MOCK BUILDERS =====

    /**
     * Creates a mock EventBuilder for testing event logging.
     */
    public static EventBuilder mockEventBuilder() {
        EventBuilder event = mock(EventBuilder.class, withSettings().defaultAnswer(RETURNS_SELF));
        return event;
    }

    /**
     * Creates a mock KeycloakSession with EntityManager support.
     */
    public static KeycloakSession mockKeycloakSession(EntityManager entityManager) {
        KeycloakSession session = mock(KeycloakSession.class);
        JpaConnectionProvider jpaProvider = mock(JpaConnectionProvider.class);

        when(session.getProvider(JpaConnectionProvider.class)).thenReturn(jpaProvider);
        when(jpaProvider.getEntityManager()).thenReturn(entityManager);

        return session;
    }

    /**
     * Creates a mock EntityManager for database operations.
     */
    public static EntityManager mockEntityManager() {
        EntityManager em = mock(EntityManager.class);
        // persist() is void, no stubbing needed
        return em;
    }

    /**
     * Creates a mock RealmModel.
     */
    public static RealmModel mockRealm(String realmId) {
        RealmModel realm = mock(RealmModel.class);
        when(realm.getId()).thenReturn(realmId);
        when(realm.getName()).thenReturn("test-realm");
        return realm;
    }

    /**
     * Creates a mock UserModel.
     */
    public static UserModel mockUser(String userId, String username, String email) {
        UserModel user = mock(UserModel.class);
        when(user.getId()).thenReturn(userId);
        when(user.getUsername()).thenReturn(username);
        when(user.getEmail()).thenReturn(email);
        return user;
    }

    // ===== RESULT BUILDERS =====

    /**
     * Creates a successful TurnstileVerificationResult.
     */
    public static CloudflareTurnstileService.TurnstileVerificationResult successResult() {
        return successResult("example.com", "2024-01-01T12:00:00Z");
    }

    /**
     * Creates a successful TurnstileVerificationResult with custom values.
     */
    public static CloudflareTurnstileService.TurnstileVerificationResult successResult(
            String hostname, String challengeTs) {
        return new CloudflareTurnstileService.TurnstileVerificationResult(
                true,
                null,
                challengeTs,
                hostname,
                "{\"success\":true,\"hostname\":\"" + hostname + "\"}"
        );
    }

    /**
     * Creates a failed TurnstileVerificationResult.
     */
    public static CloudflareTurnstileService.TurnstileVerificationResult failureResult(String errorCodes) {
        return new CloudflareTurnstileService.TurnstileVerificationResult(
                false,
                errorCodes,
                null,
                null,
                "{\"success\":false,\"error-codes\":[\"" + errorCodes + "\"]}"
        );
    }

    /**
     * Creates a failed TurnstileVerificationResult with default error.
     */
    public static CloudflareTurnstileService.TurnstileVerificationResult failureResult() {
        return failureResult("invalid-input-response");
    }

    /**
     * Creates a dummy/synthetic success result for skipped verification.
     */
    public static CloudflareTurnstileService.TurnstileVerificationResult dummySuccessResult() {
        return new CloudflareTurnstileService.TurnstileVerificationResult(
                true,
                null,
                null,
                null,
                "{\"success\":true,\"skipped\":true}"
        );
    }

    // ===== CONFIG BUILDERS =====

    /**
     * Creates a default configuration map with common settings.
     */
    public static Map<String, String> defaultConfig() {
        Map<String, String> config = new HashMap<>();
        config.put(CloudflareTurnstileAuthenticator.CONFIG_SITE_KEY, "test-site-key");
        config.put(CloudflareTurnstileAuthenticator.CONFIG_SECRET_KEY, "test-secret-key");
        config.put(CloudflareTurnstileAuthenticator.CONFIG_WIDGET_MODE, "managed");
        config.put(CloudflareTurnstileAuthenticator.CONFIG_WIDGET_THEME, "auto");
        config.put(CloudflareTurnstileAuthenticator.CONFIG_IMPLEMENTATION_METHOD, "SEPARATE_PAGE");
        config.put(CloudflareTurnstileAuthenticator.CONFIG_FAIL_MODE, "FAIL_CLOSED");
        config.put(CloudflareTurnstileAuthenticator.CONFIG_FAIL_ACTION, "BLOCK");
        config.put(CloudflareTurnstileAuthenticator.CONFIG_ALLOWLIST_BEHAVIOR, "VERIFY_BUT_ALLOW");
        config.put(CloudflareTurnstileAuthenticator.CONFIG_RECORD_VERIFICATIONS, "true");
        config.put(CloudflareTurnstileAuthenticator.CONFIG_CONNECT_TIMEOUT, "5000");
        config.put(CloudflareTurnstileAuthenticator.CONFIG_READ_TIMEOUT, "5000");
        config.put(CloudflareTurnstileAuthenticator.CONFIG_IP_ALLOWLIST, "");
        config.put(CloudflareTurnstileAuthenticator.CONFIG_IP_BLOCKLIST, "");
        return config;
    }

    /**
     * Creates a configuration map with specific settings.
     */
    public static Map<String, String> configWith(String key, String value) {
        Map<String, String> config = defaultConfig();
        config.put(key, value);
        return config;
    }

    /**
     * Creates a configuration map with multiple specific settings.
     */
    public static Map<String, String> configWith(Map<String, String> overrides) {
        Map<String, String> config = defaultConfig();
        config.putAll(overrides);
        return config;
    }

    /**
     * Creates an empty configuration map.
     */
    public static Map<String, String> emptyConfig() {
        return new HashMap<>();
    }

    // ===== ENTITY BUILDERS =====

    /**
     * Creates a CloudflareTurnstileCheckEntity with minimal required fields.
     */
    public static CloudflareTurnstileCheckEntity minimalEntity() {
        CloudflareTurnstileCheckEntity entity = new CloudflareTurnstileCheckEntity();
        entity.setRealmId("test-realm-id");
        entity.setIpAddress("192.168.1.1");
        entity.setTimestamp(java.time.Instant.now());
        entity.setSuccess(true);
        return entity;
    }

    /**
     * Creates a CloudflareTurnstileCheckEntity with full audit data.
     */
    public static CloudflareTurnstileCheckEntity fullEntity() {
        CloudflareTurnstileCheckEntity entity = new CloudflareTurnstileCheckEntity();

        // User context
        entity.setUserId("user-123");
        entity.setUsername("testuser");
        entity.setEmail("test@example.com");
        entity.setRealmId("realm-123");

        // Request data
        entity.setIpAddress("192.168.1.1");
        entity.setTimestamp(java.time.Instant.now());
        entity.setFlowType("login");

        // Verification result
        entity.setSuccess(true);
        entity.setErrorCodes(null);
        entity.setChallengeTs("2024-01-01T12:00:00Z");
        entity.setHostname("example.com");
        entity.setRawResponse("{\"success\":true}");

        // Configuration context
        entity.setFailMode("FAIL_CLOSED");
        entity.setFailAction("BLOCK");
        entity.setAllowlistBehavior("VERIFY_BUT_ALLOW");
        entity.setImplementationMethod("SEPARATE_PAGE");

        // IP processing status
        entity.setIpAllowlisted(false);
        entity.setIpBlocklisted(false);
        entity.setVerificationSkipped(false);

        // Final outcome
        entity.setAuthenticationAllowed(true);
        entity.setActionReason("Success");

        // Correlation
        entity.setEventId("event-123");
        entity.setSessionId("session-123");

        return entity;
    }

    // ===== CONSTANTS =====

    public static final String TEST_REALM_ID = "test-realm-123";
    public static final String TEST_USER_ID = "test-user-123";
    public static final String TEST_USERNAME = "testuser";
    public static final String TEST_EMAIL = "test@example.com";
    public static final String TEST_IP_ADDRESS = "192.168.1.1";
    public static final String TEST_SESSION_ID = "test-session-123";
    public static final String TEST_EVENT_ID = "test-event-123";
    public static final String TEST_HOSTNAME = "example.com";
    public static final String TEST_CHALLENGE_TS = "2024-01-01T12:00:00Z";
}
