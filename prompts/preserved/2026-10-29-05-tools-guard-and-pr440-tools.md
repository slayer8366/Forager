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
