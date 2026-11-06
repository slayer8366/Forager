# Review: PR #463 `ci/worker-guards-to-main` (the two Worker guard workflows reach `main`)

Sorrel Tally, 2026-11-01 (late evening, PST). Dispatch from Aspen; my own should-fix from the #461 confirmation. Read-only.
Claims marked **read**, **computed** or **inferred**.

## 1. Baseline

- Head 51541c286, one commit, base `main` 0a819b3d6 (the #461 merge), an ancestor. **computed**
- Two files, both added: `.github/workflows/worker-folder-guard.yml` (+26) and `.github/workflows/worker-release.yml` (+26);
  `git diff --name-only` against main lists nothing else. **computed**

## 2. Prediction

1. Both files are byte-identical to pre-main's.
2. `tools/check-release.sh` on main already accepts `--equality-only` (it arrived with #461), and main's Worker folder equals pre-main's.
3. On #463 itself the release guard reports "skipping" (its `if:` wants a `worker/` head ref) and the folder guard does not
   trigger (its `paths:` filter names the folder, which this PR does not touch).

## 3. Checks

- **Identical:** `git diff --quiet origin/pre-main origin/ci/worker-guards-to-main -- .github/workflows/<file>` is clean for
  both files. **computed**
- **The script the workflows call is on main:** `origin/main:server/pmtiles-worker/tools/check-release.sh` contains
  `--equality-only` (7 mentions, the flag parse among them), and `git diff --quiet origin/main origin/pre-main -- server/pmtiles-worker`
  is clean: main's folder is pre-main's. **computed**
- **Triggers once merged:** `worker-release.yml` runs on `pull_request` to `main` with `if: startsWith(github.head_ref, 'worker/')`;
  `worker-folder-guard.yml` runs on `pull_request` to `main` with `paths: ["server/pmtiles-worker/**"]`. Both fetch `main`
  and `pre-main` and call the script. With the files on main, a `worker/*` branch cut from main carries them, so the merge ref
  has them and both run. **read / inferred**
- **On this PR:** the head's check runs show "A worker/* release changes only server/pmtiles-worker, and it equals pre-main's:
  skipping", which is the release guard declining a non-`worker/` branch, as written; the folder guard did not run, as its
  paths filter says. The pull_request CI run was still in progress when I wrote this; nothing in it reads these files. **read**

## 4. Scoring the prediction

All three held.

## 5. Verdict

**Blocking:** none. **Should fix:** none.

**Note:** after this merges, the first `worker/*` release will be the first time either guard runs in CI; worth a glance at its
check list then.

**Merge?** Yes.
