package com.zymlabs.keycloak.cloudflare.turnstileprovider;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for IpAddressUtils - IP address and CIDR matching.
 *
 * Tests both IPv4 and IPv6 addresses with various CIDR notations and edge cases.
 */
@DisplayName("IP Address Utils Tests")
class IpAddressUtilsTest {

    // ========== IPv4 Single IP Matching ==========

    @Test
    @DisplayName("Should match single IPv4 address")
    void testSingleIpv4Match() {
        assertThat(IpAddressUtils.isIpInList("192.168.1.100", "192.168.1.100"))
                .isTrue();
    }

    @Test
    @DisplayName("Should not match different IPv4 address")
    void testSingleIpv4NoMatch() {
        assertThat(IpAddressUtils.isIpInList("192.168.1.100", "192.168.1.101"))
                .isFalse();
    }

    @Test
    @DisplayName("Should match IPv4 in comma-separated list")
    void testIpv4InList() {
        assertThat(IpAddressUtils.isIpInList("192.168.1.100", "192.168.1.1,192.168.1.100,192.168.1.200"))
                .isTrue();
    }

    @Test
    @DisplayName("Should handle whitespace in IP list")
    void testIpv4WithWhitespace() {
        assertThat(IpAddressUtils.isIpInList("192.168.1.100", " 192.168.1.1 , 192.168.1.100 , 192.168.1.200 "))
                .isTrue();
    }

    // ========== IPv4 CIDR Matching ==========

    @Test
    @DisplayName("Should match IPv4 in /24 CIDR range")
    void testIpv4InCidr24() {
        assertThat(IpAddressUtils.isIpInList("192.168.1.100", "192.168.1.0/24"))
                .isTrue();
    }

    @Test
    @DisplayName("Should not match IPv4 outside /24 CIDR range")
    void testIpv4OutsideCidr24() {
        assertThat(IpAddressUtils.isIpInList("192.168.2.100", "192.168.1.0/24"))
                .isFalse();
    }

    @Test
    @DisplayName("Should match IPv4 in /16 CIDR range")
    void testIpv4InCidr16() {
        assertThat(IpAddressUtils.isIpInList("192.168.50.100", "192.168.0.0/16"))
                .isTrue();
    }

    @Test
    @DisplayName("Should match IPv4 in /8 CIDR range")
    void testIpv4InCidr8() {
        assertThat(IpAddressUtils.isIpInList("10.50.100.200", "10.0.0.0/8"))
                .isTrue();
    }

    @Test
    @DisplayName("Should match IPv4 in /32 CIDR (single host)")
    void testIpv4InCidr32() {
        assertThat(IpAddressUtils.isIpInList("192.168.1.100", "192.168.1.100/32"))
                .isTrue();

        assertThat(IpAddressUtils.isIpInList("192.168.1.101", "192.168.1.100/32"))
                .isFalse();
    }

    @Test
    @DisplayName("Should match IPv4 in /0 CIDR (all addresses)")
    void testIpv4InCidr0() {
        assertThat(IpAddressUtils.isIpInList("1.2.3.4", "0.0.0.0/0"))
                .isTrue();

        assertThat(IpAddressUtils.isIpInList("255.255.255.255", "0.0.0.0/0"))
                .isTrue();
    }

    // ========== IPv6 Single IP Matching ==========

    @Test
    @DisplayName("Should match single IPv6 address")
    void testSingleIpv6Match() {
        assertThat(IpAddressUtils.isIpInList("2001:db8::1", "2001:db8::1"))
                .isTrue();
    }

    @Test
    @DisplayName("Should not match different IPv6 address")
    void testSingleIpv6NoMatch() {
        assertThat(IpAddressUtils.isIpInList("2001:db8::1", "2001:db8::2"))
                .isFalse();
    }

    @Test
    @DisplayName("Should match IPv6 loopback")
    void testIpv6Loopback() {
        assertThat(IpAddressUtils.isIpInList("::1", "::1"))
                .isTrue();
    }

    // ========== IPv6 CIDR Matching ==========

    @Test
    @DisplayName("Should match IPv6 in /32 CIDR range")
    void testIpv6InCidr32() {
        assertThat(IpAddressUtils.isIpInList("2001:db8::1234", "2001:db8::/32"))
                .isTrue();
    }

    @Test
    @DisplayName("Should not match IPv6 outside /32 CIDR range")
    void testIpv6OutsideCidr32() {
        assertThat(IpAddressUtils.isIpInList("2001:db9::1", "2001:db8::/32"))
                .isFalse();
    }

    @Test
    @DisplayName("Should match IPv6 in /64 CIDR range")
    void testIpv6InCidr64() {
        assertThat(IpAddressUtils.isIpInList("2001:db8:1234:5678::1", "2001:db8:1234:5678::/64"))
                .isTrue();
    }

    @Test
    @DisplayName("Should match IPv6 in /128 CIDR (single host)")
    void testIpv6InCidr128() {
        assertThat(IpAddressUtils.isIpInList("2001:db8::1", "2001:db8::1/128"))
                .isTrue();

        assertThat(IpAddressUtils.isIpInList("2001:db8::2", "2001:db8::1/128"))
                .isFalse();
    }

    // ========== Mixed IPv4/IPv6 Lists ==========

    @Test
    @DisplayName("Should match IPv4 in mixed IPv4/IPv6 list")
    void testMixedListIpv4Match() {
        String mixedList = "192.168.1.0/24,2001:db8::/32,10.0.0.1";
        assertThat(IpAddressUtils.isIpInList("192.168.1.100", mixedList))
                .isTrue();
    }

    @Test
    @DisplayName("Should match IPv6 in mixed IPv4/IPv6 list")
    void testMixedListIpv6Match() {
        String mixedList = "192.168.1.0/24,2001:db8::/32,10.0.0.1";
        assertThat(IpAddressUtils.isIpInList("2001:db8::1234", mixedList))
                .isTrue();
    }

    @Test
    @DisplayName("Should not match IPv4 against IPv6 CIDR")
    void testIpv4AgainstIpv6Cidr() {
        assertThat(IpAddressUtils.isIpInList("192.168.1.100", "2001:db8::/32"))
                .isFalse();
    }

    @Test
    @DisplayName("Should not match IPv6 against IPv4 CIDR")
    void testIpv6AgainstIpv4Cidr() {
        assertThat(IpAddressUtils.isIpInList("2001:db8::1", "192.168.1.0/24"))
                .isFalse();
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("Should return false for null IP address")
    void testNullIpAddress() {
        assertThat(IpAddressUtils.isIpInList(null, "192.168.1.0/24"))
                .isFalse();
    }

    @Test
    @DisplayName("Should return false for empty IP address")
    void testEmptyIpAddress() {
        assertThat(IpAddressUtils.isIpInList("", "192.168.1.0/24"))
                .isFalse();
    }

    @Test
    @DisplayName("Should return false for null IP list")
    void testNullIpList() {
        assertThat(IpAddressUtils.isIpInList("192.168.1.100", null))
                .isFalse();
    }

    @Test
    @DisplayName("Should return false for empty IP list")
    void testEmptyIpList() {
        assertThat(IpAddressUtils.isIpInList("192.168.1.100", ""))
                .isFalse();
    }

    @Test
    @DisplayName("Should skip empty entries in list")
    void testEmptyEntriesInList() {
        assertThat(IpAddressUtils.isIpInList("192.168.1.100", "192.168.1.1,,192.168.1.100,,"))
                .isTrue();
    }

    @Test
    @DisplayName("Should handle invalid IP format gracefully")
    void testInvalidIpFormat() {
        assertThat(IpAddressUtils.isIpInList("not-an-ip", "192.168.1.0/24"))
                .isFalse();
    }

    @Test
    @DisplayName("Should skip invalid entry in list and continue")
    void testInvalidEntryInList() {
        assertThat(IpAddressUtils.isIpInList("192.168.1.100", "invalid-ip,192.168.1.100,another-bad-ip"))
                .isTrue();
    }

    @Test
    @DisplayName("Should handle invalid CIDR format gracefully")
    void testInvalidCidrFormat() {
        // Missing prefix length
        assertThat(IpAddressUtils.isIpInList("192.168.1.100", "192.168.1.0/"))
                .isFalse();

        // Invalid prefix length
        assertThat(IpAddressUtils.isIpInList("192.168.1.100", "192.168.1.0/99"))
                .isFalse();

        // Non-numeric prefix length
        assertThat(IpAddressUtils.isIpInList("192.168.1.100", "192.168.1.0/abc"))
                .isFalse();
    }

    // ========== Localhost Addresses ==========

    @Test
    @DisplayName("Should match IPv4 localhost")
    void testIpv4Localhost() {
        assertThat(IpAddressUtils.isIpInList("127.0.0.1", "127.0.0.1"))
                .isTrue();
    }

    @Test
    @DisplayName("Should match IPv6 localhost")
    void testIpv6Localhost() {
        assertThat(IpAddressUtils.isIpInList("::1", "::1"))
                .isTrue();
    }

    @Test
    @DisplayName("Should match localhost in CIDR range")
    void testLocalhostInCidr() {
        assertThat(IpAddressUtils.isIpInList("127.0.0.1", "127.0.0.0/8"))
                .isTrue();
    }

    // ========== Validation Tests ==========

    @Test
    @DisplayName("validateIpList should return null for valid list")
    void testValidateValidList() {
        assertThat(IpAddressUtils.validateIpList("192.168.1.0/24,10.0.0.1,2001:db8::/32"))
                .isNull();
    }

    @Test
    @DisplayName("validateIpList should return null for empty list")
    void testValidateEmptyList() {
        assertThat(IpAddressUtils.validateIpList(""))
                .isNull();

        assertThat(IpAddressUtils.validateIpList(null))
                .isNull();
    }

    @Test
    @DisplayName("validateIpList should return error for invalid IP")
    void testValidateInvalidIp() {
        String error = IpAddressUtils.validateIpList("not-an-ip");
        assertThat(error)
                .isNotNull()
                .contains("Invalid IP/CIDR 'not-an-ip'");
    }

    @Test
    @DisplayName("validateIpList should return error for invalid CIDR")
    void testValidateInvalidCidr() {
        String error = IpAddressUtils.validateIpList("192.168.1.0/99");
        assertThat(error)
                .isNotNull()
                .contains("Invalid prefix length");
    }

    @Test
    @DisplayName("validateIpList should return errors for multiple invalid entries")
    void testValidateMultipleInvalidEntries() {
        String error = IpAddressUtils.validateIpList("not-an-ip,192.168.1.0/99,another-bad-ip");
        assertThat(error)
                .isNotNull()
                .contains("not-an-ip")
                .contains("192.168.1.0/99");
    }

    @Test
    @DisplayName("validateIpList should handle mixed valid and invalid entries")
    void testValidateMixedEntries() {
        String error = IpAddressUtils.validateIpList("192.168.1.1,invalid,10.0.0.0/8");
        assertThat(error)
                .isNotNull()
                .contains("invalid")
                .doesNotContain("192.168.1.1")
                .doesNotContain("10.0.0.0/8");
    }

    // ========== Boundary Tests ==========

    @Test
    @DisplayName("Should match network address in CIDR range")
    void testNetworkAddressInCidr() {
        assertThat(IpAddressUtils.isIpInList("192.168.1.0", "192.168.1.0/24"))
                .isTrue();
    }

    @Test
    @DisplayName("Should match broadcast address in CIDR range")
    void testBroadcastAddressInCidr() {
        assertThat(IpAddressUtils.isIpInList("192.168.1.255", "192.168.1.0/24"))
                .isTrue();
    }

    @Test
    @DisplayName("Should not match address just outside CIDR range")
    void testJustOutsideCidrRange() {
        // 192.168.2.0 is just outside 192.168.1.0/24
        assertThat(IpAddressUtils.isIpInList("192.168.2.0", "192.168.1.0/24"))
                .isFalse();

        // 192.168.0.255 is just outside 192.168.1.0/24
        assertThat(IpAddressUtils.isIpInList("192.168.0.255", "192.168.1.0/24"))
                .isFalse();
    }

    @Test
    @DisplayName("Should match first usable address in /30 subnet")
    void testFirstUsableInSmallSubnet() {
        // /30 subnet: network .0, usable .1-.2, broadcast .3
        assertThat(IpAddressUtils.isIpInList("192.168.1.1", "192.168.1.0/30"))
                .isTrue();
    }

    @Test
    @DisplayName("Should match last usable address in /30 subnet")
    void testLastUsableInSmallSubnet() {
        assertThat(IpAddressUtils.isIpInList("192.168.1.2", "192.168.1.0/30"))
                .isTrue();
    }
}
