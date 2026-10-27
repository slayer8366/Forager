# Dispatch 2026-10-27-12: a staging copy of the tile Worker for test builds, report first (a coder, Opus; report only)

Written by Rowan Waymark, 2026-10-27, against pre-main effbef21b. RECORD -1745.

**Authorisation.** The owner asked what a test server should be. The planner recommended a staging copy of the Worker, deployed from pre-main and used by test builds. The owner: "Write the dispatch for that and I'll have a coder get on it". This dispatch covers the report only. Building, deploying, or changing anything on Cloudflare each need the owner's own word later.

Describe products generically. Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name.

## The recommendation to test (the planner's, not a ruling)
- **A second Worker** (for example `forager-pmtiles-staging`) runs the same code as server/pmtiles-worker at pre-main's head, at its own address, with its own rate-limiter bindings and its own routing app key.
- **It deploys itself** whenever pre-main changes, the way the live Worker deploys from main today (RECORD -1609).
- **The live Worker goes back to main only.** The manual pre-main deploys of RECORD -1703 and -1741 then stop.
- **Debug and test builds of the app use the staging address; release builds use the live one.** The owner's phones run debug builds today.
- **Same map data, read only.** Staging reads the same R2 bucket (forager-maps) read-only: basemap, reference trails, route squares and names. A separate staging bucket is a later step, and out of scope here.

## Report, with file:line or a cited source for every claim
1. **Today's setup.** Read server/pmtiles-worker/wrangler.toml: the bindings, secrets, routes and rate limiters. Read the server README, and RECORD -1609, -1703, -1706, -1741 and -1743. How do automatic builds from main work today? Read the repo for any Cloudflare build config. Say what you can't see from the repo.
2. **Cloudflare's options,** read from Cloudflare's own documentation:
   - a wrangler `[env.staging]` against a separately named Worker;
   - whether Workers Builds can deploy one Worker from pre-main and another from main from the same repo;
   - whether a binding can be read-only, or what stops staging code writing to R2 (does the Worker's code write anything?);
   - plan limits and cost for a second Worker and its requests;
   - how secrets (the routing app key) and rate limiters are set per environment.
3. **The app side.** Where does the app hold the Worker address? Grep StreetStyle.kt, the routing client, the reference-trails manifest source, fonts and sprites, the offline styles. What is the cleanest way for debug builds to use staging (a build-type field, for instance) with no new setting for the user?
   - What happens to areas downloaded under one address and then opened under the other? Styles and tiles are keyed by URL: this is the "Older map style" notice and the MapLibre cache.
   - How do release builds and the Play pipeline stay on the live address? Show it can't be got wrong.
4. **Risks.** What could reach the live Worker or the live data by mistake? What does the owner have to do by hand in the Cloudflare dashboard? Does anything change for Aspen's uploads?
5. **A setup plan.** Steps in order, each marked as repo change, Cloudflare dashboard (the owner's hands), or wrangler. Then the checks that prove it: staging serves pre-main's v3; the live server is unchanged; a debug build reaches staging; a release build reaches live.
6. **The four disclosure sections.**

## Rules
- **Report only.** Build nothing.
- **Cloudflare:** read-only. You may run read-only wrangler commands (whoami, deployments list) and read Cloudflare's documentation. No `wrangler deploy`, no secret or binding change, no new Worker, no dashboard change.
- **Network:** GitHub, Cloudflare's documentation, and the read-only wrangler commands. Nothing else. Never put the owner's email or any personal detail in a request.
- **The Restricted Object:** never read, name or quote it.
- **Disk:** a sparse worktree under /mnt/work/forager-wt/staging-server-report.
- **Output:** write docs/audits/2026-10-27-staging-server-report.md on a new branch, staging-server-report, and push it. Leave the RECORD untouched.
- **Long conversation:** past about 200k tokens, push and write a handoff.
