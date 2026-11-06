# Review 2026-11-01: PR #457, the Worker folder stands alone, and the worker/* release guard

Sorrel Tally, the independent reviewer (Fable), for Aspen's dispatch "sorrel-worker-self-contained". Started about
13:30 PST, finished about 13:55 PST on 2026-11-01. Quick by design. Each claim is marked **read**, **computed** or
**inferred**. Read-only; the only runs were the Worker's own suite and the guard script on scratch commits in `/tmp`.

## Baseline (read)

PR #457, `worker-self-contained-tests` at `a43a41a05` → `pre-main` `b535bc025` (the overflow merge, #455), two
commits (`d98d3980e` tests first, `a43a41a05`), `pre-main` an ancestor, PR `CLEAN`, all six CI checks green. The
delta: `worker.test.mjs` (−15/+6), four new test files, `tools/check-release.sh`, the workflow, `package.json`'s test
line, three lines of `ci.yml`, a README section.

## 1. Nothing the old tests asserted is lost (read, and computed by running them)

The old `worker.test.mjs` reached outside the folder twice: the `import` of `map-style/overrides/forager-reference.mjs`
for the archive names, and the v2-frozen test's sums of `map-style/forager-street-v2.json` and its preview.

- The names now come from `test/reference-names.mjs`, a two-constant copy (`"reference"`, `"reference-trails"`), and
  `cross-folder.test.mjs` asserts the copy equals what `map-style/overrides/forager-reference.mjs` exports (pre-main's
  lines 23 and 26 say the same two values; read). Every test that used the names still uses them, through the copy.
- The v2 test splits: the Worker's bundled copy stays in `worker.test.mjs` (`7547507b…`); the app's and the preview's
  copies move to `cross-folder.test.mjs` with the same two sums. On `pre-main`, where CI's Worker job runs in the
  whole repository, both run.
- On the branch, in the full repository: the bundle builds and **67 tests pass, 0 skipped** (the old suite was 60;
  the seven new are the two cross-folder checks, the self-contained scan and the four guard cases). No assertion from
  `pre-main`'s suite is absent; two have moved file.

## 2. The copy and its cross-check (read)

The copy is two exported constants with a comment naming the one place they are written. The cross-check imports the
written file dynamically and `deepEqual`s both values; it skips, printing the reason, when any of the three `map-style`
files it needs is absent. One shape to know: the skip is all-or-nothing, so on today's `main`, which has the v2 files
but not `overrides/forager-reference.mjs`, the v2 sums check skips too although it could have run. Harmless: v2's sums
on `main` are the frozen ones (computed), and the check runs on every `pre-main` PR.

`self-contained.test.mjs` scans every `.mjs`/`.js`/`.ts` under the folder except `node_modules`, `dist` and `fixtures`
for quoted paths beginning with `../` that resolve outside the folder, allowing only `cross-folder.test.mjs`. It passes
on the branch. It is a lint on literal strings: a path assembled from variables would pass it. Acceptable for a guard
against the mistake that actually happened.

## 3. The guard (read; computed on scratch commits)

`check-release.sh BASE HEAD PRE_MAIN` fails if any file changed between `merge-base(BASE, HEAD)` and `HEAD` lies outside
`server/pmtiles-worker`, or if `git diff --name-only PRE_MAIN HEAD -- server/pmtiles-worker` is non-empty (that second
diff is tree against tree, so an added, missing, renamed or edited file all count). The workflow runs it on
`pull_request` to `main` when the head branch starts with `worker/`, with `fetch-depth: 0` and an explicit fetch of
`main` and `pre-main`; `actions/checkout` is pinned by SHA.

Run against real refs, from a scratch worktree of `main`:

| Case | Result |
|---|---|
| G1: `main` + `git checkout <fix> -- server/pmtiles-worker`, nothing else (the README's recipe) | ok, exit 0 |
| G2 (my revert): the same plus one line in the repository's `README.md` | fails, names `README.md`, exit 1 |
| G3 (my revert): the folder one byte off | fails, names `server/pmtiles-worker/README.md`, exit 1 |
| G4: the same release judged against today's `pre-main` | fails, naming the seven files #457 adds or changes: the guard is in the PR and not yet on `pre-main`, so #457 must merge before the first `worker/*` release is opened |
| G5: `main` moved ahead by an unrelated commit after the release branched | ok: the merge-base keeps the release's own diff folder-only |

The four cases in `check-release.test.mjs` build small repositories and run the script from its entry point (pass;
outside file; folder differs; folder missing a file); they pass on the branch and in both shapes below.

**A gap, not in the dispatch's scope but worth saying:** the guard runs only for `worker/*` heads. A PR to `main` from
any other branch that touched the Worker folder would deploy that folder unguarded. If `main` ever takes an app release
by merging `pre-main` whole, the folder arrives equal to `pre-main`'s and that is fine; the hole is a PR that changes
the folder to something else. The equality half of the script, run on every PR to `main` whose diff touches the folder,
would close it. Should-fix, small.

## 4. A folder-only `main` run (computed)

Two shapes, both with the bundle built first:

- the folder copied alone into an empty repository, no `map-style` at all: **67 tests, 65 pass, 2 skipped, 0 fail**,
  and the skip reason printed once at the top ("map-style lacks overrides/forager-reference.mjs, forager-street-v2.json,
  forager-street-v2.preview.json: a folder-only Worker release on main; checked on pre-main") and on each skipped line;
- the real release shape, today's `main` with the PR's folder checked out over it (what CI sees on a `worker/*` PR):
  **67, 65 pass, 2 skipped, 0 fail**, the reason naming the one missing file.

## Verdict

### Blocking

None.

### Should-fix

1. Run the folder-equality check on every PR to `main` that touches `server/pmtiles-worker`, not only `worker/*`
   heads, so no other route can change the deployed folder to something that is not `pre-main`'s. A second job in the
   same workflow with `paths: [server/pmtiles-worker/**]` and the same script would do.

### Notes

- Merge #457 before the first `worker/*` release PR is opened; until then the guard would fail the release on its own
  files (G4).
- The cross-folder skip is all-or-nothing on the three files; on today's `main` the v2 sums could have run. Fine as is.
- The self-contained scan sees literal `../` strings only.

**Merge verdict for #457: mergeable.** Nothing the old suite asserted is lost, the copy is cross-checked where the
source is present and skipped with its reason where it is not, the guard passes a proper release and fails the two
ways a release can be wrong (my reverts) plus the moved-main case, and the suite passes on `pre-main`, on a folder-only
checkout and on `main` with the PR's folder. The merge is the owner's go.
