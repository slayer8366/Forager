# 2026-09-26: recordkeeping moves to Claude-kit v0.2's protocols

This note marks the point where Forager's recordkeeping changes protocol, and
records the owner's ruling on where `merge` entries start. It is written under
the dispatch preserved as `prompts/preserved/2026-09-26-10.md`, recorded in
`RECORD.md` as intent `2026-09-26-17`.

## 1. When the protocols shifted

Recordkeeping moves to Claude-kit v0.2's protocols with PR #118, which installs
the kit: tag `v0.2`, the annotated tag object `a2b8025` pointing to commit
`b1bb0bd`. From then on the record kinds, the terminal and note outcomes, and
the checkers (`check_record.py`, `check_prompts.py`, with `check_kit.py` as the
drift check) are the kit's. So are the coder's rules in the vendored
`.claude/agents/coder.md`: the sweep, `merge` entries, `stopped`
dispatch-notes, and merges only after a backup.

## 2. Where `merge` entries start

`merge` entries start with the open PRs and move forward. A `merge` entry is
written for every merge into `main` whose merge commit is not an ancestor of
`af12a69` (`af12a69603ab38295099ef27f0b3114c2a9ccd74`, the PR #117 merge),
which was `main`'s tip when the kit was installed. That holds whatever order
the PRs merge in, and it includes the merge of PR #118 itself.

The PRs open into `main` when this note was written (`gh pr list --state open`,
2026-09-26): #101, #104, #109, #111, #112, #113 and #118.

## 3. No backfill

Merges into `main` up to and including `af12a69` get no `merge` entry. With no
`merge` entry in the record, `coder.md` item 1 would have the first sweep write
one for every merge commit on `main`'s first-parent chain. Instead, the first
sweep stops at `af12a69`. This note is the owner's ruling that sets that limit.

## 4. No history changed

Every entry written before the shift stands as written, in Forager's pre-kit
forms. That includes the 2026-09-26 entries that recorded stopped coder
dispatches as an intent plus an `abandoned` terminal (`2026-09-26-02` with
`-03`, `-04` with `-05`, `-06` with `-07`, and `-08` with `-09`), where the kit
would now use a `stopped` dispatch-note. None of them is converted, re-dated or
rewritten.

## 5. Source

The owner, in the planner session on 2026-09-26:

> Start with the open PRs moving forward. Write a note stating this is when
> recordkeeping protocols shifted. No history should be changed.

This answers the question raised in terminal `2026-09-26-14` and in intent
`2026-09-26-15`'s Notes: how far back the kit's first sweep writes `merge`
entries. Dispatch: `prompts/preserved/2026-09-26-10.md`. Intent: `2026-09-26-17`.
