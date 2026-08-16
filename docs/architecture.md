# Architecture — LangGraph4j SDLC

LangGraph4j `StateGraph<OrchestratorState>` is the control plane. The URL shortener (`com.example.shortener`) is the data plane.

Specialist nodes are **pluggable**. Canned scenarios (`greenfield` | `brownfield` | `ambiguous`) use `Catalog` templates — no API key. Live feature/bugfix runs (`scripts/sdlc.sh`) keep the same graph, gates, and HITL; Cursor local agents fill understand, decompose, implement, and retry via `scripts/cursor-worker`.

```mermaid
flowchart TD
  ingest --> understand
  understand -->|ambiguous| clarify[clarify interruptBefore]
  clarify --> understand
  understand -->|clear| decompose
  decompose --> plan --> constGate[constitution gate]
  constGate -->|fail| stop[safe-stop]
  constGate -->|pass| fanout[parallel: impact / risk / test strategy]
  fanout --> join[join_design]
  join --> seed[snapshot workspace]
  seed --> impl[implement task DAG]
  impl --> validate[SAST + tests]
  validate -->|fail retries left| retry --> seed
  validate -->|exhausted| fallback --> validate
  validate -->|still fail| rollback --> stop
  validate -->|pass| docs --> hitl[HITL interruptBefore]
  hitl -->|approve| summary
  hitl -->|request_changes| replan --> decompose
  hitl -->|reject| stop
```

**Validate:** canned runs FR checks on the product classpath plus workspace SAST. Live runs SAST on the workspace and overlays generated Java onto a temp copy of the module, then `mvn -Dtest=com.example.shortener.* test`.

**HITL approve + `--apply`:** copies `runs/<thread>/workspace/com/example/shortener/*.java` into `src/main/java`. The graph never git-commits.

Checkpoints: `MemorySaver` + `threadId`. Resume: `GraphInput.resume(updates)` in the same JVM (`--interactive`).

How to run each path: [scenarios.md](scenarios.md). How to watch a run: [observability.md](observability.md). Setup: [SETUP.md](../SETUP.md).
