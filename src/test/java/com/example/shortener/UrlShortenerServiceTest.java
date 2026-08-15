package com.example.shortener;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlShortenerServiceTest {

    private UrlShortenerService service;

    @BeforeEach
    void setUp() {
        service = new UrlShortenerService();
    }

    @Test
    void shortenReturnsNonEmptyCodeForValidUrl() {
        String code = service.shorten("https://example.com/some/long/path");
        assertNotNull(code);
        assertFalse(code.isBlank());
        assertTrue(code.matches("[0-9A-Za-z]+"));
    }

    @Test
    void resolveReturnsOriginalUrlForKnownCode() {
        String longUrl = "https://example.com/page";
        String code = service.shorten(longUrl);
        Optional<String> resolved = service.resolve(code);
        assertTrue(resolved.isPresent());
        assertEquals(longUrl, resolved.get());
    }

    @Test
    void resolveReturnsEmptyForUnknownAndBlank() {
        assertTrue(service.resolve("doesNotExist").isEmpty());
        assertTrue(service.resolve("").isEmpty());
        assertTrue(service.resolve(null).isEmpty());
    }

    @Test
    void shortenRejectsBlankAndDangerousSchemes() {
        assertThrows(IllegalArgumentException.class, () -> service.shorten("   "));
        assertThrows(IllegalArgumentException.class, () -> service.shorten("javascript:alert(1)"));
        assertThrows(IllegalArgumentException.class, () -> service.shorten("file:///etc/passwd"));
    }

    @Test
    void shorteningSameUrlTwiceReturnsSameCode() {
        String longUrl = "https://example.com/idempotent";
        assertEquals(service.shorten(longUrl), service.shorten(longUrl));
    }

    @Test
    void shortCodesUseSecureRandom() throws Exception {
        var randomField = UrlShortenerService.class.getDeclaredField("random");
        randomField.setAccessible(true);
        assertInstanceOf(SecureRandom.class, randomField.get(service));
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 8; i++) {
            codes.add(service.shorten("https://example.com/page-" + i));
        }
        assertEquals(8, codes.size());
        assertNotEquals("1", service.shorten("https://example.com/other"));
    }

    @Test
    void clickCountsAndUnknownCodes() {
        String code = service.shorten("https://example.com/counted");
        assertEquals(0L, service.clickCount(code).orElseThrow());
        service.resolve(code);
        service.resolve(code);
        assertEquals(2L, service.clickCount(code).orElseThrow());
        assertTrue(service.clickCount("missing").isEmpty());
        assertEquals(code, service.shorten("https://example.com/counted"));
        assertEquals(2L, service.clickCount(code).orElseThrow());
    }

    @Test
    void capacityExceeded() {
        UrlShortenerService tiny = new UrlShortenerService(1);
        tiny.shorten("https://example.com/1");
        assertThrows(IllegalStateException.class, () -> tiny.shorten("https://example.com/2"));
    }
}
