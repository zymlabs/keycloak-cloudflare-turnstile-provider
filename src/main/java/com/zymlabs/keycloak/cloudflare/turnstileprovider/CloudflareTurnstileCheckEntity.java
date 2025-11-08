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
                @Index(name = "idx_turnstile_session_id", columnList = "session_id")
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
                        "AND c.success = false ORDER BY c.timestamp DESC")
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

    @Column(name = "raw_response", columnDefinition = "TEXT")
    private String rawResponse;

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

    public String getRawResponse() {
        return rawResponse;
    }

    public void setRawResponse(String rawResponse) {
        this.rawResponse = rawResponse;
    }

    @Override
    public String toString() {
        return String.format("CloudflareTurnstileCheckEntity{id=%d, userId='%s', realmId='%s', " +
                        "ipAddress='%s', timestamp=%s, success=%s, errorCodes='%s'}",
                id, userId, realmId, ipAddress, timestamp, success, errorCodes);
    }
}
