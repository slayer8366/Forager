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

## Addendum: the Play release workflow (Sorrel's review item 1, owner: "Yes, add it (Recommended)")

Sorrel's review (`prompts/preserved/2026-11-08-sorrel-review-509.md` on records-after-173) pointed out that
`.github/workflows/release.yml` builds the real Play bundle with `bundleRelease`. That runs the same
`packageReleaseBundle` task, on the same `ubuntu-24.04` runner (`release.yml:49`), and still used the 2 GB heap.
On the owner's word it gets the same change, in the step "Build the signed release bundle" only (about lines
144–150):

- the same `"-Dorg.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8"` on its `./gradlew` call;
- the same daemon-heap readout line after the build.

The readout prints only the `-Xmx` token. Signing values reach Gradle through environment variables, not JVM
arguments, and in any case nothing but that one token is extracted. Nothing else in release.yml changed.

How it was checked, and what wasn't: release.yml can't run from a PR (main only, `release` environment, signing
secrets), so **this change has not run in a real release**. What was checked:
- the file still parses as YAML (Python `yaml.safe_load`);
- the step's `run:` script, pulled out of the parsed YAML, passes `bash -n`;
- the job sets up the same JDK 21 (`setup-java`, line 86, so `jps` is on PATH) and the same `setup-gradle`
  (line 92) as ci.yml's job, and nothing in release.yml or `gradle.properties` turns the daemon off;
- the two added lines are the same form as ci.yml's, which ran green with `Gradle daemon heap: -Xmx4096m` in run
  42509592023.

`actionlint` isn't installed on this laptop and wasn't run. The first real release run's log should show
`Gradle daemon heap: -Xmx4096m`. If it shows anything else, the setting didn't reach the daemon.

## Evidence

- ci.yml on this PR: run 42509592023 on `fb072a21c`, all 8 jobs success. The release-compile job's log shows
  `packageRelease` and `packageReleaseBundle` ran, `BUILD SUCCESSFUL in 3m 13s`, and `Gradle daemon heap: -Xmx4096m`.
  Later SHAs on this branch change only release.yml and this note. Their ci.yml run IDs are in the hand-back.
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
