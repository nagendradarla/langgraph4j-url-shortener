package com.example.agentic;

import com.example.shortener.UrlShortenerServer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;

/**
 * CLI:
 *   run live --requirement ... | --requirement-file <path> [--auto-approve|--interactive] [--apply] [--pr]
 *   run greenfield|brownfield|ambiguous [--auto-approve|--interactive] [--clarify memory]
 *   resume THREAD --approve|--reject|--request-changes|--clarify memory
 *   serve [port]
 *
 * HITL resume uses an in-memory checkpointer: --interactive keeps the same JVM.
 */
public final class AgenticMain {

    private static final Scanner STDIN = new Scanner(System.in);

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("""
                    Usage:
                      java ... AgenticMain serve [port]
                      java ... AgenticMain run live --requirement <text> | --requirement-file <path> [--auto-approve|--interactive] [--apply] [--pr]
                      java ... AgenticMain run <greenfield|brownfield|ambiguous> [--auto-approve|--interactive] [--clarify memory]
                      java ... AgenticMain resume <threadId> --approve|--reject|--request-changes|--clarify memory
                    """);
            return;
        }
        String cmd = args[0];
        if ("serve".equals(cmd)) {
            String port = args.length > 1 ? args[1] : "8080";
            UrlShortenerServer.main(new String[]{port});
            return;
        }
        Path runs = Path.of(System.getProperty("agentic.runs", "runs"));
        Runner runner = new Runner(runs);
        if ("run".equals(cmd)) {
            String scenario = args[1];
            boolean auto = has(args, "--auto-approve");
            boolean interactive = has(args, "--interactive");
            boolean apply = has(args, "--apply") || has(args, "--pr");
            boolean publish = has(args, "--pr");
            String storage = flagValue(args, "--clarify", "memory");
            String requirement = "live".equals(scenario)
                    ? liveRequirement(args)
                    : Catalog.requirement(scenario);
            if ("live".equals(scenario) && requirement.isBlank()) {
                System.err.println("run live requires --requirement <text> or --requirement-file <path>");
                return;
            }
            boolean injectSast = "greenfield".equals(scenario);
            Runner.RunRequest request = new Runner.RunRequest(scenario, requirement, injectSast, apply, publish);
            if (auto || interactive) {
                GraphLog.line("start scenario=" + scenario
                        + " mode=" + (interactive ? "interactive-HITL" : "auto-approve")
                        + (apply ? " apply" : "")
                        + (publish ? " pr" : ""));
                var result = runner.runUntilComplete(request, current -> {
                    if (interactive) {
                        return promptHuman(current);
                    }
                    if (current.nextNode().contains("clarify")) {
                        return Map.of("clarificationStorage", storage, "clarificationNotes", storage);
                    }
                    return Map.of("hitlAction", "approve");
                });
                print(result);
            } else {
                print(runner.start(request));
            }
            return;
        }
        if ("resume".equals(cmd)) {
            String thread = args[1];
            Map<String, Object> updates;
            if (has(args, "--approve")) {
                updates = Map.of("hitlAction", "approve");
            } else if (has(args, "--reject")) {
                updates = Map.of("hitlAction", "reject");
            } else if (has(args, "--request-changes")) {
                updates = Map.of("hitlAction", "request_changes");
            } else {
                String storage = flagValue(args, "--clarify", "memory");
                updates = Map.of("clarificationStorage", storage, "clarificationNotes", storage);
            }
            print(runner.resume(thread, updates));
        }
    }

    private static Map<String, Object> promptHuman(Runner.RunResult current) {
        System.out.println();
        System.out.println("======== HUMAN GATE ========");
        System.out.println("thread_id=" + current.threadId());
        System.out.println("next_node=" + current.nextNode());
        System.out.println("phase=" + current.phase());
        if (current.state() != null) {
            System.out.println("runDir=" + current.state().runDir());
        }
        if (current.nextNode().contains("clarify")) {
            if (current.state() != null && !current.state().ambiguities().isEmpty()) {
                System.out.println("Ambiguities:");
                for (String q : current.state().ambiguities()) {
                    System.out.println("  - " + q);
                }
            } else {
                System.out.println("Ambiguity: where should analytics be stored?");
            }
            System.out.print("Enter clarification (default memory): ");
            String line = stdin().trim();
            if (line.isEmpty()) {
                line = "memory";
            }
            GraphLog.line("human clarification=" + line);
            return Map.of("clarificationStorage", line, "clarificationNotes", line);
        }
        if (current.state() != null) {
            Path gate = Path.of(current.state().runDir(), "HITL_GATE.md");
            try {
                if (Files.exists(gate)) {
                    System.out.println();
                    System.out.println(Files.readString(gate));
                }
            } catch (Exception e) {
                System.out.println("(could not read HITL_GATE.md: " + e.getMessage() + ")");
            }
        }
        System.out.print("HITL action [approve|reject|request_changes] (default approve): ");
        String action = stdin().trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (action.isEmpty()) {
            action = "approve";
        }
        if (!action.equals("approve") && !action.equals("reject") && !action.equals("request_changes")) {
            System.out.println("Unknown action '" + action + "', treating as reject (safe-stop).");
            action = "reject";
        }
        GraphLog.line("human HITL action=" + action);
        return Map.of("hitlAction", action);
    }

    private static String stdin() {
        if (!STDIN.hasNextLine()) {
            return "";
        }
        return STDIN.nextLine();
    }

    private static void print(Runner.RunResult result) {
        System.out.println("thread_id=" + result.threadId());
        System.out.println("interrupted=" + result.interrupted());
        System.out.println("next=" + result.nextNode());
        System.out.println("status=" + result.status());
        System.out.println("phase=" + result.phase());
        if (result.state() != null) {
            System.out.println("runDir=" + result.state().runDir());
            System.out.println("hitl=" + result.state().hitl());
            System.out.println("publish=" + result.state().value("publish").orElse(Map.of()));
            System.out.println("metrics=" + result.state().metrics());
        }
    }

    private static boolean has(String[] args, String flag) {
        for (String a : args) {
            if (flag.equals(a)) {
                return true;
            }
        }
        return false;
    }

    private static String flagValue(String[] args, String flag, String fallback) {
        for (int i = 0; i < args.length - 1; i++) {
            if (flag.equals(args[i])) {
                return args[i + 1];
            }
        }
        return fallback;
    }

    static String liveRequirement(String[] args) throws Exception {
        String file = flagValue(args, "--requirement-file", "");
        if (file.isBlank()) {
            file = System.getProperty("agentic.requirementFile", "");
        }
        if (!file.isBlank()) {
            Path path = Path.of(file);
            if (!Files.isRegularFile(path)) {
                throw new IllegalArgumentException("requirement file not found: " + path.toAbsolutePath());
            }
            return Files.readString(path).trim();
        }
        return requirementValue(args);
    }

    private static String requirementValue(String[] args) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if ("--requirement".equals(args[i])) {
                for (int j = i + 1; j < args.length; j++) {
                    if (args[j].startsWith("--")) {
                        break;
                    }
                    if (sb.length() > 0) {
                        sb.append(' ');
                    }
                    sb.append(args[j]);
                }
                break;
            }
        }
        return sb.toString();
    }
}
