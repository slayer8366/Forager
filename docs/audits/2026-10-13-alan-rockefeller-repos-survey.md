# Alan Rockefeller's repositories: what Forager, forager-forecast and a future ID app could use

Date: 2026-10-13. A read-only survey by a planner-dispatched agent. Shallow clones were made in /tmp and deleted; nothing was changed, pushed or built. Bases: Forager 49ed18d6 (branch inat-share-spec), forager-forecast local origin/main 36cc647 (not fetched, so possibly behind). Permission: RECORD -965 (the owner: "I have his permission to use anything"). Anything used is credited alongside the other credits (owner, 2026-10-13).

Claims are marked read, inferred or could not determine, as in the agent's report. Line numbers are approximate where marked "around".

## Ranked shortlist

1. **Observation-Workbench (MIT), `api/client.py`.** For the ID app (read):
   - `create_observation_v2` (around line 1120) creates an observation with an app-chosen ID, so a resend never makes a duplicate;
   - `create_observation_photo_v2` (around line 1211) uploads and attaches a photo in one call, so no orphan photo is left;
   - `RateLimiter` (around lines 185-250) paces calls at one a second, with backoff;
   - `reconciliation/observation_creation.py:80-87` records that a typed species name can silently create an observation with no taxon.
2. **Dikarya-phylogenetic-tree-generator (MIT), `app/services/inaturalist_oauth_service.py`:** the only real iNaturalist OAuth sign-in in the repositories (read). It goes authorize, then code for token, then token for an API token. A phone app would need the public-client form, without a client secret; whether iNaturalist offers that form could not be determined.
3. **mycomap-atlas (GPL-3.0, R), `R/hosts.R` and its README** (read): fungal distribution models from DNA-confirmed records. A host-tree layer built from BIGMAP and Canada's national forest inventory, where the forecast chose TreeMap 2023 and SCANFI (D84). Also: a target-group background set, a log-effort term, block cross-validation with the Boyce index, and layer tests. For the forecast to compare against. Ideas only: the code is R, and its data is behind mycomap.org.
4. **images.dikarya.us (MIT), `sync/its.py`, `sync/taxonomy.py`, `sync/mushroom_observer.py`** (read): photos imported only when they carry an ITS barcode of at least 50 bases, filtered by licence (`licenses.py`), with taxonomy and Mushroom Observer name mapping. About 600k photos, per the README. A training or test source for the ID app; each photo keeps its own licence, mostly CC BY-NC per the README.
5. **inat.visualizer.py (MIT), `plot_observations` (around line 4899) and `calendar_aligned_week_numbers` (around line 967)** (read): a whole-year, week-by-week occurrence chart for one organism near a point. Forager's Seasonal tab shows one month at a time (`AvailabilitySearchUi.kt:1144`, `GetSightingsUseCase.kt:16`). Raw counts, with no effort adjustment. Ideas only, rebuilt in Kotlin. Its week-boundary rule differs from the forecast's D106 and is worth comparing.

## Also noted

- **inat.label.py (MIT):** herbarium labels as text, word-processor files or PDFs, with QR codes. Its coordinate-precision rule (`get_coordinates`, around lines 1254-1343) is worth reusing if Forager ever prints specimen labels; Forager has no label output today. Its edible/toxic signage (`normalize_edibility`) conflicts with Forager's no-identification rule and is not to be adopted.
- **Lost-Phone-Finder (GPL-3.0, Kotlin):** finds a lost phone in the woods by Bluetooth, and keeps scanning with the screen off (`docs/SCREEN_OFF.md`, `service/SearchWakeLock.kt`). A possible Forager field-safety idea. It's the one repository whose code could be pasted in directly.
- **inat.nearbyobservations.py (MIT):** rolls a species up to its genus for a "related finds nearby" link. Minor; Forager already queries by place and taxon (`INaturalistApi.kt:31-55`).
- **faststack (MIT):** photo culling and light editing; marginal. Its white-balance and levels routines might help ID photo intake.
- **Dikarya, `voucher_sync_service.py`:** reads a voucher label with a QR detector, falling back to OCR. An idea for the ID app's photo intake.
- **Not relevant** (from descriptions): the sequence tools, the faster tree-software builds, the stock monitor, the security tool, motoinat.py, inat.photodownloader.py.

## Licence notes (no legal conclusions)

- MIT code keeps its copyright notice wherever copied.
- Four repositories are GPL-3.0: Lost-Phone-Finder, mycomap-atlas, motoinat and the photo downloader. The owner reports Alan's permission to use anything (RECORD -965). Re-implementing ideas, as opposed to copying code, avoids the question either way.
- Photos linked by images.dikarya.us carry their own photographers' licences, which Alan's permission doesn't change.
