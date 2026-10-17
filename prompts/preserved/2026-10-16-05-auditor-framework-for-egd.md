# The production gate and the UX auditor: a framework for E-GD

Written by Forager's planner (Rowan Waymark) on 2026-10-16, at the owner's request: "Can you write out the auditor framework we discussed earlier, for EGD?" It sets down, in project-independent form, the framework the owner set up in Forager that day (Forager RECORD -1085 to -1087, -1094, -1097, -1100). It is offered to E-GD's curator to adopt, adapt or reject. Forager-specific details appear only as the origin.

**Status: untested in practice.** As of writing, the framework is in place in Forager but no promotion has been audited under it. Everything below is design plus what the bootstrapping run showed; nothing yet shows how it behaves on a real release.

## The problem it answers

In Forager, every finished task had been merging straight into the branch that store builds come from. A change could pass every automated test and still reach the public with a screen that confused people, a layout broken by real system bars, or a camera that misbehaved on a phone nobody tested. Tests showed the code did what the builder intended. Nothing checked that what shipped was fit for the people using it.

The owner's ruling, verbatim: "Main is the sacred live production branch where all Play builds come from. So anything going into main from this point (not retroactive on already running coders), has to be tested, debugged, and approved for public use."

## 1. Three branches, three jobs

- **Production** (Forager: `main`). Every public build comes from it. It changes only by a promotion through the gate in section 2. Documents count too: nothing reaches production any other way.
- **Working** (Forager: `pre-main`). Finished work lands here. Small tasks merge directly. A large task that is not experimental is built on its own branch, which merges here when done.
- **Experiments** (Forager: `dev`). Production, plus developer-only tools, plus whatever experiments are running. It is rebased onto production after every production change, "to reflect workability" (the owner): it shows whether the experiments still work on what is actually released. It is never merged anywhere. An experiment chosen to ship is carried onto the working branch without the developer tools, then promoted like everything else.

A change already in flight when the framework is adopted continues as it was dispatched. The owner made the rule "not retroactive on already running coders".

## 2. The gate: a promotion from working to production

A promotion passes four things, in order:

1. **Automated checks** pass on the working branch, at the exact commit being promoted.
2. **A device check** of a plain build, without developer tools, on the project's real test devices, with any extra form factor added when screens change. Bugs found here are fixed on the working branch first, then checked again.
3. **The auditor's report** against the project's QA philosophy (sections 3 and 4).
4. **The owner's approval for public use.** Then the merge.

Two variants:
- **A promotion that changes no user-facing code** skips the device check, since there is nothing to see on a device. The auditor still reads any public text, such as a privacy policy or store listing, against what the product actually does.
- **An urgent fix for a live bug** starts from production and goes through the same gate at the front of the queue, then is carried into the working branch so it isn't lost. The owner's word: "Same gate, front of queue". A fix made by reverting a change gets a short audit: does it match the earlier approved state, and does it start on the devices?

## 3. The auditor

- **What it is.** A user-experience specialist, run as a fresh agent for each promotion. It is independent of whoever built the work and keeps no attachment to it.
- **What it reads:**
  - the automated results;
  - the device-check evidence;
  - the list of known bugs;
  - accessibility;
  - copy;
  - every changed behaviour, walked as a step path (tap, shows, Back).
- **What it reports:** a verdict with items, and what it couldn't check.
  - **Pass:** nothing found that a user would meet.
  - **Pass with items:** items, none of which risks a user's data, safety or trust. The owner ships or holds each one.
  - **Fail:** an item that would crash, lose data, mislead about anything the product measures, break one of the owner's rulings, or ship something unfinished.
  - **Each item states** where it is (the screen and the step path), what the user would experience, why it matters (the principle it breaks), and the evidence, or "not seen on a device".
- **What the verdict does: it advises, and the owner decides.** The owner's word: "Advises, you decide". Nothing reaches production without the owner's approval, and a fail goes to the owner with a recommendation. It is not a veto.
- **Device first, emulator as the fallback.** The owner's word: "For the philosophy, running on a device is recommended. Also add an option for an emulator run it device check itself isn't available". So:
  - the auditor's own walk-through runs on a real device;
  - where none is available, an emulator run is allowed, and every result from it is labelled "emulator, not a device";
  - the QA philosophy lists what an emulator can't stand in for: real location and sensors in the field, real system bars and touch, heat and battery, outdoor legibility, the device maker's own hardware behaviour, and an upgrade over a real store install;
  - a check that needs one of these stays "not seen on a device".
  - On a shared machine the emulator counts as a heavy job, run only when nothing else heavy is running.
- **What it does not do.** It writes no product code, fixes nothing it finds, merges nothing, and decides nothing on the owner's behalf. It never silences or weakens a test. It never turns the owner's words into rules of its own, and never settles a conflict between two rulings: it puts both sides to the owner.

## 4. The QA philosophy: what the auditor audits against

- **Bootstrapping.** If none exists, the auditor's first task is to draft one. The owner's words: "I also empower the auditor to draft a QA philosophy to propose if none exists". The draft:
  - first confirms no QA philosophy exists under any name, and stops if one does;
  - marks every principle as either **"the owner's words"**, quoted exactly with its source, or **"proposed by the auditor"**, with its reason and prior art;
  - says, for each principle, the test an auditor applies;
  - defines the verdicts, and the evidence a promotion must show;
  - lists what only a device can show;
  - ends with numbered questions, one per proposal and per open choice, each with a recommendation, so the owner can answer from a phone.
- **Where prior art disagrees with an owner ruling,** or two rulings pull different ways, the draft shows both sides and leaves the choice to the owner.
- **Adoption.** The owner answers. The auditor folds the answers in, marking each adopted proposal "adopted from the auditor's proposal", never as the owner's words. The ruled version goes through the working branch to production at the next promotion.
- **In Forager's run:**
  - the draft found 13 principles already in the owner's words across the project's rules and record, and proposed 8 more: starts without crashing on a real device; an update never loses user data; accessible; survives interruption; nothing unfinished; known bugs listed before release; public words stay true; battery cost stated;
  - it asked 24 questions, and the owner adopted every recommendation;
  - three questions were genuine conflicts: an accessibility standard against a fixed camera layout; a platform guideline against a deliberately disabled platform backup; and two rulings on honest placeholder text.

## 5. Recording

Each promotion is a piece of work in the record:
- an intent with the full fields of RECORDING.md;
- the auditor's report filed unedited beside it;
- a merge entry with its backup;
- one terminal.

The auditor's dispatch follows the project's dispatch format.

## What the bootstrapping run showed

- **Writing it down revived an old structure.** Forager's working-branch model had existed before and lapsed. The working branch was 943 commits behind production, with nothing of its own, when the rule was revived.
- **Drafting was quick, and the draft overran.** The auditor drafted the philosophy in about 15 minutes, at about 3,600 words against a 1,500-word aim. Its 24 questions were answerable from a phone in one pass.
- **Ambiguous branch rules took two corrections.** The owner's first statement ("pre-main as the working branch for non extensive tasks") needed two rounds of questions before it covered large tasks, experiments, the experiments branch's base and urgent fixes. One answer was corrected minutes later ("Dev is main + any experiment we have running"). Frameworks like this should be written as a step path and confirmed whole before anything is recorded.
- **Not yet known:** how long a real promotion audit takes; whether the auditor's items are ones the owner acts on; and whether "advises, you decide" holds under release pressure.

## Sources in Forager

- RECORD.md on records-after-173: -1085 (the auditor and the draft), -1086 and -1087 (the branch rules), -1092 (the draft filed), -1094 (device first, emulator fallback), -1097 (adoption), -1100 (merged into pre-main).
- `docs/process/qa-philosophy.md` and `docs/process/planner-profile.md` ("Branches and the road to main"), both on pre-main.
- The auditor's reports: prompts/preserved/2026-10-16-03-report.md.
