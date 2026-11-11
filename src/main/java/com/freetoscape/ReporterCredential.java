package com.freetoscape;

import java.util.Arrays;
import javax.inject.Singleton;

/** Process-local reporter credential, bound to the server approved at entry. */
@Singleton
final class ReporterCredential {
    private char[] value;
    private String server;
    private boolean environmentAllowed = true;

    static String serverAddress(String input) {
        String base = input == null ? "" : input.trim().replaceAll("/+$", "");
        FreeToScapePlugin.validateFeedUrl(base);
        String path = java.net.URI.create(base).getPath();
        if (path != null && (path.endsWith("/v1/feed") || path.endsWith("/v1/clan/links") || path.endsWith("/v1/clan/snapshot")))
            throw new IllegalArgumentException("Use the clan sync server base address, without an API endpoint.");
        return base;
    }

    synchronized void use(String address, char[] input) {
        String destination = serverAddress(address);
        if (input == null || input.length < 32 || input.length > 256)
            throw new IllegalArgumentException("Use the dedicated reporter credential supplied by the bot operator (32–256 characters).");
        for (char character : input) {
            if (character < 33 || character > 126)
                throw new IllegalArgumentException("The reporter credential must contain printable characters without spaces.");
        }
        char[] replacement = input.clone();
        erase();
        value = replacement;
        server = destination;
        environmentAllowed = false;
    }

    synchronized void importEnvironment(String address, String input) {
        if (environmentAllowed && value == null && input != null) {
            char[] characters = input.toCharArray();
            try { use(address, characters); }
            finally { Arrays.fill(characters, '\0'); }
        }
    }

    synchronized String forServer(String address) {
        return value != null && server.equals(serverAddress(address)) ? new String(value) : null;
    }

    synchronized boolean configuredFor(String address) {
        return value != null && server.equals(serverAddress(address));
    }

    synchronized void clear() {
        erase();
        environmentAllowed = false;
    }

    private void erase() {
        if (value != null) Arrays.fill(value, '\0');
        value = null;
        server = null;
    }
}
