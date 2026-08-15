package com.example.agentic;

import com.example.shortener.UrlShortenerServer;

import java.nio.file.Path;
import java.util.Map;

/**
 * CLI:
 *   run greenfield|brownfield|ambiguous [--auto-approve] [--clarify memory]
 *   resume THREAD --approve|--reject|--request-changes|--clarify memory
 *   serve [port]
 */
public final class AgenticMain {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("""
                    Usage:
                      java ... AgenticMain serve [port]
                      java ... AgenticMain run <greenfield|brownfield|ambiguous> [--auto-approve] [--clarify memory]
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
            String storage = flagValue(args, "--clarify", "memory");
            if (auto) {
                var result = runner.runUntilComplete(scenario, true, current -> {
                    if (current.nextNode().contains("clarify")) {
                        return Map.of("clarificationStorage", storage);
                    }
                    return Map.of("hitlAction", "approve");
                });
                print(result);
            } else {
                print(runner.start(scenario, true));
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
                updates = Map.of("clarificationStorage", flagValue(args, "--clarify", "memory"));
            }
            print(runner.resume(thread, updates));
        }
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
}
