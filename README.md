# LangGraph4j URL Shortener

Java URL shortener with a LangGraph4j SDLC orchestrator: spec → task DAG → parallel design → implement → SAST/JUnit gates → retry/fallback/rollback → human approval.

**Setup and how to add features:** [SETUP.md](SETUP.md)

## Quick start

```bash
cd ~/github/langgraph4j-url-shortener
mvn test
mvn -q exec:java -Dexec.args="run greenfield --auto-approve"
# live feature/bugfix (needs CURSOR_API_KEY in .env):
./scripts/sdlc.sh -r "Add GET /metrics for links and capacity"
./scripts/sdlc.sh -f requirements/feature-bulk-shorten.txt
mvn -q exec:java -Dexec.args="serve 8080"
```

## Architecture

See [docs/architecture.md](docs/architecture.md). Constitution: [constitution.md](constitution.md).
