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
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlShortenerServerBulkTest {

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
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void bulkShortenHappyPath() throws Exception {
        String body = "https://example.com/a\nhttps://example.com/b";
        HttpResponse<String> response = post("/shorten/bulk", body);
        assertEquals(200, response.statusCode());
        assertEquals("text/plain; charset=utf-8",
                response.headers().firstValue("Content-Type").orElse(""));
        String[] codes = response.body().split("\n", -1);
        assertEquals(2, codes.length);
        assertFalse(codes[0].isBlank());
        assertEquals(302, get("/" + codes[0]).statusCode());
        assertEquals(302, get("/" + codes[1]).statusCode());
    }

    @Test
    void bulkShortenEmptyBodyReturns200WithEmptyResponse() throws Exception {
        HttpResponse<String> response = post("/shorten/bulk", "");
        assertEquals(200, response.statusCode());
        assertEquals("", response.body());
    }

    @Test
    void bulkShortenRejectsInvalidBatch() throws Exception {
        HttpResponse<String> response = post("/shorten/bulk",
                "https://example.com/a\nfile:///etc/passwd");
        assertEquals(400, response.statusCode());
    }

    @Test
    void bulkShortenRejectsNonPost() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/shorten/bulk"))
                .GET()
                .timeout(Duration.ofSeconds(2))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(405, response.statusCode());
    }

    @Test
    void singleShortenUnchanged() throws Exception {
        HttpResponse<String> response = post("/shorten", "https://example.com/single");
        assertEquals(200, response.statusCode());
        assertFalse(response.body().isBlank());
    }

    @Test
    void bulkShortenRejectsWhenCapacityExceeded() throws Exception {
        HttpServer tiny = new UrlShortenerServer(new UrlShortenerService(2)).start(0);
        try {
            String url = "http://127.0.0.1:" + tiny.getAddress().getPort();
            post(url, "/shorten", "https://example.com/seed");
            HttpResponse<String> response = post(url, "/shorten/bulk",
                    "https://example.com/one\nhttps://example.com/two");
            assertEquals(503, response.statusCode());
        } finally {
            tiny.stop(0);
        }
    }

    @Test
    void bulkIdempotentWithSingleShorten() throws Exception {
        String singleCode = post("/shorten", "https://example.com/same").body();
        String bulkCode = post("/shorten/bulk", "https://example.com/same").body();
        assertEquals(singleCode, bulkCode);
    }

    @Test
    void bulkCodesReportStats() throws Exception {
        String code = post("/shorten/bulk", "https://example.com/stats-bulk").body();
        assertEquals("0", get("/stats/" + code).body());
        assertEquals(302, get("/" + code).statusCode());
        assertEquals("1", get("/stats/" + code).body());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return post(baseUrl, path, body);
    }

    private HttpResponse<String> post(String base, String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "text/plain; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(2))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .GET()
                .timeout(Duration.ofSeconds(2))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
