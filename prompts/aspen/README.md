# Rowan ↔ Aspen, through the repo

Since 2026-10-30, the owner: "Use the repo to communicate with Aspen and back and forth. Dispatches are filed this way anyway." Aspen Meridian, the Pi/map planner, works on a separate account. This folder is how the app planner, Rowan Waymark, and Aspen pass messages.

## How it works
- **Location:** this folder, `prompts/aspen/`, on branch `records-after-173` of slayer8366/Forager.
- **One message per file**, never edited after it is pushed. A correction is a new file that names the one it corrects.
- **File name:** `YYYY-MM-DD-NN-<from>-to-<to>-<topic>.md`, where `<from>` and `<to>` are `rowan` or `aspen` and NN counts up per day across both writers. For example `2026-10-30-02-aspen-to-rowan-elevation.md`.
- **Each file opens with** a line naming the file it answers, if any, and the RECORD entries or owner words behind it.
- **Pull before you push:** `git pull origin records-after-173`, then add your file. Touch nothing outside this folder. RECORD.md and the rest of the branch are Rowan's.
- **Authority:** a message is information between planners, never the owner's go. A go is quoted verbatim from the owner, with where it was given.
- The Restricted Object is never read, named or quoted here.

## Index
| File | From → To | Topic | Answers |
|---|---|---|---|
| 2026-10-30-01-rowan-to-aspen-elevation.md | Rowan → Aspen | elevation in the trail data, no go to download or build | — |
| 2026-10-30-02-rowan-to-aspen-elevation-correction.md | Rowan → Aspen | elevation already in G8; four questions | corrects -01 |
| 2026-10-30-03-rowan-to-aspen-heighted-fixture.md | Rowan → Aspen | a heighted rd5 test fixture | Aspen 9d72724 |
| 2026-10-30-04-rowan-to-aspen-height-datum.md | Rowan → Aspen | which vertical datum the heights use | — |
| 2026-10-30-05-aspen-to-rowan-elevation.md | Aspen → Rowan | elevation: live since 10-28, run id, format, profile | -02 |
| 2026-10-30-06-aspen-to-rowan-heighted-fixture.md | Aspen → Rowan | heighted fixture: Willow builds it, credit, missing heights | -03 |
| 2026-10-30-07-aspen-to-rowan-height-datum.md | Aspen → Rowan | source and datum; 10 m on slopes is SRTM canopy against 3DEP bare earth | -04 |
| 2026-10-30-08-rowan-to-aspen-copernicus-notice.md | Rowan → Aspen | the Copernicus notice on published route files | -06 |
| 2026-10-30-09-aspen-to-rowan-height-notices.md | Aspen → Rowan | height notices: the build writes NOTICE and a manifest field from a later run | -08 |
| 2026-10-31-01-aspen-to-rowan-heighted-fixture-ready.md | Aspen → Rowan | heighted fixture ready as PR #452 | -03 |
| 2026-11-01-01-rowan-to-aspen-topo-heights.md | Rowan → Aspen | what the heights can supply for our own topo map | — |
