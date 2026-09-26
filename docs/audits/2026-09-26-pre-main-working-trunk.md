# 2026-09-26: `pre-main` becomes Forager's working trunk, `main` its release trunk

This note records the owner's adoption of Claude-kit's pre-main branch model
in Forager, what the model says, and what Forager does not yet match. It was
written under intent `2026-09-26-32` (dispatch
`prompts/preserved/2026-09-26-23.md`).

## 1. The owner's rulings

These are from the planner session on 2026-09-26, in order, as the dispatch
quotes them:

1. "Can't have me hitting buttons. Those hooks need to change". The owner then
   chose "build and device" for `approval_exempt_types`, and later reported
   "Done" (`2d34a44`) and "No approval message". See
   `2026-09-26-approval-exempt-build-device.md`.
2. Asked who merges the camera-strip PRs, the owner said: "merge into pre main
   without device check. defer device check for later".
3. "check the kit for pre main instructions". The planner found the model
   (section 2) and gave the owner a step 1: create `pre-main` at `76905d4` and
   set `protected_branches`. The owner replied "step 1 done", which is commit
   `29e4e09`.

The owner made both repository changes personally:

- **The branch.** `pre-main` was created on the remote at `76905d4`
  (`76905d4993813caaead1caad5db6fd18da5c97e8`, the PR #119 merge, which was
  also `main`'s tip).
- **The config.** Commit `29e4e09`, "kit.json: pre-main is the working trunk,
  main the release trunk (owner, 2026-09-26)", sets
  `.claude/kit.json`'s `"protected_branches": ["pre-main", "main"]`.

**The planner's calls**, recorded as the planner's and not the owner's:

- Merging PR #120 into `pre-main` is authorised under ruling 2, because it
  carries record and config only, no app code.
- The main checkout is excluded from updates until the owner decides.
- The owner's "The kit has a way of dealing with this" is read as
  `approval_exempt_types`.

## 2. The kit's model

Cited to `Claude-kit@5ba1581:README.md:130-214`, the sections "Branches" and
"Merging and undoing a merge".

- **Order matters.** `protected_branches` lists the branches history_guard
  protects, in order.
- **The working trunk is the first.** Every build branches from it, its pull
  request targets it, and the build's coder merges it there.
- **Release trunks are the later ones.** They receive promotion pull requests
  from the working trunk. A coder merges a promotion only under a merge
  dispatch, which the planner writes after the owner has read the evidence:
  the builds' intents and terminals, their failing-first and revert-check
  lines, their CI runs, and any deferrals.
- **Leaving work out of a promotion** is a revert on the working trunk.
  `git revert -m 1` of the feature's merge commit goes on a branch, with
  `RECORD.md` and `prompts/preserved/` restored from HEAD and a `revert`
  entry, and it is merged by its own pull request with its own backup.
- **Every merge needs a backup first.** Before any merge the coder fetches and
  writes a backup of the target branch: a bundle, `merge.json`,
  `MANIFEST.sha256`, and an `INDEX.md` line. history_guard denies the merge
  without it.
- **The finish line ends before the merge.** A build's finish line ends with
  the PR open, CI green on its final commit, the backup written and the
  terminal pushed. The next sweep's `merge` entry records the merge.
- **The main checkout is updated after each merge** with `update_worktree.py`.
  In the kit's own repository, "the main checkout sits on pre-main" (README
  line 141).

**The device check is deferred to promotion.** Under ruling 2, builds merge
into `pre-main` without a device check. The device check for the camera strip
is deferred until promotion to `main`.

## 3. The first merge under the model

PR #120, the planner worktree's record and config branch, is retargeted from
`main` to `pre-main` and merged there by its coder under intent `2026-09-26-32`.
Its backup folder is `~/Zynergy/forager-repo-backups/2026-09-26-01`. The name
follows `update_worktree.py`'s `<UTC date>-NN` convention, and **the planner
confirmed it**, noting that Claude-kit's own record names its backups the same
way (its merge entry for PR #38 cites "backup 2026-09-26-06").

## 4. What Forager does not yet match

These are recorded here, and no action was taken on any of them.

- **The main checkout is not on `pre-main`.**
  `/home/zynergy-labs/Zynergy/Forager` is on `claude/new-session-vto65i`,
  2 commits ahead of its remote and 2 behind. It has an uncommitted
  `.gitignore` change, and no `.claude/kit.json`, `.claude/hooks/` or
  `.claude/settings.json`. The kit's model has the main checkout on the
  working trunk, and the dispatch hook saves dispatches there. It was not
  updated, by the planner's call.
- **`pre-main` is not protected on GitHub.**
  `gh api repos/slayer8366/Forager/branches/pre-main/protection` returns
  HTTP 404, `{"message":"Branch not protected", ...}`, and
  `gh api repos/slayer8366/Forager/branches/pre-main` gives
  `"protected": false`. history_guard is the only guard on it. The kit's
  README describes its own two trunks as protected on GitHub (pull request
  required, enforced for admins, the two CI checks). Earlier, for `main`, the
  owner ruled on 2026-09-26: "No protection" (decision B, intent
  `2026-09-26-21`). No ruling covers `pre-main` yet.
- **CI does not run on pushes to `pre-main`.** `.github/workflows/ci.yml`'s
  `push` trigger is `branches: [main]`. Its `pull_request` trigger has no
  branch filter, so a PR into `pre-main` gets CI, but a merge into `pre-main`
  gets only that PR's `pull_request` run.
- **Unclaimed store copies in another worktree.** `prompts/preserved/`
  `2026-09-26-20.md`, `2026-09-26-21.md` and `2026-09-26-22.md` are held
  untracked and unclaimed in
  `/home/zynergy-labs/Zynergy/Forager/.claude/worktrees/bridge-cse_01UaJLvLqRfppskJ6Kb4kFVc`,
  the owner's journal-research planner session. All three are pulses. They
  took `-20` to `-22` from the shared dispatch counter. By the planner's
  ruling they stay out of this record.

## 5. A premise that turned out to match

The dispatch said the kit README describing pre-main was newer than the
vendored `v0.2` tag. It is not. In `~/Zynergy/Claude-kit`:

- `git merge-base --is-ancestor 5ba1581 v0.2` succeeds. `5ba1581` is an
  ancestor of the `v0.2` tag's commit `b1bb0bd` (PR #40), and
  `git diff 5ba1581 v0.2 -- README.md` is empty, so `v0.2` already describes
  the pre-main model.
- The tag was created at 2026-09-26T03:17:56Z. Forager's install commit
  `6d686ad`, which vendored v0.2, is dated 03:55:25Z.
- The kit README is not among the vendored files.
- All 26 hashes in `.claude/kit.lock` match the files at `v0.2`.

On this point Forager matches the kit. The wrong premise was the planner's,
and it is recorded in intent `2026-09-26-32`'s terminal.
