package com.zymlabs.keycloak.cloudflare.turnstileprovider;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for CloudflareTurnstileService.
 *
 * Note: These tests verify the service structure and error handling.
 * Integration tests with actual Cloudflare API would require valid credentials.
 */
@DisplayName("Cloudflare Turnstile Service Tests")
class CloudflareTurnstileServiceTest {

    @Test
    @DisplayName("Should create service with default timeouts")
    void testServiceCreationDefaultTimeouts() {
        CloudflareTurnstileService service = new CloudflareTurnstileService("test-secret");
        assertThat(service).isNotNull();
        service.close();
    }

    @Test
    @DisplayName("Should create service with custom timeouts")
    void testServiceCreationCustomTimeouts() {
        CloudflareTurnstileService service = new CloudflareTurnstileService("test-secret", 10000, 10000);
        assertThat(service).isNotNull();
        service.close();
    }

    @Test
    @DisplayName("Should handle null token")
    void testNullToken() {
        CloudflareTurnstileService service = new CloudflareTurnstileService("test-secret");
        CloudflareTurnstileService.TurnstileVerificationResult result = service.verify(null, "1.2.3.4");

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCodes()).isEqualTo("missing-input-response");

        service.close();
    }

    @Test
    @DisplayName("Should handle empty token")
    void testEmptyToken() {
        CloudflareTurnstileService service = new CloudflareTurnstileService("test-secret");
        CloudflareTurnstileService.TurnstileVerificationResult result = service.verify("", "1.2.3.4");

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCodes()).isEqualTo("missing-input-response");

        service.close();
    }

    @Test
    @DisplayName("TurnstileVerificationResult should have correct getters")
    void testVerificationResult() {
        CloudflareTurnstileService.TurnstileVerificationResult result =
                new CloudflareTurnstileService.TurnstileVerificationResult(
                        true,
                        null,
                        "2024-01-01T00:00:00Z",
                        "example.com",
                        "{\"success\":true}"
                );

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getErrorCodes()).isNull();
        assertThat(result.getChallengeTs()).isEqualTo("2024-01-01T00:00:00Z");
        assertThat(result.getHostname()).isEqualTo("example.com");
        assertThat(result.getRawResponse()).isEqualTo("{\"success\":true}");
    }

    @Test
    @DisplayName("TurnstileVerificationResult toString should work")
    void testVerificationResultToString() {
        CloudflareTurnstileService.TurnstileVerificationResult result =
                new CloudflareTurnstileService.TurnstileVerificationResult(
                        false,
                        "timeout-or-duplicate",
                        null,
                        "example.com",
                        "{\"success\":false}"
                );

        String str = result.toString();
        assertThat(str)
                .contains("success=false")
                .contains("timeout-or-duplicate")
                .contains("example.com");
    }
}
