package com.example.agentic;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Catalog {

    static final String GREENFIELD =
            "Build a URL shortener from scratch: shorten valid http(s) URLs, redirect "
                    + "by short code, reject javascript/file/data schemes, idempotent shorten, "
                    + "unguessable codes, and reliability via /health /ready plus capacity limits.";

    static final String BROWNFIELD =
            "Existing shortener already shortens and redirects. Add click counts on "
                    + "successful resolve and GET /stats/{code}. Persistence remains out of scope "
                    + "(in-memory only). Do not change shorten/redirect behavior.";

    static final String AMBIGUOUS =
            "Add analytics for follows and persist click data so we can see how links "
                    + "perform. Keep the current APIs working.";

    private Catalog() { }

    static String requirement(String scenario) {
        return switch (scenario) {
            case "brownfield" -> BROWNFIELD;
            case "ambiguous" -> AMBIGUOUS;
            default -> GREENFIELD;
        };
    }

    static Map<String, Object> greenfieldSpec() {
        Map<String, String> frs = new LinkedHashMap<>();
        frs.put("FR-1", "Valid long URL yields a short code.");
        frs.put("FR-2", "Known code resolves to the original URL.");
        frs.put("FR-3", "Unknown code returns not-found.");
        frs.put("FR-4", "Invalid or dangerous schemes are rejected.");
        frs.put("FR-5", "Shortening the same URL twice returns the same code.");
        frs.put("FR-6", "Short codes use SecureRandom, not java.util.Random.");
        frs.put("FR-7", "Health/readiness and capacity limit.");
        return Map.of("title", "URL Shortener Service", "scenario", "greenfield", "frs", frs);
    }

    static Map<String, Object> brownfieldSpec() {
        Map<String, String> frs = new LinkedHashMap<>();
        frs.put("FR-001", "New codes start at click count 0.");
        frs.put("FR-002", "Successful resolve increments count by 1.");
        frs.put("FR-003", "Failed/unknown resolve does not increment.");
        frs.put("FR-004", "GET /stats/{code} returns the current count.");
        frs.put("FR-005", "Unknown code stats lookup is not-found, not zero.");
        frs.put("FR-006", "Counts are in-memory only; no database.");
        frs.put("FR-007", "Existing shorten/resolve behavior is unchanged.");
        return Map.of("title", "Click counts on resolve", "scenario", "brownfield", "frs", frs);
    }

    static Map<String, Object> ambiguousSpec(Map<String, Object> clarification) {
        String storage = String.valueOf(clarification.getOrDefault("storage", "unspecified"));
        boolean durable = List.of("sqlite", "postgres", "database").contains(storage);
        Map<String, String> frs = new LinkedHashMap<>();
        frs.put("FR-A1", "Successful follows are counted.");
        frs.put("FR-A2", "Count can be retrieved without following again.");
        frs.put("FR-A3", durable
                ? "Storage=" + storage + ". Durable store requested; prototype falls back to in-memory."
                : "Storage=" + storage + ". In-memory only.");
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("title", "Analytics with persistence decision");
        spec.put("scenario", "ambiguous");
        spec.put("frs", frs);
        spec.put("clarification", clarification);
        return spec;
    }

    static List<Map<String, Object>> tasks(String scenario) {
        return switch (scenario) {
            case "brownfield" -> List.of(
                    task("T001", "Baseline existing tests still pass", "FR-007", List.of(), true, "baseline"),
                    task("T002", "In-memory click map at 0", "FR-001,FR-006", List.of("T001"), false, "service"),
                    task("T003", "Increment on successful resolve", "FR-002,FR-003", List.of("T002"), false, "increment"),
                    task("T004", "GET /stats/{code}", "FR-004,FR-005", List.of("T003"), false, "http"),
                    task("T005", "Tests for counts", "acceptance", List.of("T004"), false, "tests"));
            case "ambiguous" -> List.of(
                    task("TA1", "Normalize analytics FRs", "FR-A1", List.of(), true, "service"),
                    task("TA2", "Stats lookup API", "FR-A2", List.of("TA1"), false, "http"),
                    task("TA3", "Apply storage decision / fallback", "FR-A3", List.of("TA1"), true, "storage"),
                    task("TA4", "Tests", "acceptance", List.of("TA2", "TA3"), false, "tests"));
            default -> List.of(
                    task("T1", "URL validator allow-list", "FR-4", List.of(), true, "validator"),
                    task("T5", "Health and capacity reliability", "FR-7", List.of(), true, "reliability"),
                    task("T2", "Service shorten + SecureRandom", "FR-1,FR-5,FR-6", List.of("T1"), false, "service"),
                    task("T3", "Service resolve + not-found", "FR-2,FR-3", List.of("T2"), false, "resolve"),
                    task("T4", "HTTP API", "FR-1,FR-2,FR-7", List.of("T3", "T5"), false, "http"),
                    task("T6", "Tests mapped to FRs", "acceptance", List.of("T4"), false, "tests"));
        };
    }

    private static Map<String, Object> task(String id, String title, String fr, List<String> deps,
                                            boolean parallel, String kind) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("id", id);
        t.put("title", title);
        t.put("fr", fr);
        t.put("dependsOn", new ArrayList<>(deps));
        t.put("parallel", parallel);
        t.put("kind", kind);
        t.put("status", "pending");
        return t;
    }

    static String resource(String name) {
        try (InputStream in = Catalog.class.getResourceAsStream("/templates/" + name)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource templates/" + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
