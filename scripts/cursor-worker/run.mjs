#!/usr/bin/env node
/**
 * Sidecar: run a Cursor local agent against a cwd and wait for completion.
 *
 * Usage:
 *   node run.mjs --cwd <dir> --prompt-file <path>
 *
 * Env:
 *   CURSOR_API_KEY   required
 *   CURSOR_MODEL     optional, default composer-2.5
 *
 * Exit: 0 finished, 1 startup/config failure, 2 run executed but failed.
 */
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { Agent, CursorAgentError } from "@cursor/sdk";

function arg(name, fallback = "") {
  const idx = process.argv.indexOf(name);
  if (idx >= 0 && idx + 1 < process.argv.length) {
    return process.argv[idx + 1];
  }
  return fallback;
}

const cwd = resolve(arg("--cwd", process.cwd()));
const promptFile = arg("--prompt-file");
if (!promptFile) {
  console.error("usage: node run.mjs --cwd <dir> --prompt-file <path>");
  process.exit(1);
}

const apiKey = (process.env.CURSOR_API_KEY || "").trim();
if (!apiKey) {
  console.error("CURSOR_API_KEY is required");
  process.exit(1);
}

const modelId = (process.env.CURSOR_MODEL || "composer-2.5").trim();
const prompt = readFileSync(resolve(promptFile), "utf8");

let agent;
try {
  agent = await Agent.create({
    apiKey,
    model: { id: modelId },
    // Shell always spawns /bin/zsh inside Cursor's command sandbox; that
    // path is missing there (ENOENT) and crashes this process. The Java
    // graph owns mvn/SAST. Agents write files with edit/read tools.
    disallowedTools: ["shell", "task"],
    local: { cwd, sandboxOptions: { enabled: false } },
  });
  const agentId = agent.agentId || agent.agent_id || "";
  const run = await agent.send(prompt);
  const runId = run.id || "";
  console.log("[cursor-worker] agentId=" + agentId + " runId=" + runId);
  const result = await run.wait();
  const status = result.status || "unknown";
  console.log("[cursor-worker] status=" + status);
  if (status === "error") {
    process.exit(2);
  }
  process.exit(0);
} catch (err) {
  if (err instanceof CursorAgentError) {
    console.error(
      "startup failed: " + err.message + ", retryable=" + err.isRetryable,
    );
    process.exit(1);
  }
  console.error(err);
  process.exit(1);
} finally {
  if (agent) {
    try {
      if (typeof agent[Symbol.asyncDispose] === "function") {
        await agent[Symbol.asyncDispose]();
      } else if (typeof agent.close === "function") {
        await agent.close();
      }
    } catch {
      // best-effort dispose
    }
  }
}
