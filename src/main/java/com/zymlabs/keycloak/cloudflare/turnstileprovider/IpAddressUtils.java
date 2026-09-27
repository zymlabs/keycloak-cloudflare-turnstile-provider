package com.zymlabs.keycloak.cloudflare.turnstileprovider;

import org.jboss.logging.Logger;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * Utility class for IP address and CIDR range matching.
 *
 * Supports both IPv4 and IPv6 addresses and CIDR notation.
 * Used for IP allowlist/blocklist filtering in the Cloudflare Turnstile authenticator.
 */
public class IpAddressUtils {

    private static final Logger logger = Logger.getLogger(IpAddressUtils.class);

    private static final Pattern IPV4_LITERAL = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");
    // Hex groups and colons, optionally an embedded IPv4 tail and a zone id. getByName does the
    // full validation; this only guarantees the value can't be treated as a hostname.
    private static final Pattern IPV6_LITERAL = Pattern.compile("^[0-9A-Fa-f:.]*:[0-9A-Fa-f:.]*(%[0-9A-Za-z_.\\-]+)?$");

    /**
     * Parses an IPv4/IPv6 literal without ever performing a DNS lookup.
     *
     * <p>{@link InetAddress#getByName(String)} resolves anything that isn't a literal through DNS.
     * Accepting hostnames would let whoever controls (or spoofs) a DNS record decide which clients
     * are allowlisted, and would put blocking DNS lookups on the login path.
     *
     * @throws UnknownHostException if the value is not an IP literal
     */
    static InetAddress parseLiteral(String value) throws UnknownHostException {
        if (value == null || !(IPV4_LITERAL.matcher(value).matches() || IPV6_LITERAL.matcher(value).matches())) {
            throw new UnknownHostException("Not an IP address literal: " + value);
        }
        return InetAddress.getByName(value);
    }

    /**
     * Check if an IP address matches any entry in a comma-separated list of IPs/CIDRs.
     *
     * @param ipAddress IP address to check (e.g., "192.168.1.100" or "2001:db8::1")
     * @param ipList Comma-separated list of IPs/CIDRs (e.g., "192.168.1.0/24,10.0.0.1")
     * @return true if IP matches any entry in the list, false otherwise
     */
    public static boolean isIpInList(String ipAddress, String ipList) {
        if (ipAddress == null || ipAddress.trim().isEmpty()) {
            logger.warn("IP address is null or empty");
            return false;
        }

        if (ipList == null || ipList.trim().isEmpty()) {
            return false;
        }

        try {
            InetAddress ip = parseLiteral(ipAddress);

            for (String entry : ipList.split(",")) {
                entry = entry.trim();
                if (entry.isEmpty()) {
                    continue;
                }

                try {
                    if (entry.contains("/")) {
                        // CIDR notation
                        if (isIpInCidr(ip, entry)) {
                            logger.debugf("IP %s matched CIDR entry: %s", ipAddress, entry);
                            return true;
                        }
                    } else {
                        // Single IP
                        InetAddress entryIp = parseLiteral(entry);
                        if (ip.equals(entryIp)) {
                            logger.debugf("IP %s matched single IP entry: %s", ipAddress, entry);
                            return true;
                        }
                    }
                } catch (UnknownHostException | IllegalArgumentException e) {
                    // Invalid entry - log warning and skip
                    // IllegalArgumentException catches NumberFormatException as well
                    logger.warnf("Invalid IP/CIDR entry '%s': %s", entry, e.getMessage());
                }
            }
        } catch (UnknownHostException e) {
            logger.warnf("Invalid IP address '%s': %s", ipAddress, e.getMessage());
            return false;
        }

        return false;
    }

    /**
     * Check if an IP address is within a CIDR range.
     * Supports both IPv4 and IPv6.
     *
     * @param ip IP address to check
     * @param cidr CIDR notation (e.g., "192.168.1.0/24" or "2001:db8::/32")
     * @return true if IP is in the CIDR range, false otherwise
     * @throws UnknownHostException if CIDR format is invalid
     * @throws NumberFormatException if prefix length is invalid
     */
    static boolean isIpInCidr(InetAddress ip, String cidr) throws UnknownHostException, NumberFormatException {
        String[] parts = cidr.split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid CIDR format: " + cidr);
        }

        InetAddress network = parseLiteral(parts[0]);
        int prefixLength = Integer.parseInt(parts[1]);

        byte[] ipBytes = ip.getAddress();
        byte[] networkBytes = network.getAddress();

        // IP versions must match (both IPv4 or both IPv6)
        if (ipBytes.length != networkBytes.length) {
            return false;
        }

        // Validate prefix length
        int maxPrefixLength = ipBytes.length * 8;
        if (prefixLength < 0 || prefixLength > maxPrefixLength) {
            throw new IllegalArgumentException(
                    String.format("Invalid prefix length %d for %s address (must be 0-%d)",
                            prefixLength, ipBytes.length == 4 ? "IPv4" : "IPv6", maxPrefixLength));
        }

        // Compare bits up to prefix length
        int bits = prefixLength;
        for (int i = 0; i < networkBytes.length && bits > 0; i++) {
            int mask = bits >= 8 ? 0xFF : (0xFF << (8 - bits)) & 0xFF;
            if ((ipBytes[i] & mask) != (networkBytes[i] & mask)) {
                return false;
            }
            bits -= 8;
        }

        return true;
    }

    /**
     * Validate that an IP/CIDR list is well-formed.
     * Returns an error message if invalid, null if valid.
     *
     * @param ipList Comma-separated list of IPs/CIDRs
     * @return Error message if invalid, null if valid
     */
    public static String validateIpList(String ipList) {
        if (ipList == null || ipList.trim().isEmpty()) {
            return null; // Empty list is valid
        }

        StringBuilder errors = new StringBuilder();
        int errorCount = 0;

        for (String entry : ipList.split(",")) {
            entry = entry.trim();
            if (entry.isEmpty()) {
                continue;
            }

            try {
                if (entry.contains("/")) {
                    // CIDR notation - validate format
                    String[] parts = entry.split("/");
                    if (parts.length != 2) {
                        errors.append(String.format("Invalid CIDR format '%s'; ", entry));
                        errorCount++;
                        continue;
                    }

                    InetAddress network = parseLiteral(parts[0]);
                    int prefixLength = Integer.parseInt(parts[1]);
                    int maxPrefixLength = network.getAddress().length * 8;

                    if (prefixLength < 0 || prefixLength > maxPrefixLength) {
                        errors.append(String.format("Invalid prefix length %d in '%s' (must be 0-%d); ",
                                prefixLength, entry, maxPrefixLength));
                        errorCount++;
                    }
                } else {
                    // Single IP - just validate format
                    parseLiteral(entry);
                }
            } catch (UnknownHostException e) {
                errors.append(String.format("Invalid IP/CIDR '%s'; ", entry));
                errorCount++;
            } catch (NumberFormatException e) {
                errors.append(String.format("Invalid prefix length in '%s'; ", entry));
                errorCount++;
            }
        }

        if (errorCount > 0) {
            return errors.toString().trim();
        }

        return null;
    }
}
