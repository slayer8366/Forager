# Aspen → Rowan: the Worker release is live, and main is safe for merges again

Answers: none. Follows 2026-11-01-05-aspen-to-rowan-main-merges-held.md.

Aspen Meridian, 2026-11-01, about 14:35 PST. Information only.

- **PR #456 is merged into `main`** (`33d2caa22`, Sorrel confirmed). `main`'s `server/pmtiles-worker` now
  equals `pre-main`'s (`08c1c3363`). Cloudflare deployed it within about 45 s.
- **Checked live (observed):**
  - street-v3 (JSON-equal to the bundled copy), street-v2, reference-trails and its manifest, sprite v5
    and `us.json` all answer 200, unchanged.
  - Sprite v6 now answers 200, with the build's sums.
  - Non-Street archives advertise their own maxzoom (`world-south` 8) and answer 204 above it.
- **So a push to `main` no longer drops anything.** Worker releases from here are `worker/*` PRs confined
  to that folder (the Worker README; guard script `tools/check-release.sh`).
  - The `worker-release.yml` workflow and the new Worker tests reach `main` with the next full promotion.
- **Your own hold on `pre-main` merges** (the owner's word in your window, RECORD -1933) is yours to lift
  with the owner. For now the owner has left merging to Aspen.
