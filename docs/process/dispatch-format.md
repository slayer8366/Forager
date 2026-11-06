# Forager's dispatch format

How a Forager dispatch file is laid out. The owner asked for it on 2026-10-16: "And dispatch format too" (RECORD -1116), after the record's header was rewritten to point at E-GD's RECORDING.md (-1111).

It is matched to two sources, both on E-GD-Philosophy's branch Test-1:
- RECORDING.md's fields for an intent;
- the dispatch-authoring and dispatch-as-file skills.

CLAUDE.md still governs. Where this file and CLAUDE.md disagree, CLAUDE.md wins and this file is wrong.

## Where a dispatch lives

- **The file.** `prompts/preserved/YYYY-MM-DD-NN.md` on the records branch. It is committed together with its intent entry in RECORD.md, before any coder starts.
- **Once committed, it isn't edited.** A change to a running task is a new file (`-amendment-N.md`), or a message quoted in a continuation entry.
- **Reports.** The coder's reports are filed unedited beside it as `YYYY-MM-DD-NN-report.md`, with a phase name where there are phases.
- **The coder reads the file itself,** never a summary of it.

## The sections, in order

Each section says which intent field it supplies. The intent quotes or points at these, so the dispatch and the record can't drift apart.

1. **Title.** `# Dispatch YYYY-MM-DD-NN: <what it does, in plain words>`.
2. **Header.** Who wrote it and when; the RECORD ID of its intent; the design entries it builds on.
3. **Authorisation.** The owner's words, verbatim, including their yes to "Start a coder on X now?". A design answer is not a go.
4. **Writing rule.** One fixed line: "Describe products generically. Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name."
5. **Change.** What will be different afterwards. For anything a person sees, this is the owner's step path (tap → shows → Back), as confirmed with the owner. *Intent field: Change.*
6. **Scope boundary.** What may be touched (branch, folders, files), and what must not be: main, release builds, other coders' files, RECORD.md and the audit index (the planner writes those). *Intent field: Scope boundary.*
7. **Baseline.** The base branch and the commit it was written against, checked against the remote at writing. Every claim about the code is a premise to re-check, never a fact. *Intent field: Baseline.*
8. **Verify first.** Each premise, with file and line, for the coder to confirm before building. Where anything is uncertain, the work runs in two phases:
   - Phase 1 reads and reports, then stops;
   - Phase 2 builds, on the planner's go.
9. **Predictions.**
   - **Outcome:** what the planner expects the finished work to do, written here before it is built. *Intent field: Prediction (outcome, planner).*
   - **Mechanism:** the coder writes this in its Phase 1 report (or its first message when there is no Phase 1), before building, and the planner adds it to the record with a continuation. *Intent field: Prediction (mechanism, coder).*
10. **Evidence required.**
    - Tests first, each shown failing for its reason.
    - Revert checks run under CLAUDE.md's runner rules: compile log first, restore from a saved copy, forward change confirmed afterwards.
    - CI counts read from the JUnit XML.
    - Real coordinate touches where a touch is the claim.
    - Device-only items listed separately, as a device check written to the device-check-authoring skill.
11. **Stops.** What makes the coder stop and report instead of choosing:
    - an owner-level choice;
    - a wrong premise;
    - main or a release build having to change;
    - a test having to be weakened;
    - a migration number or another globally unique value;
    - two failed attempts at one symptom.

    *Intent field: Abort conditions.*
12. **Finish line.** What counts as done, for example "PR into pre-main, green, merged on the owner's word" (an experiment's would read "PR into dev, green, unmerged; the phone check the owner agreed to, S22 first"). *Intent field: Finish line.*
13. **Final message.** The completion report:
    - commits and PR;
    - failing-first evidence;
    - revert checks;
    - CI counts;
    - what is untested;
    - every call beyond the dispatch;
    - the adb steps for any device check.

## From dispatch to record

- **The intent** is written when the dispatch is committed. It carries Title, Change, Scope boundary, Baseline, Prediction (outcome, planner), Finish line, Abort conditions and `Dispatch-file`. The coder's mechanism prediction follows by continuation, before building starts.
- **Each report** gets an outcome entry with `Continues`.
- **One terminal closes the intent** when the work ends, whether done, partial (with what was deferred) or abandoned (with the working state). An outcome that only says `Continues` closes nothing.
- **Past dispatch files stay as they were written.** This format applies from the first dispatch after it is merged into pre-main.
