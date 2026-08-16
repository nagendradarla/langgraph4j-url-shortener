package com.example.shortener;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlShortenerServiceBulkTest {

    private UrlShortenerService service;

    @BeforeEach
    void setUp() {
        service = new UrlShortenerService();
    }

    @Test
    void bulkShortenReturnsCodesInOrder() {
        List<String> codes = service.bulkShorten("https://example.com/a\nhttps://example.com/b");
        assertEquals(2, codes.size());
        assertEquals("https://example.com/a", service.resolve(codes.get(0)).orElseThrow());
        assertEquals("https://example.com/b", service.resolve(codes.get(1)).orElseThrow());
    }

    @Test
    void bulkShortenEmptyBodyReturnsEmptyList() {
        assertTrue(service.bulkShorten("").isEmpty());
    }

    @Test
    void bulkShortenParsesCrlfLines() {
        List<String> codes = service.bulkShorten("https://example.com/a\r\nhttps://example.com/b");
        assertEquals(2, codes.size());
        assertEquals("https://example.com/a", service.resolve(codes.get(0)).orElseThrow());
    }

    @Test
    void bulkShortenRejectsBlankLine() {
        assertThrows(IllegalArgumentException.class,
                () -> service.bulkShorten("https://example.com/a\n\nhttps://example.com/b"));
        assertEquals(0, service.health().get("links"));
    }

    @Test
    void bulkShortenRejectsInvalidLineWithoutMutatingStore() {
        service.shorten("https://example.com/seed");
        assertEquals(1, service.health().get("links"));
        assertThrows(IllegalArgumentException.class,
                () -> service.bulkShorten("https://example.com/new\njavascript:alert(1)"));
        assertEquals(1, service.health().get("links"));
    }

    @Test
    void bulkShortenRejectsDataScheme() {
        assertThrows(IllegalArgumentException.class,
                () -> service.bulkShorten("https://example.com/a\ndata:text/html,hi"));
        assertEquals(0, service.health().get("links"));
    }

    @Test
    void bulkShortenIdempotentWithinBatchAndAcrossRequests() {
        String single = service.shorten("https://example.com/shared");
        List<String> codes = service.bulkShorten(
                "https://example.com/shared\nhttps://example.com/new\nhttps://example.com/shared");
        assertEquals(single, codes.get(0));
        assertEquals(single, codes.get(2));
        assertEquals(single, service.bulkShorten("https://example.com/shared").get(0));
    }

    @Test
    void bulkShortenAtomicCapacityRejection() {
        UrlShortenerService tiny = new UrlShortenerService(2);
        tiny.shorten("https://example.com/existing");
        assertThrows(IllegalStateException.class,
                () -> tiny.bulkShorten("https://example.com/1\nhttps://example.com/2"));
        assertEquals(1, tiny.health().get("links"));
    }

    @Test
    void bulkShortenClickCountsAvailable() {
        List<String> codes = service.bulkShorten("https://example.com/counted");
        String code = codes.get(0);
        assertEquals(0L, service.clickCount(code).orElseThrow());
        service.resolve(code);
        assertEquals(1L, service.clickCount(code).orElseThrow());
    }
}
