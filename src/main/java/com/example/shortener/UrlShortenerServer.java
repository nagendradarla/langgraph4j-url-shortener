package com.example.shortener;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** POST /shorten, POST /shorten/bulk, GET /stats/{code}, GET /{code}, GET /health, GET /ready. */
public class UrlShortenerServer {

    private final UrlShortenerService service;

    public UrlShortenerServer() {
        this(new UrlShortenerService());
    }

    public UrlShortenerServer(UrlShortenerService service) {
        this.service = service;
    }

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        new UrlShortenerServer().start(port);
        System.out.println("URL shortener listening on http://localhost:" + port);
    }

    public HttpServer start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/shorten/bulk", this::handleBulkShorten);
        server.createContext("/shorten", this::handleShorten);
        server.createContext("/stats", this::handleStats);
        server.createContext("/health", this::handleHealth);
        server.createContext("/ready", this::handleReady);
        server.createContext("/", this::handleResolve);
        server.start();
        return server;
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        respond(exchange, 200, service.health().toString());
    }

    private void handleReady(HttpExchange exchange) throws IOException {
        boolean ready = "ok".equals(service.health().get("status"));
        respond(exchange, ready ? 200 : 503, ready ? "ready" : "not ready");
    }

    private void handleBulkShorten(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            respond(exchange, 405, "Method Not Allowed");
            return;
        }
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        try {
            String response = String.join("\n", service.bulkShorten(body));
            respond(exchange, 200, response);
        } catch (IllegalArgumentException e) {
            respond(exchange, 400, "Invalid URL");
        } catch (IllegalStateException e) {
            respond(exchange, 503, "Capacity exceeded");
        }
    }

    private void handleShorten(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            respond(exchange, 405, "Method Not Allowed");
            return;
        }
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        try {
            respond(exchange, 200, service.shorten(body));
        } catch (IllegalArgumentException e) {
            respond(exchange, 400, "Invalid URL");
        } catch (IllegalStateException e) {
            respond(exchange, 503, "Capacity exceeded");
        }
    }

    private void handleStats(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            respond(exchange, 405, "Method Not Allowed");
            return;
        }
        String path = exchange.getRequestURI().getPath();
        String code = path.startsWith("/stats/") ? path.substring("/stats/".length()) : "";
        if (code.isBlank()) {
            respond(exchange, 404, "Not found");
            return;
        }
        Optional<Long> count = service.clickCount(code);
        if (count.isPresent()) {
            respond(exchange, 200, Long.toString(count.get()));
        } else {
            respond(exchange, 404, "Not found");
        }
    }

    private void handleResolve(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            respond(exchange, 405, "Method Not Allowed");
            return;
        }
        String code = exchange.getRequestURI().getPath().replaceFirst("^/", "");
        if (code.isBlank() || SetReserved.contains(code)) {
            respond(exchange, 400, "Missing short code");
            return;
        }
        Optional<String> longUrl = service.resolve(code);
        if (longUrl.isPresent()) {
            exchange.getResponseHeaders().add("Location", longUrl.get());
            respond(exchange, 302, "");
        } else {
            respond(exchange, 404, "Not found");
        }
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        // Drain the request so HTTP keep-alive (browsers) does not hang.
        try (var ignored = exchange.getRequestBody()) {
            ignored.readAllBytes();
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        // Without Content-Type, browsers often sniff the body, fail, and show a blank tab.
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
        exchange.close();
    }

    private static final class SetReserved {
        private static boolean contains(String code) {
            return "shorten".equals(code) || "stats".equals(code)
                    || "health".equals(code) || "ready".equals(code) || "metrics".equals(code);
        }
    }
}
