# Aspen → Rowan: hold all merges to main; Aspen does the merging for now

Answers: none. The owner asked for this to be passed on.

Aspen Meridian, 2026-11-01, about 14:10 PST.

**The owner, in Aspen's window, verbatim:** "I'll leave the merging to you for the time being. Let Rowan
know also".

**Why main is frozen right now (observed and read):**
- **Cloudflare Workers Builds redeploys the tile Worker** from `main`'s `server/pmtiles-worker` on
  **every push to main**, whatever the push touches.
- **`main`'s Worker folder (`177215135`) is older than the live Worker.** It lacks street-v3, the
  official-trails band and its manifest, which are live, and which came from hand deploys out of
  `pre-main`.
- **So any merge to main right now**, even app-only, would redeploy the old Worker and drop Trail Map's
  style and the official trails on every phone.

**What's in progress (the owner's go, recorded in Forager-cartography
`docs/design/2026-10-31-global-coverage.md`):**
1. **PR #457 into `pre-main`:** the Worker's tests made self-contained, plus a `worker/*` release guard.
   It's in Sorrel's review, and Aspen merges it.
2. **PR #456 into `main`:** a Worker-folder-only release (`worker/overflow-street-only`), refreshed from
   `pre-main` after #457. Aspen merges it, and Cloudflare deploys `pre-main`'s Worker, plus the overflow
   fix and the sprite v6 route.
3. **After that:** Worker releases are `worker/*` PRs to main confined to that folder (the README
   explains). A full `pre-main` -> `main` promotion is safe again once #456 is in.

**For you:**
- Please don't merge anything to `main`, and ask your coders not to, until Aspen confirms #456 is live
  and checked. I'll send a follow-up file.
- Merges to `pre-main` are unaffected, apart from what the owner has left to Aspen.
