# Review 2026-11-01: PR #458, the Worker folder guard for any PR to `main` (should-fix 1 of the #457 review)

Sorrel Tally, the independent reviewer (Fable). About 14:30 PST on 2026-11-01. Quick; read-only; the only runs were the
Worker's suite and the guard script on scratch commits. Claims marked **read** or **computed**.

**Baseline (read):** `worker-folder-guard` at `539cdd105` → `pre-main` `08c1c3363`, two commits (tests first); the
delta is the new workflow, 15 lines in `check-release.sh` (`--equality-only`), two test cases, three README lines. CI
on the head: every check green but the APK job, still running at the time of reading.

**Does it close the gap (read, computed):** yes. `worker-folder-guard.yml` runs on `pull_request` to `main` with
`paths: ["server/pmtiles-worker/**"]` and no branch condition, so any PR from any branch that touches the folder runs
`check-release.sh --equality-only origin/main <head> origin/pre-main`, which skips the "outside the folder" half and
keeps the equality half. On real refs from a scratch worktree of `main`: a PR that edits `index.ts` and an app file
fails naming `index.ts` (exit 1); a PR carrying `pre-main`'s folder plus an app file passes (exit 0); the same PR under
the full `worker/*` check fails on the app file, as it should, so the two guards divide the work correctly. The paths
filter is why this is its own workflow (a `paths` filter applies to the whole file), as the comment says.

**Tests (computed):** the suite on the branch, 69 tests, 69 pass. My revert: `--equality-only` made to skip the
equality diff too → exactly one failure, "--equality-only fails a pull request whose folder differs from pre-main's".

**Note:** a release branch cut from an older `pre-main` fails if `pre-main`'s folder has moved since; that is the
intended meaning of "equal to pre-main's" at check time.

**Merge verdict for #458: mergeable.** The gap named in the #457 review is closed. The merge is the owner's go.
