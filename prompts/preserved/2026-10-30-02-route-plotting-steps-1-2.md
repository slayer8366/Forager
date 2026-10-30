# Dispatch 2026-10-30-02: route plotting steps 1 and 2, a trip that carries a route and the plotting logic (a coder, Opus)

Written by Rowan Waymark, 2026-10-30. RECORD -1826. Cut from **pre-main 21d846b9e**. Verify it is still the remote head before you start.

**Authorisation.** The owner, verbatim: "Go", given to "a go for route steps 1–2, the stored route and the plotting logic". The decisions behind it are RECORD -1817, -1818, -1821, -1822 and -1824.

**Read first:** the plan docs/plans/2026-10-29-waypoint-route-plotting-plan.md on origin/records-after-173, including both addenda. Its "What exists" section was read at 21d846b9e; re-check every line you rely on. Describe products generically. Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name.

## What the two steps are
- **Step 1, data.** A planned trip can carry a route, and its date becomes optional.
  - Migration 22→23. It rebuilds `planned_trips` with a nullable date and adds storage for a route.
  - The route keeps its own copy of its ordered stops: each waypoint's name and position, so deleting a waypoint later changes nothing.
  - Per leg: its line, its kind (trail or straight), its length, and the reason it is straight.
  - Elevation per point is needed later (step 4b, online lookup, fill in later). Every Room column must land with a reader in the same change (CLAUDE.md, Room/DataStore pitfall). So propose how heights are stored without a second migration, or say plainly that step 4b brings its own migration.
  - Add the backup TableSpec. Undated trips sort after dated ones. Rows and the bubble show no date when there is none. Existing trips keep their dates.
  - The new migration goes in its own file, because Migrations.kt is 1266 lines.
- **Step 2, plotting logic (domain, headless).** A use case takes an ordered list of waypoints and returns legs plus a total.
  - It finds route data for each leg (FindRouteDataForPoints) and routes each leg in turn (TrailRouter).
  - A leg with no usable data comes back as a straight leg with its reason, and every fallback is logged.
  - No Android UI types. Unit-test it on a fake TrailRouter.

Nothing new appears on screen in either step, apart from the trip date becoming optional in the existing row and bubble. Any new word on screen is the owner's: propose it and do not ship it.

## Stop first (look only, no code)
Write docs/audits/2026-10-30-route-plotting-first-stop.md covering:
- (a) the domain model for a plotted route;
- (b) the schema: tables, columns, indexes and how each column is read, plus the height question above;
- (c) the 22→23 migration's steps, and how its test proves existing trips survive;
- (d) the claimed schema version, checked against every remote branch (`git log --remotes -S "Migration(22, 23)"`; it was unclaimed at 05:30 UTC on 2026-10-30);
- (e) what an undated trip shows in the row and the bubble, and its sort order;
- (f) any file over 800 lines you would touch, and your split plan.
Push it, and wait for the planner's amendment.

## After the amendment
- Step 1 and step 2 are **separate PRs into pre-main**, step 1 first. No merge.
- Tests fail first, then revert checks, one per rule. The build log must have no compile errors. Restore from a saved copy, never from git, and confirm the forward change is still present. A revert check that runs on a scratch branch needs no restore.
- Test the migration with Room's migration test helper, from a real v22 database containing trips.
- **Modular rule:** new files stay under 400 lines.
- CI only, with no Gradle on the laptop. gh is logged in; run gh commands bare.
- No device runs. A schema change never goes on a phone without the planner's word (no downgrade; debug builds wipe on downgrade).

## Rules
- Branches `route-plot-data` (step 1) and `route-plot-logic` (step 2), each in a worktree at ~/.cache/forager-wt/<branch>.
- Network: GitHub only.
- The Restricted Object: never read, name or quote it.
- Past about 200k tokens: push and hand off.
- Ambiguity: stop and report.

## Amendment 1 (2026-10-30): answers to the first stop, and the go (RECORD -1827)
The first stop is docs/audits/2026-10-30-route-plotting-first-stop.md (route-plot-data, e9f4daa39). Accepted as proposed, including its corrected premises, with these answers.

**The owner's answers:**
- **A waypoint off the trail:** "Dashed link to trail (Recommended)". A leg runs stop → trail → stop. The two joins are drawn thin and dashed, and they count in the distance. Store them so the drawing can tell them apart from the trail part.
- **A route trip's pin:** "At the first stop (Recommended)". The trip's `location` is the first stop.
- **The delete label with no date:** "\"Delete trip <name>\" (Recommended)". Dated trips keep their present label.
- **The same waypoint tapped twice in a row:** "Ignore the second tap (Recommended)". The domain refuses consecutive identical stops (step 3 ignores the tap). A→B→A is allowed.

**The planner's rulings on mechanics:**
- **Q1:** the route tables land in step 1. The repository and backup/restore read every column, which satisfies the reader rule.
- **Q3, heights:** H3. Step 4b brings its own migration, 23→24.
- **Q5:** both a whole-route method and a single-leg method.
- **Q7:** undated trips sort by name, then id.
- **Q8:** `PlotRouteUseCase` stays unwired in AppContainer until step 3.
- **Leg kind:** derived from which nullable field is set, as proposed. Add a test that a row with both set, or neither, is rejected on read and logged.
- **JournalBackupTest split:** a move-only commit first, green by itself, as proposed.
- Re-check schema version 23 against every remote ref before opening the step 1 PR.

**The go: build step 1, then step 2.** These are separate PRs into pre-main, with no merge. CI only. No phone.

## Amendment 2 (2026-10-30): Sorrel's review of #445 and #446 (RECORD -1837)
Sorrel's review (Fable) is docs/reviews/2026-10-30-sorrel-pr445-pr446.md (sorrel-pr445-pr446, 9eb44c501). It has no S findings. The owner: "Tighten 2 tests, then merge (Recommended)".
- **N1, on #445:** the older-backup tests at JournalBackupTest.kt:732 and :780 also assert that the route tables are empty after restoring a pre-23 backup, following the path-table precedent beside them. Do a revert check that seeds a route row on restore.
- **N2, on #446:** PlotRouteUseCaseTest.kt:212-216 collects the reasons actually asserted above it into a set and compares that with StraightReason.entries. Do a revert check that deletes one assertStraight line.
- Merge pre-main into each branch if it has moved, then get both green on CI. The planner merges #445, then #446.
- **N8**, a test pinning the stored reason names, goes with step 3, as do N9 and N10. N3 to N7 are record only.
