package com.example.agentic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * After HITL approve: commit allow-listed product files and open a PR.
 * Never merges, never force-pushes, never writes git config.
 */
final class GitPublisher {

    record Result(boolean committed, boolean pushed, boolean openedPr,
                  String branch, String prUrl, String detail) { }

    private GitPublisher() { }

    static Result publish(Path repoRoot, List<Path> relativeFiles, String branch,
                          String title, String body) throws IOException, InterruptedException {
        if (relativeFiles == null || relativeFiles.isEmpty()) {
            return new Result(false, false, false, branch, "", "no files to publish");
        }
        List<String> allowed = new ArrayList<>();
        for (Path rel : relativeFiles) {
            Path normalized = rel.normalize();
            if (!allowedPath(normalized)) {
                continue;
            }
            Path abs = repoRoot.resolve(normalized);
            if (Files.isRegularFile(abs)) {
                allowed.add(normalized.toString().replace('\\', '/'));
            }
        }
        if (allowed.isEmpty()) {
            return new Result(false, false, false, branch, "", "no allow-listed product files");
        }

        Exec git = exec(repoRoot, 15, "git", "rev-parse", "--show-toplevel");
        if (git.code != 0) {
            return new Result(false, false, false, branch, "", "not a git repo: " + git.out);
        }

        String startPoint = "HEAD";
        Exec originHead = exec(repoRoot, 15, "git", "rev-parse", "--abbrev-ref", "origin/HEAD");
        if (originHead.code == 0 && !originHead.out.isBlank()) {
            startPoint = originHead.out.trim();
        }
        Exec switchBranch = exec(repoRoot, 15, "git", "switch", "-c", branch, startPoint);
        if (switchBranch.code != 0) {
            switchBranch = exec(repoRoot, 15, "git", "switch", "-c", branch);
        }
        if (switchBranch.code != 0) {
            Exec reuse = exec(repoRoot, 15, "git", "switch", branch);
            if (reuse.code != 0) {
                return new Result(false, false, false, branch, "",
                        "could not create branch: " + switchBranch.out + reuse.out);
            }
        }

        List<String> add = new ArrayList<>();
        add.add("git");
        add.add("add");
        add.add("--");
        add.addAll(allowed);
        Exec staged = exec(repoRoot, 15, add.toArray(String[]::new));
        if (staged.code != 0) {
            return new Result(false, false, false, branch, "", "git add failed: " + staged.out);
        }

        Exec diff = exec(repoRoot, 15, "git", "diff", "--cached", "--quiet");
        if (diff.code == 0) {
            return new Result(false, false, false, branch, "", "no product diff to commit");
        }

        Exec commit = exec(repoRoot, 30, "git", "commit", "-m", title + "\n\n" + body);
        if (commit.code != 0) {
            return new Result(false, false, false, branch, "", "git commit failed: " + commit.out);
        }

        Exec remote = exec(repoRoot, 15, "git", "remote");
        if (remote.code != 0 || !remote.out.contains("origin")) {
            return new Result(true, false, false, branch, "", "committed; no origin remote to push");
        }

        Exec push = exec(repoRoot, 120, "git", "push", "-u", "origin", "HEAD");
        if (push.code != 0) {
            return new Result(true, false, false, branch, "", "git push failed: " + push.out);
        }

        Exec gh = exec(repoRoot, 120, "gh", "pr", "create", "--title", title, "--body", body);
        if (gh.code != 0) {
            return new Result(true, true, false, branch, "", "gh pr create failed: " + gh.out);
        }
        String url = firstHttpUrl(gh.out);
        return new Result(true, true, true, branch, url, "opened " + url);
    }

    static String branchName(String threadId, String title) {
        String id = threadId == null ? "run" : threadId;
        if (id.length() > 8) {
            id = id.substring(0, 8);
        }
        String slug = (title == null ? "change" : title)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.length() > 32) {
            slug = slug.substring(0, 32).replaceAll("-+$", "");
        }
        if (slug.isBlank()) {
            slug = "change";
        }
        return "sdlc/" + id + "-" + slug;
    }

    static boolean allowedPath(Path relative) {
        String p = relative.toString().replace('\\', '/');
        if (p.startsWith("/") || p.contains("..") || p.endsWith(".env")) {
            return false;
        }
        return p.startsWith("src/main/java/com/example/shortener/")
                || p.startsWith("src/test/java/com/example/shortener/");
    }

    private static String firstHttpUrl(String text) {
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                return trimmed;
            }
        }
        return text.trim();
    }

    private static Exec exec(Path cwd, int timeoutSec, String... command)
            throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(cwd.toFile());
        pb.redirectErrorStream(true);
        pb.environment().put("GIT_TERMINAL_PROMPT", "0");
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean done = process.waitFor(timeoutSec, TimeUnit.SECONDS);
        if (!done) {
            process.destroyForcibly();
            return new Exec(124, "timed out: " + String.join(" ", command));
        }
        return new Exec(process.exitValue(), output);
    }

    private record Exec(int code, String out) { }
}
