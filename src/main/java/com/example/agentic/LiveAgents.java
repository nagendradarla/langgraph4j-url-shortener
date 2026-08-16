package com.example.agentic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Prompts and artifact IO for live Cursor-backed SDLC nodes. */
final class LiveAgents {

    private LiveAgents() { }

    static Map<String, Object> understand(OrchestratorState state) throws IOException, InterruptedException {
        Path runDir = Path.of(state.runDir());
        Path specFile = runDir.resolve("spec.json");
        String clarification = String.valueOf(state.clarification().getOrDefault("notes",
                state.str("clarificationNotes", "")));
        String prompt = """
                You are the requirements agent in a gated SDLC. Do not implement code.
                Do not git commit or push. Do not modify src/.

                Requirement:
                %s

                Human clarification (may be empty):
                %s

                Read the existing product at src/main/java/com/example/shortener.
                Write spec.json to this exact path (create parent dirs if needed):
                %s

                spec.json schema:
                {
                  "title": "short title",
                  "frs": { "FR-1": "one testable requirement", "FR-2": "..." },
                  "ambiguities": ["question if blocking", "..."]
                }

                If the requirement is clear enough to implement a prototype, use an empty ambiguities array.
                If a blocking product decision is missing (storage, auth, etc.), put questions in ambiguities
                and still include any FRs you can.
                """.formatted(state.requirement(), clarification.isBlank() ? "(none)" : clarification,
                specFile.toAbsolutePath());
        CursorWorker.run(Path.of(".").toAbsolutePath(), prompt);
        return readSpec(specFile);
    }

    static List<Map<String, Object>> decompose(OrchestratorState state) throws IOException, InterruptedException {
        Path runDir = Path.of(state.runDir());
        Path tasksFile = runDir.resolve("tasks.json");
        String prompt = """
                You are the planner agent in a gated SDLC. Do not implement code.
                Do not git commit or push.

                Spec JSON:
                %s

                Write tasks.json to this exact path:
                %s
                {
                  "tasks": [
                    {
                      "id": "T1",
                      "title": "short action",
                      "fr": "FR-1",
                      "dependsOn": [],
                      "parallel": true,
                      "kind": "service"
                    }
                  ]
                }

                kind must be one of: validator, service, resolve, increment, http, tests, storage, reliability, baseline.
                Independent tasks with no unmet deps may set parallel=true.
                Every FR must be covered by at least one task.
                """.formatted(JsonSupport.stringify(state.spec()), tasksFile.toAbsolutePath());
        CursorWorker.run(runDir, prompt);
        return readTasks(tasksFile);
    }

    static void implement(OrchestratorState state, boolean fix) throws IOException, InterruptedException {
        Path workspace = Path.of(state.workspace());
        String reports = "";
        if (fix) {
            Path test = Path.of(state.runDir(), "artifacts", "test-report.json");
            Path sast = Path.of(state.runDir(), "artifacts", "sast-report.json");
            reports = "\nTest report:\n" + readIfExists(test) + "\nSAST report:\n" + readIfExists(sast) + "\n";
        }
        String prompt = """
                You are the implementer agent. Edit Java under com/example/shortener in this cwd.
                Constitution: Java 17, JUnit happy/edge/failure for public behavior, no java.util.Random
                for short codes (use SecureRandom), scheme allow-list for URLs, no hardcoded secrets,
                no git commit/push/merge.

                Requirement:
                %s

                Spec:
                %s

                Tasks:
                %s
                %s
                Implement or fix the workspace so SAST HIGH/CRITICAL is clean and unit tests can pass.
                Do not modify files outside com/example/shortener except optional src/test/java tests.
                """.formatted(state.requirement(), JsonSupport.stringify(state.spec()),
                JsonSupport.stringify(state.tasks()), reports);
        CursorWorker.run(workspace, prompt);
    }

    static Map<String, Object> readSpec(Path specFile) throws IOException {
        if (!Files.exists(specFile)) {
            throw new IllegalStateException("Requirements agent did not write " + specFile);
        }
        Map<String, Object> raw = JsonSupport.parseObject(Files.readString(specFile));
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("title", String.valueOf(raw.getOrDefault("title", "Live requirement")));
        spec.put("scenario", "live");
        Object frs = raw.get("frs");
        spec.put("frs", frs instanceof Map<?, ?> m ? m : Map.of());
        List<String> ambiguities = new ArrayList<>();
        Object amb = raw.get("ambiguities");
        if (amb instanceof List<?> list) {
            for (Object item : list) {
                String q = String.valueOf(item).trim();
                if (!q.isBlank()) {
                    ambiguities.add(q);
                }
            }
        }
        spec.put("ambiguities", ambiguities);
        return spec;
    }

    static List<Map<String, Object>> readTasks(Path tasksFile) throws IOException {
        if (!Files.exists(tasksFile)) {
            throw new IllegalStateException("Planner agent did not write " + tasksFile);
        }
        Map<String, Object> raw = JsonSupport.parseObject(Files.readString(tasksFile));
        Object list = raw.get("tasks");
        if (!(list instanceof List<?> tasks)) {
            throw new IllegalStateException("tasks.json missing tasks array");
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : tasks) {
            if (item instanceof Map<?, ?> m) {
                Map<String, Object> task = new LinkedHashMap<>();
                m.forEach((k, v) -> task.put(String.valueOf(k), v));
                task.putIfAbsent("status", "pending");
                if (!(task.get("dependsOn") instanceof List<?>)) {
                    task.put("dependsOn", List.of());
                }
                out.add(task);
            }
        }
        return out;
    }

    private static String readIfExists(Path path) throws IOException {
        return Files.exists(path) ? Files.readString(path) : "(missing)";
    }
}
