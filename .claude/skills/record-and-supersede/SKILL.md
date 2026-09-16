---
name: record-and-supersede
description: How to keep a project record that stays trustworthy as it ages — index rows, decision records, and the rule that a later finding supersedes an earlier one rather than overwriting it. Use this whenever you finish a dispatch, record a decision, correct something previously written, or find that a document's claim is no longer true. Use it especially when the correction is embarrassing or small, because those are the ones that get silently edited and that is how a record stops being evidence.
---

# Record and supersede

A project record is only useful if a reader can trust that what it says was true when written and is marked where it stopped being true. Records that get silently corrected teach nothing about where the uncertainty was. Records that never get corrected become traps.

The discipline is simple: append, mark, preserve. Never quietly rewrite a published claim.

## Superseding, not overwriting

When a later finding contradicts something already recorded:

- Leave the original wording in place.
- Add the correction with its date and the evidence that produced it.
- Quote the withdrawn wording in the correction so a reader who only sees the new text still knows what was replaced.

A record that says "this previously said WSL2; the machine is native Ubuntu, corrected on this date, with the check that established it" is more useful than one that simply says Ubuntu. The second hides that anyone was ever wrong, which means the next reader cannot tell which other claims might be in the same state.

In practice this means a `# CORRECTION` line, an appended section, or a superseding note under the title — whatever the document's form allows — rather than an edit to the sentence itself.

## The exception: unpublished claims

Something not yet committed can be edited directly. There is no published claim to withdraw, and preserving a draft's wrong wording helps nobody.

Say which you did. "Neither had been committed, so I edited them directly rather than superseding" tells the reader you knew the rule and why it did not apply.

## Index rows

Every dispatch, finding, and decision gets a row: what it was, when, what commit, what it superseded if anything. The index is what makes the record searchable later, and it is what lets a fresh session find the reasoning without reading every document.

An index row that supersedes an earlier row should say so explicitly, pointing at the row it replaces. A stale row that nobody marked is worse than no row.

## Decision records

For architectural decisions, record the decision, the alternatives considered, what each would have cost, and the reasoning. The alternatives matter as much as the choice: a future reader asking "why not X" should find X already considered rather than reopening it.

Record the decision's consequences too, especially the ones accepted deliberately. "This means a flip animation when opening from landscape, accepted as the cost of a frame that never moves" prevents that animation being filed as a bug later.

## Findings that belong to nobody's dispatch

Work turns up things outside its scope: a panel that reads files on the main thread, a test that has now failed three times, a script whose check is decoupled from what it checks. These need their own row rather than a sentence in an unrelated report.

A finding mentioned only inside a report about something else is effectively lost. The next person searching for it has no reason to look there.

## Citing documents that do not exist

Sometimes the record references something outside the repository — a planning document, a prior conversation, a decision made verbally. When you cannot cite it as read, say so: quote what the code comment claims it said, and state plainly that the document is not in this tree.

If a search establishes it never existed here, record that too. An unanswered "where is this" invites the next reader to repeat the search.
