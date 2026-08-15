package com.example.agentic;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.state.StateSnapshot;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/** Runs the LangGraph4j SDLC graph with HITL resume. */
public final class Runner {

    private final Path runsRoot;
    private final MemorySaver saver = new MemorySaver();
    private final CompiledGraph<OrchestratorState> graph;

    public Runner(Path runsRoot) {
        this.runsRoot = runsRoot;
        try {
            this.graph = SdlcGraph.compile(saver);
        } catch (GraphStateException e) {
            throw new IllegalStateException("Failed to compile SDLC graph", e);
        }
    }

    public RunResult start(String scenario, boolean injectSastFailure) {
        String threadId = UUID.randomUUID().toString();
        Path runDir = runsRoot.resolve(threadId);
        Map<String, Object> input = new HashMap<>();
        input.put("scenario", scenario);
        input.put("requirement", Catalog.requirement(scenario));
        input.put("threadId", threadId);
        input.put("runDir", runDir.toString());
        input.put("injectSastFailure", injectSastFailure);
        input.put("maxIterations", 5);
        RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();
        Optional<OrchestratorState> state = invoke(GraphInput.args(input), config);
        return packageResult(threadId, config, state);
    }

    public RunResult resume(String threadId, Map<String, Object> updates) {
        RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();
        Optional<OrchestratorState> state = invoke(GraphInput.resume(updates), config);
        return packageResult(threadId, config, state);
    }

    public RunResult runUntilComplete(String scenario, boolean injectSastFailure,
                                      Function<RunResult, Map<String, Object>> onInterrupt) {
        RunResult current = start(scenario, injectSastFailure);
        int hops = 0;
        while (current.interrupted() && hops++ < 8) {
            Map<String, Object> decision = onInterrupt.apply(current);
            current = resume(current.threadId(), decision);
        }
        return current;
    }

    private Optional<OrchestratorState> invoke(GraphInput input, RunnableConfig config) {
        try {
            return graph.invoke(input, config);
        } catch (Exception e) {
            throw new IllegalStateException("Graph invoke failed", e);
        }
    }

    private RunResult packageResult(String threadId, RunnableConfig config, Optional<OrchestratorState> state) {
        OrchestratorState values = state.orElse(null);
        boolean interrupted = false;
        String next = "";
        try {
            StateSnapshot<OrchestratorState> snap = graph.getState(config);
            if (snap != null) {
                next = snap.next() == null ? "" : snap.next();
                interrupted = !next.isBlank() && !"__END__".equals(next) && !END_SENTINEL.equals(next);
                if (values == null) {
                    values = snap.state();
                }
            }
        } catch (Exception ignored) {
            // No checkpoint yet.
        }
        if (values == null) {
            values = new OrchestratorState(Map.of("status", "unknown"));
        }
        return new RunResult(threadId, interrupted, next, values.status(), values.phase(), values);
    }

    private static final String END_SENTINEL = org.bsc.langgraph4j.StateGraph.END;

    private OrchestratorState snapshotState(RunnableConfig config) {
        try {
            StateSnapshot<OrchestratorState> snap = graph.getState(config);
            return snap == null ? null : snap.state();
        } catch (Exception e) {
            return null;
        }
    }

    public record RunResult(String threadId, boolean interrupted, String nextNode,
                            String status, String phase, OrchestratorState state) { }
}
