# Dispatch 2026-11-05-04: The field check of the pick-up chooser, and the way home's off-trail walk, written as device checks (Hazel, Opus, read only)

Written by Rowan Waymark, 2026-11-05. RECORD -2138 (intent). It follows Elder's audit items 4 and 5 (prompts/preserved/2026-11-05-02-report.md; RECORD -2135) and the owner's rulings (-2136). Every fact below is a premise to re-check, with file and line, at pre-main ee0651fe0.

## Authorisation
The owner, verbatim:
- to "The pick-up chooser at your end of a Navigate … How should it ship?": **"Field check first"**. The option read "Before promoting, someone takes the S22 to a spot off the trail near streets, with a good GPS fix, and Navigates, so the chooser can be seen working."
- to "You asked for the way home's figures to be judged on a walk off the trail … How should it ship?": **"Walk first"**. The option read "The promotion waits for a walk that leaves the trail and comes back."

Writing these checks is the planner's step towards both. Doing them is the owner's (or whoever carries the phone).

## Writing rule
Describe products generically. You write no files in the repository: your report is your final message, and the planner files it.

## Change
Nothing changes in the app. Two device checks, written to the device-check-authoring skill:
- cheapest step first;
- each step with its pass condition and its evidence;
- observations marked as not gates;
- the adb or log reads the planner does afterwards.

**1. The pick-up chooser** (dispatch 2026-11-04-04 A2, A4; RECORD -2050, -2059):
- what a person sees on the phone when a pick-up point is chosen at the walker's end, and when it isn't;
- the conditions that make it run: a fix of 15 m or better, an area whose tiles carry the fence mark, the walker off the trail, and streets near;
- how to tell "chosen" from "by trail" on screen, and from the log. A successful choice isn't logged today (RECORD -2103): say whether the check can be judged from the screen alone. If it can't, say what would be needed, and **stop: don't propose code in this dispatch**;
- where the planner's S22 areas (Region 1 and 2, now with the copy) would allow it, without naming coordinates.

**2. The way home's off-trail walk** (RECORD -1699, -1726, -1729; the way home's code under `domain/wayhome`):
- what the owner asked the walk to judge, in the owner's words;
- what to do on the walk (leave the trail, come back);
- what the screen shows, and what to look for: whether the dim ever appears before the re-route (-1726);
- what the log or the walk logger keeps, and what the planner reads afterwards (-1729's "read the log");
- what counts as a pass, and what is only an observation.

## Scope boundary
- **Read only:** pre-main ee0651fe0, records-after-173, and the S22 session folders under `~/s22-2026-11-04/` (read only).
- **Not:** code, branches, commits, Gradle or the phone.
- Scratch goes in `/mnt/work/scratch/field-checks/`, never `/tmp`.

## Stops
Stop and report on:
- a check that can't be judged without new code or logging;
- an owner-level choice;
- a wrong premise.

## Finish line
Your report, with both device checks, filed by the planner as `prompts/preserved/2026-11-05-04-report.md` and brought to the owner.
