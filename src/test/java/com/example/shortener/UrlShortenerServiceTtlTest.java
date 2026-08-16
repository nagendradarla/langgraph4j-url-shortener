package com.example.shortener;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlShortenerServiceTtlTest {

    private UrlShortenerService service;

    @BeforeEach
    void setUp() {
        service = new UrlShortenerService();
    }

    @Test
    void singleLineShortenNeverExpires() throws Exception {
        String code = service.shorten("https://example.com/permanent");
        assertTrue(service.resolve(code).isPresent());
        Thread.sleep(1100);
        assertTrue(service.resolve(code).isPresent());
        assertEquals(2L, service.clickCount(code).orElseThrow());
    }

    @Test
    void twoLineShortenExpiresAfterTtl() throws Exception {
        String code = service.shorten("https://example.com/temporary\nttl=1");
        assertTrue(service.resolve(code).isPresent());
        assertEquals(1L, service.clickCount(code).orElseThrow());
        Thread.sleep(1100);
        assertTrue(service.resolve(code).isEmpty());
        assertTrue(service.clickCount(code).isEmpty());
    }

    @Test
    void expiredResolveDoesNotIncrementClicks() throws Exception {
        String code = service.shorten("https://example.com/no-more-clicks\nttl=1");
        service.resolve(code);
        Thread.sleep(1100);
        service.resolve(code);
        service.resolve(code);
        assertTrue(service.clickCount(code).isEmpty());
    }

    @Test
    void repeatShortenReturnsSameCodeAndIgnoresLaterTtl() {
        String first = service.shorten("https://example.com/idempotent-ttl\nttl=1");
        String second = service.shorten("https://example.com/idempotent-ttl\nttl=3600");
        assertEquals(first, second);
    }

    @Test
    void repeatShortenWithTtlAfterPermanentKeepsPermanentCode() throws Exception {
        String code = service.shorten("https://example.com/already-there");
        String withTtl = service.shorten("https://example.com/already-there\nttl=1");
        assertEquals(code, withTtl);
        Thread.sleep(1100);
        assertTrue(service.resolve(code).isPresent());
    }

    @Test
    void repeatShortenWithoutTtlAfterExpiringEntryKeepsOriginalExpiry() throws Exception {
        String code = service.shorten("https://example.com/expiring-first\nttl=1");
        assertEquals(code, service.shorten("https://example.com/expiring-first"));
        Thread.sleep(1100);
        assertTrue(service.resolve(code).isEmpty());
    }

    @Test
    void malformedTtlReturnsInvalidUrl() {
        assertThrows(IllegalArgumentException.class,
                () -> service.shorten("https://example.com\nttl=bad"));
    }

    @Test
    void invalidUrlStillRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.shorten("javascript:alert(1)"));
        assertThrows(IllegalArgumentException.class,
                () -> service.shorten("javascript:alert(1)\nttl=10"));
    }

    @Test
    void bulkShortenDoesNotAcceptTtlLines() {
        assertThrows(IllegalArgumentException.class,
                () -> service.bulkShorten("https://example.com/a\nttl=10"));
    }

    @Test
    void statsReturnZeroBeforeFirstResolve() {
        String code = service.shorten("https://example.com/stats\nttl=60");
        assertEquals(0L, service.clickCount(code).orElseThrow());
    }
}
