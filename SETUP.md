# Setup and agentic workflow

This is a **Java 17** URL shortener driven by **LangGraph4j**. The graph is the SDLC orchestrator; Maven/JUnit/SAST are the gates. Humans approve release.

## 1. Prerequisites

- JDK 17+ (`java -version`)
- Maven 3.9+ (`mvn -version`)

```bash
cd ~/github/langgraph4j-url-shortener
```

## 2. Build, test, SAST

```bash
mvn test
mvn -q exec:java -Dexec.mainClass=com.example.shortener.sast.SastScanner
```

All JUnit tests must pass. SAST must report zero HIGH/CRITICAL findings before HITL.

## 3. Run the product

```bash
mvn -q exec:java -Dexec.mainClass=com.example.shortener.UrlShortenerServer
# or
mvn -q exec:java -Dexec.args="serve 8080"
```

```bash
curl -X POST --data "https://example.com/long" http://127.0.0.1:8080/shorten
curl -i http://127.0.0.1:8080/<code>
curl http://127.0.0.1:8080/stats/<code>
curl http://127.0.0.1:8080/health
```

## 4. Run the agentic SDLC (LangGraph4j)

The graph: **understand → (clarify HITL) → decompose → plan → constitution gate → parallel design (impact | risk | tests) → implement → validate (JUnit checks + SAST) → retry/fallback/rollback → docs → HITL → summary**.

```bash
# Greenfield: first implement injects java.util.Random so SAST fails, then retries with SecureRandom
mvn -q exec:java -Dexec.args="run greenfield --auto-approve"

# Brownfield: add click counts on an existing core shortener
mvn -q exec:java -Dexec.args="run brownfield --auto-approve"

# Ambiguous: pauses before clarify; auto-approve still answers storage=memory then HITL
mvn -q exec:java -Dexec.args="run ambiguous --auto-approve --clarify memory"
```

Manual HITL (no `--auto-approve`):

```bash
mvn -q exec:java -Dexec.args="run greenfield"
# graph pauses before hitl; note thread_id
mvn -q exec:java -Dexec.args="resume THREAD_ID --approve"
# or --reject  or --request-changes
```

Each run writes `runs/<threadId>/`:

| File | Purpose |
|------|---------|
| `workspace/` | Generated Java for that spec |
| `HITL_GATE.md` | Review package |
| `ENGINEERING_SUMMARY.md` | Plan, metrics, limitations |
| `audit.jsonl` | Decision lineage |
| `metrics.json` | retries, rollbacks, e2e ms |
| `artifacts/` | plan, test, SAST reports |

## 5. Add a feature with the agentic workflow

Do **not** edit `001` behavior in place as an untracked change. Treat every increment as a new requirement through the graph.

### Step A — Write the requirement

In one sentence, plus what is out of scope. Example:

> Add custom aliases for short codes. Aliases must still reject dangerous schemes. Persistence remains out of scope.

If anything is unclear (store? auth? TTL?), that is an **ambiguous** scenario — the graph must interrupt for clarify.

### Step B — Encode it as a scenario (or run ad hoc)

1. Add FRs and tasks in `src/main/java/com/example/agentic/Catalog.java`  
   - Every task maps to an FR  
   - Mark independent tasks `parallel=true`  
   - List `dependsOn` for the DAG  
2. Add brownfield impact notes in `SdlcNodes.impact` (which classes change).  
3. Keep constitution: JUnit happy/edge/failure, SAST, no merge without HITL, max 5 retries.

### Step C — Run the graph

```bash
mvn -q exec:java -Dexec.args="run brownfield --auto-approve"
```

For a real review, omit `--auto-approve` and resume with `--approve` only after reading `HITL_GATE.md`.

### Step D — Gates that must pass

1. Constitution gate (spec FRs + tasks exist)  
2. Implement writes workspace Java  
3. SAST on workspace (weak `Random` is HIGH)  
4. FR checks (shorten/resolve/stats/health)  
5. Fail → retry (max 5) → fallback clean template → rollback snapshot → safe-stop  
6. HITL: Approve / Request changes (replan) / Reject  

The agent **never** merges. You own that.

### Step E — After approval

Copy accepted workspace files into `src/main/java` if the run produced the desired increment, or implement the FRs in the product module to match the spec. Re-run `mvn test` and SAST. Open a PR; do not push to `main` without review.

## 6. Constitution (non-negotiable)

See `constitution.md`:

- Java 17 only for app, tests, and SAST  
- JUnit 5 happy / edge / failure  
- HIGH/CRITICAL SAST blocks HITL  
- Max 5 autonomous iterations  
- Human approve / request changes / reject  
- Traceability: task ↔ FR ↔ test  

## 7. Troubleshooting

| Symptom | What to do |
|---------|------------|
| `GraphStateException` on compile | Graph wiring error — check `SdlcGraph` |
| SAST flags `new Random()` | Expected on greenfield iteration 0; retry should apply `SecureRandom` |
| HITL never pauses | You passed `--auto-approve`; run without it |
| Ambiguous never asks | Clarification already in state; start a new thread |
| Tests look at `src/main/java` | Run Maven from the repo root |

## 8. Assignment mapping

| Assignment | This repo |
|------------|-----------|
| Requirement understanding | `understand` + spec FRs |
| Task decomposition | `Catalog.tasks` DAG |
| Brownfield reasoning | `impact` node |
| Orchestration | LangGraph4j `StateGraph`, parallel design branch, cycles, checkpoints |
| HITL | `interruptBefore("clarify","hitl")` + resume |
| Retry / fallback / rollback | quality-gate routing |
| Metrics | `metrics.json` |
| Three scenarios | greenfield, brownfield, ambiguous |
| Working prototype | `UrlShortenerServer` |
| Setup | this file |
