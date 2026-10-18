# Planner handoff, 2026-10-18 (written 13:55Z, 06:55 PDT Sunday)

Written by the outgoing planner (Rowan Waymark) at the owner's word "Write a handoff". It replaces `2026-10-17-planner-handoff.md` as the start point.

**Next free RECORD ID: -1276** (-1274 records design decision 1; this handoff is -1275). The records worktree is `~/.cache/forager-wt/records`, on branch `records-after-173`. **Forager's IDs stay below 2000:** the forecast keeps its own record from -2001 (Alder; memory "record-id-blocks").

## Urgent first: the work drive is full

`/mnt/work`, which holds `~/Zynergy`, `~/.gradle` and `~/Android`, had **158 MB free (55 of 58 GB)** at 13:53Z. Hazel reported a write failing there.

**What's on it:**
- `forager-repo-backups` 15 GB, of which about 1.4 GB of full-repo bundles are from 2026-10-18's migration and undo;
- `device-evidence` 11 GB;
- `forecast-data` 7.4 GB;
- old forecast worktrees, 0.7 to 1.4 GB each.

The owner was asked what to clear and answered "Write a handoff" instead, so **nothing has been deleted**.
- **To ask the owner:** the 83 MB S22 APK in `device-evidence/apk-255` is re-downloadable from CI. The migration bundles are the only copy of the deleted migration branches, so they need the owner's word.
- **Forecast data and worktrees are Alder's to propose,** not ours to delete.
- **Tell Alder** about the full drive: t6b-night's venv lives on `/mnt/work`.

## Read first

- `~/Zynergy/Forager/CLAUDE.md`;
- the memory index;
- `docs/process/planner-profile.md` on pre-main;
- RECORD.md, entries -1251 to -1275.

New memory notes since the last handoff:
- "planner-coder-separation";
- "record-id-blocks";
- "helpers-by-name-not-pronoun";
- "ios-port-report-location".

## Standing changes since the 2026-10-17 handoff

- **The forecast has its own planner, Alder Bearing,** in session `session_01BeCyTz4uLqzv9n1u8R42ht` ("Forecast planner (forager-forecast)").
  - Alder's profile is on forecast main (PR #7, b0bc10a). Alder's record is RECORD.md on the forecast branch `forecast-record`, with IDs from -2001.
  - Alder owns T8, W42, t6b-night and T7's leftovers.
  - Rowan does the app only, and records no forecast work.
  - Talk to Alder by `send_message` to that session id.
- **E-GD standard,** the owner: "whoever writes the code shouldn't write the plan and vice versa". A coder may write its own intent for a dispatched task; plans and decisions are the planner's.
- **The record migration was undone and its history rewritten away,** at the owner's word. records-after-173 runs -1253 → -1254 with no migration commits, and everything forecast before -2001 stays in Forager's record. The full story is in the transcript only, by the owner's "Nothing to record".
- **The mapping service stays on the existing Cloudflare account** (the `forager-pmtiles…workers.dev` one), kept apart from zynergy-labs, which is the website. The owner: "Option 2. Zynergy-labs is the website and I intentionally created that to keep it separate from the Forager mapping service" (-1272).
- **The build side of the map** (the old Pi planner's stage 4 onward) is back with the planner, with Hazel coding (-1272).

## In flight

**1. Hazel, `topup-items`:** dispatch 2026-10-18-03, -1270.
- **What it covers:** Elder's top-up items:
  - the offline-maps screen keeps Download Maps only (no OK or Cancel), in both orientations;
  - the landscape pill is Maps' size from the first visit;
  - the pill's focus and field state survive tab switches;
  - known-bugs updated.
- **State:** verify-first accepted. Its "H2" height route was the planner's call, still to be told to the owner: on a first landscape visit while a sundown or Back-by line shows, the pill is a little short, and that goes on known-bugs. The branch is at 61c3b181. Hazel is building; await the report.

**2. Hazel, `map-launch-fixes`:** dispatch 2026-10-18-04, -1273. Hazel's verify-first is in, with **three owner questions not yet asked:**
- **(A) Glyphs.** A wrong premise of the planner's: the app draws no text on the map. The area markers went in 192db6c7. Hazel recommends dropping "glyphs" from both style builders and deleting the unused font stack: no server, no font, no licence. Alternatives: bundle the fonts, or serve them from the Worker.
- **(B) User-Agent.** Hazel will build the no-choice fix: install the HTTP client inside MapLibreInitializer's success path. For the rare remaining failure, Hazel recommends option (i): treat it as an init failure (logged, retried; the map blank as on any init failure), so no library User-Agent ever goes out. Alternatives: (ii) accept the library User-Agent, logged; (iii) block OSM and OpenTopoMap tiles with a message.
- **(C) OpenTopoMap's full credit:** about 379 dp against the S22's about 384 dp portrait width, an estimate, so it may wrap. Hazel also found that the topo-night caption probably wraps already. Options: (a) allow two lines; (b) a smaller caption; (c) the full credit in tap-to-reveal attribution and the short form in the caption; (d) merge duplicate credits.

Hazel is building the no-choice parts and waiting on the planner; the branch isn't pushed yet.

**3. The release path:**
- pre-main e61e248a has CI green, and Elder's top-up audit passed with items (-1268).
- After items 1 and 2 merge into pre-main (each on the owner's word, after an S22 check): the promotion PR pre-main → main, on the owner's approval; then the release build from main on the owner's laptop (signing keys are only there); then the store listing check (T9; the owner pastes the listing text); then the owner uploads to production. Play production access was granted (-1209).
- **Deferred by the owner:** the height checks D1 and D2.

**4. The map plan:**
- `docs/plans/2026-10-18-basemap-launch-plan.md` on branch basemap-launch-plan, da66084e, unmerged, with Elder's design review and Juniper's research filed beside it (-1271). It needs amending for -1272 (the account: the existing mapping account, not zynergy-labs) and for design decision 1.
- **Design decisions:** the owner is going through Elder's eleven one at a time. **Decision 1 is answered: "Whole US built first"** (-1274): Protomaps' US cut, so the own map covers the whole country from day one, with no fallback needed in the US. **Next is decision 2,** the default map. See plan section 4 (decisions 6 to 16) and Elder's review.
- **Open:** a custom domain on the mapping account, to replace workers.dev.

## Earlier carry-overs, unchanged

- **The camera lens-fix device run** on dev (-1149): the owner said "tomorrow", and it doesn't gate release.
- **The 66 older open intents,** waiting for E-GD's lookup and monthly record.
- **The tidiness pass:**
  - scratch branches: today's `scratch-lerc-revert-*`, `scratch-tabs-*`, `revert-tcnr-*` and more;
  - old PRs #101, #104 and #109;
  - old worktrees, which also relieve the disk.
- **The iOS report** is on branch `claude/ios-port-feasibility-mvsjcr`; the owner raises iOS after the release.
- **Vector maps beyond the US** (-1243): direction only.

## Reminders

None are bound to this session now. The W42 and t6b reminders moved to Alder.

## The owner's style today

Fast, with short answers, often "(Recommended)". The owner reads screenshots closely, corrects the planner's framing quickly (the separation is planner and coder, not repo), and wants things restated as step paths with sketches before they're built.

## Correction, appended 14:05Z by the same planner

After this handoff was written, the owner ruled (-1276): "Yes I lift the 08/22 ruling. Cost was a factor then, now it isn't. The Pi is the map builder, that decision stands as written. Pi planner is live, let's hand off the task. Nothing gets down here. Moving to Pi".

- **The map build and serving work goes to the Pi planner,** session_019RAXWotCV56kkPRiRVeukE ("raspberrypi-tender-creek"), which has been sent the plan and this handoff. It is **not** "back to the planner, Hazel coding", as section "Standing changes" above says.
- **The app side stays here:** the A0 launch fixes and A4's app switch, and the owner's design decisions 2 to 11.

The text above is left as written.
