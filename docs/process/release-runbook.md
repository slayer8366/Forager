# Release runbook

From release 2 (1.0.5026, live on Play production 2026-11-09). The owner, after it: "Shaky release but good practice run. Add to your notes our path to success so we can go smoother", then "And also upload your notes to GitHub's Forager repo".

## The owner's standing rulings (2026-11-09, verbatim)

- **Releases are production.** "Never ask. Always assume production". Any release path that targets another track is a defect to fix, not a question.
- **Release number = GitHub run number.** "release 3 must match the number given in GitHub. That is an absolute rule." Release 3 must "contain the updated next release, whatever that is. This is a hard gate." Release N is run #N of `.github/workflows/play-production.yml` ("Release to Play production"). Run #1 was a placeholder the environment refused; run #2 was release 2 (5026).
- **The owner approves every release.** "Always require my approval". The release happens "Not when I start it, when I approve it": the planner starts the run, the run waits, and it publishes on the owner's approval, with no other step from the owner.

## The path that worked

1. **Final gate, before the promotion:** run `gh workflow run play-premain-signed.yml --ref pre-main` ("Signed build from pre-main (final gate)"). The owner approves it. It builds and signs pre-main and uploads it to Play's internal track as a draft that goes to no one. The owner tests that exact signed file from Play Console, under App bundle explorer, then the version code, then Downloads, then Signed, universal APK, installing it over the Play version on a phone with records. The owner, 2026-11-09: "Add an option to sign and release on pre main before the promotion as the final gate". This workflow is a separate file, so it never takes a release number.
2. **The code is on main**, by a promotion from pre-main. If main's tree equals the tested pre-main, play-production.yml can publish the tested bundle itself with its `version_code` input; otherwise it builds main.
3. **Walk it on the S22 first**, with CI's debug APK of the same tree (`gh run download <run> -n app-debug-apk`). It installs over the S22's debug build and keeps its data, so there is no wipe and no wait on a download from Play. Warn the owner before the phone moves. Walk early, while the owner has time, not after publishing.
4. **Write the release notes** (Play's limit is 500 characters per language) and check each line against the code that ships. The workflow sends no notes; the owner adds them in Play Console.
5. **Start the release:** `gh workflow run play-production.yml --ref main`, with no input. Never start this workflow for anything but a release: a test or dry run takes the next release's number.
6. **The owner approves the run in GitHub.** It then builds, signs, verifies the signature and version, and publishes to production with status completed, with no rollout click. Google's review took minutes for 5026.
7. **Report** the version code and name, and the production track before and after.

## Already settled

- **Play permission:** the Play service account has the production release permission. It was refused ("The caller does not have permission") until the owner granted it in Play Console under Users and permissions, at account level, not inside the app.
- **Approval:** the `release` GitHub environment keeps the owner as required reviewer and allows main only. The signing and Play keys live there and cannot be read or copied.

## Stumbles to skip

- **Track:** release 2 first went to internal testing, because the old workflow targeted it. Fixed: play-production.yml targets production.
- **Reruns:** a rerun builds the commit it was first started from, and a commit cannot change (its name is a fingerprint of its contents). Rerun a failed release run only if main has not moved since; it keeps its number. A refusal from Play changes nothing, so rerunning after one is safe.
- **Re-releasing a bundle:** Play refuses an upload with a versionCode it has seen. To release a bundle already in Play, run play-production.yml with the `version_code` input. To discard an internal draft, use `play-discard-draft.yml`. It has its own concurrency group so it can never cancel a release run waiting for approval.

## Open before release 3

The owner: "I need to test the release before it uploads". Step 1's final gate answers it. One setting is still needed: the `release` environment allows main only, so the final-gate run is refused until pre-main is added to it (GitHub, under Settings, Environments, release, Deployment branches and tags, Add deployment branch or tag rule, Ref type Branch, name `pre-main`). The required reviewer stays. play-production.yml still refuses anything but main in its own first step.
