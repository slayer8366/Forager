# Planner handoff, 2026-10-12 12:01 UTC (Monday 05:01 PDT)

Written by the outgoing planner session (Rowan Waymark) for the next one. Read first:
- ~/Zynergy/Forager/CLAUDE.md;
- the memory index;
- docs/process/planner-profile.md (on main);
- RECORD.md on origin/records-after-173, entries -917 to -943 (this session).

**Next free RECORD ID: -944**, and this handoff takes it. The records worktree is ~/.cache/forager-wt/records (branch records-after-173).

Standing rules confirmed this session:
- Times go to the owner in Pacific (PDT); RECORD stays UTC.
- Products are described generically.
- The owner's screenshots stay off GitHub.
- Coders run as subagents on opus.
- App builds go to CI only, never the laptop.
- Check the actual code before telling the owner what something shows.
- One new rule, the **dev branch** (memory "dev-branch-holds-dev-tools", RECORD -930, -931, -939), described in section 2.

## 1. Today

- **The OMS meeting is this evening (PDT).** The forecast site is live and briefed. The owner chose to leave the briefing card unchanged.
- **The owner wants to start with the new mobile design system plugin.** They added it during this session, and it didn't load here. The owner, asked what it's for: "The design of the strip mostly." So the first job is a design review of the rounded strip (PR #221) with that plugin. Use the renders and the owner's mockup. Bring the findings to the owner as choices; don't send them to a coder unasked.

## 2. Forager app

### main and dev

**main is a576c051** (PR #220 merged, -939). It holds none of the following, in any build:
- the walk logger;
- diagnostics;
- crash capture or crash logs;
- the Settings debug rows;
- the alert record files;
- the synthetic forecast switch.

The privacy policy, the delete-data page and the README lost their crash lines. The owner's ruling on this: "Absolutely zero trace", meaning no notes, comments or docs about these tools on main. Past reports and history stay ("Current code only").

**dev is 0ed8b3f0:** main plus one commit that restores all of the dev tools. It is the owner's development build.
- **After every merge to main,** rebase dev:
  1. back up a bundle to ~/Zynergy/forager-repo-backups/;
  2. `git rebase origin/main`;
  3. keep it to exactly one commit;
  4. `git push --force-with-lease=dev:<old sha>`.
- **CI runs on its own** when dev is pushed (ci.yml push branches: main, pre-main, dev).
- **Never merge dev into main.**
- Last CI: main 38223860061 (587 suites, 4,573 tests, 0 failures); dev 38223882627 (612 suites, 4,691 tests, 0 failures).

### The beta (-923)

The owner builds and signs the beta from main once PR #221 merges. That PR is the only thing the beta still waits on.
- **Draft release notes** (written in this session, about 380 characters):
  > What's new
  > • Navigate from a waypoint on a journal entry's map.
  > • Return to start now ends by itself when you arrive.
  > • A new journal entry page, with height gained measured by the barometer.
  > • Records tidied up: find photos shown uncropped, and Offline maps in the Tools drawer.
  > • Fixes to climb totals and to the height at the end of a walk.

  Rewrite them after #221 so they cover:
  - Navigate from journal entry maps;
  - Return ends on arrival;
  - the journal entry page with barometer height;
  - the Records tidy-up;
  - climb and end-height fixes;
  - the new rounded map strip.
- **The owed phone checks for the older code passed,** on the owner's account (-924). Only a sudden change in elevation is untested.
- **The release variant has never been built on CI.** The owner's signed build is its first build. The checks on that build:
  - Settings shows no Diagnostics or Crash logs rows;
  - recording, export, photos and Return work as before.

### PR #221, the rounded, tucked map strip (dispatch 2026-10-12-06; rulings -932 and -934 to -943)

**State:** draft, head 4b8e5c6c, on branch rounded-map-strip. **The owner stopped the coder at about 12:05 UTC** ("Tell the coder to stop"). Its last CI run, 38246598522, has completed; read its result. Nothing is running. Dispatch a new coder only on the owner's word, and probably after the plugin design review.
- Check whether the PR is marked ready and CI is green, and read its report: docs/audits/2026-10-12-rounded-map-strip-report.md, on the branch.
- **If it's unfinished,** dispatch a new coder from the branch head. Give it these instructions, which are all recorded in -940 to -943:
  - **Landscape card (-940):** the same width as in portrait, permanently. The pill takes the remaining width beside it.
  - **Evening line (-941):** stacked in short pieces in the right column, never cut off.
  - **Straight-line note (-942):** removed in route mode. The owner: "Distance by trail is the only honest framing."
  - **Pill (-943):** thinner, at least 40 dp, with the card's text closer under it.
  - **The tuck must be visible,** with a shadow cast by the pill onto the card in day and night. The owner: "I don't see it on the preview". This was sent to the coder without its own RECORD entry; -932 is the ruling it implements.
  - **A lighter rectangle** under the middle column in the renders. The coder was investigating it.
- **The arrows stay as they are (-942).** The turn needle already aims about 25 m ahead along the trail in route mode, and the middle arrow is a north needle.
- **Layout recap:**
  - three columns: distance + "ahead 5° ▲" | "Alt" + "▲ 134° SE" | grid reference + the evening stack;
  - X above ⋮ at the right edge;
  - the fit order is kind → turn degrees → "Alt" → "≤ 16 ft" → the grid reference to its own line;
  - the bottom bar has rounded top corners (Maps tab);
  - the map credit line has only its top-right corner rounded.
- **Screenshots** are in ~/Zynergy/device-evidence/2026-10-12-nav-strip-mockup/ (renders under robolectric-*), alongside the owner's mockup, which stays off GitHub.
- **Robolectric images can't be attached in this session** (no thread). Describe them in words.
- **Leftover revert-check/* branches** (r15 to r19, r3b) remain on the remote. The coder was told to delete its own once they were recorded; check.
- **Before the merge:**
  - the owner's word;
  - a backup of main;
  - a read of the diff for @Ignore and removed assertions;
  - after the merge, the dev rebase;
  - a merge entry quoting the owner's "Distance by trail is the only honest framing. And keeping it to one number is less UI clutter".
- **The owner's phone checks:** from the dispatch's device list, plus the pill's thinness, the tuck shadow, the corners of the bottom bar, the credit line over the real map, and landscape at portrait width.

### Production release (after the beta, -905, -917, -922, -925, -926)

- **D1:** one small release.
- **D2:** R8 stays off.
- **D3:** no native symbols this release (-922). The real fix is packaging the map library's prefab copies plus the NDK, alongside R8 later. Report on branch native-symbols e4154aba.
- **D4, lint:** "Everything reasonable". The survey is on branch lint-survey ce75f629, 376 issues. Dispatch the fixes after the beta. Keep scratch-lint-survey until lint-on-CI reuses its job.
- **D5, tidiness:** delete scratch branches. The ten records-tidy-revert-* branches are deleted, backed up at 2026-10-12-03.

## 3. Forecast site

zynergy-site PR #15 is merged (-933). The opened phone panel no longer crushes the readout. Production: panel_fit_check 44/44, phone_check 21/22. The one failure is the old analytics beacon (-877), which is still an unasked privacy question for the owner.

## 4. Open owner questions, none urgent

- **Mycorrhizal habitat map (-913, -914):** T7's O4 and O7, and where it fits in the order.
- **Land ownership options:** report on branch land-ownership-research.
- **The Pi relay note** (prompts/preserved/2026-10-11-04-pi-relay.md).
- **The analytics beacon.**
- **The beta tester's ideas (-916):** structured find conditions, and an offline species guide.

## 5. Incidents this session, for the record

- **A permission refusal:** creating a private GitHub repository and pushing to it was refused by the session's permission system. Nothing was created. The owner chose the dev branch instead.
- **History rewritten at the owner's word:** two records-branch commits (an entry and its removal) were removed by force-push. The owner said "Just act as if it never happened" and "Go ahead and rewrite history". The backup is at 2026-10-12-04-records-pre-rewrite. Don't mention that entry again.
- **A usage-limit cutoff:** both coders were stopped at about 07:15 UTC by a session usage limit and resumed after 07:30. Nothing was lost.
- **A planner slip:** the planner approved a coder plan ("units 1 to 5 and 7") that the coder said it hadn't sent. Superseded by -930.
