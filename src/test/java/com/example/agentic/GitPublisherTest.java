package com.example.agentic;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitPublisherTest {

    @Test
    void rejectsEnvAndPathEscape() {
        assertFalse(GitPublisher.allowedPath(Path.of(".env")));
        assertFalse(GitPublisher.allowedPath(Path.of("../secrets.txt")));
        assertTrue(GitPublisher.allowedPath(Path.of("src/main/java/com/example/shortener/UrlShortenerService.java")));
        assertTrue(GitPublisher.allowedPath(Path.of("src/test/java/com/example/shortener/BulkTest.java")));
    }

    @Test
    void branchNameIsStableAndSafe() {
        assertEquals("sdlc/f856fa4e-bulk-url-shortening-post-shorten",
                GitPublisher.branchName("f856fa4e-f3be-46a0-9bd8-2de4c6becca7",
                        "Bulk URL shortening (POST /shorten/bulk)"));
    }
}

