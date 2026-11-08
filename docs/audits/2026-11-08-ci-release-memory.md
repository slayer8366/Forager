# CI release-packaging memory (RECORD -2191), 2026-11-08

Coder: Hazel Cairnwright. Branch `ci-release-memory` from `origin/pre-main` at `81cc37bda`. PR #509 into pre-main.

## What failed

Run 42504332419, job "Release compiles and packages (unsigned)" (job 128803601528). It compiled the release
variant, then `:app:packageReleaseBundle` and `:app:packageRelease` both failed at 17:24:02–03 UTC:

- `packageReleaseBundle`: `A failure occurred while executing ...PackageBundleTask$BundleToolWorkAction` >
  `Java heap space`, caused by `java.lang.OutOfMemoryError: Java heap space`.
- `packageRelease`: `...PackageAndroidArtifact...` work action, caused by `java.lang.OutOfMemoryError` and
  `java.lang.OutOfMemoryError: Java heap space`.

Both stack traces go through `org.gradle.workers.internal.NoIsolationWorkerFactory`. A no-isolation worker runs
inside the Gradle daemon JVM, so the heap that ran out was the **Gradle daemon's**, not a separate worker,
R8 or bundletool process. (R8 does not run in this job: no minify task appears in the log.)

## Settings before and after

| | Before | After |
|---|---|---|
| Gradle daemon heap, release-compile job | `-Xmx2048m` (`gradle.properties:1`, `org.gradle.jvmargs`) | `-Xmx4096m`, passed as `-Dorg.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8` on the job's `./gradlew` line |
| Every other job | `-Xmx2048m` | unchanged |

The change is in `.github/workflows/ci.yml`, release-compile job, step "Compile and package the release variant
without signing" (the `./gradlew` call and the comment above it, about lines 701–712). Since a command-line
`org.gradle.jvmargs` replaces the whole value, `-Dfile.encoding=UTF-8` from `gradle.properties` is repeated.
After the build, the step prints `Gradle daemon heap: -Xmx...`, read from the live daemon through `jps -lv`. That
way the log shows whether the setting reached the daemon, which a green result alone can't show.

Why only this job: the failure is in packaging tasks, and only this job runs them. The debug build, lint and test
jobs didn't fail this way, and raising their heap would change jobs that work. Why 4 GB: it doubles the old heap
and leaves room on the runner. `ubuntu-24.04` on a public repo is the standard 4-vCPU, 16 GB runner, and the
Kotlin daemon, which takes its heap from the Gradle daemon's arguments when `kotlin.daemon.jvmargs` is unset (it is
unset in this repo), could also grow to 4 GB. 4 + 4 GB plus overhead still fits.

## Evidence

- The CI run on this PR: run ID and result are given in the PR conversation and the hand-back. It's the result for
  the SHA that carries the workflow change.
- A single past OOM can't be made to happen again on demand. The same code passed in the run before (42492588638)
  and the run after (42506464973) with the old 2 GB heap. So a green run here fits the fix but doesn't prove it.
  The only thing a green run shows by itself is that the new setting doesn't break the job, plus the
  daemon-heap line confirming the setting got through.

## Confirmed vs inferred

- Confirmed from the failed job's log: the two failing tasks, the OOM messages, and the no-isolation worker
  factory in both traces. Confirmed from the tree: `gradle.properties:1` sets `-Xmx2048m`, no workflow file
  sets `GRADLE_OPTS`, `-Xmx` or `org.gradle.jvmargs`, the job runs on `ubuntu-24.04`, and the repo is public.
- Inferred: that a no-isolation worker's heap is the daemon's heap. That's how Gradle's worker API works, but I
  didn't read it from this run. Also inferred: that the runner has 16 GB (GitHub's published spec for standard
  public-repo Linux runners). The log doesn't print it. And that 4 GB is enough. No peak-heap figure exists for
  this job.

## Could not determine

- Why this run ran out when the runs on either side didn't. The log has no GC or heap figures. The two packaging
  tasks running at the same moment in one daemon is a plausible cause, but it isn't shown.
- The job's actual peak heap, before or after. Nothing in the log records it.

## Premises that were wrong

- None found. The dispatch's description of the failure matches the log. `GRADLE_OPTS`, given as one example
  in the dispatch, would have been the wrong knob here: it sizes the Gradle client JVM, not the daemon. So it was
  not used.

## Decided beyond scope

- Added the line that prints the daemon's `-Xmx` after the build. It's informational only and can't fail the
  step, and its only purpose is showing that the setting reached the daemon.
- No row added to `docs/audits/README.md`, which isn't named in the dispatch and is a serialization point. Left
  for the planner.
