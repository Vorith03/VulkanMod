# Chat Rollover and Handoff Protocol

This protocol exists because long-running development chats eventually accumulate enough history, tool output, logs, and superseded hypotheses that continuing in the same chat becomes less reliable and less efficient.

## Recommended chat lifetime

A development chat should normally be rolled over after roughly **one substantial milestone or 2–4 hours of active repository work**, whichever comes first.

This is a guideline, not a timer. A chat should be restarted sooner when context quality is visibly degrading, and it may continue longer when the current task is tightly scoped and the agent still has a clear, accurate view of repository state.

Agents should proactively recommend starting a new chat when **any two** of the following are true:

- the chat has covered multiple independent debugging/fix cycles;
- several builds, CI runs, or runtime-test artifacts have accumulated;
- important facts are increasingly being recovered from summaries rather than the live working context;
- old failures/hypotheses are taking substantial context despite already being resolved;
- the agent has to repeatedly re-establish current HEAD, current artifact, or current blocker;
- tool output or logs from earlier work are making it harder to distinguish current evidence from stale evidence;
- a major milestone has just completed and the next work is a meaningfully different phase;
- the agent notices itself repeating investigation already settled in the repository or handoff notes;
- a clean handoff would be shorter and more reliable than carrying the existing conversation forward.

Do **not** interrupt an active atomic operation merely because the nominal time/milestone threshold has been reached. Finish the immediate build/fix/verification loop first, leave the repository in a coherent state, and then recommend rollover.

## When rollover is due

The agent should explicitly tell the user that this is a good point to start a fresh chat and briefly explain why. The recommendation should not imply that work is lost or that the user must manually reconstruct project state.

Before recommending the new chat, the current agent must prepare the handoff.

### 1. Stabilize repository state

Before handoff when practical:

- finish the current atomic edit;
- commit coherent changes to `forge-1.20.1`;
- do not leave unexplained half-edits;
- record whether the latest CI/build is green, red, pending, or not run;
- record any artifact that the user still needs to test.

If work genuinely must stop with an incomplete experiment, say exactly what is incomplete and do not represent it as a finished fix.

### 2. Produce a concise handoff

The handoff must include at least:

- repository and branch;
- exact current HEAD SHA and commit title;
- important commits made in the outgoing chat;
- highest verified milestone;
- what is known to work;
- what has failed and the exact evidence/root cause where known;
- current unresolved blocker or investigation;
- latest CI run/result and artifact, if relevant;
- user-side runtime configuration required for testing;
- any pending user test and exactly what log/output is needed;
- next recommended repository action;
- important hypotheses that are **not yet proven**, clearly labeled as such;
- files/docs that the next agent should read first.

Prefer durable repository facts over a narrative transcript. Do not copy large logs into the handoff when an error signature, run ID, file name, or commit is enough to recover the evidence.

### 3. Put durable knowledge in the repository

If the outgoing chat discovered something future agents should know regardless of conversation history, put it in an appropriate repository document before rollover when practical.

Examples:

- `AGENTS.md` for standing development rules;
- `docs/FORGE_PORT_AUDIT.md` for port-specific findings;
- `docs/PERFORMANCE_AUDIT.md` for performance findings;
- this file for chat/handoff policy.

Do not use the handoff as the only storage location for important architectural conclusions that should survive many chats.

### 4. Tell the user what to do

When ready, recommend that the user open a new chat/work session and use a short continuation request such as:

> Continue work on `Vorith03/VulkanMod`, branch `forge-1.20.1`. Read `AGENTS.md` and the current handoff/project docs first, inspect current HEAD and recent CI, then continue from the documented next action. Do not redo settled investigation without new evidence.

If the environment supports direct access to the same connected GitHub repository, the user should **not** be asked to paste the entire old conversation.

If there is critical state that exists only in the current conversation (for example, a runtime log that was never committed and cannot otherwise be retrieved), the outgoing agent must identify it explicitly and tell the user what needs to be carried into the new chat.

## Starting a new chat

A new agent resuming this project must:

1. Read root `AGENTS.md` before making repository changes.
2. Read this chat rollover protocol when the work is a continuation.
3. Inspect `forge-1.20.1` HEAD and recent commits.
4. Read the relevant durable project docs, especially current audits/handoffs.
5. Inspect the latest CI/build evidence before predicting failures.
6. Re-establish the highest **verified** milestone.
7. Identify the exact next unresolved task.
8. Continue from that task rather than repeating historical archaeology.

The new agent should trust current repository state and evidence over prose from an older handoff if they conflict.

## Practical rule

A chat is long enough when carrying its history is becoming more expensive or less reliable than reconstructing the current state from Git, CI, logs, and concise handoff notes.

The goal is not frequent restarts for their own sake. The goal is to keep each agent working from a compact, high-confidence context while preserving continuous project progress.