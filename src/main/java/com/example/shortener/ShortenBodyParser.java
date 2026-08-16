package com.example.shortener;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

/** Parses POST /shorten bodies: one URL line, or URL plus {@code ttl=SECONDS}. */
public final class ShortenBodyParser {

    public record ParsedRequest(String url, OptionalLong ttlSeconds) { }

    private ShortenBodyParser() { }

    public static ParsedRequest parse(String body) {
        List<String> lines = splitLines(body);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Invalid URL");
        }
        if (lines.size() == 1) {
            String url = lines.get(0).trim();
            if (url.isEmpty()) {
                throw new IllegalArgumentException("Invalid URL");
            }
            return new ParsedRequest(url, OptionalLong.empty());
        }
        String url = lines.get(0).trim();
        if (url.isEmpty()) {
            throw new IllegalArgumentException("Invalid URL");
        }
        OptionalLong ttl = parseTtlLine(lines.get(1).trim());
        if (ttl.isEmpty()) {
            throw new IllegalArgumentException("Invalid URL");
        }
        return new ParsedRequest(url, ttl);
    }

    private static OptionalLong parseTtlLine(String line) {
        if (!line.startsWith("ttl=") || line.length() == 4) {
            return OptionalLong.empty();
        }
        String secondsText = line.substring(4);
        if (!secondsText.matches("[0-9]+")) {
            return OptionalLong.empty();
        }
        try {
            long seconds = Long.parseLong(secondsText);
            return OptionalLong.of(seconds);
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    static List<String> splitLines(String body) {
        if (body == null || body.isEmpty()) {
            return List.of();
        }
        String[] parts = body.split("\n", -1);
        List<String> lines = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (part.endsWith("\r")) {
                part = part.substring(0, part.length() - 1);
            }
            lines.add(part);
        }
        while (lines.size() > 1 && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }
}
