# Sorrel — re-check of PR #509, item 1 (release.yml heap), 2026-11-08

Branch ci-release-memory, fb072a21c..1d12ab255 (two commits: 266d4031e release.yml, 1d12ab255 audit note). Reviewer on Fable 5.1. Nothing changed, nothing pushed, no Gradle run.

## Verdict: pass (no new items)

Item 1 from the first review is closed as the owner asked: the Play release workflow's "Build the signed release bundle" step now runs `bundleRelease` with the same 4 GB daemon heap as ci.yml's check job.

## Items back to Hazel
None.

## Owner's calls
None new. The one standing caveat is the same as before: 4 GB is a doubling, not a measurement, and the release workflow cannot be exercised before a real release run (see below).

## Notes
1. The two added lines are character-for-character the same form as ci.yml:710 and ci.yml:713, which ran green with `Gradle daemon heap: -Xmx4096m` on fb072a21c (run 42509592023, first review). The only difference is release.yml keeps its existing `--no-configuration-cache`.
2. If a run ever shows two `-Xmx` tokens on that line, it means two Gradle daemons were alive (Gradle starts a second one when JVM args differ). Nothing in release.yml starts a daemon before this step (`scripts/setup-android-sdk.sh` has no gradle call), so one is expected. Cosmetic either way.

## What I checked
- Diff scope: `git diff --stat fb072a21c..origin/ci-release-memory` = 2 files, `.github/workflows/release.yml` (+7/-1) and `docs/audits/2026-11-08-ci-release-memory.md` (+29/-2). ci.yml diff between the two heads is empty (0 lines). release.yml at fb072a21c equals origin/pre-main's (diff 0 lines), so the branch's only release.yml change is this one. Confirmed.
- release.yml change is inside the "Build the signed release bundle" step only (lines 144–150 of the new file): a 3-line comment, the `-Dorg.gradle.jvmargs=...` argument with the gradlew call wrapped onto two lines, a 1-line comment, the heap echo. Step `env` (4 secrets), the log-grep checks, and every other step are untouched. Confirmed by reading the whole file.
- jvmargs keep everything: `gradle.properties:1` is `org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8`, nothing else; the replacement `-Xmx4096m -Dfile.encoding=UTF-8` drops nothing. No `systemProp.*`, no `org.gradle.daemon=false`, no GRADLE_OPTS anywhere in gradle.properties, the build scripts, `.github/` or `scripts/`. Confirmed.
- The echo can't fail the step: `set -euo pipefail` is on; the pipeline sits inside `$(...)` as an argument to `echo`, so `set -e` sees echo's own status, and `|| echo 'not found'` covers a failed pipeline (including `jps` missing, exit 127). Confirmed by reading; same construction already ran in ci.yml.
- How secrets reach the step: as environment variables only (`env:` block lines 135–139 → `providers.environmentVariable(...)` in `app/build.gradle.kts:125–126` and `:195–201`). No `-P`, no `-D`, no file with secret contents is passed to Gradle (the keystore path in `FORAGER_SIGNING_STORE_FILE` is a path, not a secret, and also an env var). `jps -lv` prints main class and JVM arguments, never environment variables. The only `systemProperty(` in the build (`app/build.gradle.kts:649`) is a test-task flag on a worker JVM (class GradleWorkerMain, filtered out by `grep GradleDaemon`) and is not a secret. After the grep narrows to `-Xmx[0-9]*[mMgG]` only a heap token can print. Confirmed.
- Log artifact: release.yml has no upload-artifact step (header, line 16: "no build artifact upload"); the echo runs outside the `tee` so it is not in `app/build/release-gradle.log` either; it goes to the step console only, as a heap size. Confirmed.
- JDK for jps: `actions/setup-java` temurin 21 (line 86) is a JDK, so `jps` is on PATH, as in ci.yml where the same line produced output. Confirmed by parity with the ci.yml run; not run here.
- YAML parses (`python3 -I` + `yaml.safe_load`, 12 steps); the step's `run:` script passes `bash -n`. Confirmed.
- ci.yml run 42512494904: workflow CI, branch ci-release-memory, headSha 1d12ab255 (= origin/ci-release-memory head), conclusion success, all 8 jobs success including "Release compiles and packages (unsigned)". Confirmed via gh.
- Audit note: its line citations (release.yml:49 runner, :86 setup-java, :92 setup-gradle, lines 144–150) all match the file; it states plainly the change has not run in a real release and says what the first real run's log should show. Its evidence section now names run 42509592023 on fb072a21c. Matches what I read.
- RECORD -2191 on records-after-173 records the owner's "Yes, add it (Recommended)" and the go (Record / Build / Merge #509 once green). The README index row for the audit is still the planner's, as the first review noted.

## What I could not determine
- Whether the change works in release.yml itself. release.yml runs only from main via workflow_dispatch behind the `release` environment and its secrets; no PR can exercise it. That the `-Dorg.gradle.jvmargs` form reaches the daemon is shown by ci.yml's run, not by this file. The first real release run's log must show `Gradle daemon heap: -Xmx4096m`; anything else means the setting did not take.
- Whether 4 GB is enough for `packageReleaseBundle` on the signed build (same open point as the first review; no peak-heap figure exists).
- Whether the signed release build's daemon differs in any way from the unsigned check build's (R8 is off per RECORD -917, so I expect the same tasks, but I did not run it).
- No `actionlint` run (not installed here either).
