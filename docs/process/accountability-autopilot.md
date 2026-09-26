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

Autopilot requires phase 2: the intent gate, and the coder's SubagentStop report gate. Without those, an unattended coder's output is only as good as its self-report, and nobody is present to question it. The phase 3 auditor, serving as captain, is required at the Captain rung of Earning authority; below it, the owner has not yet seen enough to trust it unattended. Unattended is exactly when an independent reader of each diff is worth the most.

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

**Planner decisions, declared.** Every unattended dispatch carries a section, "Planner decisions": each choice the dispatch makes that the scope file does not. This is EGD PROTOCOL rule 6 applied to the planner. A missing section blocks. An empty one is allowed, and the captain challenges it.

**The captain's second job.** Besides checking each diff against its dispatch, the captain (the phase 3 auditor, see Chain of command) checks each dispatch against the scope file. Did it decide something the scope left open without declaring it? Did it quietly narrow or stretch the goal? Its findings go into the return brief.

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

## Chain of command

The hierarchy is fixed:

- **Owner:** the operator. Sets the destination and the fence in the scope file.
- **Captain:** the phase 3 auditor, raised to this role. Holds the owner's intent aboard and says when the course is off.
- **Navigator:** the planner. Plots the course as dispatches, and decides how to correct it.
- **Crew:** the coder and pulse agents. Move the ship.

Orders flow down: scope, then findings, then dispatches. Evidence flows up: reports, then audits, then the return brief. Escalation goes one level at a time. The crew stops to the navigator. A disagreement between navigator and captain goes to the owner. The log is open to every level.

The position is fixed, but its authority is earned, not granted: every call the captain makes is checked, by the drills and by the owner's rulings (see Earning authority). That is the lab's evidence policy applied to the hierarchy itself.

### What the captain measures against

The captain gives context and leaves the navigator room. It says where the course is off and why, measured against a standing order, and it does not say how to correct it.

"Off course" is defined in advance, so the captain is precise and not a matter of taste. Every finding cites the order it is measured against:

- the scope's goal, and whether a dispatch narrows or stretches it;
- a closed decision, and whether a dispatch or diff contradicts it;
- the fence: paths, types, forbidden actions;
- the evidence rules: claims without file:line, a test reported as failing first without its failure pasted;
- a "Planner decisions" section that omits a choice the dispatch visibly makes.

A drift that cites no order is not a finding. The captain logs it as an observation for the owner, but it binds no one.

### Graded calls

- **Note.** Something worth recording; the course is fine. The navigator may leave it unanswered.
- **Adjust.** The course is off relative to a cited order. Once earned (see Earning authority), the navigator answers before the next dispatch, either "adjusted, here is the change" or "not adjusting, here is the evidence". The dispatch hook blocks the next dispatch while an Adjust has no answer.
- **All stop.** The course touches the fence or a closed decision. That line of work halts until the owner rules, and the navigator moves to independent work. An All stop cannot be declined.

A declined Adjust goes to the top of the return brief, for the owner to settle.

### Earning authority

The captain's authority is not granted at launch. It is earned in front of someone ready to fact-check it: the owner.

**How the captain acts, so every call can be checked quickly.**

- Each call is a claim with a check path. It gives the order, cited as file:line in the scope or the record, and the evidence, cited as file:line in the dispatch or diff. It states exactly what it claims, and what would prove it wrong.
- Each call keeps three things apart: what the captain read, what it observed, and what it inferred.
- No call is inflated beyond the grade its evidence supports.
- When the owner overrules a call, the captain records it and may argue once, with evidence (EGD). It does not argue again.
- When the captain misses something the owner or a drill finds, it records the miss itself.

**How the owner checks.** At first, every call, and every "on course" report on a sample of dispatches. Each check is recorded as a ruling: upheld, overruled, or unclear, with one line why. The rulings are the captain's record.

**The ladder.**

- **Lookout, at launch.** Note and Adjust calls are advisory; the owner rules on each. All stop is binding from the first day, because the costs are lopsided. A wrong stop costs a stall, but a missed one costs damage. Any hand on a ship or a lab bench can call a stop. Authority to change course is what gets earned.
- **Captain.** Adjust calls become binding on the navigator once the record shows all of the following:
  - a run of calls fact-checked by the owner, with no citation found false;
  - every drill caught, at the right grade;
  - "on course" reports that held up when the owner sampled them.

  The thresholds are the owner's to set. The planner proposes 10 checked calls and at least 3 drills as a starting point, to be revised with evidence.
- **Demotion.** One fabricated citation sends the captain back to lookout: a line cited that does not say what the call claims. So does a pattern of missed drills. Trust is slow to earn and quick to lose, and the ladder should behave the same way.

Autopilot requires a captain at the Captain rung. The owner does not go ashore with an untested captain aboard.

### Keeping the captain alert

A captain that goes quiet looks the same as a course that is right, so silence is not allowed.

- **Report every time.** The captain reports on every dispatch, including "on course", with the orders it checked and the evidence it read. A pass is data.
- **Fresh context.** Each review runs as a new subagent, so the captain is not lulled by a long session.
- **Drills.** Now and then, the owner or the dispatch hook plants a known off-course marker in a dispatch: a path outside the fence, or a dropped closed decision. Did the captain call it, and at the right grade? This is EGD's sabotage test, applied to the captain. A missed drill is recorded, never hidden.
- **Its own record.** The tally keeps count of calls made, calls declined by the navigator, how the owner ruled on each, and drills caught or missed. If the owner keeps siding against the captain, the captain is raising noise and its orders or prompt need work. If the captain misses drills, it has stopped being alert.

## Change of watch: fresh sessions by rule

Ships change the watch on a fixed schedule because a tired officer doesn't know they're tired. A long session has the same problem. As context fills and compacts, earlier decisions blur, and the session can't tell what it has lost. So sessions end by rule, not when they feel degraded.

This applies with the owner aboard and on autopilot alike.

### Who changes watch, and when

- **Crew and captain:** already fresh. Every coder, pulse and captain run is a new subagent with its own context. Rule: one dispatch per subagent run, never reused.
- **Navigator:** the long-lived session, and the one that degrades. It changes watch at the first of:
  - a set number of dispatches in this session (the planner proposes 5, for the owner to set);
  - a set number of hours;
  - any compaction event. A PreCompact hook marks it: the navigator finishes the step in hand, starts no new dispatch, and the watch changes;
  - a scope or phase boundary.

### Handover comes from the log, not from memory

The outgoing navigator is the degraded one, so its summary is not the source. EGD PROTOCOL rule 8 applies: a handover note is testimony, and the incoming watch reads the sources.

A SessionStart hook runs the taking-the-watch checklist, and the new navigator reads, citing each by hash:

1. BOOTSTRAP and CLAUDE.md, and its role;
2. HEAD, and the approved scope with the budget left;
3. unterminated intents (EGD rule 10), surfaced before any new work;
4. open captain calls, and any Adjust without an answer;
5. decisions pending, and the open device queue;
6. the last few preserved dispatches and their reports.

The outgoing navigator's last message may be kept as a handover note, if the Stop hook can capture it. The new watch treats it as a pointer to sources, never as a source.

### Autopilot runs in legs

On autopilot there is no owner to start the next session. A runner script outside Claude Code starts one navigator session per watch, headless. It waits for that session to end, then starts the next, until the scope is done, the budget is spent, or everything is blocked. Each leg is a fresh context. The record carries the state between legs.

### To verify before building

- Whether a SessionStart hook can inject context into the new session, or only print it.
- Whether a PreCompact hook can stop compaction, or only observe it.
- Whether the Stop hook receives the transcript, so it can capture the handover note.
- The headless launch flags on 2.1.280.

Where a mechanism doesn't exist, the rule still holds. It just needs another trigger: a budget count kept by the dispatch hook instead of a compaction signal, for instance.

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
- screenshots reviewed by the captain before anything is committed;
- any shot showing a non-Forager surface deleted unviewed, and counted in the report.

## The return brief

When the session stops, the planner writes `autopilot/return-<date>.md`. Nothing in it is new: every line points at an entry in the record, the queue or the prompt store. In reading order:

1. **Why it stopped:** goal met, budget spent, or all remaining items blocked.
2. **Planner decisions to review:** every declared planner decision across the run, with the dispatch it sits in and the captain's calls on it. Declined Adjust calls come first. First, because review before merge is where the lost gate now lives.
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
- A planner decision nobody noticed was a decision. Declaring is a habit, not a detector (EGD PROTOCOL rule 6, stated limit). The captain raises the odds of catching it; it does not guarantee it.
- Device state drifting while unattended: battery, a system update, a dropped USB connection. The pulse at the start of each device dispatch records `adb devices` and the build, and a mismatch stops that dispatch.
- A queued check that goes stale in a way file matching misses, for example a dependency bump. Staleness by file is a floor, not a guarantee.

## Build order

Autopilot is phase 4. It comes after phase 2 and phase 3, and once the captain has earned the Captain rung with the owner aboard. It is one dispatch, containing:

- the Stop hook;
- the change-of-watch rules: the SessionStart checklist, the PreCompact trigger, and the runner script for legs;
- the fixed fence, added to every scope's Forbidden paths by the dispatch hook;
- the autopilot branch of the dispatch hook (scope checks, Planner decisions section);
- `DEVICE_QUEUE.md` and `check_queue.py`;
- the return brief template.

The first run is short: a scope with a budget of two dispatches and one queued check. The operator runs the queue on return, and the record says whether the brief was usable before autopilot gets a longer leash.

## Closed decisions (operator, 2026-09-22)

1. **Scopes, not texts.** The operator approves a scope file. The planner writes the dispatches while the operator is away, under the checks above.
2. **Agent-run device checks while unattended.** Allowed, under the phase 1 guards and the mitigations above.
3. **Room inside, a hard fence outside.** Oversight relaxes on autopilot by necessity. Agents get room to decide inside the scope, and the scope's edge is made hard enough that they know where not to cross.
4. **Chain of command.** Owner, captain, navigator, crew. The captain gives context and room, but calls off-course precisely against cited orders, in grades the navigator must answer, once the captain has earned that authority (decision 5).
5. **Authority is earned first.** The captain starts as a lookout, with every call fact-checked by the owner. Adjust becomes binding only when the record shows it can be trusted. All stop binds from the start, because a wrong stop is cheap and a missed one is not.
6. **Change of watch.** Sessions end by rule to prevent memory degradation. The navigator changes watch on a dispatch count, a time limit, any compaction, or a scope boundary, and the incoming watch takes state from the record, not from the outgoing session's memory.
