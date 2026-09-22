# Autopilot mode: addendum to the accountability design

Proposal, 2026-09-22. Extends `accountability-design-claude-code.md`. Nothing here is built. Its decisions are closed; see the end.

## What it is

The operator approves a scope of work and leaves. The planner works through it unattended:

- writing and dispatching builds and pulses inside an approved scope;
- reading reports;
- running every device check an agent can run on the connected phone.

It stops when the scope's goal is met, a budget runs out, or nothing runnable is left. Whatever needs a person goes into lists waiting for the operator's return:

- hands-on device checks;
- decisions the work stopped on;
- work the planner thinks should come next.

EGD's warning applies unchanged (OVERVIEW, "About running on autopilot"). Work done unattended is internally consistent and checked against its own instructions. Nobody has compared it to what the operator actually wanted. Autopilot changes when the operator reviews. It does not remove the review.

## Prerequisites

Autopilot requires phase 2: the intent gate, and the coder's SubagentStop report gate. Without those, an unattended coder's output is only as good as its self-report, and nobody is present to question it. The phase 3 auditor is strongly recommended. Unattended is exactly when an independent reader of each diff is worth the most.

## How it keeps running, and how it stops

A Stop hook on the main session blocks the planner from ending its turn while the approved scope still has runnable work. It stops the session when any of these holds:

- the planner declares the scope's goal met, with the evidence;
- the budget set at launch is spent (dispatches or hours, whichever comes first);
- every remaining item is blocked.

Blocked means the item depends on a pending decision, a failed dispatch, or a hands-on check that hasn't run.

## Approval under autopilot (decision B, by scope)

Before leaving, the operator approves a scope file, `autopilot/scope-<date>.md`. The planner writes the dispatches from it while the operator is away. What is lost is EGD's main gate: the operator reading each prompt before it goes. The design does not pretend otherwise. It moves that review to the return, and it keeps everything reversible until then.

**The scope file.** Machine-checkable, so a hook can hold the planner to it:

- **Goal:** one paragraph, in the operator's words.
- **Allowed paths:** globs the coder may change.
- **Forbidden paths:** globs it may not change, even inside an allowed glob.
- **Allowed dispatch types:** some of build, device, pulse.
- **Closed decisions:** each one quoted, which every dispatch must carry.
- **Open questions the planner may not close:** anything that touches one goes to decisions pending.
- **Budget:** dispatches and hours.

**The dispatch hook on autopilot.** On top of its phase 1 section check, it blocks a dispatch when:

- the dispatch's Scope boundary names a path outside Allowed paths, or inside Forbidden paths;
- its type is not allowed;
- its Closed decisions section omits any decision quoted in the scope file.

A blocked dispatch goes to `autopilot/proposals.md`. The check is structural: a dispatch can pass it and still decide something the operator never decided.

**Planner decisions, declared.** Every unattended dispatch carries a section, "Planner decisions": each choice the dispatch makes that the scope file does not. This is EGD PROTOCOL rule 6 applied to the planner. A missing section blocks. An empty one is allowed, and the auditor challenges it.

**The auditor's second job.** Besides checking each diff against its dispatch, the auditor checks each dispatch against the scope file. Did it decide something the scope left open without declaring it? Did it quietly narrow or stretch the goal? Its findings go into the return brief.

**Nothing merges.** Merge stays the operator's. Every planner decision made while the operator is away sits on an unmerged branch, reversible, until the operator has read it.

## Room inside, a hard fence outside

Oversight relaxes on autopilot by necessity. So the design gives the agents room to decide inside the scope, and makes the edge of the scope hard enough that they know exactly where not to cross.

### Inside the fence: the planner decides

The coder still never decides. It stops on ambiguity, exactly as when the operator is present. On autopilot the stop goes to the planner, and the planner may close it, provided that the decision:

- lies inside Allowed paths and outside Forbidden paths;
- touches none of the scope's open questions;
- does not depend on the physical world or on what counts as true. EGD PROTOCOL rule 7 makes those the operator's, non-delegable. A device check result the agent can read is evidence; a judgement about what a user sees or wants is not.
- is reversible before merge.

Each such decision is declared in the next dispatch's "Planner decisions" section, with the evidence it rests on, and goes to the top of the return brief. Two failed fixes on one symptom still mean data only, never a third guess, whoever decides.

### At the fence: stop and queue

These go to `autopilot/decisions-pending.md`, with the question, the options, the evidence each rests on, and which work they block:

- any decision failing a test above;
- a change to the scope itself: its goal, its paths, its closed decisions;
- an abort condition met;
- a dispatch whose base or premise turns out wrong in a way that changes the goal, not just the next step.

The planner then moves to work that doesn't depend on the stopped item.

### The fence the scope cannot move

Some paths are forbidden on every autopilot run, whatever the scope file says. The dispatch hook adds them to Forbidden paths itself:

- `.claude/**`: agents, settings, hooks. An agent must never be able to loosen its own guards.
- The checkers: `check_record.py`, `check_prompts.py`, `check_queue.py`, and their tests.
- `CLAUDE.md`.
- `autopilot/scope-*.md`: the approved scope cannot be edited from inside it.
- The headers of `RECORD.md` and `DEVICE_QUEUE.md`. Their entries are append-only anyway, and enforced.

Some actions are forbidden too:

- merge;
- any push to main;
- force-push;
- the destructive device commands from phase 1;
- adding or upgrading a dependency.

The last is the only one not traced to an incident. It is here because a dependency change reaches outside every path glob at once. Recorded as the planner's proposal, for the operator to strike if it costs more than it catches.

## The device check queue

`DEVICE_QUEUE.md` is append-only, in the same style as `RECORD.md`.

**A check entry** is written by the coder when its report has device items no agent can run, or ones Decision 2 keeps for the operator. It holds:

- ID;
- the PR and the commit it was written against;
- the files it exercises;
- setup;
- steps ordered cheapest first, grouped by shared setup;
- a pass condition and named evidence per step;
- which steps are observations rather than gates.

**A result entry** closes exactly one check entry. It holds the device, the OS version, the build tested, a per-step result, and the matrix row.

**`check_queue.py`** renders the open list and validates the store. It marks an entry **stale** when any file it exercises has changed on its PR since the entry's commit, because a pass on the old build is evidence about the old build. A stale entry is re-run from the top, never resumed.

Queued checks block merge, not building. Every PR that has open checks says so in its description, and the stack-aware rule holds: a PR stacked on one with open checks inherits them.

## Agent-run device checks while unattended (Decision 2)

With the phone left connected, unlocked and on Stay awake, the agent can run its own device checks under the phase 1 guards:

- foreground check before every input and screenshot;
- no uninstall, no `pm clear`;
- `connectedAndroidTest` only with the APK left installed;
- signature check before any install.

What the guards don't cover while unattended:

- A notification or incoming call can change the foreground app between the guard's check and the command.
- A screenshot taken while Forager is in front can still include a notification shade dropped over it.

Mitigations:

- Do Not Disturb on for the run;
- screenshots reviewed by the auditor before anything is committed;
- any shot showing a non-Forager surface deleted unviewed, and counted in the report.

## The return brief

When the session stops, the planner writes `autopilot/return-<date>.md`. Nothing in it is new: every line points at an entry in the record, the queue or the prompt store. In reading order:

1. **Why it stopped:** goal met, budget spent, or all remaining items blocked.
2. **Planner decisions to review:** every declared planner decision across the run, with the dispatch it sits in and the auditor's findings on it. First, because review before merge is where the lost gate now lives.
3. **Device checks waiting for you:** the rendered open queue, cheapest first, stale entries marked. Anything touching the same setup is grouped, so the phone is picked up once.
4. **Decisions pending:** each with its options and evidence, and what it blocks.
5. **What ran:** each dispatch by prompt file, PR, result, and audit findings.
6. **Proposals:** work the planner would have dispatched next, unapproved, with reasons.
7. **Anything that went wrong in the run itself:**
   - guard blocks;
   - shots deleted unviewed;
   - aborts;
   - the budget used.

## What this does not catch

- A scope approved wrong, or read differently by the planner than meant. The path and decision checks enforce the letter of the scope, not its intent. Only the return review catches this.
- A planner decision nobody noticed was a decision. Declaring is a habit, not a detector (EGD PROTOCOL rule 6, stated limit). The auditor raises the odds of catching it; it does not guarantee it.
- Device state drifting while unattended: battery, a system update, a dropped USB connection. The pulse at the start of each device dispatch records `adb devices` and the build, and a mismatch stops that dispatch.
- A queued check that goes stale in a way file matching misses, for example a dependency bump. Staleness by file is a floor, not a guarantee.

## Build order

Autopilot is phase 4. It comes after phase 2 (required) and phase 3 (recommended). It is one dispatch, containing:

- the Stop hook;
- the fixed fence, added to every scope's Forbidden paths by the dispatch hook;
- the autopilot branch of the dispatch hook (scope checks, Planner decisions section);
- `DEVICE_QUEUE.md` and `check_queue.py`;
- the return brief template.

The first run is short: a scope with a budget of two dispatches and one queued check. The operator runs the queue on return, and the record says whether the brief was usable before autopilot gets a longer leash.

## Closed decisions (operator, 2026-09-22)

1. **Scopes, not texts.** The operator approves a scope file. The planner writes the dispatches while the operator is away, under the checks above.
2. **Agent-run device checks while unattended.** Allowed, under the phase 1 guards and the mitigations above.
3. **Room inside, a hard fence outside.** Oversight relaxes on autopilot by necessity. Agents get room to decide inside the scope, and the scope's edge is made hard enough that they know where not to cross.
