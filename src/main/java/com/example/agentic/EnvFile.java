package com.example.agentic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Loads KEY=VALUE pairs from {@code .env} without printing secrets. */
final class EnvFile {

    private static Map<String, String> cached;

    private EnvFile() { }

    static String get(String key) {
        String fromEnv = System.getenv(key);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        String fromFile = loadDefault().get(key);
        return fromFile == null || fromFile.isBlank() ? "" : fromFile.trim();
    }

    static Map<String, String> loadDefault() {
        if (cached != null) {
            return cached;
        }
        Path path = Path.of(System.getProperty("agentic.env", ".env"));
        try {
            cached = Files.exists(path) ? load(path) : Map.of();
        } catch (IOException e) {
            cached = Map.of();
        }
        return cached;
    }

    static Map<String, String> load(Path path) throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        for (String raw : Files.readAllLines(path)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            if ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }
            out.put(key, value);
        }
        return out;
    }
}
