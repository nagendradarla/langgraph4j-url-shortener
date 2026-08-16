package com.example.agentic;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgenticMainTest {

    @AfterEach
    void clearRequirementFileProperty() {
        System.clearProperty("agentic.requirementFile");
    }

    @Test
    void liveRequirementReadsFileFlag(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("req.txt");
        Files.writeString(file, "Build POST /shorten/bulk.\n");
        String text = AgenticMain.liveRequirement(new String[]{
                "run", "live", "--requirement-file", file.toString(), "--interactive"
        });
        assertEquals("Build POST /shorten/bulk.", text);
    }

    @Test
    void liveRequirementFallsBackToSystemProperty(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("req.txt");
        Files.writeString(file, "Add GET /metrics\n");
        System.setProperty("agentic.requirementFile", file.toString());
        String text = AgenticMain.liveRequirement(new String[]{"run", "live", "--interactive"});
        assertEquals("Add GET /metrics", text);
    }

    @Test
    void liveRequirementJoinsInlineText() throws Exception {
        String text = AgenticMain.liveRequirement(new String[]{
                "run", "live", "--requirement", "Fix", "stats", "404", "--interactive"
        });
        assertEquals("Fix stats 404", text);
    }

    @Test
    void liveRequirementBlankWithoutInput() throws Exception {
        assertTrue(AgenticMain.liveRequirement(new String[]{"run", "live"}).isBlank());
    }
}
