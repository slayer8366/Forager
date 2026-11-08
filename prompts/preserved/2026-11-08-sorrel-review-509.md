# Sorrel Tally review: PR #509, CI release-packaging memory (RECORD -2191)

Reviewer: Sorrel Tally (Fable 5.1). Date 2026-11-08. Coder: Hazel (Opus).
Reviewed: origin/ci-release-memory fb072a21c (2 commits) against origin/pre-main 81cc37bda.
Ruling: RECORD -2191 on records-after-173 (RECORD.md:36713): "A Hazel raises the packaging step's memory in CI, small change, own PR; nothing in the app changes."

## Verdict: pass with items

No defects back to Hazel. One call for the owner, three notes.

## Defects back to Hazel
None.

## The owner's calls
1. The Play release workflow still packages on 2 GB. The change raises the heap only in ci.yml's check job "Release compiles and packages (unsigned)". The job that builds the real Play bundle, `.github/workflows/release.yml:144` (`./gradlew ... bundleRelease`), runs the same `packageReleaseBundle` task on the same runner type (`release.yml:49`, ubuntu-24.04) with `gradle.properties:1`'s `-Xmx2048m`. The ruling's option text says "in CI", which Hazel followed exactly, and the record's heading says "before the next release", which this change does not protect. Whether release.yml should get the same line is the owner's decision, not a defect: the dispatch scope was met.

## Notes
2. The 4 GB figure is a doubling, not a measurement. Hazel says so plainly (audit "Could not determine": no peak-heap figure). I agree there is no data to size it from; the failure is a one-off (neighbouring runs 42492588638 and 42506464973 passed, confirmed via gh). A green run on this PR shows the setting does not break the job and reached the daemon; it does not show 4 GB is enough. Hazel's audit and the PR body say this correctly.
3. "The Kotlin daemon inherits the Gradle daemon's heap" is inferred by Hazel and by me. I did not find it in either log. The arithmetic (4 + 4 GB on a 16 GB runner) holds if it is true; if the Kotlin daemon kept 2 GB the margin is wider. Either way no starvation risk I can see. The 16 GB runner figure is GitHub's published spec, not read from any log (Hazel marks it inferred; same here).
4. Not an item, a reading: the PR run's job started exactly one daemon (`Starting a Gradle Daemon`, one line), so there is no second 2 GB daemon alive beside the 4 GB one.

## What I checked
- Diff: 2 files, `.github/workflows/ci.yml` (+8/-1) and `docs/audits/2026-11-08-ci-release-memory.md` (new). gradle.properties, build.gradle.kts files, settings.gradle.kts unchanged (`git diff --quiet` between the two heads on those paths). No app source, test, skip list, filter or allowlist touched. Confirmed.
- The failed log, read myself (`gh run view 42504332419 --log-failed`, job 128803601528, branch next-turn, sha 1cc818e14): two failures, `:app:packageReleaseBundle` and `:app:packageRelease`, both `java.lang.OutOfMemoryError: Java heap space`, both traces through `org.gradle.workers.internal.NoIsolationWorkerFactory$1$1.create`. Innermost frames: bundletool `ZipBuilder.preloadEntryData` -> `ByteStreams.toByteArray` for the bundle; zipflinger `Compressor.deflate` -> `ByteArrayOutputStream.<init>` inside `ApkFlinger.writeFile` for the APK. Both tasks are listed FAILED consecutively at 17:24:03, after compileReleaseKotlin, mergeDexRelease and lintVitalRelease had completed. No minify/R8 task appears in that log (grep). Hazel's description matches the log. Confirmed.
- What ran out: a no-isolation worker runs in the daemon JVM, so the daemon heap is the right knob. This is Gradle's documented worker-API behaviour, inferred not read from the run; the traces are consistent with it (the work action frames sit directly under Gradle's in-process worker factory, no process boundary).
- Did the setting take effect: PR run 42509592023 (sha fb072a21c, all 8 jobs success), job 128819559036, log line 702: `Gradle daemon heap: -Xmx4096m`, after `BUILD SUCCESSFUL in 3m 13s`, 59 tasks executed, both packaging tasks present. Confirmed from the log.
- Flags kept: `gradle.properties:1` is exactly `org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8`. The replacement `-Xmx4096m -Dfile.encoding=UTF-8` keeps every other flag because there is no other flag. Nothing dropped. Confirmed.
- Nothing else sets heap: across `.github/` (ci.yml, release.yml, worker-folder-guard.yml, worker-release.yml) and `scripts/`, the only `jvmargs`/`GRADLE_OPTS`/`-Xmx` mention is the new line at ci.yml:710. `kotlin.daemon.jvmargs` is not set anywhere in the tree (only a doc mentions it, `docs/navigation/2026-10-07-gpx-import-report.md:23`). Confirmed.
- The diagnostic line (ci.yml:713): `echo "Gradle daemon heap: $(jps -lv | grep GradleDaemon | grep -o -- '-Xmx[0-9]*[mMgG]' || echo 'not found')"`. Under `set -euo pipefail` it cannot fail the step: the `||` catches a failed pipeline inside the substitution, and `echo`'s own status is what `set -e` sees. If `jps` were absent it prints "not found" (with a stderr "command not found" line, not a failure). Leakage: `jps -lv` prints all JVMs' arguments but only the `-Xmx` token is extracted; it runs outside the `tee` so nothing enters the uploaded log artifact. On a public repo the printed value is a heap size. Confirmed by reading; behaviour confirmed by the PR log line.
- Report honesty: Hazel's audit separates confirmed (the two tasks, OOM messages, NoIsolationWorkerFactory, gradle.properties value, runner label, repo visibility) from inferred (no-isolation worker = daemon heap, 16 GB runner, 4 GB enough) and lists what could not be determined (why this run and not its neighbours, peak heap). Each confirmed claim I re-read matched. "GRADLE_OPTS sizes the client JVM, not the daemon" is correct Gradle behaviour and the right reason not to use it. The audit says the README index row was left for the planner; that is consistent with CLAUDE.md's serialization-point rule.
- Base: branch is cut from 81cc37bda, which is origin/pre-main's head today. Confirmed.

## What I could not determine
- Whether 4 GB is enough. No peak-heap figure exists in either log, and the OOM has not recurred.
- Why run 42504332419 ran out when the runs before and after it, on similar code, did not. The log has no GC output. Two packaging work actions in one daemon at the same moment is plausible and unshown, as Hazel says.
- The Kotlin daemon's actual heap in the PR run (not logged), and the runner's actual RAM (not logged).
- I did not run Gradle (rule) and did not fetch Gradle's or GitHub's docs; the two general claims above (no-isolation workers run in-daemon; standard public runner has 16 GB) are from knowledge, consistent with the logs, and marked inferred.
