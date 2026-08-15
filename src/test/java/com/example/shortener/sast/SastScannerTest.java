package com.example.shortener.sast;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SastScannerTest {

    @TempDir
    Path tempDir;

    @Test
    void flagsWeakRandom() throws Exception {
        Path src = tempDir.resolve("Weak.java");
        Files.writeString(src, "class Weak { java.util.Random r = new Random(); }");
        List<SastScanner.Finding> findings = SastScanner.scan(tempDir);
        assertTrue(findings.stream().anyMatch(f -> "CWE-338".equals(f.cwe())));
        assertTrue(SastScanner.isBlocking(findings));
    }

    @Test
    void productionSourcesAreClean() throws Exception {
        List<SastScanner.Finding> findings = SastScanner.scan(Path.of("src/main/java"));
        assertTrue(findings.isEmpty());
        assertFalse(SastScanner.isBlocking(findings));
    }
}
