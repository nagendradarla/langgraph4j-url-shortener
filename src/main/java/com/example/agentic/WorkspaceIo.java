package com.example.agentic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

final class WorkspaceIo {

    private WorkspaceIo() { }

    static void ensure(Path runDir) throws IOException {
        Files.createDirectories(runDir.resolve("workspace"));
        Files.createDirectories(runDir.resolve("artifacts"));
    }

    static void snapshot(Path workspace, Path snapshot) throws IOException {
        deleteTree(snapshot);
        if (Files.exists(workspace)) {
            copyTree(workspace, snapshot);
        }
    }

    static void restore(Path snapshot, Path workspace) throws IOException {
        deleteTree(workspace);
        if (Files.exists(snapshot)) {
            copyTree(snapshot, workspace);
        } else {
            Files.createDirectories(workspace);
        }
    }

    static void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }

    static void appendJsonl(Path path, String line) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, line + System.lineSeparator(),
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND);
    }

    private static void copyTree(Path src, Path dest) throws IOException {
        try (Stream<Path> walk = Files.walk(src)) {
            walk.forEach(path -> {
                try {
                    Path target = dest.resolve(src.relativize(path).toString());
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(target);
                    } else {
                        Files.createDirectories(target.getParent());
                        Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            });
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            });
        }
    }
}
