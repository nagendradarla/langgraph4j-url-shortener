package com.example.shortener;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Shorten, resolve, click counts, capacity guard. */
public class UrlShortenerService {

    private static final Set<String> RESERVED = Set.of("shorten", "stats", "health", "ready", "metrics");

    private final ConcurrentHashMap<String, String> codeToUrl = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> urlToCode = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> clickCounts = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final int maxEntries;

    public UrlShortenerService() {
        this(100_000);
    }

    public UrlShortenerService(int maxEntries) {
        this.maxEntries = maxEntries;
    }

    public String shorten(String longUrl) {
        if (!UrlValidator.isValid(longUrl)) {
            throw new IllegalArgumentException("Invalid URL: " + longUrl);
        }
        String existing = urlToCode.get(longUrl);
        if (existing != null) {
            return existing;
        }
        String code = generateUniqueCode();
        codeToUrl.put(code, longUrl);
        urlToCode.put(longUrl, code);
        clickCounts.put(code, new AtomicLong(0));
        return code;
    }

    /** Validates every line, then commits all codes atomically (all-or-nothing). */
    public List<String> bulkShorten(String body) {
        List<String> lines = parseBulkLines(body);
        if (lines.isEmpty()) {
            return List.of();
        }
        for (String line : lines) {
            if (!UrlValidator.isValid(line)) {
                throw new IllegalArgumentException("Invalid URL in batch");
            }
        }
        synchronized (this) {
            int newEntries = 0;
            Set<String> pending = new HashSet<>();
            for (String line : lines) {
                if (urlToCode.containsKey(line) || pending.contains(line)) {
                    continue;
                }
                pending.add(line);
                newEntries++;
            }
            if (codeToUrl.size() + newEntries > maxEntries) {
                throw new IllegalStateException("capacity_exceeded");
            }
            Map<String, String> batchCodes = new HashMap<>();
            List<String> codes = new ArrayList<>(lines.size());
            for (String line : lines) {
                String existing = urlToCode.get(line);
                if (existing != null) {
                    codes.add(existing);
                    continue;
                }
                String assigned = batchCodes.get(line);
                if (assigned != null) {
                    codes.add(assigned);
                    continue;
                }
                String code = generateUniqueCode();
                codeToUrl.put(code, line);
                urlToCode.put(line, code);
                clickCounts.put(code, new AtomicLong(0));
                batchCodes.put(line, code);
                codes.add(code);
            }
            return List.copyOf(codes);
        }
    }

    private static List<String> parseBulkLines(String body) {
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
        return lines;
    }

    private String generateUniqueCode() {
        if (codeToUrl.size() >= maxEntries) {
            throw new IllegalStateException("capacity_exceeded");
        }
        String code;
        do {
            long n = random.nextLong() & Long.MAX_VALUE;
            code = Base62Codec.encode(n % 1_000_000_000L);
        } while (codeToUrl.containsKey(code) || RESERVED.contains(code));
        return code;
    }

    public Optional<String> resolve(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String destination = codeToUrl.get(code);
        if (destination == null) {
            return Optional.empty();
        }
        AtomicLong count = clickCounts.get(code);
        if (count != null) {
            count.incrementAndGet();
        }
        return Optional.of(destination);
    }

    public Optional<Long> clickCount(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        AtomicLong count = clickCounts.get(code);
        return count == null ? Optional.empty() : Optional.of(count.get());
    }

    public Map<String, Object> health() {
        int used = codeToUrl.size();
        return Map.of(
                "status", used < maxEntries ? "ok" : "degraded",
                "links", used,
                "capacity", maxEntries);
    }
}
