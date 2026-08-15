package com.example.agentic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SdlcOrchestratorTest {

    @TempDir
    Path tmp;

    @Test
    void greenfieldRetriesSastThenHitlApprove() {
        Runner runner = new Runner(tmp);
        var result = runner.runUntilComplete("greenfield", true, current -> {
            if (current.nextNode().contains("clarify")) {
                return Map.of("clarificationStorage", "memory");
            }
            return Map.of("hitlAction", "approve");
        });
        assertFalse(result.interrupted());
        assertEquals("completed", result.status());
        assertEquals("approve", result.state().hitl().get("action"));
        assertTrue(result.state().iteration() >= 1);
        assertTrue(Path.of(result.state().runDir(), "HITL_GATE.md").toFile().exists());
        assertTrue(Path.of(result.state().runDir(), "ENGINEERING_SUMMARY.md").toFile().exists());
        assertTrue(((Number) result.state().metrics().getOrDefault("retries", 0)).intValue() >= 1);
    }

    @Test
    void brownfieldImpactAndStats() {
        Runner runner = new Runner(tmp);
        var result = runner.runUntilComplete("brownfield", false,
                current -> Map.of("hitlAction", "approve"));
        assertEquals("completed", result.status());
        assertEquals("brownfield", result.state().<Map<String, Object>>value("impact").orElseThrow().get("mode"));
        assertTrue(Boolean.TRUE.equals(result.state().testReport().get("passed")));
    }

    @Test
    void ambiguousClarifyThenComplete() {
        Runner runner = new Runner(tmp);
        var first = runner.start("ambiguous", false);
        assertTrue(first.interrupted());
        assertTrue(first.nextNode().contains("clarify"));
        var second = runner.resume(first.threadId(), Map.of("clarificationStorage", "memory"));
        Runner.RunResult finalResult = second;
        if (second.interrupted()) {
            assertTrue(second.nextNode().contains("hitl"));
            finalResult = runner.resume(first.threadId(), Map.of("hitlAction", "approve"));
        }
        assertEquals("completed", finalResult.status());
        assertEquals("memory", finalResult.state().clarification().get("storage"));
    }

    @Test
    void hitlRejectSafeStops() {
        Runner runner = new Runner(tmp);
        var result = runner.runUntilComplete("brownfield", false,
                current -> Map.of("hitlAction", "reject"));
        assertEquals("rejected", result.status());
        assertEquals("stopped", result.phase());
    }
}
