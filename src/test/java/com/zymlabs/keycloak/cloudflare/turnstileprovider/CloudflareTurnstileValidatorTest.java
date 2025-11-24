package com.zymlabs.keycloak.cloudflare.turnstileprovider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.FormContext;
import org.keycloak.authentication.ValidationContext;
import org.keycloak.connections.httpclient.HttpClientProvider;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CloudflareTurnstileValidator utility class.
 */
@DisplayName("CloudflareTurnstileValidator Tests")
class CloudflareTurnstileValidatorTest {

    // ===== VALIDATION RESULT TESTS =====

    @Test
    @DisplayName("ValidationResult.success should create successful result")
    void testValidationResult_Success() {
        CloudflareTurnstileService.TurnstileVerificationResult verifyResult = TestUtils.successResult();

        CloudflareTurnstileValidator.ValidationResult result =
                CloudflareTurnstileValidator.ValidationResult.success(verifyResult);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getErrorCode()).isNull();
        assertThat(result.getErrorMessage()).isNull();
        assertThat(result.getVerificationResult()).isEqualTo(verifyResult);
    }

    @Test
    @DisplayName("ValidationResult.failure should create failed result")
    void testValidationResult_Failure() {
        CloudflareTurnstileValidator.ValidationResult result =
                CloudflareTurnstileValidator.ValidationResult.failure("test_error", "Test error message");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("test_error");
        assertThat(result.getErrorMessage()).isEqualTo("Test error message");
        assertThat(result.getVerificationResult()).isNull();
    }

    @Test
    @DisplayName("ValidationResult should provide all getters")
    void testValidationResult_Getters() {
        CloudflareTurnstileService.TurnstileVerificationResult verifyResult = TestUtils.failureResult();

        CloudflareTurnstileValidator.ValidationResult result =
                new CloudflareTurnstileValidator.ValidationResult(
                        false, "error_code", "error_msg", verifyResult);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("error_code");
        assertThat(result.getErrorMessage()).isEqualTo("error_msg");
        assertThat(result.getVerificationResult()).isEqualTo(verifyResult);
    }

    // ===== IP ALLOWLIST TESTS =====

    @Test
    @DisplayName("isIpAllowed should return true for IP in allowlist")
    void testIsIpAllowed_IpInAllowlist() {
        assertThat(CloudflareTurnstileValidator.isIpAllowed("192.168.1.1", "192.168.1.0/24"))
                .isTrue();
        assertThat(CloudflareTurnstileValidator.isIpAllowed("10.0.0.100", "10.0.0.0/8"))
                .isTrue();
    }

    @Test
    @DisplayName("isIpAllowed should return false for IP not in allowlist")
    void testIsIpAllowed_IpNotInAllowlist() {
        assertThat(CloudflareTurnstileValidator.isIpAllowed("203.0.113.1", "192.168.1.0/24"))
                .isFalse();
        assertThat(CloudflareTurnstileValidator.isIpAllowed("8.8.8.8", "10.0.0.0/8,172.16.0.0/12"))
                .isFalse();
    }

    @Test
    @DisplayName("isIpAllowed should return false for null allowlist")
    void testIsIpAllowed_NullAllowlist() {
        assertThat(CloudflareTurnstileValidator.isIpAllowed("192.168.1.1", null))
                .isFalse();
    }

    @Test
    @DisplayName("isIpAllowed should return false for empty allowlist")
    void testIsIpAllowed_EmptyAllowlist() {
        assertThat(CloudflareTurnstileValidator.isIpAllowed("192.168.1.1", ""))
                .isFalse();
        assertThat(CloudflareTurnstileValidator.isIpAllowed("192.168.1.1", "   "))
                .isFalse();
    }

    @Test
    @DisplayName("isIpAllowed should handle invalid IP format gracefully")
    void testIsIpAllowed_InvalidIpFormat() {
        // Should return false on exception, not throw
        assertThat(CloudflareTurnstileValidator.isIpAllowed("invalid-ip", "192.168.1.0/24"))
                .isFalse();
    }

    // ===== IP BLOCKLIST TESTS =====

    @Test
    @DisplayName("isIpBlocked should return true for IP in blocklist")
    void testIsIpBlocked_IpInBlocklist() {
        assertThat(CloudflareTurnstileValidator.isIpBlocked("203.0.113.1", "203.0.113.0/24"))
                .isTrue();
        assertThat(CloudflareTurnstileValidator.isIpBlocked("198.51.100.50", "198.51.100.0/24"))
                .isTrue();
    }

    @Test
    @DisplayName("isIpBlocked should return false for IP not in blocklist")
    void testIsIpBlocked_IpNotInBlocklist() {
        assertThat(CloudflareTurnstileValidator.isIpBlocked("192.168.1.1", "203.0.113.0/24"))
                .isFalse();
        assertThat(CloudflareTurnstileValidator.isIpBlocked("8.8.8.8", "198.51.100.0/24"))
                .isFalse();
    }

    @Test
    @DisplayName("isIpBlocked should return false for null blocklist")
    void testIsIpBlocked_NullBlocklist() {
        assertThat(CloudflareTurnstileValidator.isIpBlocked("203.0.113.1", null))
                .isFalse();
    }

    @Test
    @DisplayName("isIpBlocked should return false for empty blocklist")
    void testIsIpBlocked_EmptyBlocklist() {
        assertThat(CloudflareTurnstileValidator.isIpBlocked("203.0.113.1", ""))
                .isFalse();
        assertThat(CloudflareTurnstileValidator.isIpBlocked("203.0.113.1", "   "))
                .isFalse();
    }

    @Test
    @DisplayName("isIpBlocked should handle invalid IP format gracefully")
    void testIsIpBlocked_InvalidIpFormat() {
        // Should return false on exception, not throw
        assertThat(CloudflareTurnstileValidator.isIpBlocked("invalid-ip", "203.0.113.0/24"))
                .isFalse();
    }

    // ===== VALIDATE TURNSTILE TESTS =====

    @Test
    @DisplayName("validateTurnstile should block IP in blocklist")
    void testValidateTurnstile_IpBlocked() {
        CloudflareTurnstileService service = mock(CloudflareTurnstileService.class);
        Map<String, String> config = TestUtils.configWith(
                CloudflareTurnstileAuthenticator.CONFIG_IP_BLOCKLIST, "203.0.113.0/24");

        CloudflareTurnstileValidator.ValidationResult result =
                CloudflareTurnstileValidator.validateTurnstile(
                        "test-token", "203.0.113.100", config, service);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("ip_blocked");
        assertThat(result.getErrorMessage()).isEqualTo("turnstileIpBlocked");

        // Service should not be called for blocked IPs
        verify(service, never()).verify(anyString(), anyString());
    }

    @Test
    @DisplayName("validateTurnstile should allow IP in allowlist without verification (SKIP_VERIFICATION)")
    void testValidateTurnstile_IpAllowlisted_SkipVerification() {
        CloudflareTurnstileService service = mock(CloudflareTurnstileService.class);
        Map<String, String> config = TestUtils.configWith(Map.of(
                CloudflareTurnstileAuthenticator.CONFIG_IP_ALLOWLIST, "192.168.1.0/24",
                CloudflareTurnstileAuthenticator.CONFIG_ALLOWLIST_BEHAVIOR, "SKIP_VERIFICATION"));

        CloudflareTurnstileValidator.ValidationResult result =
                CloudflareTurnstileValidator.validateTurnstile(
                        "test-token", "192.168.1.100", config, service);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getVerificationResult()).isNotNull();
        assertThat(result.getVerificationResult().isSuccess()).isTrue();
        assertThat(result.getVerificationResult().getRawResponse()).contains("allowlist");

        // Service should not be called for allowlisted IPs with SKIP_VERIFICATION
        verify(service, never()).verify(anyString(), anyString());
    }

    @Test
    @DisplayName("validateTurnstile should verify allowlisted IP with VERIFY_BUT_ALLOW and succeed")
    void testValidateTurnstile_IpAllowlisted_VerifyButAllow_Success() throws Exception {
        CloudflareTurnstileService service = mock(CloudflareTurnstileService.class);
        CloudflareTurnstileService.TurnstileVerificationResult successResult = TestUtils.successResult();
        when(service.verify("test-token", "192.168.1.100")).thenReturn(successResult);

        Map<String, String> config = TestUtils.configWith(Map.of(
                CloudflareTurnstileAuthenticator.CONFIG_IP_ALLOWLIST, "192.168.1.0/24",
                CloudflareTurnstileAuthenticator.CONFIG_ALLOWLIST_BEHAVIOR, "VERIFY_BUT_ALLOW"));

        CloudflareTurnstileValidator.ValidationResult result =
                CloudflareTurnstileValidator.validateTurnstile(
                        "test-token", "192.168.1.100", config, service);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getVerificationResult()).isEqualTo(successResult);
        assertThat(result.getVerificationResult().getRawResponse()).doesNotContain("allowlist");

        // Service SHOULD be called for allowlisted IPs with VERIFY_BUT_ALLOW
        verify(service).verify("test-token", "192.168.1.100");
    }

    @Test
    @DisplayName("validateTurnstile should verify allowlisted IP with VERIFY_BUT_ALLOW even on failure")
    void testValidateTurnstile_IpAllowlisted_VerifyButAllow_Failure() throws Exception {
        CloudflareTurnstileService service = mock(CloudflareTurnstileService.class);
        CloudflareTurnstileService.TurnstileVerificationResult failureResult = TestUtils.failureResult();
        when(service.verify("test-token", "192.168.1.100")).thenReturn(failureResult);

        Map<String, String> config = TestUtils.configWith(Map.of(
                CloudflareTurnstileAuthenticator.CONFIG_IP_ALLOWLIST, "192.168.1.0/24",
                CloudflareTurnstileAuthenticator.CONFIG_ALLOWLIST_BEHAVIOR, "VERIFY_BUT_ALLOW"));

        CloudflareTurnstileValidator.ValidationResult result =
                CloudflareTurnstileValidator.validateTurnstile(
                        "test-token", "192.168.1.100", config, service);

        // Should fail based on actual Cloudflare response
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("verification_failed");
        assertThat(result.getErrorMessage()).isEqualTo("turnstileVerificationFailed");

        // Service SHOULD be called for allowlisted IPs with VERIFY_BUT_ALLOW
        verify(service).verify("test-token", "192.168.1.100");
    }

    @Test
    @DisplayName("validateTurnstile should return success for successful verification")
    void testValidateTurnstile_VerificationSuccess() throws Exception {
        CloudflareTurnstileService service = mock(CloudflareTurnstileService.class);
        CloudflareTurnstileService.TurnstileVerificationResult successResult = TestUtils.successResult();
        when(service.verify("test-token", "8.8.8.8")).thenReturn(successResult);

        Map<String, String> config = TestUtils.defaultConfig();

        CloudflareTurnstileValidator.ValidationResult result =
                CloudflareTurnstileValidator.validateTurnstile(
                        "test-token", "8.8.8.8", config, service);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getVerificationResult()).isEqualTo(successResult);

        verify(service).verify("test-token", "8.8.8.8");
    }

    @Test
    @DisplayName("validateTurnstile should return failure for failed verification")
    void testValidateTurnstile_VerificationFailure() throws Exception {
        CloudflareTurnstileService service = mock(CloudflareTurnstileService.class);
        CloudflareTurnstileService.TurnstileVerificationResult failureResult = TestUtils.failureResult();
        when(service.verify("test-token", "8.8.8.8")).thenReturn(failureResult);

        Map<String, String> config = TestUtils.defaultConfig();

        CloudflareTurnstileValidator.ValidationResult result =
                CloudflareTurnstileValidator.validateTurnstile(
                        "test-token", "8.8.8.8", config, service);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("verification_failed");
        assertThat(result.getErrorMessage()).isEqualTo("turnstileVerificationFailed");

        verify(service).verify("test-token", "8.8.8.8");
    }

    @Test
    @DisplayName("validateTurnstile should allow on exception with FAIL_OPEN")
    void testValidateTurnstile_ServiceException_FailOpen() throws Exception {
        CloudflareTurnstileService service = mock(CloudflareTurnstileService.class);
        when(service.verify(anyString(), anyString()))
                .thenThrow(new RuntimeException("Network error"));

        Map<String, String> config = TestUtils.configWith(
                CloudflareTurnstileAuthenticator.CONFIG_FAIL_MODE, "FAIL_OPEN");

        CloudflareTurnstileValidator.ValidationResult result =
                CloudflareTurnstileValidator.validateTurnstile(
                        "test-token", "8.8.8.8", config, service);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getVerificationResult()).isNotNull();
        assertThat(result.getVerificationResult().isSuccess()).isTrue();
        assertThat(result.getVerificationResult().getRawResponse()).contains("fail_open");
    }

    @Test
    @DisplayName("validateTurnstile should block on exception with FAIL_CLOSED")
    void testValidateTurnstile_ServiceException_FailClosed() throws Exception {
        CloudflareTurnstileService service = mock(CloudflareTurnstileService.class);
        when(service.verify(anyString(), anyString()))
                .thenThrow(new RuntimeException("Network error"));

        Map<String, String> config = TestUtils.configWith(
                CloudflareTurnstileAuthenticator.CONFIG_FAIL_MODE, "FAIL_CLOSED");

        CloudflareTurnstileValidator.ValidationResult result =
                CloudflareTurnstileValidator.validateTurnstile(
                        "test-token", "8.8.8.8", config, service);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("verification_error");
        assertThat(result.getErrorMessage()).isEqualTo("turnstileVerificationError");
    }

    @Test
    @DisplayName("validateTurnstile should use default fail mode when not configured")
    void testValidateTurnstile_DefaultFailMode() throws Exception {
        CloudflareTurnstileService service = mock(CloudflareTurnstileService.class);
        when(service.verify(anyString(), anyString()))
                .thenThrow(new RuntimeException("Network error"));

        Map<String, String> config = TestUtils.emptyConfig();

        CloudflareTurnstileValidator.ValidationResult result =
                CloudflareTurnstileValidator.validateTurnstile(
                        "test-token", "8.8.8.8", config, service);

        // Default is FAIL_CLOSED
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("validateTurnstile should use default allowlist with SKIP_VERIFICATION")
    void testValidateTurnstile_DefaultAllowlist_SkipVerification() {
        CloudflareTurnstileService service = mock(CloudflareTurnstileService.class);
        // Explicitly set allowlist to match the default to ensure test works reliably
        Map<String, String> config = TestUtils.configWith(Map.of(
                CloudflareTurnstileAuthenticator.CONFIG_IP_ALLOWLIST, "10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,127.0.0.0/8",
                CloudflareTurnstileAuthenticator.CONFIG_ALLOWLIST_BEHAVIOR, "SKIP_VERIFICATION"));

        // 192.168.1.1 is in 192.168.0.0/16
        CloudflareTurnstileValidator.ValidationResult result =
                CloudflareTurnstileValidator.validateTurnstile(
                        "test-token", "192.168.1.1", config, service);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getVerificationResult().getRawResponse()).contains("allowlist");

        verify(service, never()).verify(anyString(), anyString());
    }

    // ===== CONTEXT HELPER TESTS =====

    // Note: getClientIpAddress methods are simple pass-through methods to Keycloak APIs
    // They are tested indirectly through integration tests and are too simple to warrant
    // extensive mocking of internal Keycloak connection classes

    @Test
    @DisplayName("getConfig should return config map from context")
    void testGetConfig_ValidConfig() {
        AuthenticationFlowContext context = mock(AuthenticationFlowContext.class);
        AuthenticatorConfigModel configModel = mock(AuthenticatorConfigModel.class);
        Map<String, String> expectedConfig = TestUtils.defaultConfig();

        when(context.getAuthenticatorConfig()).thenReturn(configModel);
        when(configModel.getConfig()).thenReturn(expectedConfig);

        Map<String, String> config = CloudflareTurnstileValidator.getConfig(context);

        assertThat(config).isEqualTo(expectedConfig);
    }

    @Test
    @DisplayName("getConfig should return empty map when config is null")
    void testGetConfig_NullConfig() {
        AuthenticationFlowContext context = mock(AuthenticationFlowContext.class);

        when(context.getAuthenticatorConfig()).thenReturn(null);

        Map<String, String> config = CloudflareTurnstileValidator.getConfig(context);

        assertThat(config).isEmpty();
    }
}
