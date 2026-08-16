package com.example.agentic;

import com.example.shortener.UrlShortenerService;
import com.example.shortener.sast.SastScanner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class SdlcNodes {

    private SdlcNodes() { }

    static Map<String, Object> ingest(OrchestratorState state) throws IOException {
        Path runDir = Path.of(state.runDir());
        WorkspaceIo.ensure(runDir);
        audit(runDir, "ingest", "enter");
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("startedAt", Instant.now().toEpochMilli());
        metrics.put("retries", 0);
        metrics.put("rollbacks", 0);
        metrics.put("fallbacks", 0);
        metrics.put("interrupts", 0);
        boolean inject = state.scenario().equals("greenfield")
                && state.<Boolean>value("injectSastFailure").orElse(Boolean.TRUE);
        Map<String, Object> out = new HashMap<>();
        out.put("workspace", runDir.resolve("workspace").toString());
        out.put("iteration", 0);
        out.put("maxIterations", state.maxIterations());
        out.put("fallbackUsed", false);
        out.put("rolledBack", false);
        out.put("status", "running");
        out.put("phase", "requirements");
        out.put("injectSastFailure", inject);
        out.put("applyOnApprove", state.flag("applyOnApprove"));
        out.put("publishOnApprove", state.flag("publishOnApprove"));
        out.put("metrics", metrics);
        out.put("decisions", List.of(Map.of("node", "ingest", "at", Instant.now().toString())));
        return out;
    }

    static Map<String, Object> understand(OrchestratorState state) throws IOException, InterruptedException {
        String scenario = state.scenario();
        Map<String, Object> clarification = state.clarification();
        List<String> ambiguities = List.of();
        Map<String, Object> spec;
        String phase;
        if (state.live()) {
            Map<String, Object> loaded = LiveAgents.understand(state);
            @SuppressWarnings("unchecked")
            List<String> liveAmb = loaded.get("ambiguities") instanceof List<?> list
                    ? list.stream().map(String::valueOf).toList()
                    : List.of();
            ambiguities = liveAmb;
            Map<String, Object> liveSpec = new LinkedHashMap<>(loaded);
            liveSpec.remove("ambiguities");
            spec = liveSpec;
            phase = ambiguities.isEmpty() ? "specified" : "ambiguous";
        } else if ("ambiguous".equals(scenario) && clarification.isEmpty()) {
            spec = Map.of("title", "Draft — blocked on persistence ambiguity", "frs", Map.of());
            ambiguities = List.of("Where should click/analytics data be stored: memory, sqlite, or postgres?");
            phase = "ambiguous";
        } else if ("greenfield".equals(scenario)) {
            spec = Catalog.greenfieldSpec();
            phase = "specified";
        } else if ("brownfield".equals(scenario)) {
            spec = Catalog.brownfieldSpec();
            phase = "specified";
        } else {
            spec = Catalog.ambiguousSpec(clarification);
            phase = "specified";
        }
        audit(Path.of(state.runDir()), "understand", phase);
        int version = state.<Number>value("specVersion").map(Number::intValue).orElse(0) + 1;
        return Map.of(
                "spec", spec,
                "ambiguities", ambiguities,
                "phase", phase,
                "specVersion", version,
                "decisions", List.of(Map.of("node", "understand", "title", spec.get("title"))));
    }

    static Map<String, Object> clarify(OrchestratorState state) throws IOException {
        String storage = state.str("clarificationStorage", "memory");
        String notes = state.str("clarificationNotes", storage);
        Map<String, Object> clarification = new LinkedHashMap<>();
        clarification.put("storage", storage);
        clarification.put("notes", notes);
        audit(Path.of(state.runDir()), "clarify", notes);
        Map<String, Object> metrics = state.metrics();
        metrics.put("interrupts", ((Number) metrics.getOrDefault("interrupts", 0)).intValue() + 1);
        return Map.of(
                "clarification", clarification,
                "ambiguities", List.of(),
                "upstreamChanged", true,
                "phase", "clarified",
                "metrics", metrics,
                "decisions", List.of(Map.of("node", "clarify", "notes", notes)));
    }

    static Map<String, Object> decompose(OrchestratorState state) throws IOException, InterruptedException {
        List<Map<String, Object>> tasks = state.live()
                ? LiveAgents.decompose(state)
                : Catalog.tasks(state.scenario());
        audit(Path.of(state.runDir()), "decompose", String.valueOf(tasks.size()));
        return Map.of(
                "tasks", tasks,
                "phase", "decomposed",
                "decisions", List.of(Map.of("node", "decompose", "count", tasks.size())));
    }

    static Map<String, Object> plan(OrchestratorState state) throws IOException {
        Map<String, Object> plan = Map.of(
                "stack", "Java 17, JDK HttpServer, SecureRandom, LangGraph4j",
                "gates", List.of("constitution", "JUnit FR checks", "SAST", "HITL"),
                "maxIterations", state.maxIterations(),
                "scenario", state.scenario());
        WorkspaceIo.write(Path.of(state.runDir(), "artifacts", "plan.json"), plan.toString());
        return Map.of("plan", plan, "phase", "planned",
                "decisions", List.of(Map.of("node", "plan")));
    }

    static Map<String, Object> constitutionGate(OrchestratorState state) {
        boolean ok = !state.spec().isEmpty() && !state.tasks().isEmpty()
                && state.spec().get("frs") instanceof Map<?, ?> frs && !frs.isEmpty();
        if (!ok) {
            return Map.of("status", "blocked", "phase", "constitution_failed",
                    "error", "Spec FRs or tasks missing");
        }
        GraphLog.line("constitution_gate passed — spec FRs and task DAG present");
        return Map.of("phase", "constitution_passed",
                "decisions", List.of(Map.of("node", "constitution_gate", "passed", true)));
    }

    static Map<String, Object> fanoutDesign(OrchestratorState state) throws IOException {
        audit(Path.of(state.runDir()), "fanout_design", "parallel: impact | risk | test_strategy");
        GraphLog.line("FAN-OUT → impact_analysis | risk_analysis | test_strategy  (join at join_design)");
        return Map.of("phase", "design_fanout",
                "decisions", List.of(Map.of("node", "fanout_design")));
    }

    static Map<String, Object> impact(OrchestratorState state) throws IOException {
        Map<String, Object> impact;
        if ("greenfield".equals(state.scenario())) {
            impact = Map.of("mode", "greenfield", "modules",
                    List.of("UrlValidator", "UrlShortenerService", "UrlShortenerServer"));
        } else {
            impact = Map.of("mode", state.live() ? "live" : "brownfield",
                    "modules", List.of("UrlShortenerService", "UrlShortenerServer"),
                    "requirement", state.requirement(),
                    "doNotTouch", List.of("shorten idempotency", "validator allow-list"));
        }
        audit(Path.of(state.runDir()), "impact", String.valueOf(impact.get("mode")));
        return Map.of("impact", impact, "decisions", List.of(Map.of("node", "impact")));
    }

    static Map<String, Object> risk(OrchestratorState state) throws IOException {
        List<Map<String, String>> risks = new ArrayList<>();
        risks.add(Map.of("id", "R1", "risk", "Open redirect", "mitigation", "scheme allow-list"));
        risks.add(Map.of("id", "R2", "risk", "Guessable codes", "mitigation", "SecureRandom"));
        risks.add(Map.of("id", "R3", "risk", "Lost counts on restart", "mitigation", "explicit in-memory limit"));
        String storage = String.valueOf(state.clarification().getOrDefault("storage", "memory"));
        if (List.of("sqlite", "postgres").contains(storage)) {
            risks.add(Map.of("id", "R5", "risk", "Durable store out of prototype scope",
                    "mitigation", "fallback to in-memory"));
        }
        audit(Path.of(state.runDir()), "risk", String.valueOf(risks.size()));
        return Map.of("risks", risks, "decisions", List.of(Map.of("node", "risk", "count", risks.size())));
    }

    static Map<String, Object> testStrategy(OrchestratorState state) throws IOException {
        Map<String, Object> strategy = Map.of(
                "unit", "happy / edge / failure per public method",
                "http", "JDK HttpServer tests",
                "sast", "in-repo scanner, HIGH/CRITICAL block HITL");
        audit(Path.of(state.runDir()), "test_strategy", "done");
        return Map.of("testStrategy", strategy, "decisions", List.of(Map.of("node", "test_strategy")));
    }

    static Map<String, Object> joinDesign(OrchestratorState state) throws IOException {
        audit(Path.of(state.runDir()), "join_design", "sync barrier — all design branches complete");
        GraphLog.line("JOIN ← impact + risk + test_strategy  (sync barrier before implement)");
        return Map.of("phase", "design_complete", "decisions", List.of(Map.of("node", "join_design")));
    }

    static Map<String, Object> seed(OrchestratorState state) throws IOException {
        Path workspace = Path.of(state.workspace());
        Files.createDirectories(workspace.resolve("com/example/shortener"));
        if (state.live() && state.iteration() > 0) {
            audit(Path.of(state.runDir()), "seed", "keep-workspace");
            return Map.of("phase", "seeded", "decisions", List.of(Map.of("node", "seed", "kept", true)));
        }
        if (state.live() || !"greenfield".equals(state.scenario())) {
            if (state.live()) {
                copyProductFile(workspace, "UrlValidator.java");
                copyProductFile(workspace, "Base62Codec.java");
                copyProductFile(workspace, "UrlShortenerService.java");
                copyProductFile(workspace, "UrlShortenerServer.java");
            } else {
                WorkspaceIo.write(workspace.resolve("com/example/shortener/UrlShortenerService.java"),
                        Catalog.resource("core-UrlShortenerService.java.txt"));
                copyProductFile(workspace, "UrlValidator.java");
                copyProductFile(workspace, "Base62Codec.java");
            }
        }
        WorkspaceIo.snapshot(workspace, Path.of(state.runDir(), "snapshot"));
        audit(Path.of(state.runDir()), "seed", state.scenario());
        return Map.of("phase", "seeded", "decisions", List.of(Map.of("node", "seed")));
    }

    static Map<String, Object> implementAll(OrchestratorState state) throws IOException, InterruptedException {
        Path workspace = Path.of(state.workspace());
        List<Map<String, Object>> tasks = new ArrayList<>();
        for (Map<String, Object> t : state.tasks()) {
            tasks.add(new LinkedHashMap<>(t));
        }
        if (state.live()) {
            LiveAgents.implement(state, state.iteration() > 0);
            for (Map<String, Object> task : tasks) {
                task.put("status", "done");
            }
            audit(Path.of(state.runDir()), "implement", "cursor tasks=" + tasks.size());
            return Map.of("tasks", tasks, "phase", "implemented",
                    "decisions", List.of(Map.of("node", "implement_all", "mode", "cursor", "done", tasks.size())));
        }
        Set<String> done = new HashSet<>();
        boolean poison = state.flag("injectSastFailure") && state.iteration() == 0;
        int guard = 0;
        while (done.size() < tasks.size() && guard++ < 32) {
            List<Map<String, Object>> ready = new ArrayList<>();
            for (Map<String, Object> task : tasks) {
                String id = String.valueOf(task.get("id"));
                if (done.contains(id)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                List<String> deps = (List<String>) task.getOrDefault("dependsOn", List.of());
                if (done.containsAll(deps)) {
                    ready.add(task);
                }
            }
            if (ready.isEmpty()) {
                break;
            }
            List<Map<String, Object>> wave = ready.stream()
                    .filter(t -> Boolean.TRUE.equals(t.get("parallel")))
                    .toList();
            if (wave.isEmpty()) {
                wave = List.of(ready.get(0));
            }
            GraphLog.line("implement wave: " + wave.stream()
                    .map(t -> t.get("id") + "(" + t.get("kind") + ")")
                    .reduce((a, b) -> a + ", " + b).orElse("-")
                    + (wave.size() > 1 ? "  [parallel tasks]" : ""));
            for (Map<String, Object> task : wave) {
                applyTask(workspace, task, poison && "service".equals(task.get("kind")));
                task.put("status", "done");
                done.add(String.valueOf(task.get("id")));
            }
        }
        audit(Path.of(state.runDir()), "implement", "tasks=" + done.size());
        return Map.of("tasks", tasks, "phase", "implemented",
                "decisions", List.of(Map.of("node", "implement_all", "done", done.size())));
    }

    private static void applyTask(Path workspace, Map<String, Object> task, boolean poison) throws IOException {
        String kind = String.valueOf(task.get("kind"));
        Path pkg = workspace.resolve("com/example/shortener");
        Files.createDirectories(pkg);
        switch (kind) {
            case "validator" -> copyProductFile(workspace, "UrlValidator.java");
            case "reliability" -> WorkspaceIo.write(workspace.resolve("RELIABILITY.md"),
                    "Health/ready endpoints and max_entries capacity guard (FR-7).\n");
            case "service", "storage" -> {
                if (poison) {
                    WorkspaceIo.write(pkg.resolve("UrlShortenerService.java"),
                            Catalog.resource("poison-UrlShortenerService.java.txt"));
                } else {
                    copyProductFile(workspace, "UrlValidator.java");
                    copyProductFile(workspace, "Base62Codec.java");
                    copyProductFile(workspace, "UrlShortenerService.java");
                }
            }
            case "http" -> copyProductFile(workspace, "UrlShortenerServer.java");
            case "tests" -> WorkspaceIo.write(workspace.resolve("GENERATED_TESTS.md"),
                    "FR checks executed by the validate node. Task " + task.get("id") + "\n");
            default -> { /* resolve/increment/baseline covered by service copy or seed */ }
        }
    }

    static Map<String, Object> validate(OrchestratorState state) throws IOException, InterruptedException {
        Path workspace = Path.of(state.workspace());
        var findings = SastScanner.scan(workspace);
        boolean sastClean = !SastScanner.isBlocking(findings);
        List<Map<String, Object>> checks = new ArrayList<>();
        boolean testsPassed = true;
        if (state.live()) {
            Map<String, Object> mvn = WorkspaceValidator.mavenTest(Path.of("."), workspace);
            testsPassed = Boolean.TRUE.equals(mvn.get("passed"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> mvnChecks = mvn.get("checks") instanceof List<?> list
                    ? (List<Map<String, Object>>) list
                    : List.of();
            checks.addAll(mvnChecks);
            String src = Files.exists(workspace.resolve("com/example/shortener/UrlShortenerService.java"))
                    ? Files.readString(workspace.resolve("com/example/shortener/UrlShortenerService.java"))
                    : "";
            checks.add(Map.of("fr", "FR-6", "ok",
                    src.contains("SecureRandom") && !src.contains("java.util.Random")));
            testsPassed = testsPassed && checks.stream().allMatch(c -> Boolean.TRUE.equals(c.get("ok")));
            Map<String, Object> testReport = new LinkedHashMap<>();
            testReport.put("passed", testsPassed);
            testReport.put("checks", checks);
            testReport.put("count", checks.size());
            testReport.put("mvnOutput", mvn.getOrDefault("output", ""));
            Map<String, Object> sastReport = Map.of(
                    "clean", sastClean,
                    "blocking", findings.size(),
                    "findings", findings.stream().map(f -> f.cwe() + " " + f.path()).toList());
            WorkspaceIo.write(Path.of(state.runDir(), "artifacts", "test-report.json"),
                    JsonSupport.stringify(testReport));
            WorkspaceIo.write(Path.of(state.runDir(), "artifacts", "sast-report.json"),
                    JsonSupport.stringify(sastReport));
            audit(Path.of(state.runDir()), "validate", "tests=" + testsPassed + " sast=" + sastClean);
            return Map.of(
                    "testReport", testReport,
                    "sastReport", sastReport,
                    "phase", "validated",
                    "decisions", List.of(Map.of("node", "validate", "passed", testsPassed && sastClean)));
        }
        try {
            UrlShortenerService svc = new UrlShortenerService();
            String code = svc.shorten("https://example.com/a");
            checks.add(Map.of("fr", "FR-1", "ok", !code.isBlank()));
            checks.add(Map.of("fr", "FR-5", "ok", code.equals(svc.shorten("https://example.com/a"))));
            checks.add(Map.of("fr", "FR-2", "ok", svc.resolve(code).orElse("").equals("https://example.com/a")));
            checks.add(Map.of("fr", "FR-3", "ok", svc.resolve("missing").isEmpty()));
            boolean rejected;
            try {
                svc.shorten("javascript:alert(1)");
                rejected = false;
            } catch (IllegalArgumentException e) {
                rejected = true;
            }
            checks.add(Map.of("fr", "FR-4", "ok", rejected));
            checks.add(Map.of("fr", "FR-002", "ok", svc.clickCount(code).orElse(-1L) == 1L));
            checks.add(Map.of("fr", "FR-005", "ok", svc.clickCount("missing").isEmpty()));
            checks.add(Map.of("fr", "FR-7", "ok", "ok".equals(svc.health().get("status"))));
            String src = Files.exists(workspace.resolve("com/example/shortener/UrlShortenerService.java"))
                    ? Files.readString(workspace.resolve("com/example/shortener/UrlShortenerService.java"))
                    : "";
            checks.add(Map.of("fr", "FR-6", "ok",
                    src.contains("SecureRandom") && !src.contains("java.util.Random")));
        } catch (Exception e) {
            testsPassed = false;
            checks.add(Map.of("fr", "LOAD", "ok", false, "error", e.getMessage()));
        }
        testsPassed = testsPassed && checks.stream().allMatch(c -> Boolean.TRUE.equals(c.get("ok")));
        Map<String, Object> testReport = Map.of("passed", testsPassed, "checks", checks, "count", checks.size());
        Map<String, Object> sastReport = Map.of(
                "clean", sastClean,
                "blocking", findings.size(),
                "findings", findings.stream().map(f -> f.cwe() + " " + f.path()).toList());
        WorkspaceIo.write(Path.of(state.runDir(), "artifacts", "test-report.json"), testReport.toString());
        WorkspaceIo.write(Path.of(state.runDir(), "artifacts", "sast-report.json"), sastReport.toString());
        audit(Path.of(state.runDir()), "validate", "tests=" + testsPassed + " sast=" + sastClean);
        return Map.of(
                "testReport", testReport,
                "sastReport", sastReport,
                "phase", "validated",
                "decisions", List.of(Map.of("node", "validate", "passed", testsPassed && sastClean)));
    }

    static Map<String, Object> retry(OrchestratorState state) throws IOException {
        Map<String, Object> metrics = state.metrics();
        int retries = ((Number) metrics.getOrDefault("retries", 0)).intValue() + 1;
        metrics.put("retries", retries);
        audit(Path.of(state.runDir()), "retry", String.valueOf(state.iteration() + 1));
        return Map.of(
                "iteration", state.iteration() + 1,
                "injectSastFailure", false,
                "phase", "retry",
                "metrics", metrics,
                "decisions", List.of(Map.of("node", "retry", "iteration", state.iteration() + 1)));
    }

    static Map<String, Object> fallback(OrchestratorState state) throws IOException {
        Path workspace = Path.of(state.workspace());
        copyProductFile(workspace, "UrlValidator.java");
        copyProductFile(workspace, "Base62Codec.java");
        copyProductFile(workspace, "UrlShortenerService.java");
        copyProductFile(workspace, "UrlShortenerServer.java");
        Map<String, Object> metrics = state.metrics();
        metrics.put("fallbacks", ((Number) metrics.getOrDefault("fallbacks", 0)).intValue() + 1);
        audit(Path.of(state.runDir()), "fallback", "clean_template");
        return Map.of("fallbackUsed", true, "injectSastFailure", false,
                "iteration", state.iteration() + 1, "phase", "fallback", "metrics", metrics,
                "decisions", List.of(Map.of("node", "fallback")));
    }

    static Map<String, Object> rollback(OrchestratorState state) throws IOException {
        WorkspaceIo.restore(Path.of(state.runDir(), "snapshot"), Path.of(state.workspace()));
        Map<String, Object> metrics = state.metrics();
        metrics.put("rollbacks", ((Number) metrics.getOrDefault("rollbacks", 0)).intValue() + 1);
        audit(Path.of(state.runDir()), "rollback", "restored");
        return Map.of("rolledBack", true, "status", "rolled_back", "phase", "rolled_back", "metrics", metrics,
                "decisions", List.of(Map.of("node", "rollback")));
    }

    static Map<String, Object> documentation(OrchestratorState state) throws IOException {
        String hitl = """
                # HITL Review Gate

                **Scenario:** %s
                **Tests passed:** %s
                **SAST clean:** %s
                **Iteration:** %s

                Choose: Approve / Request changes / Reject.
                Approve may copy workspace Java into src/ and open a review PR. The graph will not merge.
                """.formatted(state.scenario(),
                state.testReport().get("passed"),
                state.sastReport().get("clean"),
                state.iteration());
        WorkspaceIo.write(Path.of(state.runDir(), "HITL_GATE.md"), hitl);
        audit(Path.of(state.runDir()), "documentation", "HITL_GATE.md");
        return Map.of("phase", "docs", "docs", Map.of("hitl", "HITL_GATE.md"),
                "decisions", List.of(Map.of("node", "documentation")));
    }

    static Map<String, Object> hitl(OrchestratorState state) throws IOException {
        String action = state.str("hitlAction", "reject");
        Map<String, Object> metrics = state.metrics();
        metrics.put("interrupts", ((Number) metrics.getOrDefault("interrupts", 0)).intValue() + 1);
        audit(Path.of(state.runDir()), "hitl", action);
        return Map.of("hitl", Map.of("action", action), "phase", "hitl_" + action, "metrics", metrics,
                "decisions", List.of(Map.of("node", "hitl", "action", action)));
    }

    static Map<String, Object> replan(OrchestratorState state) throws IOException {
        audit(Path.of(state.runDir()), "replan", "request_changes");
        return Map.of("upstreamChanged", true, "iteration", 0, "injectSastFailure", false,
                "phase", "replan", "specVersion",
                state.<Number>value("specVersion").map(Number::intValue).orElse(0) + 1,
                "decisions", List.of(Map.of("node", "replan")));
    }

    static Map<String, Object> summarize(OrchestratorState state) throws IOException {
        Map<String, Object> metrics = state.metrics();
        long started = ((Number) metrics.getOrDefault("startedAt", Instant.now().toEpochMilli())).longValue();
        metrics.put("endedAt", Instant.now().toEpochMilli());
        metrics.put("e2eMs", ((Number) metrics.get("endedAt")).longValue() - started);
        String md = """
                # Final engineering summary

                - Scenario: %s
                - Spec: %s
                - HITL: %s
                - Retries: %s  Rollbacks: %s  Fallbacks: %s
                - E2E latency (ms): %s

                Agents executed under constitution gates. Humans owned HITL approval.
                """.formatted(state.scenario(), state.spec().get("title"),
                state.hitl().get("action"), metrics.get("retries"), metrics.get("rollbacks"),
                metrics.get("fallbacks"), metrics.get("e2eMs"));
        WorkspaceIo.write(Path.of(state.runDir(), "ENGINEERING_SUMMARY.md"), md);
        WorkspaceIo.write(Path.of(state.runDir(), "metrics.json"), metrics.toString());
        audit(Path.of(state.runDir()), "summarize", "complete");
        return Map.of("summary", Map.of("metrics", metrics, "hitl", state.hitl()),
                "metrics", metrics, "status", "completed", "phase", "done",
                "decisions", List.of(Map.of("node", "summarize")));
    }

    static Map<String, Object> safeStop(OrchestratorState state) throws IOException {
        String status = state.status();
        if ("reject".equals(state.hitl().get("action"))) {
            status = "rejected";
        }
        audit(Path.of(state.runDir()), "safe_stop", status);
        return Map.of("status", status, "phase", "stopped",
                "decisions", List.of(Map.of("node", "safe_stop", "status", status)));
    }

    static String routeAfterUnderstand(OrchestratorState state) {
        return state.ambiguities().isEmpty() ? "decompose" : "clarify";
    }

    static String routeAfterConstitution(OrchestratorState state) {
        return "constitution_failed".equals(state.phase()) ? "safe_stop" : "fanout_design";
    }

    static String routeQuality(OrchestratorState state) {
        boolean tests = Boolean.TRUE.equals(state.testReport().get("passed"));
        boolean sast = Boolean.TRUE.equals(state.sastReport().get("clean"));
        String next;
        if (tests && sast) {
            next = "documentation";
        } else if (state.iteration() + 1 < state.maxIterations()) {
            next = "retry";
        } else if (!state.flag("fallbackUsed")) {
            next = "fallback";
        } else {
            next = "rollback";
        }
        GraphLog.line("quality gate: tests=" + tests + " sast=" + sast
                + " iteration=" + state.iteration() + " → " + next);
        return next;
    }

    static String routeHitl(OrchestratorState state) {
        String action = String.valueOf(state.hitl().getOrDefault("action", "reject"));
        return switch (action) {
            case "approve" -> "publish";
            case "request_changes" -> "replan";
            default -> "safe_stop";
        };
    }

    static Map<String, Object> publish(OrchestratorState state) throws IOException, InterruptedException {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("applied", false);
        info.put("committed", false);
        info.put("prUrl", "");
        boolean apply = state.flag("applyOnApprove") || state.flag("publishOnApprove");
        List<Path> applied = List.of();
        if (apply) {
            applied = applyWorkspaceToProduct(state);
            info.put("applied", !applied.isEmpty());
            info.put("files", applied.stream().map(Path::toString).toList());
        }
        if (!state.flag("publishOnApprove")) {
            audit(Path.of(state.runDir()), "publish", apply ? "apply-only" : "skipped");
            return Map.of("publish", info, "phase", "publish",
                    "decisions", List.of(Map.of("node", "publish", "mode", apply ? "apply" : "skip")));
        }
        String title = String.valueOf(state.spec().getOrDefault("title", "SDLC change"));
        String thread = state.str("threadId", "run");
        String branch = GitPublisher.branchName(thread, title);
        String body = """
                ## Summary
                HITL approved live run `%s` (%s).

                ## Test plan
                - Workspace SAST and overlay JUnit passed before HITL
                - Review `runs/%s/HITL_GATE.md`

                The graph does not merge this PR.
                """.formatted(thread, title, thread);
        GitPublisher.Result result = GitPublisher.publish(Path.of(".").toAbsolutePath().normalize(),
                applied, branch, title, body);
        info.put("committed", result.committed());
        info.put("pushed", result.pushed());
        info.put("openedPr", result.openedPr());
        info.put("branch", result.branch());
        info.put("prUrl", result.prUrl());
        info.put("detail", result.detail());
        WorkspaceIo.write(Path.of(state.runDir(), "PR.md"),
                "# Pull request\n\n- branch: " + result.branch()
                        + "\n- url: " + (result.prUrl().isBlank() ? "(none)" : result.prUrl())
                        + "\n- " + result.detail() + "\n");
        GraphLog.line("publish — " + result.detail());
        audit(Path.of(state.runDir()), "publish",
                (result.openedPr() ? result.prUrl() : result.detail()).replace("\"", "'"));
        return Map.of("publish", info, "phase", "publish",
                "decisions", List.of(Map.of("node", "publish", "pr", result.prUrl())));
    }

    private static List<Path> applyWorkspaceToProduct(OrchestratorState state) throws IOException {
        List<Path> applied = new ArrayList<>();
        Path overlay = Path.of(state.workspace(), "com/example/shortener");
        Path dest = Path.of("src/main/java/com/example/shortener");
        if (Files.isDirectory(overlay)) {
            Files.createDirectories(dest);
            try (var files = Files.list(overlay)) {
                files.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                    try {
                        Path target = dest.resolve(p.getFileName());
                        Files.copy(p, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        applied.add(Path.of("src/main/java/com/example/shortener").resolve(p.getFileName()));
                        GraphLog.line("apply " + p.getFileName() + " → src/main/java/com/example/shortener/");
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                });
            }
        }
        Path extraTests = Path.of(state.workspace(), "src/test/java");
        Path testDest = Path.of("src/test/java");
        if (Files.isDirectory(extraTests)) {
            WorkspaceIo.copyTree(extraTests, testDest);
            try (var walk = Files.walk(extraTests)) {
                walk.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                    Path rel = extraTests.relativize(p);
                    applied.add(Path.of("src/test/java").resolve(rel));
                    GraphLog.line("apply test " + rel + " → src/test/java/");
                });
            }
        }
        if (applied.isEmpty()) {
            GraphLog.line("apply skipped — no workspace Java");
        } else {
            audit(Path.of(state.runDir()), "apply", dest.toString());
        }
        return applied;
    }

    private static void copyProductFile(Path workspace, String name) throws IOException {
        Path src = Path.of("src/main/java/com/example/shortener").resolve(name);
        Path dest = workspace.resolve("com/example/shortener").resolve(name);
        Files.createDirectories(dest.getParent());
        Files.copy(src, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private static void audit(Path runDir, String node, String event) throws IOException {
        GraphLog.line(node + " — " + event);
        WorkspaceIo.appendJsonl(runDir.resolve("audit.jsonl"),
                "{\"node\":\"" + node + "\",\"event\":\"" + event + "\",\"ts\":\"" + Instant.now() + "\"}");
    }
}
