# Final engineering summary

Java 17 + LangGraph4j URL shortener with a gated SDLC orchestrator.

- Product: shorten, redirect, click stats, health/ready (text/plain), capacity 503
- Orchestrator: StateGraph with parallel design branch, HITL interrupts, retry/fallback/rollback, audit, metrics
- Scenarios: greenfield, brownfield, ambiguous (deterministic `Catalog`); live feature/bugfix via `scripts/sdlc.sh` (Cursor local agents behind the same graph)
- Live validate: workspace SAST + overlay `mvn test`; after HITL approve `--pr` commits product files and opens a review PR (never merges)
- Setup: SETUP.md

Limitations: in-memory storage; in-repo SAST stand-in; in-memory checkpointer (same JVM resume); live runs need Node 22.13+ and `CURSOR_API_KEY`.
