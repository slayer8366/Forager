# Dispatch 2026-10-29-05: the no-tools guard on pre-main, and the first PR tools build (#440) (a coder, Opus)

Written by Rowan Waymark, 2026-10-29. RECORD -1805. Builds on docs/audits/2026-10-29-pr-tools-build-report.md (branch pr-tools-build-report, 7a3f2f9a1); read it first. pre-main is **d100a4776**; #440's head is d1770d5b5. Verify both against the remote.

**Authorisation.** The owner, verbatim:
- "Throwaway copy per PR (Recommended)"
- "One app (Recommended)"
- "Logger, Diagnostics, Crash Logs (Recommended)"
- "Only before a phone check (Recommended)"
- "Yes, add the check (Recommended)"
- "Start a coder now"

The planner chose efa52f57d (the copy on the parked rebase, on current main; fewer clashes) as the tools' source.

Describe products generically. Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name.

## Part 1: the guard (a small PR into pre-main)
CI fails if `app/src/debug/` or `app/src/release/` exists, or if a file the tools commit adds exists anywhere in the tree.
- Its message names no tool, as the report proposed.
- It runs on pushes and PRs into pre-main and main. It must **not** fail a `tools/pr-*` branch built with "Run this branch": say how the workflow tells the two apart.
- Show it failing on a test branch that adds an empty `app/src/debug/`, and passing on pre-main.
- Open the PR into pre-main, with no merge.

## Part 2: tools/pr-440 (a throwaway branch, never a PR, never merged)
- Start from d1770d5b5 and add only the walk logger, Diagnostics and Crash Logs from efa52f57d. Leave out the alert record files and their hooks in ReturnWatch, SundownWatch and BackByWatch, and the synthetic forecast. If the logger can't be separated from those, stop and say what it needs.
- In every clash, keep pre-main's code, including ReturnWatch's way-home trails. List each resolution.
- Build with `gh workflow run ci.yml --ref tools/pr-440`. Read the result from the XML, and say plainly if a failure is unrelated to the tools.
- Report the APK's version name, which should read above 4175 so it installs over #440 on the S22, keeping Region 1. Report its schema (22), and the adb grants the logger needs.
- Don't touch the phone. The install is its own S22 session.

## Rules
- CI only, and no Gradle on the laptop.
- Network: GitHub only.
- The Restricted Object: never read, name or quote it.
- Ambiguity: stop and report.
- If you pass about 200k tokens, push and hand off.

## Amendment 1 (2026-10-29): the walk logger's own switch (RECORD -1810)
Part 2 stopped because the walk logger's switch is implemented by SyntheticForecastCellStore. The owner: "A small switch of its own (Recommended)".
- In tools/pr-440 only (debug source set), write a debug-only switch store on the same diagnostics DataStore file and the `diagnostics.walk_logger` key, held once per process.
- WalkLogger and DiagnosticsPanel read that store. AppContainer keeps pre-main's AbsentForecastCellStore.
- The four logger tests move onto the new store.
- Leave the synthetic forecast out entirely.
- Each moved test must still fail when the switch is broken: run one revert check on the new store.
- Then continue Part 2 as written. Rebuild on #440's newest head once Hazel's Amendment 4 push to altitude-sea-level lands. If it hasn't landed when you're ready, use d1770d5b5 and say so.

## Amendment 2 (2026-10-29): dev's lint setting comes along (RECORD -1813)
tools/pr-440 at 5dd087f4e (run 40958910795) is green except Android lint: 18 errors in the tools' own code, the first NewApi at AndroidWalkLogPlatform.kt:104. The owner: "Bring dev's lint setting along (Recommended)".
- Add to tools/pr-440, as a new commit and with no history rewrite, the lint change dev carries for its guarded API calls, 82b55f06a ("CI's lint check accepts dev's guarded API calls").
  - Name exactly what it changes: a baseline, a lint.xml, or a Gradle lint option.
  - Confirm it applies only to this branch, and that pre-main and main stay unchanged.
- Run "Run this branch" and read every job.
  - Lint should go green. If any of the 18 is not covered by dev's setting, list it and stop.
  - Report the new APK's version name.
- Record only, no change: WalkLoggerSwitchMidRecordingTest passed with the store broken (run 40956265183). The tools copy does not pin the store through that test.
