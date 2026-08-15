package com.example.agentic;

/** Stdout progress for live demos. Tests tolerate extra lines. */
final class GraphLog {

    private GraphLog() { }

    static void line(String message) {
        System.out.println("[sdlc] " + message);
    }
}
