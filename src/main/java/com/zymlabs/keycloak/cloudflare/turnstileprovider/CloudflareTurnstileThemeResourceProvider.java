package com.zymlabs.keycloak.cloudflare.turnstileprovider;

import org.jboss.logging.Logger;
import org.keycloak.theme.ThemeResourceProvider;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;

/**
 * Theme Resource Provider for Cloudflare Turnstile.
 *
 * Provides:
 * - JavaScript for script injection mode (turnstile-injector.js)
 * - FreeMarker templates for the separate pages (turnstile-form.ftl, turnstile-registration-form.ftl)
 *
 * Resources are accessible via: /resources/cloudflare-turnstile-resources/{path}
 */
public class CloudflareTurnstileThemeResourceProvider implements ThemeResourceProvider {

    private static final Logger logger = Logger.getLogger(CloudflareTurnstileThemeResourceProvider.class);

    @Override
    public URL getTemplate(String name) throws IOException {
        // Serve bundled templates for SEPARATE_PAGE mode
        // DO NOT intercept standard template names like "login.ftl" or "register.ftl"
        // as this breaks SCRIPT_INJECTION and CUSTOM_THEME modes
        String path = null;

        // Only serve our custom templates when explicitly requested by name
        if ("turnstile-form.ftl".equals(name)) {
            path = "theme-resources/templates/turnstile-form.ftl";
        } else if ("turnstile-registration-form.ftl".equals(name)) {
            path = "theme-resources/templates/turnstile-registration-form.ftl";
        }

        if (path != null) {
            URL resource = getClass().getClassLoader().getResource(path);
            if (resource != null) {
                logger.debugf("Serving bundled template: %s from %s", name, path);
                return resource;
            }
        }

        return null;
    }

    @Override
    public InputStream getResourceAsStream(String path) throws IOException {
        logger.debugf("ThemeResourceProvider: Requested path: %s", path);

        // Serve JavaScript resources for SCRIPT_INJECTION mode
        // Path will be like "js/turnstile-injector.js" when accessed via /resources/cloudflare-turnstile-resources/js/turnstile-injector.js
        String resourcePath = null;

        // Handle various path formats
        if (path.equals("js/turnstile-injector.js") ||
            path.endsWith("/turnstile-injector.js") ||
            path.equals("turnstile-injector.js")) {
            resourcePath = "theme-resources/js/turnstile-injector.js";
        }

        if (resourcePath != null) {
            InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath);
            if (stream != null) {
                logger.infof("Serving resource: %s from classpath: %s", path, resourcePath);
                return stream;
            } else {
                logger.warnf("Resource not found in classpath: %s (tried: %s)", path, resourcePath);
            }
        } else {
            logger.debugf("No mapping for path: %s", path);
        }

        return null;
    }

    @Override
    public Properties getMessages(String baseBundlename, Locale locale) throws IOException {
        // Try to load messages from theme-resources
        String path = "theme-resources/messages/" + baseBundlename + "_" + locale.getLanguage() + ".properties";
        InputStream stream = getClass().getClassLoader().getResourceAsStream(path);

        if (stream == null && !"en".equals(locale.getLanguage())) {
            // Fallback to English
            path = "theme-resources/messages/" + baseBundlename + "_en.properties";
            stream = getClass().getClassLoader().getResourceAsStream(path);
        }

        if (stream != null) {
            try {
                Properties props = new Properties();
                props.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
                logger.debugf("Loaded message bundle: %s for locale: %s", baseBundlename, locale);
                return props;
            } finally {
                stream.close();
            }
        }

        // No messages of this bundle here (e.g. Keycloak's "admin-messages"): an empty set, never
        // null. Keycloak merges provider messages without a null check, so null fails every request
        // that loads the bundle, such as the admin console's localized validation errors.
        return new Properties();
    }

    @Override
    public void close() {
        // No resources to clean up
    }
}
