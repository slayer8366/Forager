---
name: modular-code
description: How to write and change Forager code so it stays in small, decoupled files that several coders can work on at once, without growing new monster files. Use this before planning any change to app code, whenever a file you are about to edit is over about 800 lines, when adding a new screen, feature, ViewModel or composable, and when a dispatch runs beside another coder's. Use it especially when the quickest edit is "add a few more lines to the big file", because that is how every monster file in this repo was made.
---

# Modular code

New code goes in small files with one job each. Big files are split before anything new goes into them, not after.

The owner asked for this on 2026-10-20 (RECORD -1490). Phase 2 of the UI split was the reason. It took dozens of reviewed PRs to break up files that had grown one convenient edit at a time: AvailabilityViewModel at 1,888 lines, SightingsMap at 1,466 and AvailabilityScreen at 2,039. In the owner's words: "As the project grows, one coder will get overwhelmed with making several UI changes, so we need to be granular and sensible about how we code from now on."

## The limits

These are soft limits. The owner chose "Soft limits, split before adding". Use judgement, but say in the plan how the change keeps to them.

- **A new file aims to stay under about 400 lines.** If a new file will be bigger, it is probably doing two jobs.
- **Split a file over about 800 lines before adding a feature to it.** The split is its own small PR, a pure move, ahead of the feature. A fix of a few lines to a big file doesn't need a split first; a new capability does.
- **Line counts include comments.** Comments that record why a decision was made stay. Size alone is never a reason to delete a reason.

CI warns on a PR that grows a file past these limits. The warning is not a red build: Sorrel and the planner read it at review time.

## What "one job" means here

Each pattern below is one the repository already uses. Read the named file before writing something similar.

- **Domain logic stays out of the UI** (CLAUDE.md, Architecture). Matching, list rules and state decisions are plain Kotlin in `domain/`, testable without a screen.
- **A ViewModel delegates to collaborators.** A ViewModel holds its state and decides when work starts and stops. The work itself lives in collaborator classes: the search runs, the live location, the pending deletes, the gallery. Examples are `ui/availability/AvailabilitySearchRuns.kt`, `AvailabilityLiveLocation.kt` and `ui/log/MushroomLogPendingDeletes.kt`. Every decision to start or stop a job stays in a ViewModel function (RECORD -1450). A collaborator never decides on its own.
- **A composable takes a holder or a bundle, not a long list of parameters.** A screen that takes 142 parameters is a monster file waiting to happen. AvailabilityScreen went from 142 to 18 by grouping parameters into bundles (Phase 2.1). Group the parameters that change together.
- **State holders wrap existing State; they never copy it.** Every value a holder carries has a getter and, if it is written, a setter that reads and writes the original `MutableState`. Never store a copied value (Stage G, `docs/audits/2026-09-26-split-stage-g-completion-report.md`). Examples are `ui/map/SightingsMapState.kt`, `ui/availability/CompactMapLayoutState.kt` and `AvailabilityScreenState.kt`.
- **A map effect group goes in its own file.** The map's lifecycle, listeners, style reload, data refresh and fan each have a file (`ui/map/Map*.kt`). A new map behaviour gets a new file in that family, not more lines in `SightingsMap.kt`.

## What stays together

Some code has to stay where it is, because its position is its behaviour. Splitting it is a behaviour change, so it goes to the owner as a stop-and-ask, not as a tidy-up.

- **Composition order:** paint order and touch order. Children of a `Box` are drawn and hit-tested in the order they are written.
- **Back handler order.** The last enabled handler registered wins, so their order is the Back order. The owner's Back rule is that "Back undoes one step at a time along the exact path the user came by."
- **Effect launch order,** and the keys of `remember` and `LaunchedEffect`.
- **Map memories remembered above a tab** (`MapCameraMemory`, `MapReturnMemory`). Moving them below the tab loses the camera on every tab change.

When a move needs one of these to come along, it moves as one unconditional call at the same position, in source order (RECORD -1484). It also gets a phone check, because the test suite can't see real insets, real touch routing or a real map.

## Working in tandem

The owner chose "File ownership per dispatch".

- **Every dispatch names the files its coder may change.** A coder who needs a file outside that list stops and reports, rather than editing it.
- **Two coders running at once never share a file.** If two tasks need the same file, the planner sequences them. This is what let Phase 2.5 and 2.6 run side by side without a single conflict.
- **Serialization points are named in the dispatch.** RECORD.md, `docs/audits/README.md` and the CI configuration are written by one session at a time (CLAUDE.md, "docs/audits/README.md is a serialization point").
- **Small files make this possible.** A feature split across five small files can go to two coders. The same feature in one big file can go to only one.

## Before you write the plan

Answer these in the verify-first message:

1. Which files will this change touch, and how big is each now? (`wc -l`)
2. Does any touched file go over about 800 lines with this change? If so, what is the split, and does it go first as its own PR?
3. Which new files will this create, and what is each one's single job?
4. Does any part of this need to stay where it is for order reasons (see the list above)?

## When this skill does not apply

- **Generated files and migrations.** `data/local/Migrations.kt` is a list of past upgrades and is long by nature. Add new migrations at the end; don't split them up.
- **Test files.** They follow their own pattern. A long test class with many small cases is fine, though a new test class per feature is still better than appending to one giant class.
- **Moving code just to move it.** A big file that no current work touches can stay as it is until a feature needs to go into it. Then it is split first, as the owner's chosen rule says: "a file over ~800 must be split (as its own small PR) before new features go into it".
