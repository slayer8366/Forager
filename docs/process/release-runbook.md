# Release runbook

From release 2 (1.0.5026, live on Play production 2026-11-09). The owner, after it: "Shaky release but good practice run. Add to your notes our path to success so we can go smoother", then "And also upload your notes to GitHub's Forager repo".

## The owner's standing rulings (2026-11-09, verbatim)

- **Releases are production.** "Never ask. Always assume production". Any release path that targets another track is a defect to fix, not a question.
- **Release number = GitHub run number.** "release 3 must match the number given in GitHub. That is an absolute rule." Release 3 must "contain the updated next release, whatever that is. This is a hard gate." Release N is run #N of `.github/workflows/play-production.yml` ("Release to Play production"). Run #1 was a placeholder the environment refused; run #2 was release 2 (5026).
- **The owner approves every release.** "Always require my approval". The release happens "Not when I start it, when I approve it": the planner starts the run, the run waits, and it publishes on the owner's approval, with no other step from the owner.

## The path that worked

1. **The code is on main**, by a promotion from pre-main.
2. **Walk it on the S22 first**, with CI's debug APK of the same tree (`gh run download <run> -n app-debug-apk`). It installs over the S22's debug build and keeps its data, so there is no wipe and no wait on a download from Play. Warn the owner before the phone moves. Walk early, while the owner has time, not after publishing.
3. **Write the release notes** (Play's limit is 500 characters per language) and check each line against the code that ships. The workflow sends no notes; the owner adds them in Play Console.
4. **Start the release:** `gh workflow run play-production.yml --ref main`, with no input. Never start this workflow for anything but a release: a test or dry run takes the next release's number.
5. **The owner approves the run in GitHub.** It then builds, signs, verifies the signature and version, and publishes to production with status completed, with no rollout click. Google's review took minutes for 5026.
6. **Report** the version code and name, and the production track before and after.

## Already settled

- **Play permission:** the Play service account has the production release permission. It was refused ("The caller does not have permission") until the owner granted it in Play Console under Users and permissions, at account level, not inside the app.
- **Approval:** the `release` GitHub environment keeps the owner as required reviewer and allows main only. The signing and Play keys live there and cannot be read or copied.

## Stumbles to skip

- **Track:** release 2 first went to internal testing, because the old workflow targeted it. Fixed: play-production.yml targets production.
- **Reruns:** a rerun builds the commit it was first started from, and a commit cannot change (its name is a fingerprint of its contents). Rerun a failed release run only if main has not moved since; it keeps its number. A refusal from Play changes nothing, so rerunning after one is safe.
- **Re-releasing a bundle:** Play refuses an upload with a versionCode it has seen. To release a bundle already in Play, run play-production.yml with the `version_code` input. To discard an internal draft, use `play-discard-draft.yml`. It has its own concurrency group so it can never cancel a release run waiting for approval.
- **Undo requests:** pin the target as one commit and time (for example "pre-main as at 10:00 PT, c20598cc3") and confirm it once, before any forced push. Back up to tags first. The #514 undo went through five states before landing where it started. Backup tags `backup/*-2026-11-09` hold every state it passed through.

## Open before release 3

The owner: "I need to test the release before it uploads". play-production.yml publishes on approval, so the signed build only exists once it is live. How the owner tests the signed build before it publishes (for example a build-and-hold step, or Play's internal app sharing before publishing) is to be settled with the owner before release 3.
