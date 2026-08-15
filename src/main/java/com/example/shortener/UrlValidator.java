package com.example.shortener;

import java.net.URI;

/** Allow-lists http/https and requires a host (FR-4, CWE-601). */
public final class UrlValidator {

    private UrlValidator() { }

    public static boolean isValid(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            boolean allowed = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
            return allowed && host != null && !host.isBlank();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
