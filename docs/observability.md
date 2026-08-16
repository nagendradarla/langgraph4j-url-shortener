# SDLC workflow observability

What you can watch today versus what is worth adding. The graph stays the control plane; telemetry must not replace HITL.

## How to monitor a run right now

Watch the **same terminal** as the orchestrator. Every node prints `[sdlc]` (FAN-OUT, JOIN, quality gate, cursor-worker). In a second terminal, find the newest folder under `runs/` and tail `audit.jsonl`. HITL pauses are the only interactive checkpoints — that is intentional.

| Channel | Role |
|---------|------|
| Stdout `[sdlc]` | Live progress, same JVM |
| `audit.jsonl` | Decision lineage as nodes finish |
| Cursor `wait()` | Live implement is a black box until the sidecar exits |

## What exists today

| Signal | When you see it | Gap |
|--------|-----------------|-----|
| `[sdlc]` GraphLog | Every node enter/exit on stdout | `threadId` / `runDir` not printed at start |
| `runs/<id>/audit.jsonl` | Appended when a node finishes | No duration, no Cursor `runId` |
| `HITL_GATE.md` + `artifacts/` | After docs / validate | Nothing until that node completes |
| `metrics.json` | Only at summarize | retries/interrupts invisible mid-run |
| Cursor sidecar | `agentId` then status after `wait()` | No tokens, tools, or heartbeat for minutes |
| HUMAN GATE stdin | clarify / hitl interrupts | Correct: graph must stop, not just log |

## The live black hole

Canned `greenfield` / `brownfield` / `ambiguous` finish in seconds, so stdout is enough. Live `./scripts/sdlc.sh` spends most of its time in `CursorWorker`: Java starts the Node sidecar, then `readAllBytes()` + `waitFor(20m)`. The sidecar itself calls `run.wait()` and never `run.stream()`. Until that process exits you cannot tell understand vs implement, whether the agent is stuck, or which files it is editing.

## What to add

### Prototype (do now)

Print `threadId` and `runDir` at ingest. Stream sidecar stdout line-by-line. Use the Cursor SDK `run.stream()` then `wait()`. Write `runs/<id>/STATUS.txt` on each node so a second terminal can `watch cat`. Add `durationMs` to audit lines.

Interview-visible, no new infra, still fail-closed at HITL.

### Production (say in interview)

One OpenTelemetry span per graph node, attributes `scenario`, `iteration`, `threadId`, Cursor `runId`. Counters for retries, rollbacks, interrupts, cursor latency. Export to the bank’s existing collector — not a second orchestrator.

Matches assignment metrics: success, retry/rollback, MTTR.

### Skip (overkill)

Grafana dashboards, Jaeger UI, a `/metrics` HTTP server on the graph, or an operator console that can approve without reading `HITL_GATE.md`. Those bury the SDLC story and can look like the agent self-approves.

Durable checkpointer first if you want a real HITL UI.

## Watch commands

After ingest prints `runDir` (today: `ls -t runs | head -1`):

| Terminal | Command | Shows |
|----------|---------|-------|
| A — orchestrator | `./scripts/sdlc.sh -f requirements/feature-bulk-shorten.txt` | `[sdlc]` nodes + HITL prompts |
| B — lineage | `tail -f runs/<threadId>/audit.jsonl` | node + event + timestamp |
| B — latest run | `ls -t runs \| head -1` | `threadId` when start log is missing |
| B — review pack | `ls runs/<id>/artifacts runs/<id>/workspace` | SAST/test reports as validate finishes |

## Governance

Observability is a spectator. It must not resume the graph, skip SAST, or git-commit. Cursor `runId`s belong in `audit.jsonl` so an examiner can correlate model work with a node; they are not a second control plane.
