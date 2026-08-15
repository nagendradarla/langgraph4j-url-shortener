# Architecture — LangGraph4j SDLC

LangGraph4j `StateGraph<OrchestratorState>` is the control plane. The URL shortener (`com.example.shortener`) is the data plane.

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
  impl --> validate[FR checks + SAST]
  validate -->|fail retries left| retry --> seed
  validate -->|exhausted| fallback --> validate
  validate -->|still fail| rollback --> stop
  validate -->|pass| docs --> hitl[HITL interruptBefore]
  hitl -->|approve| summary
  hitl -->|request_changes| replan --> decompose
  hitl -->|reject| stop
```

Checkpoints: `MemorySaver` + `threadId`. Resume: `GraphInput.resume(updates)`.
