# Dispatch 2026-10-29-04: a dev-tools build of every PR, look-only report (a coder, Opus)

Written by Rowan Waymark, 2026-10-29. RECORD -1804. Cut from **pre-main d100a4776** (dev is at 82b55f06a). Verify both against the remote before relying on them.

**Authorisation.** The owner, verbatim:
- "We need to start doing pre-dev tools alongside pre-main so we can test it before the merge"
- "A tools build of every PR (Recommended)"
- "Test phones and my S26 (Recommended)"
- "Yes, report first (Recommended)"

**Look-only.** Change no workflow and no app code. Every claim needs a file:line or a commit, or is marked unverified.

## Report
1. **Where the dev tools live today.** Name the restore commit or commits on dev (-930, -1086, -1087), what they add (walk logger, diagnostics panel, others), and which files and source sets they touch.
2. **How CI can build "the PR plus the tools".**
   - Options: apply the restore commit at build time, a build flavour or source set, or something else.
   - For each option: the conflict risk as pre-main moves, and what keeps it in step.
   - CLAUDE.md applies: new capability is a new path. Release builds must never carry the tools; say how that is guaranteed and checked.
3. **Telling the builds apart on the phone.** The version name and the app's name or icon. Whether both can be installed at once (an application id suffix) or one replaces the other, and what that means for each phone's data. The database-version check before any S26 install is the owner's rule.
4. **Cost.** CI minutes per PR, and whether the tools build runs on every push or only on request.
5. **First use.** What a tools build of PR #440 would take today.

## Output
docs/audits/2026-10-29-pr-tools-build-report.md on a branch pr-tools-build-report cut from pre-main, pushed. Your final message gives the findings, plus the owner's questions in plain behaviour.

## Rules
- CI only, and no Gradle on the laptop.
- Network: GitHub only.
- The Restricted Object: never read, name or quote it.
- Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>`, with no model name.
- Ambiguity: stop and report.
