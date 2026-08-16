package com.example.shortener;

import org.junit.jupiter.api.Test;

import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShortenBodyParserTest {

    @Test
    void singleLineUrlHasNoTtl() {
        ShortenBodyParser.ParsedRequest parsed = ShortenBodyParser.parse("  https://example.com/path  ");
        assertEquals("https://example.com/path", parsed.url());
        assertTrue(parsed.ttlSeconds().isEmpty());
    }

    @Test
    void twoLineBodyParsesTtl() {
        ShortenBodyParser.ParsedRequest parsed = ShortenBodyParser.parse("https://example.com\nttl=120");
        assertEquals("https://example.com", parsed.url());
        assertEquals(120L, parsed.ttlSeconds().getAsLong());
    }

    @Test
    void crlfLineEndingsAreSupported() {
        ShortenBodyParser.ParsedRequest parsed = ShortenBodyParser.parse("https://example.com\r\nttl=30\r\n");
        assertEquals("https://example.com", parsed.url());
        assertEquals(30L, parsed.ttlSeconds().getAsLong());
    }

    @Test
    void zeroTtlIsValid() {
        assertEquals(0L, ShortenBodyParser.parse("https://example.com\nttl=0").ttlSeconds().getAsLong());
    }

    @Test
    void malformedTtlLineIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ShortenBodyParser.parse("https://example.com\nttl="));
        assertThrows(IllegalArgumentException.class,
                () -> ShortenBodyParser.parse("https://example.com\nttl=abc"));
        assertThrows(IllegalArgumentException.class,
                () -> ShortenBodyParser.parse("https://example.com\nttl=-1"));
        assertThrows(IllegalArgumentException.class,
                () -> ShortenBodyParser.parse("https://example.com\nexpires=60"));
        assertThrows(IllegalArgumentException.class,
                () -> ShortenBodyParser.parse("https://example.com\nttl= 60"));
    }

    @Test
    void blankBodyIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ShortenBodyParser.parse("   "));
        assertThrows(IllegalArgumentException.class, () -> ShortenBodyParser.parse("\n"));
    }
}
