# Completion report: `allowBackup` off for the beta, and the Play App Signing comment reopened

> **REDACTED 2026-09-09 — forbidden-term policy.** Occurrences of this project's retired reverse-DNS
> package root were replaced in this file with `com.zynergylabs.forager.app`. **This document is
> therefore not an original record**: where it names a package, the name it originally recorded has
> been altered. The change is textual only — no finding, figure, date or conclusion was edited, and
> nothing else in the file was touched.

**Date:** 2026-09-09. **Dispatch:** B4+ from the planner. **Base:** `699efa3` (the PR #79 docs
consolidation merge, `main`'s head at dispatch time). **Branch:** `claude/backup-and-signing-notes`,
built in an isolated worktree at `/home/claude/wt-b4` — the shared checkout at `/home/claude/forager`
already produced one cross-session commit collision today (recorded in
`2026-09-09-recording-notification-stop-action-completion-report.md`) and this session did not work
in it.

**Not pushed.** The git proxy returns 403 for this repository from this container and the route to
the owner's machine is down, so delivery is a git bundle
(`backup-and-signing-notes.bundle`, `699efa3..claude/backup-and-signing-notes`), not a push.
CLAUDE.md's push-before-you-tidy rule is unsatisfiable here; nothing was reset, rebased or amended
to compensate.

## 1. `android:allowBackup="false"` — a beta-scoped decision

**The finding (B4), re-verified in this branch's own files.** `app/src/main/AndroidManifest.xml`
declared `android:allowBackup="true"` on `<application>` with **no** `android:dataExtractionRules`
and **no** `android:fullBackupContent`; `app/src/main/res/xml/` contains exactly one file,
`file_paths.xml` (the FileProvider paths), so no backup rule set exists anywhere in the project.
With that configuration Android Auto Backup and device-to-device transfer take the whole app data
directory by default — the live Room database and the photo files included. `app/schemas/` holds
`4.json` through `15.json`, i.e. twelve migrations of accumulated history in the database being
copied.

The hazard is not the copy but the restore: a SQLite database copied while it may be mid-write can
be restored torn onto a fresh install, and the data that would corrupt is the field journal the
closed beta exists to collect.

**Decision (planner's, implemented here): set `android:allowBackup="false"` for the beta.** Recorded
in the manifest immediately above `<application>`, marked BETA-SCOPED and **MUST be revisited before
production**, with the alternative and the accepted cost written there rather than only here.

**Alternative rejected:** keep `allowBackup="true"` and write `android:dataExtractionRules`
(API 31+) together with `android:fullBackupContent` (API 30 and below) excluding the database and
photo directories. This is the better long-term answer — it keeps flat settings backed up while
leaving the fragile data out — and it is what
`docs/plans/journal-trips-and-offline-regions.md` ("Leak paths") already proposed. It was rejected
for the beta because it is two rule files that have to be right across two backup APIs and two
transports (cloud backup, D2D transfer), and their correctness can only be established by real
restore behaviour that nothing in this repo's Robolectric suite can exercise. That is more code, and
more unverifiable code, than a 14-day closed test needs; testers are not relying on restore during
it.

**Cost accepted, and it contradicts a recorded intent.** That same plan document says of excluding
both transports: *"Excluding both would silently lose years of field notes on a phone upgrade."*
`allowBackup="false"` does exactly that — it turns off device-to-device transfer as well as cloud
backup. A tester who changes device or reinstalls loses their journal. This is taken on knowingly
and only for the beta; it is flagged here because a future session reading the plan document alone
would find the two in conflict. GPX export covers tracks and waypoints, not the journal, so it is
not a substitute path out.

**How it was verified.** Beyond reading the source manifest, the **merged** manifest produced by the
build was read — see "Verification" below for exactly which file and what it said. Restore behaviour
itself was **not** verified: it needs a device or emulator with a backup transport, which this
container does not have. What is verified is the declaration, not its effect.

## 2. The Play App Signing comment — premise withdrawn, decision reopened

`app/build.gradle.kts`, in the `SigningIdentity` doc comment. Comment only: **no code changed**, and
the four-variable resolution, the guard, and every other paragraph are untouched.

**What the comment said.** The 2026-09-08 owner ruling recorded there instructed: never accept
Google's generated app signing key at Play App Signing enrolment; upload this keystore's key through
the PEPK tool instead. Its *entire* stated reasoning was that a Google-generated key makes the Play
release a different signing identity from the **sideloaded** beta, so every tester's sideloaded
install could only be replaced by an uninstall that destroys their journal.

**What changed.** Owner ruling, 2026-09-09: the beta is **Play closed testing only, no sideloading**.
There are no sideloaded installs whose update path needs preserving. The argument is not refuted —
its premise was withdrawn.

**What was deliberately not done: the conclusion was not silently flipped.** Removing the premise
leaves the question open, not answered the other way. The comment now states the remaining
trade-off and marks the enrolment choice an **OPEN OWNER DECISION**:

- *Google-generated app signing key:* Google holds it, so the owner cannot lose it; this keystore
  becomes the upload key, and a lost or compromised upload key can be reset by Play support. Cost:
  the identity is held by Google and is not portable off Play.
- *Own key via PEPK:* the identity is portable — the same key can sign builds distributed outside
  Play. Cost: losing that keystore is permanent and unrecoverable, and no future update to the
  listing can be signed.

Framed as *unloseable but not portable* versus *portable but unrecoverable if lost*. Nothing in the
beta forces either.

**The keystore backup obligation is unchanged and is stated as such** — this keystore is the upload
key under both options, so losing it blocks submissions either way; the only difference is whether
that block is resettable by Play or terminal. The comment says so explicitly, because "the
sideloading ruling means the keystore matters less" is the wrong inference to leave available.

The superseded reasoning is not deleted from the record: the comment names it as superseded and
still points at `2026-09-08-beta-signing-identity-completion-report.md` for the owner's cited
documentation. That earlier report was **not** edited — audits in this directory are point-in-time
records and are superseded, not rewritten (this README's own rule).

## 3. The Windows-only test failures

Written up separately: `2026-09-09-windows-only-test-failures.md`. Summary — ten failures (nine Room
migration, one FileProvider) on the owner's Windows machine, none reproducing on Linux; Windows path
handling under Robolectric is the inference and is recorded as **inferred, not proven**, because the
failure messages were never in this session's hands; the decision is that CI/Linux is the authority
and this is not a beta blocker.

## 4. `docs/audits/README.md` — four rows added in one commit

Per CLAUDE.md, that index is a serialization point, so the planner held three parallel dispatches off
it and this session, running last, added all four rows in a single commit: dispatch B0's beta consent
text report, dispatch B1a's recording-notification Stop action report, and this dispatch's two files.
Every pre-existing row was kept.

The two other dispatches' rows were taken from the row each report proposed at its own end, read out
of their branches with `git show` (`claude/beta-consent-text` at `d2d685e`,
`claude/recording-stop-action` at `e25fe42`); **neither branch was merged into this one**. B0's
proposed row was already in the index's three-column shape and was used as written. B1a's was
proposed in a five-column shape that this table does not use — its own note said the planner should
reconcile it against the current header rather than take it verbatim — so its content was reshaped
into `| Date | Scope | File |` and nothing was dropped in doing so.

## Verification

**What was run.** `./gradlew --no-daemon testDebugUnitTest` in this worktree, on Linux, with
`ANDROID_HOME`/`ANDROID_SDK_ROOT` at `/opt/android-sdk`. Numbers below are counted from
`app/build/test-results/testDebugUnitTest/TEST-*.xml`, not from Gradle's console summary.

Three full-suite runs were made, and the first one is reported honestly rather than dropped.

| # | Manifest state | suites | tests | failures | errors | skipped |
|---|---|---|---|---|---|---|
| 1 | `allowBackup="false"` (this change) | 166 | 1277 | **1** | 0 | 24 |
| 2 | `allowBackup="true"` (reverted to base) | 166 | 1277 | 0 | 0 | 24 |
| 3 | `allowBackup="false"` (change restored) | 166 | 1277 | 0 | 0 | 24 |

Run 1's single failure was
`com.zynergylabs.forager.app.ui.log.JournalTabTest > From Album on the edit form opens the picker and pulls the
selected photo into the entry`, `java.lang.AssertionError: Assert failed: The component with
ContentDescription = 'Log photo' (ignoreCase: false) is not displayed!` at `JournalTabTest.kt:374`.
It was **not** taken on trust as "a known flake": the class was re-run alone (14 tests, 0 failures),
then the whole suite was re-run in both states. It fails in neither state on a repeat run, and the
manifest attribute is not read by any test, so it is recorded as a flake in a Compose display
assertion — the same single unrelated flake the planner saw on `main`. It is **not** fixed,
diagnosed or silenced by this dispatch, and no test was touched.

The revert in run 2 was made by stashing only `AndroidManifest.xml`; the forward version was
restored from a copy saved outside the repository before the stash, not from git (CLAUDE.md: a
revert runner restores from its own saved copy, because `git checkout --` restores the *committed*
version and would have discarded this uncommitted change). `git diff --stat` after the restore
showed all three modified files still present. Both runs' logs were checked for `e:` compile errors
before their results were read — zero in each — so neither result is a stale artifact of a build
that did not compile.

The 24 skips are the CI allowlist's exact identity set: the `SKIPPED_TESTS_ALLOWLIST` in
`.github/workflows/ci.yml` was parsed and compared as `(classname, name)` pairs against the run's
own XML — 24 in the allowlist, 24 skipped, no unallowed skip and no stale entry.

**The merged manifest.** Verified, in the build output, in both directions. After run 3,
`app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml:124`
reads `android:allowBackup="false"`, and the same value appears in the other four merged/packaged
manifest outputs (`merged_manifests/debug/processDebugManifest`,
`packaged_manifests/debug/processDebugManifestForPackage`, and both `debugUnitTest` equivalents).
Two further checks on that merged file: no library injected a conflicting value or an
`android:dataExtractionRules=` / `android:fullBackupContent=` attribute (grep for both attribute
names returns nothing), and during run 2 the same file read `android:allowBackup="true"` at line 96
— so the read is actually tracking the source edit rather than reporting a constant.

**A check that passes identically before and after is suspect (CLAUDE.md).** That is exactly the
situation here and it is stated rather than glossed: the JVM unit-test suite exercises no backup
transport and reads no `allowBackup` value, so it could not have detected this change and its
passing is **not** evidence the change is correct. It is evidence only that the manifest still
merges and nothing regressed. The evidence for the change itself is the merged-manifest read above.
The `build.gradle.kts` edit is a doc comment; the suite running at all shows the file still
configures.

**What could not be verified.**

- Actual backup/restore behaviour with `allowBackup="false"` — needs a device or emulator with a
  backup transport (`bmgr`). Not available here.
- That the nine + one Windows failures are caused by path handling — inference only; see the
  separate findings note.
- Nothing was pushed, so nothing was verified against the remote's current state: `origin/main` was
  taken as `699efa3` from the local clone's refs.
