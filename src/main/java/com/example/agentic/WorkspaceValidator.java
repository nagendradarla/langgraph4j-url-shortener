package com.example.agentic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/** Overlay workspace Java onto a temp copy of the module and run {@code mvn test}. */
final class WorkspaceValidator {

    private WorkspaceValidator() { }

    static Map<String, Object> mavenTest(Path repoRoot, Path workspace) throws IOException, InterruptedException {
        Path temp = Files.createTempDirectory("sdlc-validate-");
        try {
            WorkspaceIo.copyTree(repoRoot.resolve("pom.xml"), temp.resolve("pom.xml"));
            Path src = repoRoot.resolve("src");
            if (Files.exists(src)) {
                WorkspaceIo.copyTree(src, temp.resolve("src"));
            }
            Path overlay = workspace.resolve("com/example/shortener");
            Path dest = temp.resolve("src/main/java/com/example/shortener");
            if (Files.isDirectory(overlay)) {
                Files.createDirectories(dest);
                try (Stream<Path> files = Files.list(overlay)) {
                    files.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                        try {
                            Files.copy(p, dest.resolve(p.getFileName()),
                                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    });
                }
            }
            Path extraTests = workspace.resolve("src/test/java");
            if (Files.isDirectory(extraTests)) {
                WorkspaceIo.copyTree(extraTests, temp.resolve("src/test/java"));
            }
            List<String> command = List.of("mvn", "-q", "-f", temp.resolve("pom.xml").toString(),
                    "-Dtest=com/example/shortener/*", "test");
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(temp.toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            boolean done = process.waitFor(5, TimeUnit.MINUTES);
            if (!done) {
                process.destroyForcibly();
                return Map.of("passed", false, "error", "mvn test timed out", "output", trim(output));
            }
            boolean passed = process.exitValue() == 0;
            List<Map<String, Object>> checks = new ArrayList<>();
            checks.add(Map.of("fr", "WORKSPACE_MVN_TEST", "ok", passed));
            return Map.of("passed", passed, "checks", checks, "output", trim(output),
                    "exit", process.exitValue());
        } finally {
            WorkspaceIo.deleteTree(temp);
        }
    }

    private static String trim(String output) {
        if (output == null) {
            return "";
        }
        if (output.length() <= 4000) {
            return output;
        }
        return output.substring(output.length() - 4000);
    }
}
