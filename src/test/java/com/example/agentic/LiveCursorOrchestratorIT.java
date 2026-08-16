package com.example.agentic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;

class LiveCursorOrchestratorIT {

    @TempDir
    Path tmp;

    @Test
    @EnabledIfEnvironmentVariable(named = "CURSOR_API_KEY", matches = ".+")
    void liveRequirementReachesHitlOrClarify() {
        Runner runner = new Runner(tmp);
        var result = runner.runUntilComplete(
                new Runner.RunRequest("live",
                        "Add a GET /metrics endpoint that returns links and capacity. In-memory only.",
                        false, false),
                current -> {
                    if (current.nextNode().contains("clarify")) {
                        return Map.of("clarificationNotes", "memory", "clarificationStorage", "memory");
                    }
                    return Map.of("hitlAction", "approve");
                });
        assertFalse(result.interrupted());
    }
}
