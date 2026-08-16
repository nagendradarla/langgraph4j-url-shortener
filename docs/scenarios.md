# Scenarios

Run from repo root after `mvn test`.

## Greenfield (offline, no API key)

```bash
mvn -q exec:java -Dexec.args="run greenfield --auto-approve"
```

First implement uses a weak PRNG so SAST fails; retry applies `SecureRandom`. HITL then approve.

## Brownfield (offline, no API key)

```bash
mvn -q exec:java -Dexec.args="run brownfield --auto-approve"
```

Workspace starts as shorten/resolve only; the graph adds click counts and `GET /stats/{code}`.

## Ambiguous (offline, no API key)

```bash
mvn -q exec:java -Dexec.args="run ambiguous --auto-approve --clarify memory"
```

Graph interrupts for storage (`memory|sqlite|postgres`). Durable stores fall back to in-memory with an explicit risk.

## Live feature or bugfix (Cursor agents)

Same graph and HITL. Requirement is free text or a file. Needs `CURSOR_API_KEY` in `.env` (see `.env.example`).

```bash
./scripts/sdlc.sh -r "Add GET /metrics returning links and capacity. In-memory only."
./scripts/sdlc.sh -f requirements/feature-bulk-shorten.txt
./scripts/sdlc.sh requirements/feature-optional-ttl.txt

# After HITL approve, copy workspace Java into src/main/java (never git-commits):
./scripts/sdlc.sh --apply -f requirements/feature-optional-ttl.txt
```

Default is `--interactive`. Pass `--auto-approve` only for unattended runs.
