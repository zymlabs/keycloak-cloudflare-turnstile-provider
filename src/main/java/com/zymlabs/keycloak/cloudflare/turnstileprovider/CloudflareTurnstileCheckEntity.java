package com.zymlabs.keycloak.cloudflare.turnstileprovider;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * JPA Entity for storing Cloudflare Turnstile verification checks.
 *
 * This entity records each Turnstile verification attempt for auditing,
 * analytics, and security monitoring purposes.
 */
@Entity
@Table(name = "cloudflare_turnstile_check",
        indexes = {
                @Index(name = "idx_turnstile_user_id", columnList = "user_id"),
                @Index(name = "idx_turnstile_realm_id", columnList = "realm_id"),
                @Index(name = "idx_turnstile_timestamp", columnList = "timestamp"),
                @Index(name = "idx_turnstile_user_timestamp", columnList = "user_id,timestamp"),
                @Index(name = "idx_turnstile_event_id", columnList = "event_id"),
                @Index(name = "idx_turnstile_session_id", columnList = "session_id"),
                @Index(name = "idx_turnstile_flow_type", columnList = "flow_type"),
                @Index(name = "idx_turnstile_auth_allowed", columnList = "authentication_allowed"),
                @Index(name = "idx_turnstile_fail_mode", columnList = "fail_mode"),
                @Index(name = "idx_turnstile_fail_action", columnList = "fail_action"),
                @Index(name = "idx_turnstile_ip_allowlisted", columnList = "ip_allowlisted"),
                @Index(name = "idx_turnstile_verification_skipped", columnList = "verification_skipped"),
                @Index(name = "idx_turnstile_action_reason", columnList = "action_reason"),
                @Index(name = "idx_turnstile_outcome_analysis", columnList = "realm_id,authentication_allowed,success,timestamp")
        })
@NamedQueries({
        @NamedQuery(name = "findByUserId",
                query = "SELECT c FROM CloudflareTurnstileCheckEntity c WHERE c.userId = :userId ORDER BY c.timestamp DESC"),
        @NamedQuery(name = "findByRealmId",
                query = "SELECT c FROM CloudflareTurnstileCheckEntity c WHERE c.realmId = :realmId ORDER BY c.timestamp DESC"),
        @NamedQuery(name = "findByUserIdAndDateRange",
                query = "SELECT c FROM CloudflareTurnstileCheckEntity c WHERE c.userId = :userId " +
                        "AND c.timestamp BETWEEN :startDate AND :endDate ORDER BY c.timestamp DESC"),
        @NamedQuery(name = "findFailedByRealm",
                query = "SELECT c FROM CloudflareTurnstileCheckEntity c WHERE c.realmId = :realmId " +
                        "AND c.success = false ORDER BY c.timestamp DESC"),
        @NamedQuery(name = "findByFlowType",
                query = "SELECT c FROM CloudflareTurnstileCheckEntity c WHERE c.realmId = :realmId " +
                        "AND c.flowType = :flowType ORDER BY c.timestamp DESC")
})
public class CloudflareTurnstileCheckEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", length = 36)
    private String userId;

    @Column(name = "realm_id", length = 36, nullable = false)
    private String realmId;

    @Column(name = "username", length = 255)
    private String username;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "ip_address", length = 45, nullable = false)
    private String ipAddress;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Column(name = "success", nullable = false)
    private boolean success;

    @Column(name = "error_codes", length = 500)
    private String errorCodes;

    @Column(name = "challenge_ts", length = 50)
    private String challengeTs;

    @Column(name = "hostname", length = 255)
    private String hostname;

    @Column(name = "event_id", length = 36)
    private String eventId;

    @Column(name = "session_id", length = 36)
    private String sessionId;

    @Column(name = "flow_type", length = 50)
    private String flowType;

    @Column(name = "raw_response", columnDefinition = "TEXT")
    private String rawResponse;

    // Configuration context columns
    @Column(name = "fail_mode", length = 20)
    private String failMode;

    @Column(name = "fail_action", length = 20)
    private String failAction;

    @Column(name = "allowlist_behavior", length = 30)
    private String allowlistBehavior;

    @Column(name = "implementation_method", length = 30)
    private String implementationMethod;

    // IP processing status columns
    @Column(name = "ip_allowlisted", nullable = false)
    private boolean ipAllowlisted = false;

    @Column(name = "ip_blocklisted", nullable = false)
    private boolean ipBlocklisted = false;

    @Column(name = "verification_skipped", nullable = false)
    private boolean verificationSkipped = false;

    // Final outcome columns
    @Column(name = "authentication_allowed", nullable = false)
    private boolean authenticationAllowed = false;

    @Column(name = "action_reason", length = 100)
    private String actionReason;

    // Constructors

    public CloudflareTurnstileCheckEntity() {
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getRealmId() {
        return realmId;
    }

    public void setRealmId(String realmId) {
        this.realmId = realmId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorCodes() {
        return errorCodes;
    }

    public void setErrorCodes(String errorCodes) {
        this.errorCodes = errorCodes;
    }

    public String getChallengeTs() {
        return challengeTs;
    }

    public void setChallengeTs(String challengeTs) {
        this.challengeTs = challengeTs;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getFlowType() {
        return flowType;
    }

    public void setFlowType(String flowType) {
        this.flowType = flowType;
    }

    public String getRawResponse() {
        return rawResponse;
    }

    public void setRawResponse(String rawResponse) {
        this.rawResponse = rawResponse;
    }

    public String getFailMode() {
        return failMode;
    }

    public void setFailMode(String failMode) {
        this.failMode = failMode;
    }

    public String getFailAction() {
        return failAction;
    }

    public void setFailAction(String failAction) {
        this.failAction = failAction;
    }

    public String getAllowlistBehavior() {
        return allowlistBehavior;
    }

    public void setAllowlistBehavior(String allowlistBehavior) {
        this.allowlistBehavior = allowlistBehavior;
    }

    public String getImplementationMethod() {
        return implementationMethod;
    }

    public void setImplementationMethod(String implementationMethod) {
        this.implementationMethod = implementationMethod;
    }

    public boolean isIpAllowlisted() {
        return ipAllowlisted;
    }

    public void setIpAllowlisted(boolean ipAllowlisted) {
        this.ipAllowlisted = ipAllowlisted;
    }

    public boolean isIpBlocklisted() {
        return ipBlocklisted;
    }

    public void setIpBlocklisted(boolean ipBlocklisted) {
        this.ipBlocklisted = ipBlocklisted;
    }

    public boolean isVerificationSkipped() {
        return verificationSkipped;
    }

    public void setVerificationSkipped(boolean verificationSkipped) {
        this.verificationSkipped = verificationSkipped;
    }

    public boolean isAuthenticationAllowed() {
        return authenticationAllowed;
    }

    public void setAuthenticationAllowed(boolean authenticationAllowed) {
        this.authenticationAllowed = authenticationAllowed;
    }

    public String getActionReason() {
        return actionReason;
    }

    public void setActionReason(String actionReason) {
        this.actionReason = actionReason;
    }

    @Override
    public String toString() {
        return String.format("CloudflareTurnstileCheckEntity{id=%d, userId='%s', realmId='%s', " +
                        "ipAddress='%s', timestamp=%s, success=%s, flowType='%s', errorCodes='%s'}",
                id, userId, realmId, ipAddress, timestamp, success, flowType, errorCodes);
    }
}
