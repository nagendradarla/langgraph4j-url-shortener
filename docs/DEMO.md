# Live demo — HITL and parallel SDLC workflow

Use this as a **runbook** in the interview. Rehearse once the night before. Talking points are in *italics*. Commands assume repo root.

Pair with [INTERVIEW.md](INTERVIEW.md) for Q&A.

## What you are proving

In one sitting the interviewer should see:

1. **Human-in-the-loop** — the graph **stops** and waits; you type a decision; it resumes in the same thread.
2. **Parallel design** — `FAN-OUT → impact | risk | test_strategy` then `JOIN` (sync barrier).
3. **Parallel implement** — independent tasks in one wave (`T1, T5 [parallel tasks]` on greenfield).
4. **Controlled autonomy** — quality gate can **fail**, retry, then still require a human (greenfield).
5. **Audit** — `runs/<threadId>/` is the review package.

The graph is the demo. The URL shortener is the artifact it produces.

## Why `--interactive` (read this)

Checkpoints use LangGraph4j `MemorySaver`. They live **in that JVM only**.

| Command | What happens |
|---------|----------------|
| `run … --interactive` | Same process pauses at `clarify` / `hitl`, reads stdin, resumes. **Use this live.** |
| `run … --auto-approve` | Same process auto-answers gates. Backup if stdin is awkward. |
| `run …` then a **second** `resume THREAD` Maven process | Will **not** see the checkpoint. Do not demo that. |

`--auto-approve` is a test driver. Say so if you use it: *“The graph still interrupts; the CLI supplies the human action.”*

## Night-before checklist

```bash
cd ~/github/langgraph4j-url-shortener
java -version    # 17+
mvn -version     # 3.9+
mvn test         # warm the cache; all tests green
```

Leave the IDE on `docs/architecture.md`. Have a second terminal ready for `serve` + `curl`.

If `mvn exec:java` does not accept typing (some IDE run configs swallow stdin), use the Java classpath command in [Fallback: no Maven stdin](#fallback-no-maven-stdin).

---

## Recommended live path (about 10 minutes)

**Primary:** ambiguous scenario — two human gates, then parallel design.  
**If time:** greenfield — SAST fail → retry → HITL.  
**Always optional:** product curls.

### Terminal layout

- Terminal A: orchestrator (this demo)
- Terminal B: product server (optional last)
- Editor: `docs/architecture.md` and later `runs/<threadId>/`

---

### Demo 1 — Ambiguous requirement + HITL + parallel fan-out (~6 min)

*Say: “This requirement is intentionally vague: analytics plus persist, no store named. The graph must not invent Postgres.”*

```bash
mvn -q exec:java -Dexec.args="run ambiguous --interactive"
```

#### Gate 1 — clarify

You should see `[sdlc]` lines through `understand — ambiguous`, then:

```
======== HUMAN GATE ========
next_node=...clarify...
Ambiguity: where should analytics be stored?
Enter storage [memory|sqlite|postgres] (default memory):
```

**Type:** `sqlite`  *(stronger story than memory)*  
Press Enter.

*Say: “I asked for sqlite. The prototype still falls back to in-memory, but it records that as an FR and a risk — it does not silently ship a database.”*

#### Parallel design (watch, do not type)

Look for:

```
[sdlc] constitution_gate passed — spec FRs and task DAG present
[sdlc] FAN-OUT → impact_analysis | risk_analysis | test_strategy  (join at join_design)
[sdlc] impact — brownfield
[sdlc] risk — 4
[sdlc] test_strategy — done
[sdlc] JOIN ← impact + risk + test_strategy  (sync barrier before implement)
```

*Say: “Three design nodes, then a join. Implement is not allowed until impact, risk, and test strategy all complete. That is the assignment’s parallel path plus synchronization.”*

`risk — 4` (not 3) means the sqlite/postgres extra risk was added. If you typed `memory`, you will see `risk — 3`.

Implement waves then run (sequential after TA1; TA3 is the storage decision). Then validate.

#### Gate 2 — release HITL

```
======== HUMAN GATE ========
next_node=...hitl...

# HITL Review Gate
**Scenario:** ambiguous
**Tests passed:** true
**SAST clean:** true
...
HITL action [approve|reject|request_changes] (default approve):
```

**Type:** `approve`  
Press Enter.

*Say: “Tests and SAST already passed. The agent still cannot merge. I am the change-control gate.”*

Expected tail:

```
status=completed
phase=done
hitl={action=approve}
runDir=runs/<threadId>
```

**Copy `runDir`.** Then:

```bash
THREAD=runs/<paste-thread-id>   # or the full path printed as runDir=
cat "$THREAD/HITL_GATE.md"
cat "$THREAD/ENGINEERING_SUMMARY.md"
cat "$THREAD/metrics.json"
echo "---- audit (fan-out / join / hitl) ----"
rg "fanout|join_design|clarify|hitl" "$THREAD/audit.jsonl"
```

Point at:

- `audit.jsonl` — decision lineage (node + timestamp)
- `metrics.json` — `interrupts` ≥ 1 (clarify + HITL), `retries` / `rollbacks`
- `workspace/` — generated Java
- `artifacts/sast-report.json` and `test-report.json`

If they ask “request changes?” *“That edge goes replan → decompose with a new specVersion. I can type it on the next run.”*

---

### Demo 2 — Greenfield: parallel tasks + SAST retry + HITL (~4 min)

*Say: “Greenfield injects a weak PRNG on iteration 0 so we can see the quality gate fail closed, then a bounded retry. The agent gets five loops max; it never skips HITL.”*

```bash
mvn -q exec:java -Dexec.args="run greenfield --interactive"
```

Watch for this sequence (narrate as it scrolls):

```
[sdlc] FAN-OUT → impact_analysis | risk_analysis | test_strategy
[sdlc] JOIN ← impact + risk + test_strategy
[sdlc] implement wave: T1(validator), T5(reliability)  [parallel tasks]
[sdlc] implement wave: T2(service)
...
[sdlc] quality gate: tests=false sast=false iteration=0 → retry
[sdlc] retry — 1
[sdlc] implement wave: T1(validator), T5(reliability)  [parallel tasks]
[sdlc] quality gate: tests=true sast=true iteration=1 → documentation
======== HUMAN GATE ========
```

*Say: “T1 and T5 have no dependencies, so they run in one wave. T2 depends on the validator, so it waits. First validate fails CWE-338 (`new Random()`). Retry copies `SecureRandom`. Only then do we pause for approval.”*

**Type:** `approve`

Optional: show the poison vs clean templates if they want depth:

- `src/main/resources/templates/poison-UrlShortenerService.java.txt` — iteration 0
- `src/main/java/com/example/shortener/UrlShortenerService.java` — `SecureRandom`

---

### Demo 3 — HITL reject (only if they ask, ~90s)

```bash
mvn -q exec:java -Dexec.args="run brownfield --interactive"
```

At HITL **type:** `reject`

Expected: `status=rejected`, `phase=stopped`.

*Say: “Reject is a first-class exit, not an exception. Safe-stop. Nothing is merged.”*

---

### Demo 4 — Product APIs (optional, ~2 min)

*Say: “The data plane the graph is aiming at.”*

Terminal B:

```bash
mvn -q exec:java -Dexec.args="serve 8080"
```

Terminal A:

```bash
CODE=$(curl -s -X POST --data "https://example.com/long" http://127.0.0.1:8080/shorten)
echo "code=$CODE"
curl -s -o /dev/null -w "%{http_code} %{redirect_url}\n" http://127.0.0.1:8080/$CODE
curl -s http://127.0.0.1:8080/stats/$CODE
echo
curl -s -X POST --data "javascript:alert(1)" http://127.0.0.1:8080/shorten
echo
curl -s http://127.0.0.1:8080/health
```

Expect: short code; `302` to `https://example.com/long`; stats `1`; `Invalid URL`; health `ok`.

Stop the server with Ctrl-C when done.

---

### Demo 5 — Change control: branch, commit, push (never `main`)

HITL **approve** is not a merge. *Say: “The graph does not push to main. I cut a review branch, commit the outcome, and push for a PR.”*

Always create a **new** branch. If you are already on a feature branch (not `main`/`master`), still create another branch from `HEAD` and push that — do not commit on `main`/`master`.

```bash
git checkout main
git pull --ff-only origin main

# If HEAD is main/master, or you are already on some other branch: always cut a new one.
BRANCH="demo/sdlc-$(date +%Y%m%d-%H%M)"
git checkout -b "$BRANCH"

git status
git add -A
git status
git commit -m "$(cat <<'EOF'
Record HITL-approved SDLC outcome on a review branch.

The orchestrator does not merge to main; a human pushes and opens a PR.
EOF
)"

git push -u origin HEAD
echo "Pushed $(git branch --show-current) — open a PR; do not merge from the agent."
```

`runs/` and the assignment PDF stay untracked (`.gitignore`). Do not `--force` to `main`.

If `git commit` says nothing to commit, skip the commit and still `git push -u origin HEAD` so the branch exists on the remote.

---

## Backup path if you cannot type into Maven

Same graph, CLI auto-fills human actions. Still show stdout FAN-OUT/JOIN/retry, then open `runDir`.

```bash
mvn -q exec:java -Dexec.args="run greenfield --auto-approve"
mvn -q exec:java -Dexec.args="run ambiguous --auto-approve --clarify sqlite"
```

*Say: “Auto-approve is only the driver. `interruptBefore` still fires; tests in `SdlcOrchestratorTest` resume with approve or reject on the same `Runner`.”*

Proof of HITL in tests (if they doubt the interrupt):

```bash
mvn -q -Dtest=SdlcOrchestratorTest#ambiguousClarifyThenComplete,SdlcOrchestratorTest#hitlRejectSafeStops test
```

---

## Fallback: no Maven stdin

```bash
mvn -q -DskipTests package
java -cp "target/classes:$(mvn -q -DincludeScope=runtime dependency:build-classpath -Dmdep.outputFile=/dev/stderr 2>/dev/null | tail -1)" \
  com.example.agentic.AgenticMain run ambiguous --interactive
```

Simpler if the classpath is already in the IDE: run `AgenticMain` with program args `run ambiguous --interactive`.

---

## What each `[sdlc]` line means (cheat sheet)

| Log | Point to make |
|-----|----------------|
| `understand — ambiguous` | Requirement not ready; do not decompose yet |
| `HUMAN GATE` / `clarify` | HITL #1 — persistence |
| `constitution_gate passed` | Entry gate: FRs + task DAG exist |
| `FAN-OUT → impact \| risk \| test_strategy` | Parallel design nodes |
| `JOIN ← …` | Sync barrier |
| `implement wave: T1, T5 [parallel tasks]` | Task DAG parallelism |
| `quality gate: … → retry` | Bounded autonomy, fail closed |
| `HUMAN GATE` / `hitl` | HITL #2 — release |
| `summarize — complete` | Human approved; summary + metrics written |
| `safe_stop — rejected` | Human refused; controlled stop |

---

## Files to open after a run

```
runs/<threadId>/
  HITL_GATE.md              ← what the human saw
  ENGINEERING_SUMMARY.md    ← plan / HITL / retries
  audit.jsonl               ← lineage
  metrics.json              ← retries, interrupts, e2eMs
  artifacts/plan.json
  artifacts/test-report.json
  artifacts/sast-report.json
  workspace/                ← generated product sources
  snapshot/                 ← rollback restore point
```

Graph wiring if they want code: `src/main/java/com/example/agentic/SdlcGraph.java` — search for `interruptBefore` and the three `fanout_design` edges.

---

## Timing card (keep this visible)

| Min | Move |
|-----|------|
| 0:00 | Thesis + open architecture mermaid |
| 1:00 | Start `run ambiguous --interactive` |
| 1:30 | Clarify: type `sqlite` |
| 2:00 | Narrate FAN-OUT / JOIN / implement |
| 3:30 | HITL: show `HITL_GATE.md`, type `approve` |
| 4:00 | `audit.jsonl` + `metrics.json` |
| 5:00 | If time: `run greenfield --interactive` through retry, then approve |
| 8:00 | Optional: `serve` + curl (reject `javascript:`) |
| 9:00 | Optional: Demo 5 — new branch, commit, push (not `main`) |
| 9:30 | Stop. Offer Q&A. |

If they only give you **5 minutes**: greenfield `--interactive` only (retry + parallel wave + HITL approve) and skip curls.

---

## If something goes wrong

| Symptom | Fix |
|---------|-----|
| Prompt never appears; process exits | You omitted `--interactive` / `--auto-approve`. Re-run with `--interactive`. |
| Typed `resume` in a new terminal, empty/unknown state | Expected. Use `--interactive` in **one** process. |
| No `[sdlc] FAN-OUT` lines | Rebuild: `mvn test` then re-run. |
| HITL never pauses | You used `--auto-approve`. |
| `javascript:` not rejected | Server not this repo, or old process on 8080. |
| Tests fail in the interview | `mvn -Dtest=SdlcOrchestratorTest test` still shows the graph; HTTP tests need localhost. |
| Clarify skipped | Scenario was `greenfield`/`brownfield` (no ambiguity). Use `ambiguous`. |

Do not debug Maven for more than 30 seconds. Switch to `--auto-approve` and walk `runs/`.

---

## One-sentence closers

After Demo 1: *“Ambiguity stopped the graph. Parallel design ran only after a constitution pass. A human still approved the release.”*

After Demo 2: *“The agent was allowed to retry a SAST failure. It was not allowed to approve itself.”*
