# The cartography team: four new roles named and profiled (dispatch pi-25)

Written by the Pi planner, Aspen Meridian, on 2026-10-21. Committed by Willow Quadrat. Records are
read at `pi-build` `e75fb690`, `main` `381515c6` and `records-after-173` `2605b9d6`, and E-GD at
`6fa9d68`.

## What the owner asked, verbatim, in the planner's window

1. "Based on your processes for map building, what roles would you say you need for your team? You
   already have a designer."
2. "Make profiles for all those roles and have them propose names"
3. "Sorrel and Juniper are the Forager team. Alder and Ash are the forecast team"
4. "You are the cartography team"
5. "Coder name?"
6. "Sage is on it, yes. Draft it. Double check the EGD standards"
7. Answering the planner's four open items: "1 go", "2 I'll take your recommendation", "3 stop and
   report if you're unsure, if there's a gap, or if you need more information. Evidence is the hard
   gate", "4 that's your job to write records".

## The roles, and why

The planner read `map-build/README.md` and the running build. The jobs that come after code
existed had no owner:

- **Watching a long run.** pi-19's finish was seen 50 minutes late
  (`2026-10-20-cloudflare-upload-prep.md:666-670`). Two check-ins on the running build today were
  made only because the owner asked.
- **Judging whether the content is right,** not just whether its shape passes the build's checks.
- **Checking inputs independently** of the coder who fetched them.

The coder role had no name and no profile on `pi-build`. The planner searched
`origin/pi-build` for a coder profile and for a Pi coder name, and found neither.

Three smaller jobs were folded into these roles rather than given profiles: capacity sizing stays
with the coder, licence credits go to QA, and a publisher role waits until something is served.

## The names: three proposals each, the owner's pick in bold

Each role proposed three names from a session given its own draft profile (E-GD PROTOCOL 22). The
owner picked through the question tool.

| Role | Proposals |
|---|---|
| run watcher | **Primrose Lookout**, Lichen Gauge, Yew Polestar |
| map data QA | Alder Benchmark, Yarrow Groundtruth, **Larch Traverse** |
| source verifier | Alder Touchstone, Yew Hallmark, **Holly Benchmark** |
| Pi coder | **Willow Quadrat**, Fern Tessera, Bracken Plat |

**A slip in the naming prompt:**
- The prompt that asked the roles for names did not list Alder, a forecast team name, so two
  proposals reused it.
- Neither was picked.
- The coder's naming prompt listed Alder.

## The E-GD check, and what it changed

Before the owner's go, the drafts were checked against E-GD's BOOTSTRAP (both the role section and
the non-negotiables) and PROTOCOL rule 22.

- **"Operator" means the owner in E-GD.** The watcher was first sketched as a "run operator" and is
  now the "run watcher". Only the job title changed; the name the owner picked is unchanged.
- **A profile is written from the record, not from aspiration.** Practices with no record behind
  them are marked "proposed, no record yet". Examples: Larch tracing a skipped error to its feature,
  and checking licence credits in the app.
- **Each profile gained "Picking up work".** Each also requires the five disclosure sections in every
  report (BOOTSTRAP 5), states that a name is a label and not authority, and names the commits its
  records were read at.
- **Links to the planner's private memory were removed.** A citation of "RECORD -603" was removed
  too, until it could be checked (below).

## The owner's rule of 2026-10-04, first recorded here

The owner, 2026-10-04, verbatim: "Have the coder do the prep work and then have them ask again
before doing any non-read work, such as commits. Then I give my explicit go to send them off to
work. Not at any point do they start building until I say so."

- **Where it existed before today:** in the Pi planner's memory and in Pi dispatches.
- **Not in any `RECORD.md`:** the sentence is on no remote branch. The search took the quoted
  sentence across every remote branch's `RECORD.md` on 2026-10-21.
- Willow's profile now cites this record for it.

## Found: the Pi's record IDs collide with the main stream

- The Pi planner was given `2026-09-28-600` to `-699` (`records-pi-after-166`: "The split ... the Pi
  planner uses the block 2026-09-28-600 to -699").
- The Pi's own entries run `-600` to `-604` on `records-pi-after-166` (`998917a8`, 2026-10-04).
- On `records-after-173`, the same IDs hold the main stream's entries, which run on past `-699` to
  `-1520`.
  - Example: `-603` there is a forecast merge, "Closes: 2026-09-28-589".
  - The Pi's `-603` is "a Pi coder's go stays direct from the owner".
- **No new Pi ID is taken.** The owner and Rowan rule on Pi record IDs. The planner profile already
  carried this question; it is now known to be a live collision, not a doubt.
- **The planner's own error:** at first the planner reported -603 "not found", having searched only
  `main` and `pi-build`. Searching every remote branch found both entries.

## The owner's rulings

- **Models: Fable for Larch Traverse and Holly Benchmark.** The owner: "I'll take your
  recommendation". Primrose and Willow are not ruled.
- **When to stop and report:** "stop and report if you're unsure, if there's a gap, or if you need
  more information. Evidence is the hard gate". Written into Primrose's profile as its reporting
  rule, and into the planner profile's team section for the whole team.
- **Records:** "that's your job to write records". Aspen writes each record and Willow commits it,
  keeping "Go ahead and use coders to commit." (`e146ceade`).

## What was committed (dispatch pi-25)

- `docs/process/pi-coder-profile.md`: Willow Quadrat.
- `docs/process/pi-watcher-profile.md`: Primrose Lookout.
- `docs/process/pi-map-qa-profile.md`: Larch Traverse.
- `docs/process/pi-source-verifier-profile.md`: Holly Benchmark.
- `docs/process/pi-planner-profile.md`: a section appended, "Added 2026-10-21: the cartography
  team", with no other line changed.
- This record, its index row, and the dispatch.

## Disclosure

- **Confirmed:** every commit hash cited in the four profiles exists (`git cat-file -t`, 2026-10-21).
  The audit lines cited were read on `pi-build` `e75fb690`.
- **Inferred:**
  - that the jobs listed above had no owner (from the README and the record, not from asking anyone);
  - that Sage Wayfinder runs on Fable (Rowan's team table on `main` `381515c6`).
- **Could not determine:** which model past Pi coder sessions ran on.
- **Premises that were wrong:**
  - "nobody owns watching": a report-only watcher existed for pi-23;
  - -603 "not found";
  - that Sorrel and Juniper might overlap with the new roles. They are on the app team.
- **Decided beyond scope:**
  - the "run watcher" title;
  - folding the three smaller jobs into other roles;
  - not taking a record ID.
- **Observed, not asked:**
  - Rowan's table on `main` lists Ash Transect but not Alder;
  - the build log carries one skipped feature, relation 18593520, "Tolerance must be positive".
- **Not changed:** the running build (`forager-usca-build-261017-4r`), `/srv`, `/opt` and `main`.

## Added: the reporting line

The owner, 2026-10-21, after this record was committed at `3039a658`, verbatim: "They all report to
planner who reports to the operator, which is me".
- **The line:** every cartography role (Willow, Primrose, Larch, Holly and Sage) reports to Aspen
  Meridian, and Aspen reports to the owner.
- **What it closes:** Primrose's open item, "How an unasked report reaches the owner".
- **What it confirms:** "operator" means the owner, which is why the watcher's title is not
  "operator".
- **Where it is written:** a section appended to `pi-watcher-profile.md` and a paragraph appended to
  `pi-planner-profile.md`. No earlier line changed. Dispatch pi-26.
