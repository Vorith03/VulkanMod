# Chat Rollover and Handoff Protocol

This protocol exists because long-running development chats eventually accumulate enough history, tool output, logs, and superseded hypotheses that continuing in the same chat becomes less reliable and less efficient. It also makes repository work resilient to an unexpected chat/session interruption: the repository should remain sufficient to resume useful work even if the current conversation disappears without a final handoff message.

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

## Interruption and timeout resilience

A planned rollover is not the only failure mode. A chat can end unexpectedly while an agent is editing, building, waiting on CI, or investigating a failure. Work should therefore be checkpointed **during** the session rather than relying on one final handoff at the end.

The goal is that a new session can recover from Git, CI, `AGENT_STATUS.md`, and durable project documents without needing the old chat to have ended cleanly.

### Mandatory checkpoint triggers

Create or refresh a durable checkpoint whenever practical at these points:

- after a coherent implementation milestone is reached;
- before starting a long or failure-prone build/test/CI operation when valuable changes currently exist only in the working tree;
- before asking the user for a runtime test whose result may arrive in a later chat;
- after a new investigation materially changes the active hypothesis, blocker, or next action;
- before opening a second independent workstream in the same session;
- when the session has become unusually tool-heavy or context-heavy even if a planned rollover is not yet due;
- before explicitly recommending a new chat.

A checkpoint does **not** require committing knowingly broken or incoherent code merely to create activity. Prefer a small coherent commit. If the current experiment cannot safely be committed yet, preserve the recovery information in the appropriate status/design document and state exactly what remains uncommitted or incomplete.

### Checkpoint contents

A timeout-safe checkpoint should make the following recoverable with minimal inference:

- branch and exact HEAD;
- the coherent commits created so far;
- whether meaningful work remains uncommitted or otherwise unpublished;
- the active task/gate;
- the current evidence-backed hypothesis or conclusion;
- the exact next action;
- validation already completed;
- validation still outstanding;
- any CI run or artifact that now owns the next piece of evidence;
- any user-side test already requested and the success/failure signal expected from it.

Do not turn `AGENT_STATUS.md` into a transcript. Record only state that changes what the next session should do.

### Commit before expensive validation

When a coherent change exists, prefer this ordering:

1. make the smallest logical change;
2. run the cheapest relevant local/static check available;
3. commit the coherent change;
4. push/publish it when repository policy permits;
5. run or inspect expensive CI/runtime validation;
6. update the durable checkpoint with the resulting evidence.

This ensures a failed build, disconnected tool session, or chat timeout cannot erase the implementation that was being validated.

Do not delay all commits until every possible validation stage is complete when doing so would leave substantial useful work only in ephemeral session state. Normal Git discipline still applies: checkpoints should be logically understandable and should not combine unrelated experiments.

### Bound long tasks into resumable stages

Large work items should be divided into recoverable stages such as:

`investigate -> implement -> cheap check -> checkpoint -> expensive validation -> checkpoint -> integrate`

An agent may continue through these stages without asking the user for permission when the work is already authorized. The boundaries exist so an interruption between stages loses little or no engineering state.

Do not start another unrelated investigation merely to fill time while a critical state change is still unrecorded.

### Long commands and CI waits

Before starting an operation that may take a long time, ask whether an unexpected interruption would leave the next session unable to tell what was being tested. If yes, checkpoint first.

For GitHub Actions specifically, the pushed commit and workflow run are durable evidence. Once a coherent change is pushed, record or retain enough information to identify the relevant run rather than depending on the current chat staying alive until the workflow completes.

Avoid tying several independent changes to one enormous validation cycle when smaller, ordered checkpoints can isolate failures more clearly.

### Proactive stabilization when a session is getting risky

If the current session has become long, tool-heavy, or difficult to reason about, stop opening new workstreams. Finish the smallest coherent unit already in progress, checkpoint it, refresh the handoff/status information if needed, and only then continue or recommend rollover.

The priority is **recoverability over squeezing one more unrelated task into the same conversation**.

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
- `AGENT_STATUS.md` for the current continuation checkpoint;
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
4. Read `AGENT_STATUS.md` and the relevant durable project docs.
5. Inspect the latest CI/build evidence before predicting failures.
6. Re-establish the highest **verified** milestone.
7. Identify the exact next unresolved task.
8. Check whether the previous session left any documented incomplete/uncommitted experiment or pending user test.
9. Continue from that task rather than repeating historical archaeology.

The new agent should trust current repository state and evidence over prose from an older handoff if they conflict.

If the previous chat ended unexpectedly, do not assume the absence of a final message means no progress was made. Recover from the branch tip, recent commits, CI, `AGENT_STATUS.md`, and referenced design/evidence documents first.

## Practical rule

A chat is long enough when carrying its history is becoming more expensive or less reliable than reconstructing the current state from Git, CI, logs, and concise handoff notes.

The repository should be able to survive the conversation disappearing at an inconvenient moment. The goal is not frequent restarts for their own sake; it is to keep each agent working from a compact, high-confidence context while preserving continuous project progress.
