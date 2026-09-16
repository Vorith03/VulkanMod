# Chat Rollover and Handoff Protocol

This protocol exists because long-running development chats eventually accumulate enough history, tool output, logs, and superseded hypotheses that continuing in the same chat becomes less reliable and less efficient. It also makes repository work resilient to an unexpected chat/session interruption: the repository should remain sufficient to resume useful work even if the current conversation disappears without a final handoff message.

Session startup and live-state recovery are defined once, in `AGENTS.md` Section 3A. This document governs when and how to checkpoint or roll over a conversation; it does not define a second startup procedure.

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

A planned rollover is not the only failure mode. A chat can end unexpectedly while an agent is editing, building, waiting on CI, or investigating a failure. Work should therefore be checkpointed during the session when losing the conversation would otherwise lose information needed to resume safely.

The goal is that a new session can recover from Git, CI, `AGENT_STATUS.md`, and durable project documents without needing the old chat to have ended cleanly.

### Checkpoint triggers

Create or refresh a durable checkpoint when practical if any of the following is true:

- valuable coherent changes or recovery information exist only in the working tree/chat;
- a new investigation materially changes the active hypothesis, blocker, safety boundary, or next action;
- a roadmap/milestone gate or other durable capability changes state;
- important user-machine evidence arrives or a user-side test is being requested for a later session;
- work is about to move to a meaningfully different implementation slice or independent workstream;
- the session has become unusually tool/context heavy and an interruption would make recovery ambiguous;
- a rollover is about to be recommended and the existing durable checkpoint would materially misdirect the next session.

Do **not** checkpoint merely because an expensive operation is about to run when the code and the identity of that operation are already durable. A coherent pushed commit plus its identifiable CI workflow is normally sufficient recovery state while that CI is running. Likewise, do not rewrite `AGENT_STATUS.md` merely to mirror every commit.

A checkpoint does **not** require committing knowingly broken or incoherent code merely to create activity. Prefer a small coherent commit. If an experiment cannot safely be committed yet and losing it would matter, preserve the recovery information in the appropriate status/design document and state exactly what remains uncommitted or incomplete.

### Checkpoint contents

A timeout-safe checkpoint should make the following recoverable with minimal inference:

- branch and checkpoint/HEAD identity where relevant;
- coherent commits created so far;
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
6. refresh durable status only if the resulting evidence changes the continuation state.

This ensures a failed build, disconnected tool session, or chat timeout cannot erase the implementation that was being validated without forcing a documentation write for every validation run.

Do not delay all commits until every possible validation stage is complete when doing so would leave substantial useful work only in ephemeral session state. Normal Git discipline still applies: checkpoints should be logically understandable and should not combine unrelated experiments.

### Bound long tasks into resumable stages

Large work items should be divided into recoverable stages such as:

`investigate -> implement -> cheap check -> durable code checkpoint -> expensive validation -> status checkpoint if state changed -> integrate`

An agent may continue through these stages without asking the user for permission when the work is already authorized. The boundaries exist so an interruption between stages loses little or no engineering state.

Do not start another unrelated investigation merely to fill time while a critical state change is still unrecorded.

### Long commands and CI waits

Before starting an operation that may take a long time, ask whether an unexpected interruption would leave the next session unable to tell what was being tested. If yes, checkpoint first. If the coherent change is already pushed and the workflow/run identifies the test, avoid adding redundant documentation solely because the operation is long.

For GitHub Actions specifically, the pushed commit and workflow run are durable evidence. Once a coherent change is pushed, record or retain enough information to identify the relevant run rather than depending on the current chat staying alive until the workflow completes.

Avoid tying several independent changes to one enormous validation cycle when smaller, ordered checkpoints can isolate failures more clearly.

### Proactive stabilization when a session is getting risky

If the current session has become long, tool-heavy, or difficult to reason about, stop opening new workstreams. Finish the smallest coherent unit already in progress, make its recovery state durable, refresh handoff/status information only if needed, and only then continue or recommend rollover.

The priority is **recoverability over squeezing one more unrelated task into the same conversation**.

## When rollover is due

The agent should explicitly tell the user that this is a good point to start a fresh chat and briefly explain why. The recommendation should not imply that work is lost or that the user must manually reconstruct project state.

Before recommending the new chat, the current agent must prepare the handoff.

### 1. Stabilize repository state

Before handoff when practical:

- finish the current atomic edit;
- commit coherent changes to `forge-1.20.1`;
- do not leave unexplained half-edits;
- record whether the latest CI/build is green, red, pending, or not run when that affects the next action;
- record any artifact that the user still needs to test.

If work genuinely must stop with an incomplete experiment, say exactly what is incomplete and do not represent it as a finished fix.

### 2. Produce a concise handoff

The handoff must include enough information to recover anything not already obvious from the durable checkpoint and live Git/CI. At minimum, include the current task/blocker, important work completed in the outgoing chat, any validation or user test whose result still matters, and the next recommended action. Include exact HEAD/run/artifact identifiers when they are needed to disambiguate mutable state.

Prefer durable repository facts over a narrative transcript. Do not repeat long lists of known-good history or copy large logs when an error signature, run ID, file name, commit, or focused evidence document is enough to recover the evidence.

### 3. Put durable knowledge in the repository

If the outgoing chat discovered something future agents should know regardless of conversation history, put it in an appropriate repository document before rollover when practical.

Examples:

- `AGENTS.md` for standing development rules;
- `AGENT_STATUS.md` for the current continuation checkpoint;
- a focused design/evidence document for subsystem-specific conclusions;
- this file for chat/handoff policy.

Do not use the chat handoff as the only storage location for important architectural conclusions that should survive many chats.

### 4. Tell the user what to do

When ready, recommend that the user open a new chat/work session and use a short continuation request such as:

> Continue work on `Vorith03/VulkanMod`, branch `forge-1.20.1`, from the current repository checkpoint and roadmap.

If the environment supports direct access to the same connected GitHub repository, the user should **not** be asked to paste the entire old conversation.

If there is critical state that exists only in the current conversation (for example, a runtime observation or log that was never made durable and cannot otherwise be retrieved), the outgoing agent must identify it explicitly and tell the user what needs to be carried into the new chat.

## Starting a new chat

A new session follows the canonical session-start and continuation procedure in `AGENTS.md` Section 3A.

If the previous chat ended unexpectedly, do not assume the absence of a final message means no progress was made. Inspect live Git and CI first, then recover additional Project conversation context only when important user-side evidence or unfinished state cannot be recovered from the repository.

The new session should trust current repository/CI/runtime evidence over prose from an older handoff when they conflict and should continue from the resulting live task rather than repeating historical archaeology.

## Practical rule

A chat is long enough when carrying its history is becoming more expensive or less reliable than reconstructing the current state from Git, CI, logs, and concise handoff notes.

The repository should be able to survive the conversation disappearing at an inconvenient moment. The goal is not frequent restarts for their own sake; it is to keep each agent working from a compact, high-confidence context while preserving continuous project progress.
