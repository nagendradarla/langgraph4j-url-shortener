package com.example.shortener.sast;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** In-repo SAST stand-in (CWE-338, 601, 798, 502). HIGH/CRITICAL block HITL. */
public final class SastScanner {

    public record Finding(Path path, String cwe, String severity, String description) { }

    private record Rule(String cwe, String severity, String description, Pattern pattern,
                        Pattern requiresAbsenceOf) { }

    private static final List<Rule> RULES = List.of(
            new Rule("CWE-338", "HIGH",
                    "Use of java.util.Random for a security-relevant value. Use java.security.SecureRandom.",
                    Pattern.compile("\\bnew\\s+Random\\s*\\("), null),
            new Rule("CWE-601", "HIGH",
                    "URL accepted without scheme allow-listing (open redirect / SSRF risk).",
                    Pattern.compile("class\\s+UrlValidator"),
                    Pattern.compile("https?|getScheme|scheme", Pattern.CASE_INSENSITIVE)),
            new Rule("CWE-798", "CRITICAL",
                    "Hardcoded credential/secret detected.",
                    Pattern.compile("(password|secret|api[_-]?key)\\s*=\\s*\"[^\"]+\"", Pattern.CASE_INSENSITIVE),
                    null),
            new Rule("CWE-502", "HIGH",
                    "Unsafe deserialization sink (ObjectInputStream.readObject / XMLDecoder).",
                    Pattern.compile("ObjectInputStream|XMLDecoder|readObject\\s*\\("), null)
    );

    public static void main(String[] args) throws IOException {
        Path root = Path.of(args.length > 0 ? args[0] : "src/main/java");
        List<Finding> findings = scan(root);
        System.out.println("SAST scan: " + countJavaFiles(root) + " file(s) analyzed");
        if (findings.isEmpty()) {
            System.out.println("No findings. Clean.");
            return;
        }
        int blocking = 0;
        for (Finding finding : findings) {
            System.out.println("[" + finding.severity() + "] " + finding.cwe() + "  " + finding.path());
            System.out.println("    " + finding.description());
            if ("HIGH".equals(finding.severity()) || "CRITICAL".equals(finding.severity())) {
                blocking++;
            }
        }
        System.out.println("SUMMARY: " + findings.size() + " finding(s), " + blocking + " blocking (HIGH/CRITICAL)");
        System.exit(1);
    }

    public static List<Finding> scan(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        List<Finding> findings = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().contains("/sast/"))
                    .forEach(path -> findings.addAll(scanFile(path)));
        }
        return List.copyOf(findings);
    }

    private static List<Finding> scanFile(Path path) {
        String content;
        try {
            content = Files.readString(path);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read " + path, e);
        }
        List<Finding> findings = new ArrayList<>();
        for (Rule rule : RULES) {
            if (!rule.pattern().matcher(content).find()) {
                continue;
            }
            if (rule.requiresAbsenceOf() != null && rule.requiresAbsenceOf().matcher(content).find()) {
                continue;
            }
            findings.add(new Finding(path, rule.cwe(), rule.severity(), rule.description()));
        }
        return findings;
    }

    private static long countJavaFiles(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return 0;
        }
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(".java")).count();
        }
    }

    public static boolean isBlocking(List<Finding> findings) {
        return findings.stream().anyMatch(f -> {
            String severity = f.severity().toUpperCase(Locale.ROOT);
            return "HIGH".equals(severity) || "CRITICAL".equals(severity);
        });
    }
}
