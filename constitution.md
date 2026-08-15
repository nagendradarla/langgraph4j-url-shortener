# Constitution — LangGraph4j URL Shortener

## I. Test coverage
Every public product behavior must have JUnit 5 coverage: happy path, edge case, and failure.
A task is not done until its tests exist and pass.
Application, tests, and SAST are Java-only.

## II. Security (SAST gate)
Code must pass `com.example.shortener.sast.SastScanner` before HITL.
HIGH/CRITICAL findings (CWE-338, 601, 798, 502) block HITL.

## III. Human-in-the-loop
LangGraph4j `interruptBefore` pauses on clarify and HITL.
The graph must not self-approve. Human: Approve, Request changes, or Reject.

## IV. Controlled autonomy
Implement → test → SAST → fix up to 5 times. Then fallback, then rollback and safe-stop.
Do not cross HITL autonomously.

## V. Traceability
Every task maps to a spec FR. Every change maps to a task ID. Every test maps to an FR.
Audit JSONL records decision lineage.

## VI. Change control
Release-ready output is under `runs/<thread>/`. Success requires HITL approval.
