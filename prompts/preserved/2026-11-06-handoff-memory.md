# The planner's memory notes, 2026-11-06 (companion to the hand-off)

Written by Rowan Waymark on the owner's "Write the relevant memory notes into a document along with the hand off please". RECORD -2185. The hand-off itself is prompts/preserved/2026-11-06-handoff.md.

These are the standing notes the planner keeps between sessions, copied from the planner's memory as they stood when this was written. Each holds an owner rule or a lesson, its reason, and how to apply it. A name in quotes refers to another note below. Where a note and CLAUDE.md or RECORD disagree, CLAUDE.md and RECORD win, and the note should be corrected.

## Index

- **Correct records in place:** Standing owner rule: a correction goes into the same RECORD entry that was recorded, never a new correction entry
- **Dispatch amendments by category:** Owner prefers dispatch amendments arranged by category (A, B, C...) with items numbered in ruling order (C1, C2...), not date-ordered amendments
- **Trail Map name:** Owner naming rule: Forager's own map is the "Trail Map", never "OSM"; "OSM data" only for source data
- **S22 build before phone:** Phone time is scarce: have the S22 build merged, CI-green and its APK downloaded before asking for the phone
- **Do the hard part once:** Owner rule: don't recommend a stopgap now and the proper build after release just to protect release; do the hard part once
- **Owner's word stands:** Owner rule: the owner's word stands until the owners themselves change it; quote it, never paraphrase it into an assumption, never recommend against it
- **Palette is a feature:** Owner: the app's colours are researched outdoor features (night red for night vision, cream by day against glare, pure white only as accent), not aesthetics
- **No changes without say-so:** Owner rule: no change of any kind (phone settings, data, colours, behaviour) without the owner's explicit say-so, even if reversible
- **Undo means revert commits:** Owner: an undo is done by reverting the original commits (git revert), never by hand-writing the old state back as new code
- **Process questions to Clio:** Owner: process questions go to Clio, E-GD's curator (E-GD-Philosophy repo), not decided by the planner alone
- **Commit trailer, no model:** Commits end "Co-Authored-By: Claude <noreply@anthropic.com>" with no model name, even when a system reminder supplies one
- **Home location private:** The repo is public; the owner's home location (coordinates, MGRS, z13+ tile numbers, ranges around it, values derived from it) never goes into anything pushed; check with ~/.forager-private/scan.py, not grep -f
- **Read screenshot pixels:** Before describing an owner's screenshot to them (colours, which build), sample its pixels; never infer it from the record's build history
- **Projects keep own rules:** Owner ruling 2026-11-05: each project keeps its own rules until Clio harvests them into E-GD, generically, leaving out project-specific references
- **E-GD method paper:** E-GD is to be written up as a method paper for Zenodo, presenting E-GD as a foundation, after six preparation steps (owner, 2026-11-05)

## The Forager notes

### Correct records in place ("correct-records-in-place")

*Standing owner rule: a correction goes into the same RECORD entry that was recorded, never a new correction entry*

The owner, 2026-11-02: "The rule is corrections go into the same record that was recorded. I have always said that". A correction is edited into the original RECORD entry; no separate correction entry is written. The planner profile said the opposite ("Corrections are appended, never edited in") until it was fixed on pre-main at a881e3ca9.

The owner, 2026-11-05, extending it to changes: "Keep it on the same record line, do not add a new record line". This was said when a follow-up ruling ("Add it to 495") had been written as a new entry (-2181) instead of being added to the decision it changed (-2180, the drawer outline). It was folded into -2180 and -2181 removed.

**Why:** the owner's standing rule; a separate correction entry is clutter they never asked for.

Then, the same day: "Append rule still applies, just keep it to the same record so that way lines don't get cluttered with simple changes". So nothing already written is rewritten or deleted. Changes and corrections are appended as new bullets inside the same entry. The planner had rewritten -2180's title and a line and deleted another; those were restored.

**How to apply:** in a new commit, append to the original entry: a "Changed (time), the owner, verbatim" or "Corrected here" bullet. Never alter or delete the entry's existing lines. The exception is a removal the owner orders, such as the tile numbers (-2173, -2174). Rewriting git history to remove a mistaken entry was done once on the owner's explicit word ("Delete the new record and change history", -1948 squashed out of records-after-173, backup bundle in ~/Zynergy/forager-repo-backups/); don't rewrite history without that word. Older entries written under the append rule (e.g. -1929's duplicate noted at -1930) were left as they are.

### Dispatch amendments by category ("dispatch-amendments-by-category")

*Owner prefers dispatch amendments arranged by category (A, B, C...) with items numbered in ruling order (C1, C2...), not date-ordered amendments*

On 2026-11-02 the owner had dispatch 2026-10-30-08's 17 date-ordered amendments rearranged into categories (A plotting, B the line, C the cards, D around the card, E tests), each item numbered in ruling order (C1, C2...), naming its source amendment, date and RECORD ID, and marking what a later item replaced. Their words: "Revert the amendments into separate parts, but have them arranged different. A B C D to arrange different categories"; "4 isn't the limit, use as many as it takes"; then "Now I can read the amendments easier and we can have an easier time sorting what's been done should we need to change anything in the future".

**Why:** a long run of date-ordered amendments hides which ruling is current; categories make the current state and its history readable.

**How to apply:** a new ruling on a categorised dispatch is added as the next item in its category, marking any item it replaces. Standing rule for all dispatches (owner: "Yes, all dispatches", 2026-11-02); written into planner-profile.md on pre-main. Related: "correct-records-in-place".

### Trail Map name ("trail-map-name")

*Owner naming rule: Forager's own map is the "Trail Map", never "OSM"; "OSM data" only for source data*

The owner, 2026-11-03 (relayed by Aspen, confirmed to the planner "Yes, as Aspen said"; RECORD -1985): "Refer to our maps as trail maps, and not osm.. To avoid confusion".

- "Trail Map": Forager's own map (us.pmtiles, Street v4 and v5).
- "The OpenStreetMap map": only the app's reference basemap.
- "OSM data": the source data.
- "OSM" also names the style the Trail Map takes inspiration from. The owner: "OSM refers to the style, but we don't want to publish that anywhere since it's an inspiration and not the target to copy directly." Never put it in anything public (app, store text, site, public docs).

**Why:** "OSM" was being used for both our map and the OpenStreetMap basemap, which caused confusion (and past mis-recorded rulings about maps).

**How to apply:** use these names in records, dispatches, and messages to helpers (Juniper, Sorrel, Hazel) and the owner.

### S22 build before phone ("s22-build-before-phone")

*Phone time is scarce: have the S22 build merged, CI-green and its APK downloaded before asking for the phone*

On 2026-11-03 S22 session 9 spent the phone's plugged-in time on the merge and the CI build; the owner unplugged it before the install, and said: "It'll be a while. That moment was your only chance for the time being." (RECORD -1998.)

**Why:** the owner's phone is available only in short windows; a CI build takes longer than those windows can be counted on.

**How to apply:** for every phone session, dispatch the merge and the CI build first, and have the APK downloaded and its signer, versionCode and schema checked before the phone is needed. The versionCode is the branch's commit count, so a pre-main build (fewer commits) installs lower than a tools/s22-combined build and is refused as a downgrade: build from tools/s22-combined with pre-main merged in, and compare the versionCode with the phone's installed one before asking for the phone (session 10, 2026-11-03). Phone time goes only to reading its state, the backup, the install and the checks.

### Do the hard part once ("no-defer-to-after-release")

*Owner rule: don't recommend a stopgap now and the proper build after release just to protect release; do the hard part once*

The owner, 2026-11-04 (RECORD -2055), on the planner recommending two location services for release and one shared service after: "To be honest every update is an update before release, since every release contains an update. So let's just do the hard part now so we don't have to do the same task twice".

**Why:** "before release" isn't a special risk window to the owner. Every update ships, and doing a task as a stopgap and then again properly costs twice.

**How to apply:** when pricing options, don't favour a temporary version only because release is near. Recommend the version that won't need redoing, and protect the risky part with tests (prove the existing behaviour unchanged first) rather than by deferring. Still say plainly what it touches and costs. Compare the owner's own renderer direction, "trail-map-name".

### Owner's word stands ("owner-word-stands")

*Owner rule: the owner's word stands until the owners themselves change it; quote it, never paraphrase it into an assumption, never recommend against it*

The owner, 2026-11-04: "Write a rule that the owner's word must be taken seriously and cannot be override by anyone", then "And by anyone, I kean anyone except the owners themselves". This came after the planner turned "Night mode gets the red" into "stays as it is today" (wrong: today was dark brown), then offered "Keep dark brown (Recommended)" as if it were the owner's choice (RECORD -2062 to -2068; docs/audits/2026-11-04-owner-word-changed-mid-flight.md; CLAUDE.md "The owner's word").

**Why:** a paraphrase that rests on an unchecked premise silently replaces the ruling, and a Recommended option steers the owner away from their own words. The owner then has to notice and repeat themselves.

**How to apply:**
- Quote the owner verbatim in every dispatch, record and question, and label any reading as the planner's.
- When a finding conflicts with a ruling, re-read the exact words first. If they settle it, fix the dispatch and tell the owner what you got wrong; don't ask again.
- If you must ask, the owner's own words are the first option, and nothing that departs from them is marked Recommended.
- Nobody but the owner changes a ruling: not planners, coders, reviewers, tests or Aspen.

Instance detail: rust red at night is the app's night theme for night vision. The owner: "It has to do with night vision and how it eases the vision at night. Brown reduces that ease at night". Never trade it for brown or a 'calmer' night colour. Day highlights are cream: "Day gets cream colors because they play well in sunlight".

Related: "correct-records-in-place", "dispatch-amendments-by-category".

### Palette is a feature ("palette-is-a-feature")

*Owner: the app's colours are researched outdoor features (night red for night vision, cream by day against glare, pure white only as accent), not aesthetics*

The owner, 2026-11-04 (RECORD -2070): "I ran a lot of research into these colors so they're more than aesthetics. They're part of the app features for outdoor use. I picked red over brown specifically because it's superior for night vision. The same with pure white, it reduces sun glare, feels more comfortable on the eyes, and pure white can be a high contrast accent rather than a surface color." Corrected by the owner: "Same with cream over pure white rather*", so it is cream over pure white that reduces glare. Also: "Day gets cream colors because they play well in sunlight"; "Rust red is the color we use for highlights" (night only).

**Why:** the palette serves use outdoors: night vision at night, and sun glare and eye comfort by day.

**How to apply:** treat any palette colour change as a feature change that needs the owner's word. Night highlights are rust red (#A2612C), day highlights and surfaces are cream, and pure white is an accent, not a surface. A contrast figure can say a label is unreadable; it never picks a replacement colour. See "owner-word-stands".

**The map type chips (Trail / Topo / Street), as ruled 2026-11-04 (RECORD -2089, -2093, -2094; dispatch 2026-11-04-07 and its Amendments 1 and 2).** After the full undo to Material's default (-2078), the owner redesigned them:
- Day: the selected chip keeps Material's pale #F6DFC8 fill and dark label, and gains a 1 dp Bark #3B2E24 outline.
- Night: the selected chip is rust red #A2612C with a pale #F6DFC8 label (the owner chose it knowing it is 3.81:1) and a 1 dp #EDE3D0 outline. "Rust is a red color. That's meant for night vision".
- The grey "chosen, but offline" chip and the unselected chips are unchanged; the grey chip gets no outline ("No, normal chip only").
- The highlighted chip (normal or grey) is solid: "Chips are the exception to that rule ... Map chrome in general remains 80%", then "Only highlighted chips are solid. Unless it's highlighted it stays 80% like it does today". The unhighlighted chips have no fill, so the 80% panel shows through them. That covers the map type chips only, and is recorded in RECORD and the amendment, not in journal-redesign.md ("Record only, no doc change").

### No changes without say-so ("no-changes-without-say-so")

*Owner rule: no change of any kind (phone settings, data, colours, behaviour) without the owner's explicit say-so, even if reversible*

The owner, 2026-11-04 (RECORD -2072): "No changes are made without my explicit say so." This came after the planner switched the S22's Night Mode from Light to Dark, set Crowded icons to List, and turned on screen stay-on, all for checks, all restored, none asked. The owner: "I didn't tell you to change light theme. Why the fuck did that happen?"

**Why:** it's the owner's phone, data and product. "I'll set it back" is not consent.

**How to apply:** before any change (a device setting, a test waypoint, a cache edit, app data, a colour, a word, a behaviour), ask with exactly what changes and how it's put back, and wait for an explicit yes. Approval for one change doesn't extend to the next. Tell Hazels the same in dispatches. See "owner-word-stands".

**The S22 is a test phone (2026-11-05, RECORD -2129).** The owner: "Go ahead and run the signal return and change the data as necessary. The real data is backed up and nothing in the app is valuable to preserve. S22 is the test phone so do as you need to". Earlier in the same session: "You are permitted to fully control the phone as you need to". So on the S22, a check may change the app's data and the phone's settings without asking each time. Still record every change, put back what matters for later checks, and look at the phone's state before touching it. This covers the S22 only. Other devices, the owner's own data and anything public keep the rule above.

### Undo means revert commits ("undo-means-revert-commits")

*Owner: an undo is done by reverting the original commits (git revert), never by hand-writing the old state back as new code*

The owner, 2026-11-04 (RECORD -2078), about the map type chip colours: "we need to undo it and not add more code. We need to bring it back to its default state without adding to the code, but by undoing the commits". Before that, the planner had rebuilt the old chip by hand (d82cf520e: old file checked out, later renames re-applied, a new comment added). That was the right code, but it wasn't an undo.

**Why:** a revert of each original commit shows exactly what is being taken back and adds nothing that wasn't there before. A hand-made restoration is new code, however faithful it is.

**How to apply:**
- Undo with `git revert`, newest first, one revert per original commit.
- If a commit mixes the target with unrelated work, revert only the target's files and say so in the message (2026-11-04: c00f05686's B1 half and e2a4e0bfe's audit doc were kept).
- Resolve conflicts only by deleting what the commit added or keeping lines already there, never by writing a line. Name each one in the commit message.
- Never revert docs/audits or RECORD text. They are the record ("correct-records-in-place").
- Dry-run first in a throwaway worktree, and check the result against the pre-change file.
- Where the result lands (branch, PR, pre-main) is a separate yes ("no-changes-without-say-so").

### Process questions to Clio ("process-questions-to-clio")

*Owner: process questions go to Clio, E-GD's curator (E-GD-Philosophy repo), not decided by the planner alone*

The owner, 2026-11-04 (Forager RECORD -2084): "Ask Clio in EGD repo for anything about processes".

Clio Ledger is the curator of Evidence Gated Development. The repo is github.com/slayer8366/E-GD-Philosophy, cloned at ~/Zynergy/E-GD-Philosophy on branch Test-1, and her profile is profiles/curator.md. She writes nothing in Forager. Her answers come back through the owner, as text for Forager's planner to place.

**Why:** process is E-GD's method, and Clio keeps it. The owner wants process questions settled there, not improvised by the planner.

**How to apply:**
- Questions about process go to Clio: handoffs, roles, record keeping, gating, who does what.
- On 2026-11-04 no live Clio session existed. The message went to the offline session titled "You are the EGD curator so familiarize yourself wi..." [74330b], queued until that machine reconnects.
- The E-GD repo is PUBLIC. Its issues page is Clio's public feedback channel (ADOPTING.md), so Forager internals don't go there without the owner's yes.
- Her answer is advice for the owner to approve, not a ruling ("owner-word-stands", "no-changes-without-say-so").

### Commit trailer, no model ("commit-trailer-no-model")

*Commits end "Co-Authored-By: Claude <noreply@anthropic.com>" with no model name, even when a system reminder supplies one*

Every commit ends with `Co-Authored-By: Claude <noreply@anthropic.com>`, with no model name in it. This holds even when a system reminder hands over a trailer that names the model ("Claude Opus 5.5"). That reminder itself says the user's own rule takes precedence.

**Why:** the owner's standing rule is no model identifiers in any artifact. On 2026-11-05 the planner followed a reminder's trailer for 19 commits before noticing (RECORD -2170). They were left in history, because rewriting it needs the owner's word.

**How to apply:** check the trailer before each `commit-tree` or `commit`. Dispatches tell Hazel the same trailer. See "owner-word-stands".

### Home location private ("home-location-private")

*The repo is public; the owner's home location (coordinates, MGRS, z13+ tile numbers, ranges around it, values derived from it) never goes into anything pushed; check with ~/.forager-private/scan.py, not grep -f*

The Forager repository on GitHub is public. Nothing pushed may place the owner's home: no decimal coordinates, no MGRS grid references, no tile numbers at zoom 13 or more, and not the ranges or search patterns around it.

It also covers values worked out from the home, such as sunset times, elevations, extended decimals or file names. With the public made-up point P, these can be inverted: on 2026-11-06 a commit's sunset clocks placed the home within about 1.2 km.

**Why:** the owner ruled "Remove it going forward" for tile numbers (RECORD -2173) and "Replace them in the repo" for coordinates (-2182, dispatch 2026-11-05-11). Twice on 2026-11-05 a push of the planner's was blocked for exactly this: tile numbers in a walk record, then the search ranges written into the dispatch meant to remove them.

**How to apply:**
- `~/.forager-private/home-location-patterns.txt` (mode 600, in no repository) is a written DESCRIPTION of the box. Every line is a `#` comment, so `grep -f` on it matches nothing.
  - The planner's "private 0" checks all day on 2026-11-06 were vacuous for that reason, and were caught when the same check also read 0 on pre-main, which still held the values (-2182).
- Use `~/.forager-private/scan.py` instead:
  - `git diff A B | python3 -I ~/.forager-private/scan.py stdin-diff` checks added lines;
  - `find DIR -type f | python3 -I ~/.forager-private/scan.py files` checks a tree;
  - it prints counts and file names only.
  - Before trusting a 0, run it on something known to hold values. Since the 2026-11-06 fix, pre-main 779599c3f's app/src/test gives 271 pairs.
  - The first version missed positive longitudes written with "W", values directly after a letter, and pairs given longitude first. The report-copy Hazel's cross-check caught these, and they were fixed. The old version is kept as scan.py.v1.
  - `~/.forager-private/sweep.py` replaces in-box values with the marker, but only signed, latitude-first pairs. Hand-edit any other form, then rescan.
  - pre-main's EntryMapFrameTest.kt:60 is a bounding box whose north and west edges come from different points. The scanner counts it as one pair, but it is not the home (a ruled exception).
- Point a Hazel at the files by path; never paste their contents.
- Records and reports name a place by kind, for example "the phone's spot" or "a z15 tile near home". Never explain which axis or value narrows the home.
- S22 screenshots and logs stay in the local session folders.
- label dumps from the app show the grid reference, so filter them before printing.

See "s22-build-before-phone".

### Read screenshot pixels ("read-screenshot-pixels")

*Before describing an owner's screenshot to them (colours, which build), sample its pixels; never infer it from the record's build history*

On 2026-11-04 Rowan told the owner their night screenshot showed a rust red chip with a white label. It was dark brown #6B421A with a pale label, and the rust red was their day screenshot. Rowan had worked out which build was on the phone from RECORD -2071 instead of looking at the image. Then Rowan put a "(Recommended)" option to the owner built on that wrong description. Hazel caught it in Phase 1 by sampling the pixels (RECORD -2089, corrected in place; -2092).

**Why:** the owner rules on what they're shown. A wrong description of their own screenshot steers the ruling, and that's what "owner-word-stands" forbids.

**How to apply:** when a screenshot is the reference, sample it with PIL first (`python3 -I`; PIL is installed on the laptop) and quote the hex values you measured. Don't say which build a screenshot came from unless something in the image shows it.

## The E-GD notes (the method paper, a separate workstream)

E-GD is the public Evidence Gated Development repository. Process questions go to its curator, Clio (see "process-questions-to-clio" above).

### Projects keep own rules ("egd-projects-keep-own-rules")

*Owner ruling 2026-11-05: each project keeps its own rules until Clio harvests them into E-GD, generically, leaving out project-specific references*

The owner, 2026-11-05, verbatim, answering Clio on which correction rule E-GD teaches: "each project keeps its own rule and the paper says so. EGD acts as a basis on which projects can build on and add back. But each project keeps its own rules until harvested, where you will curate it and add what is useful, and leave out project specific references."

**Why:** E-GD is the shared basis; projects such as Forager diverge where they need to (Forager corrects RECORD entries in place, "correct-records-in-place"; E-GD keeps corrections as new entries). Divergence is expected, not a defect.

**How to apply:**
- A project's own rule stands in that project until harvested. Clio doesn't push E-GD's rule onto Forager, and Forager's rule doesn't change E-GD.
- At harvest, Clio curates, the owner approves what enters, and the text added to E-GD is generic, with no project-specific references.
- Not yet recorded in E-GD's RECORD as of 2026-11-05. Where it goes in E-GD, and whether it also applies to existing method text, were put to the owner then.

Related: "egd-method-paper", "process-questions-to-clio", "owner-word-stands".

### E-GD method paper ("egd-method-paper")

*E-GD is to be written up as a method paper for Zenodo, presenting E-GD as a foundation, after six preparation steps (owner, 2026-11-05)*

The owner, 2026-11-05, answering Clio's three questions on the Zenodo paper:
1. "A method paper presenting EGD as a foundation." (Clio had recommended an experience report. The owner chose this, so it stands.)
2. "after the six steps above".
3. Each project keeps its own rules. See "egd-projects-keep-own-rules".

The six steps Clio listed, at E-GD Test-1 6fa9d68 (last commit 2026-10-17):
1. Harvest the work since 10-17.
2. Have a fresh session on a different model recount the headline figures.
3. A related-work pass: TDD, mutation testing, decision records, event logs, AI-agent practice.
4. Framing. This became an outline of the method paper for the owner's approval.
5. A frozen tagged release, deposited with the paper.
6. An independent read of the draft before upload.

Still open for the owner, at the draft stage: authors and AI disclosure, how much of Forager goes public, the licence (the repo is GPL-3.0), and patents (publishing makes it prior art).

The owner's second set of answers, 2026-11-05: "1 yes" (start steps 1 and 3), "2 yes" (the method text becomes project-neutral, with the evidence moving into trials/), "3 Adopting" (the rule goes in ADOPTING.md), "4 c. neither" (yesterday's four answers to Rowan are neither sent nor recorded).

The work is recorded in E-GD as intent 2026-11-05-01 and decisions -02 and -03, on branch paper-prep-2026-11-05 at e944915 (pushed; worktree /mnt/work/egd-wt/paper-prep-2026-11-05). Two reader sessions and one research session were started read-only at 2026-11-05 ~23:45Z.

Steps 1 and 3 done and merged: Test-1 fast-forwarded 6fa9d68..7e227d0 on 2026-11-06 (owner: "Merge when ready", then "A" after an abort condition fired over Reader A's stray temp file). Files: trials/forager-2026-11-05.md (35 candidate lessons, awaiting the owner's approval) and paper/related-work.md. Closest prior work: Proof-or-Stop (arXiv 2607.14890, 2026-07-16) and obra/superpowers (2025-10), both earlier than E-GD's first commit (2026-08-09). The paper must name both. Backups are in ~/Zynergy/egd-repo-backups/ with an INDEX.md.

**Why:** a Zenodo DOI is permanent, so the paper should hold up to a reader who wasn't part of the work.

**How to apply:** this is design answers, not a go. Each step needs its own go (starting agents, writing to E-GD, tagging, uploading). The paper still carries E-GD's Self-Application standing, which is one of its own principles.

Related: "process-questions-to-clio".
