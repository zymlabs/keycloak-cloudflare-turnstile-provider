package com.zymlabs.keycloak.cloudflare.turnstileprovider;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for CloudflareTurnstileService.
 *
 * Note: These tests verify the service structure, token validation, and error handling.
 * Full HTTP response testing (success/failure JSON parsing, network errors, etc.) would
 * require either integration tests with actual Cloudflare API or refactoring the service
 * to accept an injectable HttpClient for mocking.
 *
 * Current tests focus on:
 * - Service construction and configuration
 * - Pre-HTTP validation (null/empty/whitespace tokens)
 * - RemoteIP parameter handling
 * - Resource cleanup (close method)
 * - Result object structure
 */
@DisplayName("Cloudflare Turnstile Service Tests")
class CloudflareTurnstileServiceTest {

    // ===== SERVICE CONSTRUCTION TESTS =====

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
    @DisplayName("Should create service with zero timeouts")
    void testServiceCreationZeroTimeouts() {
        // Zero timeout means infinite wait - valid but not recommended
        CloudflareTurnstileService service = new CloudflareTurnstileService("test-secret", 0, 0);
        assertThat(service).isNotNull();
        service.close();
    }

    @Test
    @DisplayName("Should create service with very small timeouts")
    void testServiceCreationSmallTimeouts() {
        CloudflareTurnstileService service = new CloudflareTurnstileService("test-secret", 1, 1);
        assertThat(service).isNotNull();
        service.close();
    }

    @Test
    @DisplayName("Should create service with very large timeouts")
    void testServiceCreationLargeTimeouts() {
        CloudflareTurnstileService service = new CloudflareTurnstileService("test-secret", 600000, 600000);
        assertThat(service).isNotNull();
        service.close();
    }

    // ===== TOKEN VALIDATION TESTS =====

    @Test
    @DisplayName("Should handle null token")
    void testNullToken() {
        CloudflareTurnstileService service = new CloudflareTurnstileService("test-secret");
        CloudflareTurnstileService.TurnstileVerificationResult result = service.verify(null, "1.2.3.4");

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCodes()).isEqualTo("missing-input-response");
        assertThat(result.getChallengeTs()).isNull();
        assertThat(result.getHostname()).isNull();
        assertThat(result.getRawResponse()).isNull();

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
    @DisplayName("Should handle whitespace-only token")
    void testWhitespaceToken() {
        CloudflareTurnstileService service = new CloudflareTurnstileService("test-secret");
        CloudflareTurnstileService.TurnstileVerificationResult result = service.verify("   ", "1.2.3.4");

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCodes()).isEqualTo("missing-input-response");

        service.close();
    }

    @Test
    @DisplayName("Should handle token with only tabs and newlines")
    void testWhitespaceVariationsToken() {
        CloudflareTurnstileService service = new CloudflareTurnstileService("test-secret");
        CloudflareTurnstileService.TurnstileVerificationResult result = service.verify("\t\n\r", "1.2.3.4");

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCodes()).isEqualTo("missing-input-response");

        service.close();
    }

    // ===== REMOTE IP PARAMETER TESTS =====

    @Test
    @DisplayName("Should handle null remoteIp")
    void testNullRemoteIp() {
        CloudflareTurnstileService service = new CloudflareTurnstileService("test-secret");
        // Null token to avoid HTTP call, we're just testing parameter handling
        CloudflareTurnstileService.TurnstileVerificationResult result = service.verify(null, null);

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCodes()).isEqualTo("missing-input-response");

        service.close();
    }

    @Test
    @DisplayName("Should handle empty remoteIp")
    void testEmptyRemoteIp() {
        CloudflareTurnstileService service = new CloudflareTurnstileService("test-secret");
        // Null token to avoid HTTP call, we're just testing parameter handling
        CloudflareTurnstileService.TurnstileVerificationResult result = service.verify(null, "");

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isFalse();

        service.close();
    }

    @Test
    @DisplayName("Should handle whitespace remoteIp")
    void testWhitespaceRemoteIp() {
        CloudflareTurnstileService service = new CloudflareTurnstileService("test-secret");
        // Null token to avoid HTTP call, we're just testing parameter handling
        CloudflareTurnstileService.TurnstileVerificationResult result = service.verify(null, "   ");

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isFalse();

        service.close();
    }

    // ===== RESOURCE CLEANUP TESTS =====

    @Test
    @DisplayName("Close should be idempotent")
    void testCloseIdempotency() {
        CloudflareTurnstileService service = new CloudflareTurnstileService("test-secret");

        // Should not throw when called multiple times
        service.close();
        service.close();
        service.close();
    }

    @Test
    @DisplayName("Verify can be called multiple times before close")
    void testMultipleVerifyCalls() {
        CloudflareTurnstileService service = new CloudflareTurnstileService("test-secret");

        // Multiple calls with null token (avoids HTTP calls)
        CloudflareTurnstileService.TurnstileVerificationResult result1 = service.verify(null, "1.2.3.4");
        CloudflareTurnstileService.TurnstileVerificationResult result2 = service.verify("", "1.2.3.4");
        CloudflareTurnstileService.TurnstileVerificationResult result3 = service.verify("   ", "1.2.3.4");

        assertThat(result1.isSuccess()).isFalse();
        assertThat(result2.isSuccess()).isFalse();
        assertThat(result3.isSuccess()).isFalse();

        service.close();
    }

    // ===== VERIFICATION RESULT TESTS =====

    @Test
    @DisplayName("TurnstileVerificationResult should have correct getters for success")
    void testVerificationResultSuccess() {
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
    @DisplayName("TurnstileVerificationResult should have correct getters for failure")
    void testVerificationResultFailure() {
        CloudflareTurnstileService.TurnstileVerificationResult result =
                new CloudflareTurnstileService.TurnstileVerificationResult(
                        false,
                        "timeout-or-duplicate",
                        null,
                        null,
                        "{\"success\":false,\"error-codes\":[\"timeout-or-duplicate\"]}"
                );

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCodes()).isEqualTo("timeout-or-duplicate");
        assertThat(result.getChallengeTs()).isNull();
        assertThat(result.getHostname()).isNull();
        assertThat(result.getRawResponse()).contains("timeout-or-duplicate");
    }

    @Test
    @DisplayName("TurnstileVerificationResult should handle multiple error codes")
    void testVerificationResultMultipleErrors() {
        CloudflareTurnstileService.TurnstileVerificationResult result =
                new CloudflareTurnstileService.TurnstileVerificationResult(
                        false,
                        "timeout-or-duplicate,invalid-input-response",
                        null,
                        null,
                        "{\"success\":false,\"error-codes\":[\"timeout-or-duplicate\",\"invalid-input-response\"]}"
                );

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCodes()).contains("timeout-or-duplicate");
        assertThat(result.getErrorCodes()).contains("invalid-input-response");
        assertThat(result.getErrorCodes()).contains(",");
    }

    @Test
    @DisplayName("TurnstileVerificationResult should handle network error format")
    void testVerificationResultNetworkError() {
        CloudflareTurnstileService.TurnstileVerificationResult result =
                new CloudflareTurnstileService.TurnstileVerificationResult(
                        false,
                        "network-error",
                        null,
                        null,
                        "Error: Connection timeout"
                );

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCodes()).isEqualTo("network-error");
        assertThat(result.getRawResponse()).contains("Error:");
    }

    @Test
    @DisplayName("TurnstileVerificationResult toString should include all fields")
    void testVerificationResultToString() {
        CloudflareTurnstileService.TurnstileVerificationResult result =
                new CloudflareTurnstileService.TurnstileVerificationResult(
                        false,
                        "timeout-or-duplicate",
                        "2024-01-01T00:00:00Z",
                        "example.com",
                        "{\"success\":false}"
                );

        String str = result.toString();
        assertThat(str)
                .contains("success=false")
                .contains("timeout-or-duplicate")
                .contains("example.com");
    }

    @Test
    @DisplayName("TurnstileVerificationResult toString should handle null values")
    void testVerificationResultToStringNulls() {
        CloudflareTurnstileService.TurnstileVerificationResult result =
                new CloudflareTurnstileService.TurnstileVerificationResult(
                        true,
                        null,
                        null,
                        null,
                        null
                );

        String str = result.toString();
        assertThat(str).contains("success=true");
        // Should not throw NPE with null fields
    }
}
