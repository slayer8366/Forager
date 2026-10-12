# Planner handoff, 2026-10-12 04:02 UTC (Sunday 21:02 PDT)

Written by the outgoing planner session (Rowan Waymark) for the next one. First read ~/Zynergy/Forager/CLAUDE.md, the memory index, docs/process/planner-profile.md, and RECORD.md on origin/records-after-173, entries -854 to -909. **Next free RECORD ID: -910.** The records worktree is ~/.cache/forager-wt/records (branch records-after-173).

Standing rules confirmed this session:
- Times go to the owner in Pacific (PDT); RECORD stays UTC.
- Products are described generically.
- The owner's phone screenshots and data stay off GitHub.
- Coders run as subagents on opus.
- App builds go to CI: `gh workflow run ci.yml --ref <branch>` (the trigger is on main since PR #216, README "Run this branch on CI"). There are no local Gradle builds while forecast jobs run.
- **Check the page's or app's actual code before telling the owner what it shows.** This session told the owner twice that something was shown when it wasn't (-898).

## 1. The deadline

**The OMS meeting is Monday 2026-10-12, in the evening (PDT)**, not the morning (-909). Everything for it is live and briefed.

- **Live:** https://zynergy-labs.com/Forager/forecast/.
- **What it shows:**
  - our own street map beneath the layers, with a see-through slider (-857 to -860, PR #10);
  - the PNW pilot weather model for the week of 5 October (PR #11, -900; the manifest gained base_rate and is now 074879ff…, cells ac6038d3…);
  - five relative ratings against the average week (2.16% = 1,138 / 52,718): Below average, About average, Above average, High, Very high, with cut-offs 0.67 / 1.5 / 3 / 5 (-902). The readout is truncated to one decimal (-903; PR #12, -907).
- **Banner:** "Pilot model. Not validated. Reviewed. Not the forecast.", then the owner's status line from -863.
- **Production check:** 70 of 71. The one failure is the hosting analytics beacon, refused by script-src, which predates all of this (-877).
- **Briefing:** forecast pnw-pilot-plans d8f1d9b, docs/briefings/2026-10-12-oms-briefing.md. It has a one-page card on top and the evidence below. It was independently read (-878) and updated through -907. **The owner still has to read it through, and look at the live page on a phone.**

## 2. In flight right now

- **Phone layout: a peeking bottom panel (-908, the owner wanted it "tonight", now with slack per -909).**
  - The site coder is a subagent of the old session, and the next session can't message it.
  - It pushed zynergy-site branch `forecast-phone-peek` at fa81394, on top of main e1c3f8a9.
  - **Next:** check whether a PR is open and its checks pass (check.py and slider_check.py at 390x844 and 1366x860, plus the new peek, drag and touch checks named in -908). If unfinished, dispatch a new site coder from -908.
  - The merge is on the owner's word.
  - Checks live in ~/Zynergy/device-evidence/2026-10-10-pilot-map/check.py and ~/Zynergy/device-evidence/2026-10-11-street-basemap/slider_check.py; use that folder's .venv. Originals are saved beside them.

## 3. Forecast state

- **All planned runs are done:**

| Run | Skill | Verdict |
| --- | --- | --- |
| Headline, 1,000 m, all years | −0.0035 | worse |
| D33 (3), 5,000 m | −0.0080 | worse; not reviewed |
| D33 (4), CC only | | not shown |
| Random-date comparison | | not shown |
| Hedge | | not shown |

- **Gate:** one hour failed the land-route gate, overridden by -854. Review pass 2 confirmed the rounding explanation (-861).
- **Owner's choice:** show the weather model, labelled (-861).
- **After the meeting:** a wider fit over the whole map area. The weather is already pulled to 111° W (-901).
- **Data:** ~/Zynergy/forecast-data-pnw-pilot. Scoring output: scoring/out/pnw-pilot/2026-10-05/.

## 4. Forager app

- **main 921aa9c7:**
  - PR #216, the CI trigger (-869, -872);
  - PR #218, Records tidy (-881);
  - PR #219, the journal entry page, Climb fix, barometer height with migration 20 to 21, the end-drop smoothing fix, and Navigate in entry-map bubbles (-894).
- **Test builds:**
  - The S22 has 1.0.3227+g035fcbf1, the smoothing fix, before Navigate.
  - **The S26 has the owner's own install of 1.0.3222+g69309a18, which is before the smoothing fix.** Install main's CI build before the S26 checks.
- **Owed phone checks (post-merge list, also T7 of the release plan):**
  - Journal: step 3 (existing entries, S26), step 7 (battery), step 8 (45-minute flat pocket walk, swapped pockets if both phones go, -888), step 9 (a known climb), and the Navigate tap.
  - The Records tidy nine-step S22 check.
  - The Return-ends-on-arrival S22 steps (PR #214).
- **Observed and unexplained:** on a shared walk the S22's barometer swung ±1.4 hPa between samples while the S26 stayed within ±0.2 (-887, -889).
- **Leftover branches:** ten records-tidy-revert-* scratch branches remain on GitHub. Deleting them is the owner's call.

## 5. Next work (scope only; no go yet)

- **Production release first** (-905). Task list: docs/plans/2026-10-11-production-release-tasks.md on records-after-173 (-906).
  - The owner decides D1 to D5 first: the cut; R8, recommended off; native symbols, recommended on; lint policy; the tidiness list.
- **Then trail navigation and maps** (-904): docs/plans/trail-navigation-and-own-tiles.md and its spec (2026-10-04), as amended:
  - -879: Cloudflare serves; the Pi builds and keeps a copy, with no live fallback.
  - -880: weekly builds; first layers street, offline maps with names and terrain shading.
- **The Pi planner** owns the map service from stage 4 on. **The relay note awaits the owner:** prompts/preserved/2026-10-11-04-pi-relay.md.
- **Land ownership:** research report on Forager branch land-ownership-research, 77252a82 (docs/audits/2026-10-11-land-ownership-research.md).
  - Recommended: B now (public land plus "private, owner not shown", OR/WA, map only), C next (route penalty), D only after walk tests, and E (picking rules) as its own decision.
  - **Plain entry warnings are not reliable on town lots** (-883). The options haven't been put to the owner as a decision yet.

## 6. Accounts and the site

- **Cloudflare:**
  - The map-tiles account (a6a899e0…, Worker forager-pmtiles) went onto the $5 plan on 2026-10-11 for the website street map (-860).
  - The website account (aac482d6…) is on the free plan. Under -481/-879 it will host the new map service and need the paid plan at launch.
- **Open owner question:** the hosting provider's visitor statistics are blocked by the site's script-src (-877). Allow them or not is a privacy choice, not yet asked as a decision.

## 7. Backups this session

All in ~/Zynergy/forager-repo-backups/, each a verified bundle with SHA256SUMS: 2026-10-11-05 (pre-#216), -06 (site pre-#10), -07 (pre-#218), -08 (pre-#219), -09 (site pre-#11), -10 (site pre-#12).

## 8. Agents of the old session (can't be messaged by the next one)

Each is idle once its report came in, except the site coder on -908 (section 2). Nothing else is running on the laptop:
- the scoring rain services ended;
- no fit is running;
- the T6b continental run stays stopped (-815).

## Addendum, 04:30 UTC: the phone panel is ready for the owner's word

The site coder finished -908.
- **PR:** zynergy-site PR #13 (https://github.com/slayer8366/zynergy-site/pull/13), head fa8139406a227746d32dc87d620b898305262e74, on main e1c3f8a9. The Cloudflare Pages check passed.
- **Preview:** https://forecast-phone-peek.zynergy-site.pages.dev/Forager/forecast/.
- **Results:**
  - check.py: 71/71 with 8 skipped;
  - slider_check.py: 34/34;
  - a new phone_check.py: 15/15, every new check shown failing on a broken copy first. Evidence is in ~/Zynergy/device-evidence/2026-10-12-phone-peek/.
- **Layout:** the breakpoint is 760 px. At 390x844 the peek takes 24% of the screen on soil and 35% on the pilot layer, and up to 45% with a readout.
- **Fixed during the build:** the opened map credits overflowed under the header on phones.
- **The coder's calls for the owner to confirm or reverse:**
  - choosing a layer returns the panel to the peek;
  - the panel is 80% opaque (CLAUDE.md map-chrome rule);
  - the handle is solid only while open.
- **Not verified:** a real phone (toolbars, safe-area insets, drag feel).
- **Next:** ask the owner for the merge word. Then back up site main, merge pinned to fa813940, run check.py against production, and write the merge entry.

## Addendum 2: PR #13 merged (RECORD -911)

The owner said "Merge when ready". zynergy-site PR #13 was merged as 6abc74f0 at 04:03:25Z, with backup 2026-10-12-01. Production: check.py 70/71 and phone_check.py 14/15; the one failure in each is the old analytics beacon. **Next free RECORD ID: -912.** Still owed: the owner's look at the live page on a real phone before the meeting.

## Addendum 3: two site issues after PR #13 (RECORD -912)

In one phone maker's built-in browser, the opened phone panel runs up over the header; other mobile browsers are fine. In desktop mode on a phone, the map is too tall, and the owner wants it capped at 1:1. A site coder was dispatched from the old session on -912; check zynergy-site for its branch or PR. The merge is on the owner's word. **Next free RECORD ID: -913.**

## Addendum 4: a mycorrhizal habitat map (RECORD -913)

The owner wants "a map focused on mycorrhizal mushrooms", meaning a **habitat** map: where host trees and soils suit mycorrhizal mushrooms as a group, not a weekly forecast, useful beyond foragers. It widens the drafted T7 (forager-forecast pnw-pilot-plans docs/dispatch/2026-10-11-t7-pnw-pilot-habitat.md) from chanterelles to the group. This is scope only. Before dispatching, ask the owner T7's O4 and O7, and where it sits against the production release and trail navigation and maps. Positioning (-914): other apps map burn morels; Forager takes the mycorrhizal angle. **Next free RECORD ID: -915.**

## Addendum 5: the -912 fixes are in PR #14 (not merged)

- **PR:** zynergy-site PR #14 (https://github.com/slayer8366/zynergy-site/pull/14), head b39970382670184521da816697b00b91bde67c0e, on main 6abc74f0. The Pages check passed.
- **Preview:** https://ff060a20.zynergy-site.pages.dev/Forager/forecast/
- **Results:** check.py 71/71, slider 34/34, phone_check 22/22 (7 new, each shown failing on a broken copy first; main itself fails 3 of them).
- **What changed:**
  - The panel is now bounded inside the map area by several independent means.
  - The desktop map is capped square (980x1700 window gives a 660x660 map; 1366x860 is unchanged).
- **Cause in the affected browser: not confirmed.** The symptom matches an unbounded panel; the owner's two suspects (no dvh, large text) did not reproduce it in Chromium 140.
- **Recommended before merging:** the owner opens the preview in that browser and confirms. Then the merge on the owner's word, with the usual backup, production check and merge entry.

## Addendum 6: PR #14 merged (RECORD -915)

The owner confirmed the preview in the affected browser ("works now"; "Desktop looks better") and said "Merge when ready". PR #14 was merged as b8ac1e2c at 04:43:44Z, with backup 2026-10-12-02. Production phone_check: 21/22, the old analytics beacon aside. **Next free RECORD ID: -917.** A beta sign-up asked for structured find conditions and an offline species guide (-916); not prioritised yet.
