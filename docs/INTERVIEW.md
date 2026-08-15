# Interview presentation — LangGraph4j URL Shortener

Study this the night before. Speak from the talk track; use the Q&A bank only when asked. Pair with [DEMO.md](DEMO.md) for the live graph.

## Thesis (say this in the first 30 seconds)

This assignment is not “build a URL shortener.” The shortener is the **data plane**. The work they grade is the **control plane**: a LangGraph4j SDLC orchestrator that turns a requirement into reviewable engineering output under **controlled autonomy**.

Agents execute multi-step work. Humans own clarification, approval, and merge. The graph cannot self-approve.

## What they are scoring

From the assignment, in their language:

| Criterion | What you show |
|-----------|----------------|
| Agentic orchestration | `StateGraph` with entry/exit gates, cycles, checkpoints, re-plan |
| Non-linear stateful execution | Clarify loop, quality-gate retry, HITL `request_changes` → decompose |
| Sequential **and** parallel paths | Design fan-out (`impact` \| `risk` \| `test_strategy`) then `join_design`; task DAG waves |
| HITL for high-impact actions | `interruptBefore("clarify","hitl")` — Approve / Request changes / Reject |
| Bounded retries, fallback, rollback, safe-stop | Max 5 iterations → clean template fallback → snapshot restore → stop |
| Policy / change control | `constitution.md` as a hard gate before design work |
| Audit + metrics | `audit.jsonl`, `metrics.json` (retries, rollbacks, interrupts, e2e ms) |
| Three scenarios | Greenfield, brownfield, ambiguous |
| Engineering judgment | Honest limits; what you would change in production |

Do **not** lead with Base62 or HttpServer. Lead with governance. The product APIs prove the outputs are real.

## 8-minute talk track

### 1. Problem framing (45s)

The requirement was: take an intent (sometimes ambiguous), decompose it, run a full SDLC, and stop at a human gate. Financial-services constraint: **autonomy with bounds**, not unbounded agents.

I split the system:

- **Control plane:** `com.example.agentic` — LangGraph4j `StateGraph<OrchestratorState>`
- **Data plane:** `com.example.shortener` — shorten, redirect, stats, health/ready, capacity

The constitution (`constitution.md`) is policy-as-code: JUnit coverage, SAST HIGH/CRITICAL blocks HITL, max 5 autonomous loops, humans never get skipped.

### 2. Graph (90s)

Walk `docs/architecture.md` (or this sequence):

```
ingest → understand ─┬─ (ambiguous) clarify [interrupt] → understand
                     └─ decompose → plan → constitution gate
                                              ├─ fail → safe-stop
                                              └─ FAN-OUT: impact | risk | test_strategy
                                                         └─ JOIN → seed snapshot
                                                                   → implement task DAG
                                                                   → validate (FR checks + SAST)
                                                                        ├─ fail, retries left → retry
                                                                        ├─ exhausted → fallback
                                                                        ├─ still fail → rollback → stop
                                                                        └─ pass → docs → HITL [interrupt]
                                                                                              ├─ approve → summary
                                                                                              ├─ request_changes → replan → decompose
                                                                                              └─ reject → safe-stop
```

Call out four properties they asked for by name:

1. **Explicit dependency graph** — nodes and edges in `SdlcGraph`; tasks have `dependsOn`.
2. **Synchronization** — `join_design` does not run until all three design branches finish.
3. **Cross-stage context** — `OrchestratorState` carries spec, tasks, reports, metrics; `decisions` / `audit` are append-only.
4. **Dynamic re-plan** — HITL `request_changes` increments `specVersion` and returns to `decompose`.

### 3. Parallelism — two layers (45s)

**Graph-level:** after the constitution gate, `fanout_design` has three outgoing edges. That is a real fork/join, not a linear script.

**Task-level:** `implement_all` schedules a DAG. Independent tasks marked `parallel=true` run in the same wave. Greenfield example: validator (T1) and reliability (T5) have no dependencies, so they execute together before the service task.

Be precise if asked “are those three Java threads?” — the **control-flow contract** is parallel with a join. The runtime may schedule them as a superstep. Task waves are explicit in `implementAll`.

### 4. HITL and controlled autonomy (60s)

Two interrupt points, compiled with `interruptBefore("clarify", "hitl")`:

- **Clarify** — ambiguous persistence. Graph does not guess a database.
- **HITL** — only after tests + SAST pass. Actions: approve, request changes, reject. Default if the human does not act is not merge; `hitl` without an action is reject.

Greenfield **injects** `java.util.Random` on iteration 0 so SAST (CWE-338) fails on purpose. Retry clears the poison and copies `SecureRandom`. That is the demo of bounded autonomy: the agent may fix and re-validate; it may not skip the human.

### 5. Product that the graph produces (45s)

Working service, not a mock:

| API | Behavior |
|-----|----------|
| `POST /shorten` | http(s) only; idempotent; 400 / 503 |
| `GET /{code}` | 302 Location |
| `GET /stats/{code}` | click count; unknown is 404 not 0 |
| `GET /health`, `/ready` | liveness vs capacity |

Security choices you can defend: scheme allow-list (CWE-601), `SecureRandom` (CWE-338), reserved path names, in-memory capacity cap.

### 6. Scenarios (45s)

- **Greenfield** — full FRs; first implement fails SAST; retry; HITL.
- **Brownfield** — seed is shorten/resolve only; graph adds counts + `/stats`; impact node lists `doNotTouch` (idempotency, validator).
- **Ambiguous** — “add analytics and persist” with no store. Interrupt. Durable store request is recorded as a **risk** and falls back to in-memory with an explicit FR.

### 7. Close (30s)

“If I productionized this tomorrow: persistent checkpointer, real SAST/CI, LLM-backed understand/implement **behind the same graph**, and HITL in a review UI. I would not remove the constitution or let the agent merge.”

Then: “I can run the graph live — HITL plus the parallel fan-out.”

## Assignment → code map

| Assignment requirement | Where it lives |
|------------------------|----------------|
| Requirement understanding | `SdlcNodes.understand`, `Catalog.*Spec` |
| Ambiguity handling | `routeAfterUnderstand` → `clarify` interrupt |
| Task decomposition | `Catalog.tasks` DAG (`dependsOn`, `parallel`) |
| Brownfield reasoning | `SdlcNodes.impact` + seed from `core-UrlShortenerService` template |
| Orchestration | `SdlcGraph.compile` |
| HITL | `interruptBefore`, `Runner.resume` / `--interactive` |
| Retry / fallback / rollback | `routeQuality` |
| Traceability | task `fr` field; validate FR checks; `audit.jsonl` |
| Metrics | `metrics.json` |
| Constitution | `constitution.md`, `constitutionGate` |
| Working prototype | `UrlShortenerServer` |
| Tests | `SdlcOrchestratorTest`, product JUnit |

## How to handle “is this really agentic?”

Yes — **orchestration** is agentic. No — **nodes are not LLM calls**.

Say it this way:

> The assignment asked for an agentic **execution model**: stateful multi-step work, tools/gates, interrupts, re-planning, bounded autonomy. LangGraph4j is that model. I implemented specialist nodes in Java so the prototype is deterministic, unit-testable, and honest about gates. Swapping `understand` / `implement` for model+tool nodes does not change the graph, constitution, or HITL. I would rather show a real quality gate than a flaky LLM demo.

That is engineering judgment, not a dodge.

## Design decisions worth defending

**Why LangGraph4j, not a for-loop.** A loop cannot interrupt, checkpoint, fan-out/join, or resume with `GraphInput.resume`. The assignment asked for a graph with gates.

**Why Java 17, not Python LangGraph.** The product is Java; one language for app, tests, and SAST. LangGraph4j is the native control plane.

**Why JDK `HttpServer`, not Spring.** Scope is the orchestrator. Spring would bury the assignment under framework noise. The service is still concurrent (`ConcurrentHashMap`, `AtomicLong`) and testable.

**Why in-repo SAST.** A stand-in that encodes CWE-338/601/798/502 and **blocks HITL**. The point is the gate, not a vendor scanner. Production: SpotBugs/CodeQL/Sonar on the same edge.

**Why inject a SAST failure.** To prove retry is not dead code. Interviewers will ask “does the failure path actually run?” Greenfield iteration 0 is the evidence.

**Why unknown stats are 404, not 0.** Zero would lie about codes that never existed (FR-005).

**Why clicks increment on resolve.** Analytics follow successful follows. Failed lookup does not increment. Trade-off: GET is not side-effect free — call it out; a bank might want `POST /{code}/click` plus a 302.

**Why MemorySaver.** Same-JVM resume is enough for a prototype and for tests. Production needs a durable checkpointer so an operator console can resume another process. `--interactive` exists because of that limit.

## Limitations (say these before they trap you)

1. Checkpointer is in-memory — HITL resume must stay in one JVM (`--interactive`).
2. SAST is regex-based, not a compiler IR scanner.
3. Validate-node FR checks use the **product classpath** service plus workspace source for FR-6, not a full Maven rebuild of the generated workspace.
4. Storage is in-memory; durable stores are an explicit fallback + risk.
5. Implement copies templates / product files; it is not an LLM writing novel code.
6. No auth, tenancy, or abuse rate-limiting on the public redirect.

Then: what you would do next (persistent checkpoints, CI SAST, LLM implementer, SQLite behind an interface).

## Potential questions and answers

### Orchestration

**Q. Walk me through the graph as if I have never seen LangGraph.**

A. It is a compiled state machine. Each node is a function `State → partial State`. Edges are static or conditional. The checkpointer snapshots state at every step. `interruptBefore` means “stop before this node and wait for a human update.” Resume applies those updates and continues the same `threadId`. That is how clarify and HITL work.

**Q. Where is the entry gate? The exit gate?**

A. Entry: constitution gate — no FRs or tasks, no design work. Exit: HITL must approve before `summarize`; reject and quality-exhausted rollback both go to `safe_stop` → END.

**Q. How do you prevent a runaway agent?**

A. `maxIterations = 5`, `recursionLimit = 80`, quality routing that degrades retry → fallback → rollback, and HITL that cannot be skipped. The implementer never writes to `main`.

**Q. What happens if the human requests changes?**

A. `routeHitl` → `replan` → `decompose`. `specVersion` increments, iteration resets, SAST poison is cleared. Upstream change is recorded (`upstreamChanged`). Same constitution and HITL apply again.

**Q. How is parallel design different from parallel implement?**

A. Design: three **graph nodes**, three edges from `fanout_design`, join at `join_design`. Implement: one graph node that internally walks a **task DAG** and batches ready `parallel=true` tasks. Both are in the assignment’s “sequential and parallel paths with synchronization.”

**Q. Could two parallel nodes clobber state?**

A. Design nodes write **different keys** (`impact`, `risks`, `testStrategy`). Audit/decisions use appender channels. I would not fan-out two writers onto the same scalar without a reducer. That is a real LangGraph footgun; I avoided it.

**Q. Why `interruptBefore` instead of a node that polls Slack?**

A. The interrupt is part of the compiled graph: checkpoint, pause, resume with typed updates. Polling is an external side channel that can drift from state. HITL belongs in the orchestrator.

### HITL / governance

**Q. Can the agent approve itself?**

A. No. `hitl` reads `hitlAction` from resume input. Missing action defaults to reject. `--auto-approve` is a **demo/test driver**, not a graph edge. Constitution: the graph must not self-approve.

**Q. What does the human actually review?**

A. `runs/<thread>/HITL_GATE.md` plus workspace Java, `artifacts/test-report.json`, `artifacts/sast-report.json`, `audit.jsonl`. Approve means “this run is accepted,” not “push to production.”

**Q. How would this map to Schwab change control?**

A. Clarify ≈ product/security question on persistence. Constitution ≈ policy pack (secure coding, testing). SAST ≈ mandated scanner. HITL ≈ peer review + CAB for high-impact. Audit JSONL ≈ decision lineage for exam/audit. Metrics ≈ reliability of the **delivery system**, not just the shortener.

### Product / security

**Q. How do you stop open redirects?**

A. `UrlValidator` allow-lists `http`/`https` and requires a host. `javascript:`, `file:`, `data:` throw 400. SAST CWE-601 looks for a validator that does not mention scheme.

**Q. Why not `java.util.Random`?**

A. Short codes are security-relevant (unguessable). `Random` is CWE-338. `SecureRandom` plus collision retry and reserved-name skip.

**Q. How is shorten idempotent?**

A. `urlToCode` reverse index. Same long URL returns the same code without allocating a new one. That is FR-5 and it is in the brownfield `doNotTouch` list.

**Q. How do you handle capacity?**

A. `maxEntries` (default 100k). Past that, shorten throws `capacity_exceeded` → HTTP 503. `/ready` is 503 when degraded so an orchestrator can stop sending writes.

**Q. Is the service thread-safe?**

A. Maps are `ConcurrentHashMap`; counts are `AtomicLong`. Idempotent insert is not a single atomic “check then put” across both maps — I would mention `putIfAbsent` as a production hardening. Prototype is honest about that.

**Q. How would you scale unique codes?**

A. This prototype: `SecureRandom` in a loop. At large scale: pre-allocated ranges, Redis INCR + Base62, or a Snowflake-style id, plus a uniqueness constraint. I would not use a hash of the URL if we need unguessable codes (hash is deterministic and enumerable if the URL is guessable). Idempotency can still key off the long URL.

**Q. 301 vs 302?**

A. 302 so clients and caches re-fetch; click counts stay meaningful. 301 would be cached forever and under-count. For a permanent short link, some products use 301 and accept that trade-off — I chose analytics accuracy.

### Scenarios / brownfield

**Q. What is brownfield here?**

A. Workspace is seeded with a core shortener (shorten + resolve only). The spec adds click counts and `GET /stats/{code}` without changing existing shorten/redirect behavior. Impact analysis names the modules and the do-not-touch list.

**Q. What if the requirement is vague?**

A. Ambiguous scenario does not invent Postgres. It interrupts, records storage, and if the human asked for sqlite/postgres, the FR states the fallback and risk R5 is added. That is controlled autonomy: do not silently expand scope.

### Testing / quality

**Q. What is your test strategy?**

A. Constitution: every public behavior has happy / edge / failure JUnit. Orchestrator tests drive real graph runs: SAST retry + approve, brownfield impact, ambiguous clarify, HITL reject → `rejected`. Product tests cover validator, codec, service, HTTP.

**Q. Why not compile the generated workspace with Maven inside validate?**

A. Time and isolation. Validate runs FR checks against the live service and greps workspace sources for `SecureRandom`. That is a prototype gap. Production gate: compile + test the workspace in a sandbox job, then SAST.

**Q. How do you measure the orchestrator?**

A. `retries`, `fallbacks`, `rollbacks`, `interrupts`, `e2eMs`. Assignment asked for success rate, retry/rollback frequency, MTTR, latency. I have the counters; success is `status=completed` after approve. MTTR in this prototype is “iterations × loop time until green or rollback.”

### Process / AI use

**Q. How did you use AI on this?**

A. AI for scaffolding and iteration speed. I owned: constitution, graph topology, security gates, scenario design (including **intentional** SAST failure), and what not to automate (merge). If a generated node skipped HITL, that would be a failed assignment even if the shortener worked.

**Q. What would you do with another two days?**

A. Durable checkpointer; HITL UI; compile/test the workspace in-process; SQLite behind a `LinkStore` interface; LLM planner that **proposes** FRs a human confirms; Wire mock for redirect allow-list tests against SSRF to link-local.

**Q. What would you refuse to add?**

A. Auto-merge. LLM implementer without SAST. Persistence without a clarify/HITL when the spec is silent. Weak random “because it is faster.”

## Time-box variants

**5 minutes:** Thesis → graph sketch → HITL + fan-out → greenfield SAST retry → limitations → “I can demo.”

**15 minutes:** Full talk track + live `ambiguous --interactive` (see DEMO.md) + one product curl.

**30 minutes:** Add brownfield impact/`doNotTouch`, reject path, and “how this becomes a bank SDLC bus.”

## Files to have open

1. `docs/architecture.md` — mermaid
2. `constitution.md` — policy
3. `src/main/java/com/example/agentic/SdlcGraph.java` — interrupt + fan-out edges
4. A `runs/<thread>/` directory from a rehearsal (HITL_GATE, audit, metrics)
5. `docs/DEMO.md` — your live script

## Tone

Speak as the engineer who **bounded** the agent, not as someone who “let Copilot build it.” Use “the graph must not self-approve,” “safe-stop,” “decision lineage.” If you do not know, say the trade-off and the production follow-up. That matches the assignment’s last line: humans own oversight, approvals, and final quality.
