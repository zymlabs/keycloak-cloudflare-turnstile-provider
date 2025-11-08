package com.zymlabs.keycloak.cloudflare.turnstileprovider;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for CloudflareTurnstileCheckEntity.
 */
@DisplayName("Cloudflare Turnstile Check Entity Tests")
class CloudflareTurnstileCheckEntityTest {

    @Test
    @DisplayName("Should create entity with default constructor")
    void testDefaultConstructor() {
        CloudflareTurnstileCheckEntity entity = new CloudflareTurnstileCheckEntity();
        assertThat(entity).isNotNull();
        assertThat(entity.getId()).isNull();
    }

    @Test
    @DisplayName("Should set and get all properties")
    void testSettersAndGetters() {
        CloudflareTurnstileCheckEntity entity = new CloudflareTurnstileCheckEntity();
        Instant now = Instant.now();

        entity.setId(1L);
        entity.setUserId("user-123");
        entity.setRealmId("realm-456");
        entity.setUsername("testuser");
        entity.setEmail("test@example.com");
        entity.setIpAddress("192.168.1.100");
        entity.setTimestamp(now);
        entity.setSuccess(true);
        entity.setErrorCodes(null);
        entity.setChallengeTs("2024-01-01T00:00:00Z");
        entity.setHostname("example.com");
        entity.setEventId("event-789");
        entity.setSessionId("session-012");
        entity.setRawResponse("{\"success\":true}");

        assertThat(entity.getId()).isEqualTo(1L);
        assertThat(entity.getUserId()).isEqualTo("user-123");
        assertThat(entity.getRealmId()).isEqualTo("realm-456");
        assertThat(entity.getUsername()).isEqualTo("testuser");
        assertThat(entity.getEmail()).isEqualTo("test@example.com");
        assertThat(entity.getIpAddress()).isEqualTo("192.168.1.100");
        assertThat(entity.getTimestamp()).isEqualTo(now);
        assertThat(entity.isSuccess()).isTrue();
        assertThat(entity.getErrorCodes()).isNull();
        assertThat(entity.getChallengeTs()).isEqualTo("2024-01-01T00:00:00Z");
        assertThat(entity.getHostname()).isEqualTo("example.com");
        assertThat(entity.getEventId()).isEqualTo("event-789");
        assertThat(entity.getSessionId()).isEqualTo("session-012");
        assertThat(entity.getRawResponse()).isEqualTo("{\"success\":true}");
    }

    @Test
    @DisplayName("Should handle failed verification with error codes")
    void testFailedVerification() {
        CloudflareTurnstileCheckEntity entity = new CloudflareTurnstileCheckEntity();

        entity.setSuccess(false);
        entity.setErrorCodes("timeout-or-duplicate,invalid-input-response");

        assertThat(entity.isSuccess()).isFalse();
        assertThat(entity.getErrorCodes())
                .contains("timeout-or-duplicate")
                .contains("invalid-input-response");
    }

    @Test
    @DisplayName("Should produce meaningful toString")
    void testToString() {
        CloudflareTurnstileCheckEntity entity = new CloudflareTurnstileCheckEntity();
        Instant now = Instant.now();

        entity.setId(1L);
        entity.setUserId("user-123");
        entity.setRealmId("realm-456");
        entity.setIpAddress("192.168.1.100");
        entity.setTimestamp(now);
        entity.setSuccess(true);

        String str = entity.toString();

        assertThat(str)
                .contains("id=1")
                .contains("userId='user-123'")
                .contains("realmId='realm-456'")
                .contains("ipAddress='192.168.1.100'")
                .contains("success=true");
    }

    @Test
    @DisplayName("Should allow null user info for pre-auth checks")
    void testPreAuthCheck() {
        CloudflareTurnstileCheckEntity entity = new CloudflareTurnstileCheckEntity();

        // Pre-auth: no user info yet
        entity.setUserId(null);
        entity.setUsername(null);
        entity.setEmail(null);
        entity.setRealmId("realm-456");
        entity.setIpAddress("192.168.1.100");
        entity.setTimestamp(Instant.now());

        assertThat(entity.getUserId()).isNull();
        assertThat(entity.getUsername()).isNull();
        assertThat(entity.getEmail()).isNull();
        assertThat(entity.getRealmId()).isNotNull();
    }
}
