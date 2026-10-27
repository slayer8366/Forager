# Dispatch 2026-10-27-10: one manual Worker deploy from pre-main, the buildings style (RECORD -1737, -1741)

You are the Forager deploy helper (Opus), working for Rowan Waymark, the app planner. You run one production deploy and check it. You write no code and open no PR. The previous deploy, dispatch 2026-10-26-23 (RECORD -1706), is your model. Read it first (`git show origin/records-after-173:prompts/preserved/2026-10-26-23-worker-deploy.md`) and follow its steps, checks, rules and report, with the changes below. Where the two differ, this file wins.

Note: the first commit of this file (fb5d1d0ee or near) was a bad machine-edited draft that still named a4264e26a and the old file list. This text replaces it.

## The owner's word
- -1737: "Yes, after the merge (Recommended)": one manual wrangler deploy of pre-main's Worker after #426 merges. This is an exception to -1609's main-only deploys, as in -1703.
- -1741: "Go (Recommended)" on this deploy, in the planner's window.

## Base (verify first)
pre-main is at `f42fd5ef8` (the #426 merge). The live Worker is version `5bc6bc3a-d5c4-4e2c-9527-1e8f4561ffc6`, deployed from `a4264e26a` (RECORD -1706). That is the rollback target, but read the live version again before deploying.

Run `git diff --stat a4264e26a origin/pre-main -- server/pmtiles-worker/`. It must list only these three files; if it lists anything else, or pre-main has moved in that directory, stop and report:
- README.md
- src/street-style-v3.json
- test/worker.test.mjs

The only served change is the v3 style's `buildings` paint and its new `buildings_outline` layer (Sorrel: docs/reviews/2026-10-27-sorrel-pr426.md on branch sorrel-pr426).

## Changes to the -23 steps
- **Worktree:** a clean worktree of `f42fd5ef8`.
- **Before and after:** the same routes as -23's report, plus `/style/street-v3.json`. Every route except v3 must match before and after, as -23 says.
- **v3 after the deploy:** it must parse deep-equal to `src/street-style-v3.json` at `f42fd5ef8`. Its `buildings_outline` layer must be present, with buildings fill-opacity `["step",["zoom"],0.5,14,1]`.
- **The manifest:** `/reference-trails-manifest.json` must still return 200 with sha256 `6cb68e27…e70db9`.
- **Rollback:** roll back if any route other than v3 changes or fails, or if v3 doesn't match its file.
- **Deploy message:** "pre-main f42fd5ef8 (#426 buildings), RECORD -1741, deploy helper on owner's go".
- **Node:** use the Node at ~/.local/opt/node-v24.22.1-linux-x64/bin if it's there. That's the version CI uses.

## Rules
As -23:
- Network: Cloudflare (wrangler), the npm registry for `npm ci` only, and the Worker's own address. Nothing else beyond GitHub.
- One deploy only.
- Never put the owner's email or any personal detail in a request.
- Never read, name or quote the Restricted Object.
- If wrangler isn't logged in, or a permission check refuses, stop and report.
- Commit nothing. Remove the worktree when done.
