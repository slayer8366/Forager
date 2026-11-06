# Review: PR #461 `worker/street-v4-style` (Worker release of `/style/street-v4.json` to `main`)

Sorrel Tally, 2026-11-01 (late evening, PST). Dispatch from Aspen: confirm the release. Read-only: no merge, deploy, R2 write
or GitHub comment. Each claim is marked **read**, **computed** or **inferred**.

## 1. Baseline (confirmed first)

- PR #461 head 328a1388f, one commit, base `main` at 33d2caa22, which is an ancestor of the head. **computed**
- The commit's stated source, pre-main 13bcd0ebf, is the merge of PR #460 and is `origin/pre-main` as of this review. **computed**
- Main's Worker folder before this PR equals pre-main's at a43a41a05 (the folder-stands-alone commit of dispatch
  2026-11-01-coder-11), so this release carries the folder's changes since then: #458's guard script and its test (inside the
  folder) and #460's v4. **computed**
- Head CI (run 41389043987): "Tile Worker serves what its tests say" green; the three other finished jobs green; the APK job
  in_progress/ at the time of writing (it does not read the Worker folder). **read**

## 2. Prediction (before running)

1. The folder tree at the head equals pre-main's exactly (same tree hash).
2. The diff against main is confined to `server/pmtiles-worker`; its `index.ts` delta is byte-identical to #460's.
3. The guard passes on (main, head, 13bcd0ebf) and on (main, head, origin/pre-main); it fails against pre-main before #460.
4. The Worker suite on the release head passes with the cross-folder file skipped (main's `map-style` lacks the files it reads).

## 3. The dispatch's three checks

**Folder tree equals pre-main's.** Yes: `git rev-parse <ref>^{tree}:server/pmtiles-worker` is `bda932fcbed51a7f145f2e2155cb21a8f56d66dc`
at the head, at 13bcd0ebf, at `origin/pre-main` and at #460's head 83cca8f58; `git diff --stat 13bcd0ebf 328a1388f -- server/pmtiles-worker`
is empty. **computed**

**Diff confined to the folder.** Seven files, all under `server/pmtiles-worker`: `README.md` (+4), `src/index.ts` (+10),
`src/street-style-v4.json` (new, 17,009 lines), `test/check-release.test.mjs` (+19), `test/cross-folder.test.mjs` (+7/−1),
`test/worker.test.mjs` (+19), `tools/check-release.sh` (+15/−5). `git diff --name-only` filtered to paths outside the folder
is empty. The `index.ts` delta against main is line-for-line the delta #460 made against pre-main 5679eabac. The bundled v4 is
sha256 `6ce7271ed1d0da0b…`, equal to pre-main's `map-style/forager-street-v4.json`. **computed**

**Live effect only `/style/street-v4.json`.** The only runtime files that change are `index.ts` (import, comment, one route
branch) and the new bundled JSON; the README, the guard script and the three test files are not deployed behaviour. The
bundle (dry run, 1,409.69 KiB, 73.23 KiB gzipped) carries one copy each of Street v1, v2, v3 and v4. Once Workers Builds
deploys main, v1 to v3 answer as before and the new route serves v4 with sprite v6 (live) and relief tiles that 404 until
`relief.pmtiles` is uploaded (MapLibre draws nothing for them). **read / computed; the 404-as-empty step inferred**

## 4. My own runs

Guard, from a worktree at the head (`tools/check-release.sh`): **computed**

| arguments | result |
|---|---|
| `origin/main 328a1388f 13bcd0ebf` | "worker release ok: only server/pmtiles-worker changes, and it equals 13bcd0ebf's", exit 0 |
| `--equality-only origin/main 328a1388f origin/pre-main` | "worker folder ok", exit 0 |
| `origin/main 328a1388f 5679eabac` (pre-main before #460, a negative) | lists the five v4 files as differing, exit 1 |

Worker suite at the head, bundle first: 71 tests, 68 pass, 0 fail, 3 skipped ("cross-folder checks skipped: map-style lacks
overrides/forager-reference.mjs, forager-street-v4.json: a folder-only Worker release on main; checked on pre-main"). **computed**

## 5. Scoring the prediction

All four held.

## 6. Verdict

**Blocking:** none.

**Should fix (not in this PR, and not a reason to hold it):** the two guard workflows, `.github/workflows/worker-release.yml`
and `worker-folder-guard.yml`, exist on `pre-main` only. A `pull_request` workflow runs from the PR's merge ref, which here
is main plus the folder, so neither guard ran on #461: the head's check runs are the five CI jobs and nothing else, and no
"Worker release guard" or "Worker folder guard" run exists for the branch. The guard script itself is in the folder and so
reaches main with this release, but the workflows that call it cannot ride a worker/* release (rule 1 forbids files outside
the folder). They need a small PR to main of their own, or the next pre-main promotion. Until then the guard is a hand
check, which is what I did here. **read / computed**

**Notes:**
- #456, the previous folder-only release, was likewise unguarded in CI for the same reason; its folder matched pre-main at
  a43a41a05, which I confirmed above by tree hash.
- This release also brings #458's guard script and test to main, as the folder rule requires; both are tested in the suite run
  above (`check-release.test.mjs` ran, not skipped).

**Merge?** Yes: the folder equals pre-main 13bcd0ebf's, the diff is confined to it, and the live effect is the new
`/style/street-v4.json` route only. On the owner's go; CI's APK job was still in_progress/ when I wrote this.
