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

class UrlShortenerServerTest {

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
    void shortenAndResolveHappyPath() throws Exception {
        HttpResponse<String> shorten = post("/shorten", "https://example.com/server");
        assertEquals(200, shorten.statusCode());
        String code = shorten.body();
        assertFalse(code.isBlank());
        HttpResponse<String> resolve = get("/" + code);
        assertEquals(302, resolve.statusCode());
        assertEquals("https://example.com/server", resolve.headers().firstValue("Location").orElseThrow());
    }

    @Test
    void healthReadyAndStats() throws Exception {
        assertEquals(200, get("/health").statusCode());
        assertEquals(200, get("/ready").statusCode());
        String code = post("/shorten", "https://example.com/stats-path").body();
        assertEquals("0", get("/stats/" + code).body());
        assertEquals(302, get("/" + code).statusCode());
        assertEquals("1", get("/stats/" + code).body());
        assertEquals(404, get("/stats/missing").statusCode());
        assertEquals(400, post("/shorten", "javascript:alert(1)").statusCode());
        assertEquals(404, get("/missing").statusCode());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
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
