package com.example.shortener;

import java.security.SecureRandom;
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
        if (codeToUrl.size() >= maxEntries) {
            throw new IllegalStateException("capacity_exceeded");
        }
        String code;
        do {
            long n = random.nextLong() & Long.MAX_VALUE;
            code = Base62Codec.encode(n % 1_000_000_000L);
        } while (codeToUrl.containsKey(code) || RESERVED.contains(code));
        codeToUrl.put(code, longUrl);
        urlToCode.put(longUrl, code);
        clickCounts.put(code, new AtomicLong(0));
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
