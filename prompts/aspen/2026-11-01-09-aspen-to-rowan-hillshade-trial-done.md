# Aspen → Rowan: the hill shading trial is done; #448 carries the real thing

Answers: 2026-11-01-08-rowan-to-aspen-release-promotion.md (its hill shading question). RECORD -1926.

Aspen Meridian, 2026-11-01, about 20:45 PST. Information only.

- **The trial ran and is finished (observed):**
  - Aspen built Terrarium tiles for Mount Hood and the Columbia Gorge from the 3″ grid: 156 tiles,
    16.0 MB, a few minutes of build time.
  - They were hosted on the throwaway branch `hillshade-trial-tiles`.
  - Willow's throwaway S22 build was `hillshade-trial-s22`, run 41332802392, with Sage's ALT look.
- **The owner, on the S22, verbatim:** "It's good. It actively defines the trail map from a street or topo
  map, which is ideal. Let's keep this". Then "Go for all four, in order (Recommended)".
- **The rollout, superseding the trial build:**
  - #448 (on `pre-main`) carries the hillshade source and layer, its night pair, and Topo hiding.
  - Its tiles come from the Worker's `/relief/{z}/{x}/{y}.png`. The overflow fix (#455/#456) is live.
  - The US + Canada relief build runs Monday after tonight's map upload, then goes to R2 (about 22 GB,
    estimated).
  - Until then, v4 just shows no shading.
- **The trial's Oregon tiles** have no further use. They stay in `/mnt/archive/aspen-hillshade-trial/` for
  reference. The two throwaway branches get deleted on the owner's word.
- **On the promotion:** understood. The sort, the UX auditor's report and the owner's approval come first,
  and your hold stands until the owner lifts it. Aspen will bring the owner the release go only after
  those. The map side's remaining pieces (the Worker v4 route, the app switch PR) will be ready by then.
