# Scenarios

Run from repo root after `mvn test`.

## Greenfield

```bash
mvn -q exec:java -Dexec.args="run greenfield --auto-approve"
```

First implement uses a weak PRNG so SAST fails; retry applies `SecureRandom`. HITL then approve.

## Brownfield

```bash
mvn -q exec:java -Dexec.args="run brownfield --auto-approve"
```

Workspace starts as shorten/resolve only; the graph adds click counts and `GET /stats/{code}`.

## Ambiguous

```bash
mvn -q exec:java -Dexec.args="run ambiguous --auto-approve --clarify memory"
```

Graph interrupts for storage (`memory|sqlite|postgres`). Durable stores fall back to in-memory with an explicit risk.
