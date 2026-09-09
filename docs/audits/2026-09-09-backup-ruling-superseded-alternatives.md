# Ruling: `allowBackup="false"` stands permanently — and the two rulings it supersedes

**Date:** 2026-09-09. **Dispatch:** backup-ruling docs, from the owner's ruling relayed through the
planner's handoff. **Status:** the record of a decision, plus the documentation changes that carry
it. Documentation and one code comment only — no logic changed, and the manifest attribute was
already `false` before this dispatch touched anything.

## The ruling

**`android:allowBackup="false"` stands. It is not beta-scoped.** Android Auto Backup and the
device-to-device transfer that runs during a new phone's setup are off permanently. Restore is
handled by an **in-app export/import the project builds itself**.

**The owner's reasoning, recorded because it is the part a later session cannot re-derive from the
code:** Google sells cloud backup as a service. An app that does its own export keeps that value
with the app, and that is part of what justifies charging for it. The export stays local unless the
user chooses to move it.

## Two superseded rulings — recorded, not deleted

Both were planner rulings, both were reasonable when made, and neither was violated by anything
that shipped. They are written down here so a later session that re-derives one recognises it as
already-considered rather than new.

**Superseded ruling 1 — "keep backup on, add exclusion rules for crash logs only."**
Superseded because it predates the restore-corruption argument. That argument is about the live
Room database and the photo files being copied mid-write and restored torn onto a fresh install
(twelve migrations of history; `app/schemas/` holds `4.json`-`15.json`); crash logs are not the
data at risk, so a rule set that excludes only them leaves the whole problem in place.

**Superseded ruling 2 — "keep the rules file, add the database and the photo directory to the
exclusions."** Withdrawn by the planner *before* the owner ruled, and the reason is worth keeping:
excluding the database directory and the photo directory excludes essentially all user data, so it
arrives at the same end state as `allowBackup="false"` — nothing meaningful backed up — while
adding two config files (`android:dataExtractionRules` for API 31+ and `android:fullBackupContent`
for API 30 and below), two domains each, and several ways for a rule to be silently inert. Same
outcome, more surface, no verification available: nothing in this repo's Robolectric suite
exercises a backup transport, so the rules' correctness could only be established on a device.

**The earlier completion report is superseded in its framing, not corrected in place.**
`docs/audits/2026-09-09-allow-backup-and-play-signing-notes.md` recorded the decision as
"beta-scoped … MUST be revisited before production". That was the state of the decision on the day
it was written and it is left standing — this directory's own README says an audit is a
point-in-time record and a later one supersedes it rather than editing it. What was rewritten is
the **manifest comment**, because that is not a historical record: it is the instruction the next
session reads, and its `MUST be revisited before production` would have licensed flipping the
attribute back.

## The retired flag on `claude/backup-and-signing-notes`

The planner's handoff carried a flag on that branch: **"needs a dispatch, not a merge."** That flag
is now **wrong and retired** — the branch implements the ruling that won. Two things about it,
separated by how well each is evidenced:

- **Verified here:** `claude/backup-and-signing-notes` (head `07553fe`) is already an ancestor of
  `claude/integration-2026-09-09` (`git merge-base --is-ancestor`, run in this worktree). Its
  content — `allowBackup="false"` plus the Play-signing comment — is in the integration branch, so
  the merge the flag advised against has in fact already happened and nothing about the ruling
  argues for undoing it.
- **Unverified / not in the tree:** the flag's own text. `grep` across the repository finds no
  file containing "needs a dispatch, not a merge"; it reached this session only through the
  planner's handoff, so this note records it as relayed rather than as something read from a
  document.

The Play App Signing half of that branch is untouched by this ruling and remains what its own
report says it is: an **open owner decision** between a Google-generated upload key and PEPK. Retiring
the backup flag does not close that.

## What was changed to carry the ruling

| File | Change |
|---|---|
| `app/src/main/AndroidManifest.xml` | The comment above `<application>` rewritten: standing not beta-scoped, the owner's reasoning, what is actually given up, both superseded rulings, and an explicit "the in-app export/import is not built yet — do not describe it as available". The restore-corruption reasoning is kept; the attribute value was not touched. |
| `docs/beta/README.md` | The "the app allows Android's own backup … may copy Forager's data to your Google account" disclosure was **false on this tree** and is replaced by a description of what actually happens. |
| `docs/legal/privacy-policy.md` | The same claim in long form, same correction. The two TODO placeholders (contact email, Worker log retention) are untouched — they are owner-only and must not be invented. |
| `docs/audits/2026-09-09-windows-only-test-failures.md` | Amended with the failure messages, which are now available. See that note. |
| `docs/audits/2026-09-09-journaltabtest-photo-pull-flake.md` | New. Both halves of the `JournalTabTest` flake boundary. |

## How the user-facing text was worded, and why it is a description rather than a warning

Two framings were tried by the planner before the owner's, and both were rejected **by the owner**.
They are recorded because both are the natural thing to write and both are false:

1. **"A tester who switches phones loses everything."** False. `allowBackup="false"` disables the
   OS-mediated seamless transfer — the flow that repopulates apps during new-device setup. Every
   deliberate move still works and none of it runs through Auto Backup: an exported file over a
   cable, Bluetooth, Wi-Fi Direct, or a cloud folder the user picks.
2. **"The app cannot do what the OS does."** False. Restore is reading a file and writing rows. The
   app owns its schema and has twelve migrations of history; it can rebuild tracks, journal
   entries, waypoints and photos as completely as Auto Backup would.

**The only real asymmetry is triggering, not capability.** Auto Backup's advantage is that the OS
hands the data over during setup, before the app has run — zero steps from the user. The app's
version needs one deliberate step: open it and point at a file. That is a first-run design
question, not a limitation, and it does not belong in a consent document as a loss.

So both documents state it as a **description**: nothing is copied anywhere automatically, you
export when you want to, the file goes only where you send it, and importing it rebuilds
everything. No caveat framing, no "you may lose", no instruction to keep a phone.

## The honesty problem, and how it was handled

**The in-app export/import does not exist in this tree.** Nothing in `app/src/main` implements it
(this dispatch changed no code). Describing the ruling honestly and describing the feature honestly
pull in opposite directions: the ruling is permanent and present-tense, the feature is a plan.

Resolved by splitting the tense rather than softening either half. Both documents state the
permanent decision in the present tense (Android's backup **is** off; moving data **is** the app's
job, and the design is an export you trigger), and then say plainly, in bold, that the export and
import are **not in the build being tested / not built yet**, followed by the one factual
consequence for the reader — for the length of the closed test, what a tester records stays on the
phone that recorded it. That sentence is a statement of the current build's state, not a warning
about loss, and it is the minimum needed to keep the paragraph from advertising a feature that does
not exist. The manifest comment carries the same instruction to future sessions in capitals, so the
next person to write user-facing copy cannot miss it.

**A follow-up this note deliberately does not decide:** when the export/import ships, both
documents need the "not built yet" sentences removed, and the first-run design question (how a user
who has just installed on a new phone finds the import) needs an owner decision. Neither is in this
dispatch's scope.
