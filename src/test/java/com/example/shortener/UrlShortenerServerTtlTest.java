package com.example.shortener;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class UrlShortenerServerTtlTest {

    private HttpServer server;
    private HttpClient client;
    private String baseUrl;

    @BeforeEach
    void setUp() throws Exception {
        server = new UrlShortenerServer().start(0);
        int port = server.getAddress().getPort();
        baseUrl = "http://127.0.0.1:" + port;
        client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shortenWithTtlResolvesBeforeExpiry() throws Exception {
        HttpResponse<String> shorten = post("/shorten", "https://example.com/ttl\r\nttl=60\r\n");
        assertEquals(200, shorten.statusCode());
        String code = shorten.body();
        assertFalse(code.isBlank());

        HttpResponse<String> resolve = get("/" + code);
        assertEquals(302, resolve.statusCode());
        assertEquals("https://example.com/ttl", resolve.headers().firstValue("Location").orElseThrow());

        HttpResponse<String> stats = get("/stats/" + code);
        assertEquals(200, stats.statusCode());
        assertEquals("1", stats.body());
    }

    @Test
    void expiredCodeReturnsNotFoundForResolveAndStats() throws Exception {
        String code = post("/shorten", "https://example.com/expired\nttl=1").body();
        Thread.sleep(1100);

        HttpResponse<String> resolve = get("/" + code);
        assertEquals(404, resolve.statusCode());
        assertEquals("Not found", resolve.body());
        assertFalse(resolve.headers().firstValue("Location").isPresent());

        HttpResponse<String> stats = get("/stats/" + code);
        assertEquals(404, stats.statusCode());
        assertEquals("Not found", stats.body());
    }

    @Test
    void malformedTtlReturns400() throws Exception {
        HttpResponse<String> response = post("/shorten", "https://example.com\nttl=oops");
        assertEquals(400, response.statusCode());
        assertEquals("Invalid URL", response.body());
    }

    @Test
    void bulkShortenRemainsUnchanged() throws Exception {
        HttpResponse<String> response = post("/shorten/bulk", "https://example.com/a\nhttps://example.com/b");
        assertEquals(200, response.statusCode());
        String[] codes = response.body().split("\n");
        assertEquals(2, codes.length);
        assertEquals(302, get("/" + codes[0]).statusCode());
    }

    @Test
    void healthAndReadyUnchanged() throws Exception {
        assertEquals(200, get("/health").statusCode());
        assertEquals(200, get("/ready").statusCode());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(5))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
