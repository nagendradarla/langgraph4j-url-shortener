package com.example.agentic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Spawns {@code scripts/cursor-worker/run.mjs} so Java LangGraph nodes can run
 * a Cursor local agent. Requires {@code CURSOR_API_KEY}.
 */
final class CursorWorker {

    private CursorWorker() { }

    static boolean apiKeyPresent() {
        String key = EnvFile.get("CURSOR_API_KEY");
        return key != null && !key.isBlank();
    }

    static void requireApiKey() {
        if (!apiKeyPresent()) {
            throw new IllegalStateException(
                    "Live runs require CURSOR_API_KEY in the environment or .env "
                            + "(Cursor Dashboard → Integrations). "
                            + "Copy .env.example to .env and paste the key. "
                            + "Use greenfield|brownfield|ambiguous without a key.");
        }
    }

    static void run(Path cwd, String prompt) throws IOException, InterruptedException {
        requireApiKey();
        Path workerDir = workerDir();
        ensureDependencies(workerDir);
        Path promptFile = Files.createTempFile("cursor-prompt-", ".txt");
        Files.writeString(promptFile, prompt);
        Path node = findNode();
        Path script = workerDir.resolve("run.mjs");
        if (!Files.exists(script)) {
            throw new IllegalStateException("Missing Cursor sidecar: " + script.toAbsolutePath());
        }
        List<String> command = new ArrayList<>();
        command.add(node.toString());
        command.add(script.toAbsolutePath().toString());
        command.add("--cwd");
        command.add(cwd.toAbsolutePath().toString());
        command.add("--prompt-file");
        command.add(promptFile.toAbsolutePath().toString());
        GraphLog.line("cursor-worker cwd=" + cwd.toAbsolutePath());
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workerDir.toFile());
        pb.redirectErrorStream(true);
        pb.environment().put("CURSOR_API_KEY", EnvFile.get("CURSOR_API_KEY"));
        String model = EnvFile.get("CURSOR_MODEL");
        if (!model.isBlank()) {
            pb.environment().put("CURSOR_MODEL", model);
        }
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean finished = process.waitFor(20, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("Cursor worker timed out after 20 minutes\n" + output);
        }
        int code = process.exitValue();
        if (!output.isBlank()) {
            for (String line : output.split("\\R")) {
                GraphLog.line("cursor-worker " + line);
            }
        }
        Files.deleteIfExists(promptFile);
        if (code != 0) {
            throw new IllegalStateException("Cursor worker exited " + code + "\n" + output);
        }
    }

    private static Path workerDir() {
        Path override = Path.of(System.getProperty("agentic.cursorWorker", "scripts/cursor-worker"));
        return override.toAbsolutePath().normalize();
    }

    private static void ensureDependencies(Path workerDir) throws IOException, InterruptedException {
        if (Files.exists(workerDir.resolve("node_modules/@cursor/sdk"))) {
            return;
        }
        GraphLog.line("cursor-worker npm install in " + workerDir);
        ProcessBuilder pb = new ProcessBuilder("npm", "install");
        pb.directory(workerDir.toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(5, TimeUnit.MINUTES) || process.exitValue() != 0) {
            throw new IllegalStateException("npm install failed in " + workerDir + "\n" + output);
        }
    }

    private static Path findNode() {
        String fromEnv = System.getenv("NODE_BINARY");
        if (fromEnv != null && !fromEnv.isBlank() && Files.isExecutable(Path.of(fromEnv))) {
            return Path.of(fromEnv);
        }
        return Path.of("node");
    }
}
