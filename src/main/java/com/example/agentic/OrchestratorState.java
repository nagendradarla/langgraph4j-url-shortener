package com.example.agentic;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Cross-stage SDLC state checkpointed by LangGraph4j. */
public class OrchestratorState extends AgentState {

    public static final Map<String, Channel<?>> SCHEMA = Map.of(
            "decisions", Channels.appender(ArrayList::new),
            "audit", Channels.appender(ArrayList::new)
    );

    public OrchestratorState(Map<String, Object> initData) {
        super(initData);
    }

    public String str(String key, String fallback) {
        return this.<String>value(key).orElse(fallback);
    }

    public String scenario() {
        return str("scenario", "greenfield");
    }

    public String runDir() {
        return str("runDir", "runs");
    }

    public String workspace() {
        return str("workspace", runDir() + "/workspace");
    }

    public String phase() {
        return str("phase", "");
    }

    public String status() {
        return str("status", "running");
    }

    public int iteration() {
        return this.<Number>value("iteration").map(Number::intValue).orElse(0);
    }

    public int maxIterations() {
        return this.<Number>value("maxIterations").map(Number::intValue).orElse(5);
    }

    public boolean flag(String key) {
        return this.<Boolean>value(key).orElse(Boolean.FALSE);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> tasks() {
        return this.<List<Map<String, Object>>>value("tasks").orElse(List.of());
    }

    @SuppressWarnings("unchecked")
    public List<String> ambiguities() {
        return this.<List<String>>value("ambiguities").orElse(List.of());
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> spec() {
        return this.<Map<String, Object>>value("spec").orElse(Map.of());
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> testReport() {
        return this.<Map<String, Object>>value("testReport").orElse(Map.of());
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> sastReport() {
        return this.<Map<String, Object>>value("sastReport").orElse(Map.of());
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> metrics() {
        return new HashMap<>(this.<Map<String, Object>>value("metrics").orElse(Map.of()));
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> hitl() {
        return this.<Map<String, Object>>value("hitl").orElse(Map.of());
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> clarification() {
        return this.<Map<String, Object>>value("clarification").orElse(Map.of());
    }
}
