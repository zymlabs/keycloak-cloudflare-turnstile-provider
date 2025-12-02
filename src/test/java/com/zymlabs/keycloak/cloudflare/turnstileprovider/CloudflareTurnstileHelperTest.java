package com.zymlabs.keycloak.cloudflare.turnstileprovider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.keycloak.events.EventBuilder;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import jakarta.persistence.EntityManager;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CloudflareTurnstileHelper utility class.
 */
@DisplayName("CloudflareTurnstileHelper Tests")
class CloudflareTurnstileHelperTest {

    // ===== EVENT LOGGING METHODS TESTS =====

    @Test
    @DisplayName("logIpBlockedEvent should add correct event details")
    void testLogIpBlockedEvent_WithValidEvent() {
        EventBuilder event = TestUtils.mockEventBuilder();
        String ipAddress = "192.168.1.1";

        CloudflareTurnstileHelper.logIpBlockedEvent(event, ipAddress);

        verify(event).detail("cloudflare_turnstile_result", "blocked_ip");
        verify(event).detail("ip_address", ipAddress);
    }

    @Test
    @DisplayName("logIpBlockedEvent should handle null event gracefully")
    void testLogIpBlockedEvent_WithNullEvent() {
        assertThatCode(() -> CloudflareTurnstileHelper.logIpBlockedEvent(null, "192.168.1.1"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("logSkipVerificationEvent should add correct event details")
    void testLogSkipVerificationEvent() {
        EventBuilder event = TestUtils.mockEventBuilder();
        String ipAddress = "192.168.1.1";

        CloudflareTurnstileHelper.logSkipVerificationEvent(event, ipAddress);

        verify(event).detail("cloudflare_turnstile_result", "ip_allowlisted_skip_verification");
        verify(event).detail("ip_address", ipAddress);
    }

    @Test
    @DisplayName("logSkipVerificationEvent should handle null event gracefully")
    void testLogSkipVerificationEvent_WithNullEvent() {
        assertThatCode(() -> CloudflareTurnstileHelper.logSkipVerificationEvent(null, "192.168.1.1"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("logVerifyButAllowEvent should add verification result details")
    void testLogVerifyButAllowEvent_WithResult() {
        EventBuilder event = TestUtils.mockEventBuilder();
        CloudflareTurnstileService.TurnstileVerificationResult result = TestUtils.successResult();
        String ipAddress = "192.168.1.1";

        CloudflareTurnstileHelper.logVerifyButAllowEvent(event, result, ipAddress, false);

        verify(event).detail("cloudflare_turnstile_success", "true");
        verify(event).detail("cloudflare_turnstile_hostname", TestUtils.TEST_HOSTNAME);
        verify(event).detail("cloudflare_turnstile_flow_type", "login");
        verify(event).detail("cloudflare_turnstile_action", "ip_allowlisted_verify_but_allow");
        verify(event).detail("ip_address", ipAddress);
    }

    @Test
    @DisplayName("logVerifyButAllowEvent should handle registration flow")
    void testLogVerifyButAllowEvent_RegistrationFlow() {
        EventBuilder event = TestUtils.mockEventBuilder();
        CloudflareTurnstileService.TurnstileVerificationResult result = TestUtils.successResult();

        CloudflareTurnstileHelper.logVerifyButAllowEvent(event, result, "192.168.1.1", true);

        verify(event).detail("cloudflare_turnstile_flow_type", "registration");
    }

    @Test
    @DisplayName("logVerifyButAllowEvent should handle null result with fallback")
    void testLogVerifyButAllowEvent_WithNullResult() {
        EventBuilder event = TestUtils.mockEventBuilder();

        CloudflareTurnstileHelper.logVerifyButAllowEvent(event, null, "192.168.1.1", false);

        verify(event).detail("cloudflare_turnstile_action", "ip_allowlisted_verify_but_allow");
        verify(event).detail("ip_address", "192.168.1.1");
        // Should not call details that require result object
        verify(event, never()).detail(eq("cloudflare_turnstile_success"), anyString());
    }

    @Test
    @DisplayName("logVerifyButAllowEvent should include error codes when present")
    void testLogVerifyButAllowEvent_WithErrorCodes() {
        EventBuilder event = TestUtils.mockEventBuilder();
        CloudflareTurnstileService.TurnstileVerificationResult result = TestUtils.failureResult("timeout-or-duplicate");

        CloudflareTurnstileHelper.logVerifyButAllowEvent(event, result, "192.168.1.1", false);

        verify(event).detail("cloudflare_turnstile_errors", "timeout-or-duplicate");
    }

    @Test
    @DisplayName("logVerificationResult should log successful verification")
    void testLogVerificationResult_Success() {
        EventBuilder event = TestUtils.mockEventBuilder();
        CloudflareTurnstileService.TurnstileVerificationResult result = TestUtils.successResult();

        CloudflareTurnstileHelper.logVerificationResult(event, result, "192.168.1.1", false);

        verify(event).detail("cloudflare_turnstile_success", "true");
        verify(event).detail("cloudflare_turnstile_hostname", TestUtils.TEST_HOSTNAME);
        verify(event).detail("cloudflare_turnstile_flow_type", "login");
        verify(event).detail("ip_address", "192.168.1.1");
    }

    @Test
    @DisplayName("logVerificationResult should log failed verification with error codes")
    void testLogVerificationResult_Failure() {
        EventBuilder event = TestUtils.mockEventBuilder();
        CloudflareTurnstileService.TurnstileVerificationResult result =
                TestUtils.failureResult("invalid-input-response");

        CloudflareTurnstileHelper.logVerificationResult(event, result, "192.168.1.1", true);

        verify(event).detail("cloudflare_turnstile_success", "false");
        verify(event).detail("cloudflare_turnstile_errors", "invalid-input-response");
        verify(event).detail("cloudflare_turnstile_flow_type", "registration");
    }

    @Test
    @DisplayName("logVerificationResult should handle null event")
    void testLogVerificationResult_NullEvent() {
        CloudflareTurnstileService.TurnstileVerificationResult result = TestUtils.successResult();

        assertThatCode(() -> CloudflareTurnstileHelper.logVerificationResult(null, result, "192.168.1.1", false))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("logVerificationResult should handle null result")
    void testLogVerificationResult_NullResult() {
        EventBuilder event = TestUtils.mockEventBuilder();

        assertThatCode(() -> CloudflareTurnstileHelper.logVerificationResult(event, null, "192.168.1.1", false))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("logVerificationError should log fail open error")
    void testLogVerificationError_FailOpen() {
        EventBuilder event = TestUtils.mockEventBuilder();

        CloudflareTurnstileHelper.logVerificationError(event, "Connection timeout", true, false);

        verify(event).detail("cloudflare_turnstile_result", "verification_error_fail_open");
        verify(event).detail("error_message", "Connection timeout");
    }

    @Test
    @DisplayName("logVerificationError should log fail closed error")
    void testLogVerificationError_FailClosed() {
        EventBuilder event = TestUtils.mockEventBuilder();

        CloudflareTurnstileHelper.logVerificationError(event, "Network error", false, true);

        verify(event).detail("cloudflare_turnstile_result", "verification_error");
        verify(event).detail("error_message", "Network error");
    }

    @Test
    @DisplayName("logVerificationError should handle null event")
    void testLogVerificationError_NullEvent() {
        assertThatCode(() -> CloudflareTurnstileHelper.logVerificationError(null, "error", true, false))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("logFailAction should log the action taken")
    void testLogFailAction() {
        EventBuilder event = TestUtils.mockEventBuilder();

        CloudflareTurnstileHelper.logFailAction(event, "blocked");

        verify(event).detail("cloudflare_turnstile_action", "blocked");
    }

    @Test
    @DisplayName("logFailAction should handle null event")
    void testLogFailAction_NullEvent() {
        assertThatCode(() -> CloudflareTurnstileHelper.logFailAction(null, "allowed"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("addAuditContextToEvent should add all audit fields")
    void testAddAuditContextToEvent_AllFields() {
        EventBuilder event = TestUtils.mockEventBuilder();

        CloudflareTurnstileHelper.addAuditContextToEvent(
                event,
                "FAIL_CLOSED",
                "BLOCK",
                "VERIFY_BUT_ALLOW",
                "SEPARATE_PAGE",
                false,
                false,
                false,
                true,
                "Success"
        );

        verify(event).detail("cloudflare_turnstile_fail_mode", "FAIL_CLOSED");
        verify(event).detail("cloudflare_turnstile_fail_action", "BLOCK");
        verify(event).detail("cloudflare_turnstile_allowlist_behavior", "VERIFY_BUT_ALLOW");
        verify(event).detail("cloudflare_turnstile_implementation_method", "SEPARATE_PAGE");
        verify(event).detail("cloudflare_turnstile_ip_allowlisted", "false");
        verify(event).detail("cloudflare_turnstile_ip_blocklisted", "false");
        verify(event).detail("cloudflare_turnstile_verification_skipped", "false");
        verify(event).detail("cloudflare_turnstile_authentication_allowed", "true");
        verify(event).detail("cloudflare_turnstile_action_reason", "Success");
    }

    @Test
    @DisplayName("addAuditContextToEvent should handle null optional fields")
    void testAddAuditContextToEvent_NullOptionalFields() {
        EventBuilder event = TestUtils.mockEventBuilder();

        CloudflareTurnstileHelper.addAuditContextToEvent(
                event,
                null,  // failMode
                null,  // failAction
                null,  // allowlistBehavior
                null,  // implementationMethod
                true,
                false,
                true,
                true,
                null   // actionReason
        );

        // Should not call detail() for null config fields
        verify(event, never()).detail(eq("cloudflare_turnstile_fail_mode"), anyString());
        verify(event, never()).detail(eq("cloudflare_turnstile_fail_action"), anyString());
        verify(event, never()).detail(eq("cloudflare_turnstile_allowlist_behavior"), anyString());
        verify(event, never()).detail(eq("cloudflare_turnstile_implementation_method"), anyString());
        verify(event, never()).detail(eq("cloudflare_turnstile_action_reason"), anyString());

        // Should still call detail() for boolean flags
        verify(event).detail("cloudflare_turnstile_ip_allowlisted", "true");
        verify(event).detail("cloudflare_turnstile_ip_blocklisted", "false");
        verify(event).detail("cloudflare_turnstile_verification_skipped", "true");
        verify(event).detail("cloudflare_turnstile_authentication_allowed", "true");
    }

    @Test
    @DisplayName("addAuditContextToEvent should handle null event")
    void testAddAuditContextToEvent_NullEvent() {
        assertThatCode(() -> CloudflareTurnstileHelper.addAuditContextToEvent(
                null, "FAIL_CLOSED", "BLOCK", "VERIFY_BUT_ALLOW", "SEPARATE_PAGE",
                false, false, false, true, "Success"))
                .doesNotThrowAnyException();
    }

    // ===== DATABASE STORAGE METHODS TESTS =====

    @Test
    @DisplayName("createVerificationEntity should populate all fields with full data")
    void testCreateVerificationEntity_FullData() {
        CloudflareTurnstileService.TurnstileVerificationResult result = TestUtils.successResult();
        RealmModel realm = TestUtils.mockRealm(TestUtils.TEST_REALM_ID);
        UserModel user = TestUtils.mockUser(TestUtils.TEST_USER_ID, TestUtils.TEST_USERNAME, TestUtils.TEST_EMAIL);

        CloudflareTurnstileCheckEntity entity = CloudflareTurnstileHelper.createVerificationEntity(
                result,
                TestUtils.TEST_IP_ADDRESS,
                realm,
                user,
                TestUtils.TEST_SESSION_ID,
                TestUtils.TEST_EVENT_ID,
                "login",
                "FAIL_CLOSED",
                "BLOCK",
                "VERIFY_BUT_ALLOW",
                "SEPARATE_PAGE",
                false,
                false,
                false,
                true,
                "Success"
        );

        // User context
        assertThat(entity.getUserId()).isEqualTo(TestUtils.TEST_USER_ID);
        assertThat(entity.getUsername()).isEqualTo(TestUtils.TEST_USERNAME);
        assertThat(entity.getEmail()).isEqualTo(TestUtils.TEST_EMAIL);
        assertThat(entity.getRealmId()).isEqualTo(TestUtils.TEST_REALM_ID);

        // Request data
        assertThat(entity.getIpAddress()).isEqualTo(TestUtils.TEST_IP_ADDRESS);
        assertThat(entity.getTimestamp()).isNotNull();
        assertThat(entity.getFlowType()).isEqualTo("login");

        // Verification result
        assertThat(entity.isSuccess()).isTrue();
        assertThat(entity.getErrorCodes()).isNull();
        assertThat(entity.getChallengeTs()).isEqualTo(TestUtils.TEST_CHALLENGE_TS);
        assertThat(entity.getHostname()).isEqualTo(TestUtils.TEST_HOSTNAME);
        assertThat(entity.getRawResponse()).isNotNull();

        // Configuration context
        assertThat(entity.getFailMode()).isEqualTo("FAIL_CLOSED");
        assertThat(entity.getFailAction()).isEqualTo("BLOCK");
        assertThat(entity.getAllowlistBehavior()).isEqualTo("VERIFY_BUT_ALLOW");
        assertThat(entity.getImplementationMethod()).isEqualTo("SEPARATE_PAGE");

        // IP processing status
        assertThat(entity.isIpAllowlisted()).isFalse();
        assertThat(entity.isIpBlocklisted()).isFalse();
        assertThat(entity.isVerificationSkipped()).isFalse();

        // Final outcome
        assertThat(entity.isAuthenticationAllowed()).isTrue();
        assertThat(entity.getActionReason()).isEqualTo("Success");

        // Correlation
        assertThat(entity.getSessionId()).isEqualTo(TestUtils.TEST_SESSION_ID);
        assertThat(entity.getEventId()).isEqualTo(TestUtils.TEST_EVENT_ID);
    }

    @Test
    @DisplayName("createVerificationEntity should handle null user (pre-auth scenario)")
    void testCreateVerificationEntity_NullUser() {
        CloudflareTurnstileService.TurnstileVerificationResult result = TestUtils.successResult();
        RealmModel realm = TestUtils.mockRealm(TestUtils.TEST_REALM_ID);

        CloudflareTurnstileCheckEntity entity = CloudflareTurnstileHelper.createVerificationEntity(
                result,
                TestUtils.TEST_IP_ADDRESS,
                realm,
                null,  // No user
                null,
                null,
                "login",
                "FAIL_CLOSED",
                "BLOCK",
                "VERIFY_BUT_ALLOW",
                "SEPARATE_PAGE",
                false,
                false,
                false,
                true,
                "Success"
        );

        // User fields should be null
        assertThat(entity.getUserId()).isNull();
        assertThat(entity.getUsername()).isNull();
        assertThat(entity.getEmail()).isNull();

        // Other fields should still be populated
        assertThat(entity.getRealmId()).isEqualTo(TestUtils.TEST_REALM_ID);
        assertThat(entity.getIpAddress()).isEqualTo(TestUtils.TEST_IP_ADDRESS);
    }

    @Test
    @DisplayName("createVerificationEntity should handle null optional fields")
    void testCreateVerificationEntity_NullOptionalFields() {
        CloudflareTurnstileService.TurnstileVerificationResult result = TestUtils.successResult();
        RealmModel realm = TestUtils.mockRealm(TestUtils.TEST_REALM_ID);

        CloudflareTurnstileCheckEntity entity = CloudflareTurnstileHelper.createVerificationEntity(
                result,
                TestUtils.TEST_IP_ADDRESS,
                realm,
                null,
                null,  // sessionId
                null,  // eventId
                null,  // flowType
                "FAIL_CLOSED",
                "BLOCK",
                "VERIFY_BUT_ALLOW",
                "SEPARATE_PAGE",
                false,
                false,
                false,
                true,
                "Success"
        );

        assertThat(entity.getSessionId()).isNull();
        assertThat(entity.getEventId()).isNull();
        assertThat(entity.getFlowType()).isNull();
        assertThat(entity.getRealmId()).isNotNull();
    }

    @Test
    @DisplayName("storeVerificationResult should persist entity successfully")
    void testStoreVerificationResult_Success() {
        EntityManager em = TestUtils.mockEntityManager();
        KeycloakSession session = TestUtils.mockKeycloakSession(em);
        CloudflareTurnstileService.TurnstileVerificationResult result = TestUtils.successResult();
        RealmModel realm = TestUtils.mockRealm(TestUtils.TEST_REALM_ID);
        UserModel user = TestUtils.mockUser(TestUtils.TEST_USER_ID, TestUtils.TEST_USERNAME, TestUtils.TEST_EMAIL);

        CloudflareTurnstileHelper.storeVerificationResult(
                session,
                result,
                TestUtils.TEST_IP_ADDRESS,
                realm,
                user,
                TestUtils.TEST_SESSION_ID,
                TestUtils.TEST_EVENT_ID,
                "login",
                "FAIL_CLOSED",
                "BLOCK",
                "VERIFY_BUT_ALLOW",
                "SEPARATE_PAGE",
                false,
                false,
                false,
                true,
                "Success"
        );

        verify(em).persist(any(CloudflareTurnstileCheckEntity.class));
    }

    @Test
    @DisplayName("storeVerificationResult should handle database exception gracefully")
    void testStoreVerificationResult_DatabaseException() {
        EntityManager em = TestUtils.mockEntityManager();
        doThrow(new RuntimeException("Database error")).when(em).persist(any());
        KeycloakSession session = TestUtils.mockKeycloakSession(em);
        CloudflareTurnstileService.TurnstileVerificationResult result = TestUtils.successResult();
        RealmModel realm = TestUtils.mockRealm(TestUtils.TEST_REALM_ID);

        // Should not throw exception
        assertThatCode(() -> CloudflareTurnstileHelper.storeVerificationResult(
                session, result, TestUtils.TEST_IP_ADDRESS, realm, null, null, null, "login",
                "FAIL_CLOSED", "BLOCK", "VERIFY_BUT_ALLOW", "SEPARATE_PAGE",
                false, false, false, true, "Success"))
                .doesNotThrowAnyException();
    }

    // ===== UTILITY METHODS TESTS =====

    @Test
    @DisplayName("urlEncode should properly encode strings")
    void testUrlEncode_ValidString() {
        assertThat(CloudflareTurnstileHelper.urlEncode("hello world"))
                .isEqualTo("hello+world");
        assertThat(CloudflareTurnstileHelper.urlEncode("test@example.com"))
                .isEqualTo("test%40example.com");
    }

    @Test
    @DisplayName("urlEncode should handle null value")
    void testUrlEncode_NullValue() {
        assertThat(CloudflareTurnstileHelper.urlEncode(null))
                .isEqualTo("");
    }

    @Test
    @DisplayName("urlEncode should handle special characters")
    void testUrlEncode_SpecialCharacters() {
        assertThat(CloudflareTurnstileHelper.urlEncode("a&b=c"))
                .isEqualTo("a%26b%3Dc");
    }

    @Test
    @DisplayName("getConnectTimeout should return configured value")
    void testGetConnectTimeout_ConfiguredValue() {
        Map<String, String> config = TestUtils.configWith(
                CloudflareTurnstileAuthenticator.CONFIG_CONNECT_TIMEOUT, "3000");

        assertThat(CloudflareTurnstileHelper.getConnectTimeout(config))
                .isEqualTo(3000);
    }

    @Test
    @DisplayName("getConnectTimeout should return default value when not configured")
    void testGetConnectTimeout_DefaultValue() {
        Map<String, String> config = TestUtils.emptyConfig();

        assertThat(CloudflareTurnstileHelper.getConnectTimeout(config))
                .isEqualTo(5000);
    }

    @Test
    @DisplayName("getReadTimeout should return configured value")
    void testGetReadTimeout_ConfiguredValue() {
        Map<String, String> config = TestUtils.configWith(
                CloudflareTurnstileAuthenticator.CONFIG_READ_TIMEOUT, "7000");

        assertThat(CloudflareTurnstileHelper.getReadTimeout(config))
                .isEqualTo(7000);
    }

    @Test
    @DisplayName("getReadTimeout should return default value when not configured")
    void testGetReadTimeout_DefaultValue() {
        Map<String, String> config = TestUtils.emptyConfig();

        assertThat(CloudflareTurnstileHelper.getReadTimeout(config))
                .isEqualTo(5000);
    }

    // ===== URL BUILDER METHODS TESTS =====

    @Test
    @DisplayName("getContextPath should return empty string for null UriInfo")
    void testGetContextPath_NullUriInfo() {
        assertThat(CloudflareTurnstileHelper.getContextPath(null))
                .isEqualTo("");
    }

    @Test
    @DisplayName("getContextPath should return empty string for root path")
    void testGetContextPath_RootPath() throws Exception {
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getBaseUri()).thenReturn(new URI("http://localhost:8080/"));

        assertThat(CloudflareTurnstileHelper.getContextPath(uriInfo))
                .isEqualTo("");
    }

    @Test
    @DisplayName("getContextPath should return context path with /auth prefix")
    void testGetContextPath_AuthPrefix() throws Exception {
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getBaseUri()).thenReturn(new URI("http://localhost:8080/auth/"));

        assertThat(CloudflareTurnstileHelper.getContextPath(uriInfo))
                .isEqualTo("/auth");
    }

    @Test
    @DisplayName("getContextPath should handle custom context path")
    void testGetContextPath_CustomPath() throws Exception {
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getBaseUri()).thenReturn(new URI("http://localhost:8080/myapp/"));

        assertThat(CloudflareTurnstileHelper.getContextPath(uriInfo))
                .isEqualTo("/myapp");
    }

    @Test
    @DisplayName("getContextPath should handle path without trailing slash")
    void testGetContextPath_NoTrailingSlash() throws Exception {
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getBaseUri()).thenReturn(new URI("http://localhost:8080/auth"));

        assertThat(CloudflareTurnstileHelper.getContextPath(uriInfo))
                .isEqualTo("/auth");
    }

    @Test
    @DisplayName("getContextPath should handle exception gracefully")
    void testGetContextPath_Exception() {
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getBaseUri()).thenThrow(new RuntimeException("URI error"));

        assertThat(CloudflareTurnstileHelper.getContextPath(uriInfo))
                .isEqualTo("");
    }

    @Test
    @DisplayName("buildConfigJsUrl should build correct URL without context path")
    void testBuildConfigJsUrl_NoContextPath() throws Exception {
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getBaseUri()).thenReturn(new URI("http://localhost:8080/"));

        String url = CloudflareTurnstileHelper.buildConfigJsUrl(
                uriInfo, "myrealm", "cloudflare-turnstile", "sitekey123", "managed", "auto");

        assertThat(url).isEqualTo("/realms/myrealm/cloudflare-turnstile/config.js?siteKey=sitekey123&mode=managed&theme=auto&debug=false");
    }

    @Test
    @DisplayName("buildConfigJsUrl should build correct URL with /auth context path")
    void testBuildConfigJsUrl_WithAuthContext() throws Exception {
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getBaseUri()).thenReturn(new URI("http://localhost:8080/auth/"));

        String url = CloudflareTurnstileHelper.buildConfigJsUrl(
                uriInfo, "myrealm", "cloudflare-turnstile", "sitekey123", "managed", "auto");

        assertThat(url).isEqualTo("/auth/realms/myrealm/cloudflare-turnstile/config.js?siteKey=sitekey123&mode=managed&theme=auto&debug=false");
    }

    @Test
    @DisplayName("buildConfigJsUrl should URL-encode special characters")
    void testBuildConfigJsUrl_EncodesSpecialCharacters() throws Exception {
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getBaseUri()).thenReturn(new URI("http://localhost:8080/"));

        String url = CloudflareTurnstileHelper.buildConfigJsUrl(
                uriInfo, "my-realm", "cloudflare-turnstile", "key with space", "managed", "auto");

        assertThat(url).contains("siteKey=key+with+space");
    }

    @Test
    @DisplayName("buildConfigJsUrl should handle null UriInfo")
    void testBuildConfigJsUrl_NullUriInfo() {
        String url = CloudflareTurnstileHelper.buildConfigJsUrl(
                null, "myrealm", "cloudflare-turnstile", "sitekey123", "managed", "auto");

        assertThat(url).isEqualTo("/realms/myrealm/cloudflare-turnstile/config.js?siteKey=sitekey123&mode=managed&theme=auto&debug=false");
    }

    @Test
    @DisplayName("buildConfigJsUrl should include debug=true when enableDebugLogging is true")
    void testBuildConfigJsUrl_WithDebugLoggingEnabled() throws Exception {
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getBaseUri()).thenReturn(new URI("http://localhost:8080/"));

        String url = CloudflareTurnstileHelper.buildConfigJsUrl(
                uriInfo, "myrealm", "cloudflare-turnstile", "sitekey123", "managed", "auto", true);

        assertThat(url).isEqualTo("/realms/myrealm/cloudflare-turnstile/config.js?siteKey=sitekey123&mode=managed&theme=auto&debug=true");
    }

    @Test
    @DisplayName("buildConfigJsUrl should include debug=false when enableDebugLogging is false")
    void testBuildConfigJsUrl_WithDebugLoggingDisabled() throws Exception {
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getBaseUri()).thenReturn(new URI("http://localhost:8080/"));

        String url = CloudflareTurnstileHelper.buildConfigJsUrl(
                uriInfo, "myrealm", "cloudflare-turnstile", "sitekey123", "managed", "auto", false);

        assertThat(url).isEqualTo("/realms/myrealm/cloudflare-turnstile/config.js?siteKey=sitekey123&mode=managed&theme=auto&debug=false");
    }

    @Test
    @DisplayName("buildInjectorJsUrl should build correct URL without context path")
    void testBuildInjectorJsUrl_NoContextPath() throws Exception {
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getBaseUri()).thenReturn(new URI("http://localhost:8080/"));

        String url = CloudflareTurnstileHelper.buildInjectorJsUrl(uriInfo, "myrealm", "cloudflare-turnstile");

        assertThat(url).isEqualTo("/realms/myrealm/cloudflare-turnstile/resources/js/turnstile-injector.js");
    }

    @Test
    @DisplayName("buildInjectorJsUrl should build correct URL with /auth context path")
    void testBuildInjectorJsUrl_WithAuthContext() throws Exception {
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getBaseUri()).thenReturn(new URI("http://localhost:8080/auth/"));

        String url = CloudflareTurnstileHelper.buildInjectorJsUrl(uriInfo, "myrealm", "cloudflare-turnstile");

        assertThat(url).isEqualTo("/auth/realms/myrealm/cloudflare-turnstile/resources/js/turnstile-injector.js");
    }

    @Test
    @DisplayName("buildInjectorJsUrl should handle null UriInfo")
    void testBuildInjectorJsUrl_NullUriInfo() {
        String url = CloudflareTurnstileHelper.buildInjectorJsUrl(null, "myrealm", "cloudflare-turnstile");

        assertThat(url).isEqualTo("/realms/myrealm/cloudflare-turnstile/resources/js/turnstile-injector.js");
    }

    @Test
    @DisplayName("buildInjectorJsUrl should handle custom context path")
    void testBuildInjectorJsUrl_CustomContextPath() throws Exception {
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getBaseUri()).thenReturn(new URI("http://localhost:8080/keycloak/"));

        String url = CloudflareTurnstileHelper.buildInjectorJsUrl(uriInfo, "myrealm", "cloudflare-turnstile");

        assertThat(url).isEqualTo("/keycloak/realms/myrealm/cloudflare-turnstile/resources/js/turnstile-injector.js");
    }
}
