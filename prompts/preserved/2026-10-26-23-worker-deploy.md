# Dispatch 2026-10-26-23: one manual Worker deploy from pre-main (RECORD -1703, -1705)

You are the Forager deploy helper (Opus), working for Rowan Waymark, the app planner. You run one production deploy and check it. You write no code and open no PR.

## The owner's word
-1703: "Yes, after the small PR": one manual wrangler deploy of pre-main's Worker, an exception to -1609's main-only deploys. -1705: "Go" on this deploy, given in the planner's window.

## Base (verify first)
pre-main at `a4264e26a` (the #422 merge). Sorrel (docs/reviews/2026-10-26-sorrel-pr422.md on branch sorrel-pr422) confirmed that `server/pmtiles-worker/` differs from origin/main only by 8a (`cbdc359b7`, `6cfed1aba`) and #422.

Before deploying, re-run `git diff --stat origin/main origin/pre-main -- server/pmtiles-worker/`. If it lists anything beyond the files below, or pre-main has moved past `a4264e26a` in that directory, stop and report:
- README.md
- src/index.ts
- src/street-style-v3.json
- test/worker.test.mjs
- test/fixtures/make-reference-trails-pmtiles.py
- test/fixtures/reference-trails.pmtiles
- test/fixtures/reference-trails-manifest.json

## Steps
1. **Set up.** Make a clean worktree of `a4264e26a` under ~/.cache/forager-wt/worker-deploy. Install from the lock file only (`npm ci`) in server/pmtiles-worker. Use the Node CI uses for the Worker if it's available locally (~/from-flash/map-style/node/ has 24.21.0); report which you used.
2. **Before the deploy:**
   - Record the live deployment: `npx wrangler deployments list` (or `versions list`). Note the current version id; that is the rollback target.
   - Fetch and save the responses (status, content-type, cache-control, sha256 of the body) for these routes, all of which work today: `/style/street-v2.json`, the offline style if there is one, one ordinary basemap tile, one font range, one sprite file, one `/routing/` manifest or route-data path the app uses, and one `/reference-trails/10/192/393.mvt` tile.
   - Find the Worker's address and the exact routes from the README, the app code and wrangler.toml. Do NOT request `/style/street-v3.json` or `/reference-trails-manifest.json` before the deploy.
3. **Deploy.** Run `npx wrangler deploy --dry-run` first and confirm the bindings match wrangler.toml. Then run `npx wrangler deploy`, once. Record the new version id.
4. **After the deploy:**
   - Request every route from step 2 again and compare each with its before response. Bodies should match, except for anything that legitimately changes per request.
   - Then request `/style/street-v3.json` (status 200, JSON; its sha256 must equal src/street-style-v3.json, `99a613fe…2e28`) and `/reference-trails-manifest.json` (status 200, application/json, max-age=86400; its sha256 must equal the R2 object's in -1701, `6cb68e27…e70db9`).
   - Add a cache-busting query string where the route allows it, so a stale edge copy isn't mistaken for the new Worker.
5. **Rollback rule.** If any route that worked before the deploy now fails or changes unexpectedly, roll back at once to the recorded version (`npx wrangler rollback <id>`), confirm the before responses are back, and report. If only the two new routes fail, do not roll back: report and wait.
6. **Clean up.** Remove the worktree when done. Commit nothing to the repo.

## Rules
- **Network:** this dispatch allows Cloudflare (wrangler), the npm registry for `npm ci` only, and the Worker's own address. Nothing else beyond GitHub. Never put the owner's email or any personal detail in a request.
- **One deploy only.** No other wrangler commands that change state, apart from the rollback in step 5.
- **The Restricted Object:** never read, name or quote it.
- **Permission refusal:** if wrangler is not logged in, or a permission check refuses the deploy, stop and report. Don't work around it.

## Report
- the version ids before and after;
- the Node used;
- every route with its status before and after, and the hashes;
- whether a rollback happened;
- the four disclosure sections (confirmed vs inferred, could not determine, premises that were wrong, decided beyond scope).
