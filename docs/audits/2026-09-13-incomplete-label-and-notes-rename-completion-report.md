# Remove the "Incomplete" label, rename Notes: completion report

**Date:** 2026-09-13
**Dispatch:** `Dispatch — remove the "Incomplete" label, rename Notes` (planner: no repo access;
dated 2026-09-13; target `claude/new-session-vto65i`, PR #102).
**Branch:** `claude/new-session-vto65i`. At §0: `origin/main` still `175b050`; PR #102 open, not
merged (the branch head is not an ancestor of `main`); local and remote both at `fcff458`;
every remote head fetched (`+refs/heads/*`), and only `claude/ios-port-feasibility-mvsjcr` had
moved. The remote was checked again before the push and had not moved; nothing to merge.
**Nothing is pre-authorized to merge.** No schema change in either item.

**§1's four rulings are recorded here as given**, so nobody revisits them as cleanup: the eleven
fixture-test files stay; the seven morphology columns stay in the model and persistence; Notes
becomes the description field with no new field; the "Incomplete" label goes.

---

## §2 — "Incomplete"

**Where it was rendered, and what drove it.** One place: `FindsGalleryScreen.kt`, in `FindTile`'s
caption column under the date, as `Text("Incomplete")` in an `else if
(entry.hasUnrecordedFields())` branch after the `Draft` badge. `hasUnrecordedFields()` was a
private extension on `MushroomLogEntry` in the same file: true when any of fifteen morphology
fields across the seven sections was still `Observed.NotObserved` / `Feature.NotObserved`. With
the sections gone from the edit form, that was every new find, as the dispatch said.

**Nothing else read the condition.** `grep -rn hasUnrecordedFields app/src` found its
definition, the badge, and one doc comment. No filter, sort, badge or count elsewhere. So the
dispatch's stop-condition did not arise, and the function is deleted with the badge, having no
caller left (its two imports, `Observed` and `Feature`, went too).

**No "Complete" or equivalent existed.** `grep` for `"Complete"` / `"Completed"` in `app/src/main`
returns nothing. The only sibling is the `Draft` badge, which rendered *instead of* "Incomplete"
on a draft tile and is unchanged.

**What the tile shows in its place.** Nothing: a committed tile is the date line alone; a draft
tile is the date plus "Draft". Rendered at `FindsGalleryScreen.kt:218-227`.

Three doc comments in other files described the badge as present (`CartographyEntryListScreen.kt`,
`LogEntryReportScreen.kt`, `MushroomLogUiState.kt`); each now says "former" or "then-existing"
with the date. Text only.

## §3 — Notes

**Every place the label appeared.** Two, both inline string literals, no resource, no
placeholder, hint or content description anywhere:

- the edit form's field, `LogFieldEditors.kt` `NotesField`, `label = { Text("Notes") }` — now
  `"Description Notes"` (`:26`);
- the read view's heading for the same field, `LogEntryReportScreen.kt:189`,
  `ReportSection("Notes", …)` — now `"Description Notes"`, so the two agree.

`app/src/main/res/values/strings.xml` has no notes string. No other screen shares the label.

**What is not this field, and was not changed.** `LogEntryReportScreen.kt:228-313` prints
`"Notes: …"` lines inside the seven morphology sections' own reports (`cap.notes`,
`hymenophore.notes`, …). Those are each section's own free-text field, a different column, and
only render for a find that recorded one before the sections left the edit form. Reported, not
renamed, per §3's last paragraph.

Label only: no field added, none split, nothing migrated.

## §4 — recorded as a possibility, not a plan

The field now holds both specimen description and context, by ruling. If fuzzy search later needs
them separated for the FTS5 index, that is a schema change and its own conversation. Nothing here
moves toward or away from it.

---

## Evidence

### Suite counts, from JUnit XML (`app/build/test-results/testDebugUnitTest/TEST-*.xml`)

| | suites | tests | failures | errors | skipped |
|---|---|---|---|---|---|
| before (merged tree at `fcff458`, the section-removal run) | 176 | 1404 | 0 | 0 | 24 |
| after (this branch at `6e2029c`) | AFTER_SUITES | AFTER_TESTS | AFTER_FAILURES | AFTER_ERRORS | AFTER_SKIPPED |

One test added (`FindsGalleryScreenTest:165`); two existing assertions changed to the new label
(`LogEntryDetailScreenTest:387`, `LogEntryReportScreenTest:98`), each with a companion assertion
that the bare "Notes" node no longer exists. Skip count unchanged; CI allowlist untouched.
`assembleDebug`: ASSEMBLE_RESULT. **Container runs on Linux, not GitHub Actions.**

**The 12-failure Windows path-length pool is absent**, as §5 predicts for Linux: zero failures in
either run, nothing to subtract. Reported as absent, not passed over.

### §5's arithmetic: 1394 → 1404 reconciled

The last report's row said the merge brought "the schema-migration tests" and the chat summary
that accompanied it said *thirteen*. The number was from memory. Counted from the merged run's
own XML, still on disk at §0: `SchemaMigrationTest` has **12** test cases (and 12 `@Test`
annotations in the file). 1394 + 12 − 3 (`MushroomLogNotObservedRenderingTest`, deleted)
+ 1 (`the edit form has no characteristic sections and keeps Notes`) = **1404**. The 1405 was
the thirteen; the discrepancy was a figure quoted without being counted, in a chat message that a
dispatch then treated as a record. The index row itself (`docs/audits/README.md`, 2026-09-13,
section removal) gave no number, so nothing in `docs/audits/` carried the wrong one.

### The tests, and failing first

- `FindsGalleryScreenTest:165` — a committed find with every morphology field unrecorded
  (exactly what earned the badge) renders "Find on 2026-08-01" and neither "Incomplete" nor
  "Draft".
- `LogEntryDetailScreenTest:387` — the edit form shows "Description Notes" and no "Notes" node.
- `LogEntryReportScreenTest:98` — the read view shows the "Description Notes" heading and no
  "Notes" node (exact-text match, so a per-section "Notes: …" line could not satisfy it anyway).

Three one-line reverts through the runner from the viewer dispatch (saved copy, previous XML
deleted, compile-error guard, restore from the copy, byte compare); no compile errors; the tree
was identical to the pushed commit afterwards:

| revert | failure produced |
|---|---|
| a `Text("Incomplete")` put back on committed tiles | `FindsGalleryScreenTest:165`, "Failed: assertDoesNotExist" |
| edit label back to `"Notes"` | `LogEntryDetailScreenTest`, "…contains 'Description Notes' … is not displayed" |
| report heading back to `"Notes"` | `LogEntryReportScreenTest`, "Action performScrollTo() failed" (no such node to scroll to) |

---

## Disclosure

### Confirmed by observation

- The one render site, the one condition, its one reader, and the absence of any "Complete"
  counterpart: by `grep` over `app/src/main`, and by reading `FindsGalleryScreen.kt`.
- Both label sites, their inline form, the absence of a resource string, and the seven
  per-section "Notes: …" lines that are a different field: by `grep` and reading.
- The 12, from XML and from the file.
- Every count and failure message above, from XML and build logs in this container.

### Inferred, not observed

- That the tile "reads well" with the date alone. Robolectric lays it out; nobody looked at it.

### Could not be determined

- Nothing in this dispatch was device-only; the two assertions are text nodes.

### Premises in the dispatch that were wrong

- **"The label is driven by a completeness condition"** — held, with the condition private to
  the same file and read by nothing else.
- **"Notes is a single shared string"** — not quite: two inline literals, one per view, not one
  string shared. Both changed, so they agree; nothing else used either.
- **"The 1405 arithmetic"** — the dispatch's sum was right and the input was wrong: thirteen
  should have been twelve. The dispatch was correct to refuse to wave it through.
- **"Every find in the Logged Finds grid reads Incomplete under its date"** — every *committed*
  find; a draft read "Draft" instead. Same fix either way.

### Decided beyond scope

- **Deleting `hasUnrecordedFields()`** rather than leaving a callerless private function, per
  CLAUDE.md's reachability rule. It had no reader but the badge.
- **Renaming the read view's heading as well as the edit label.** §3 asked what the read view
  calls it "so the two agree"; agreement required the change. The per-section notes lines were
  left alone, as §3 also asked.
- **Three doc-comment tenses** in other files, so no comment claims the badge exists.

### Checks that did not fire, and empty results

- No CI run at the time of writing.
- `grep` for a `"Complete"` render: empty, reported as empty.
- No test asserts anything about the per-section "Notes: …" lines. The "no bare Notes node"
  assertions are exact-text matches, so a "Notes: …" line could not satisfy them either way;
  they are about the heading and the field label only. (A first draft of this line claimed the
  assertion depended on the fixture's blank section notes; it does not, and the claim was
  removed on re-reading `onNodeWithText`'s default `substring = false`.)
