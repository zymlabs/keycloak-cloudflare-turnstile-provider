package com.zymlabs.keycloak.cloudflare.turnstileprovider;

import org.jboss.logging.Logger;
import org.keycloak.services.resource.RealmResourceProvider;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.io.InputStream;

/**
 * JAX-RS Resource Provider to serve Turnstile JavaScript files.
 *
 * Accessible at: /realms/{realm}/cloudflare-turnstile/resources/js/turnstile-injector.js
 *
 * This allows Script Injection mode to work with any theme without requiring
 * custom theme modifications. The JavaScript file is served from the classpath
 * and can be loaded via standard <script> tags.
 */
public class CloudflareTurnstileResourceProvider implements RealmResourceProvider {

    private static final Logger logger = Logger.getLogger(CloudflareTurnstileResourceProvider.class);

    @Override
    public Object getResource() {
        return this;
    }

    /**
     * Serves JavaScript files from the provider's resources.
     *
     * GET /realms/{realm}/cloudflare-turnstile/resources/js/{filename}
     *
     * @param filename The JavaScript filename to serve
     * @return Response with the JavaScript file content
     */
    @GET
    @Path("resources/js/{filename}")
    @Produces("application/javascript; charset=UTF-8")
    public Response getJavaScript(@PathParam("filename") String filename) {
        logger.debugf("CloudflareTurnstileResourceProvider: Request for JavaScript file: %s", filename);

        if ("turnstile-injector.js".equals(filename)) {
            InputStream stream = getClass().getClassLoader()
                    .getResourceAsStream("theme-resources/js/turnstile-injector.js");

            if (stream != null) {
                logger.infof("Serving turnstile-injector.js from classpath");
                return Response.ok(stream)
                        .header("Content-Type", "application/javascript; charset=UTF-8")
                        .header("Cache-Control", "public, max-age=86400") // Cache for 24 hours
                        .build();
            } else {
                logger.errorf("turnstile-injector.js not found in classpath at: theme-resources/js/turnstile-injector.js");
                return Response.status(Response.Status.NOT_FOUND)
                        .type("text/plain")
                        .entity("ERROR: JavaScript file not found in provider resources")
                        .build();
            }
        }

        logger.warnf("Unknown JavaScript file requested: %s", filename);
        return Response.status(Response.Status.NOT_FOUND)
                .type("text/plain")
                .entity("File not found: " + filename)
                .build();
    }

    /**
     * Serves dynamically generated Turnstile configuration script.
     *
     * GET /realms/{realm}/cloudflare-turnstile/config.js?siteKey=xxx&mode=managed&theme=auto
     *
     * @param siteKey Cloudflare Turnstile site key
     * @param mode Widget mode (managed, non-interactive, invisible)
     * @param theme Widget theme (light, dark, auto)
     * @return Response with JavaScript that sets global config variables
     */
    @GET
    @Path("config.js")
    @Produces("application/javascript; charset=UTF-8")
    public Response getTurnstileConfig(
            @QueryParam("siteKey") String siteKey,
            @QueryParam("mode") String mode,
            @QueryParam("theme") String theme) {

        logger.debugf("Serving Turnstile config: siteKey=%s, mode=%s, theme=%s",
                     siteKey != null ? "***" : "null", mode, theme);

        // Validate required parameters
        if (siteKey == null || siteKey.trim().isEmpty()) {
            logger.error("Missing siteKey parameter for Turnstile config");
            return Response.status(Response.Status.BAD_REQUEST)
                    .type("text/plain")
                    .entity("ERROR: siteKey parameter is required")
                    .build();
        }

        // Use defaults if not specified
        String widgetMode = (mode != null && !mode.trim().isEmpty()) ? mode : "managed";
        String widgetTheme = (theme != null && !theme.trim().isEmpty()) ? theme : "auto";

        // Generate JavaScript that sets global variables
        String configScript = String.format(
                "// Cloudflare Turnstile Configuration (Auto-generated)\n" +
                "window.TURNSTILE_CONFIG = window.TURNSTILE_CONFIG || {};\n" +
                "window.TURNSTILE_CONFIG.enabled = true;\n" +
                "window.TURNSTILE_CONFIG.siteKey = %s;\n" +
                "window.TURNSTILE_CONFIG.mode = %s;\n" +
                "window.TURNSTILE_CONFIG.theme = %s;\n" +
                "console.log('[Turnstile Config] Configuration loaded:', window.TURNSTILE_CONFIG);\n",
                escapeJavaScriptString(siteKey),
                escapeJavaScriptString(widgetMode),
                escapeJavaScriptString(widgetTheme)
        );

        return Response.ok(configScript)
                .header("Content-Type", "application/javascript; charset=UTF-8")
                .header("Cache-Control", "no-cache, no-store, must-revalidate") // Don't cache config
                .build();
    }

    /**
     * Escapes a string for safe use in JavaScript string literals.
     */
    private String escapeJavaScriptString(String input) {
        if (input == null) {
            return "null";
        }
        return "\"" + input
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                + "\"";
    }

    @Override
    public void close() {
        // No resources to clean up
    }
}
