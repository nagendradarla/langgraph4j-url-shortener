package com.example.agentic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnvFileTest {

    @Test
    void parsesKeyValueAndIgnoresComments(@TempDir Path tmp) throws Exception {
        Path env = tmp.resolve(".env");
        Files.writeString(env, """
                # comment
                CURSOR_API_KEY="cursor_test"
                CURSOR_MODEL=composer-2.5

                """);
        Map<String, String> loaded = EnvFile.load(env);
        assertEquals("cursor_test", loaded.get("CURSOR_API_KEY"));
        assertEquals("composer-2.5", loaded.get("CURSOR_MODEL"));
    }
}
