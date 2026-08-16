# Cursor worker sidecar

LangGraph4j Java nodes spawn this process to run a **Cursor local agent**.

```bash
export CURSOR_API_KEY=cursor_...
npm install
node run.mjs --cwd /path/to/workspace --prompt-file /tmp/prompt.txt
```

Exit codes: `0` finished, `1` startup/config, `2` run started but failed.

Do not git commit from the agent. The Java graph owns HITL.
