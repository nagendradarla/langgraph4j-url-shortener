package com.example.agentic;

import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.MemorySaver;

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

final class SdlcGraph {

    private SdlcGraph() { }

    static CompiledGraph<OrchestratorState> compile(MemorySaver saver) throws GraphStateException {
        var graph = new StateGraph<>(OrchestratorState.SCHEMA, OrchestratorState::new)
                .addNode("ingest", node_async(SdlcNodes::ingest))
                .addNode("understand", node_async(SdlcNodes::understand))
                .addNode("clarify", node_async(SdlcNodes::clarify))
                .addNode("decompose", node_async(SdlcNodes::decompose))
                .addNode("plan", node_async(SdlcNodes::plan))
                .addNode("constitution_gate", node_async(SdlcNodes::constitutionGate))
                .addNode("fanout_design", node_async(SdlcNodes::fanoutDesign))
                .addNode("impact_analysis", node_async(SdlcNodes::impact))
                .addNode("risk_analysis", node_async(SdlcNodes::risk))
                .addNode("test_strategy", node_async(SdlcNodes::testStrategy))
                .addNode("join_design", node_async(SdlcNodes::joinDesign))
                .addNode("seed_workspace", node_async(SdlcNodes::seed))
                .addNode("implement_all", node_async(SdlcNodes::implementAll))
                .addNode("validate_all", node_async(SdlcNodes::validate))
                .addNode("retry", node_async(SdlcNodes::retry))
                .addNode("fallback", node_async(SdlcNodes::fallback))
                .addNode("rollback", node_async(SdlcNodes::rollback))
                .addNode("documentation", node_async(SdlcNodes::documentation))
                .addNode("hitl", node_async(SdlcNodes::hitl))
                .addNode("replan", node_async(SdlcNodes::replan))
                .addNode("summarize", node_async(SdlcNodes::summarize))
                .addNode("safe_stop", node_async(SdlcNodes::safeStop))
                .addEdge(START, "ingest")
                .addEdge("ingest", "understand")
                .addConditionalEdges("understand", edge_async(SdlcNodes::routeAfterUnderstand),
                        Map.of("clarify", "clarify", "decompose", "decompose"))
                .addEdge("clarify", "understand")
                .addEdge("decompose", "plan")
                .addEdge("plan", "constitution_gate")
                .addConditionalEdges("constitution_gate", edge_async(SdlcNodes::routeAfterConstitution),
                        Map.of("safe_stop", "safe_stop", "fanout_design", "fanout_design"))
                .addEdge("fanout_design", "impact_analysis")
                .addEdge("fanout_design", "risk_analysis")
                .addEdge("fanout_design", "test_strategy")
                .addEdge("impact_analysis", "join_design")
                .addEdge("risk_analysis", "join_design")
                .addEdge("test_strategy", "join_design")
                .addEdge("join_design", "seed_workspace")
                .addEdge("seed_workspace", "implement_all")
                .addEdge("implement_all", "validate_all")
                .addConditionalEdges("validate_all", edge_async(SdlcNodes::routeQuality), Map.of(
                        "documentation", "documentation",
                        "retry", "retry",
                        "fallback", "fallback",
                        "rollback", "rollback"))
                .addEdge("retry", "seed_workspace")
                .addEdge("fallback", "validate_all")
                .addEdge("rollback", "safe_stop")
                .addEdge("documentation", "hitl")
                .addConditionalEdges("hitl", edge_async(SdlcNodes::routeHitl), Map.of(
                        "summarize", "summarize",
                        "replan", "replan",
                        "safe_stop", "safe_stop"))
                .addEdge("replan", "decompose")
                .addEdge("summarize", END)
                .addEdge("safe_stop", END);

        var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .interruptBefore("clarify", "hitl")
                .recursionLimit(80)
                .build();
        return graph.compile(compileConfig);
    }
}
