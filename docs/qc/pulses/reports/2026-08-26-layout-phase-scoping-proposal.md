# Scoping proposal: a layout phase after Understory

**Date:** 2026-08-26
**From:** coder session on `claude/forager-m3-expressive-design-l4c` (PR
[#44](https://github.com/slayer8366/Forager/pull/44), open against
`claude/l4c-serialized-editing-state`)
**For:** the planner, as input to a dispatch — this is not itself a plan
**Unsolicited:** no pulse prompted this. It exists because implementing
`docs/plans/understory-design-system.md` surfaced a gap in that document's own
scope, and the owner agreed on 2026-08-26 that the gap should become the next
phase.

Read against `claude/forager-m3-expressive-design-l4c` at `77b28b7`, which is
`claude/l4c-serialized-editing-state` at `353d256` plus this branch's work.
Every count and line number below was re-measured for this document; where an
earlier document of mine got one wrong, the correction is called out rather
than silently applied.

---

## 1. The gap

`understory-design-system.md` is a token, motion and component pass over a
layout it treats as already correct. **No step in it moves anything.**

Its step 5 says so directly:

> The full-bleed map, six-tab bottom nav, right-edge icon stack and full-width
> compass strip all stay; two hardware rounds have already tuned them. What
> changes is finish.

Steps 1–4 are colour roles, `MaterialExpressiveTheme`, `MapPalette`/`Spacing`,
and the motion rewrite. Step 6 is predictive back. The one structural change
ever proposed — a `NavigationRail` on medium windows — is a recorded deferral,
not a step.

That scope is defensible: compact's arrangement was tuned across two hardware
rounds. But the design brief that produced the document was "changing the
current design entirely", and a token pass is a narrower reading of that than
the owner may have intended. The gap is now recorded in the design document
itself under "The phase after this one: layout".

## 2. Why this phase comes after, not before

Three reasons, offered as argument rather than instruction:

1. **Otherwise the finish pass is done twice.** Step 5 swaps hand-built
   components (`MapIconStack` → `VerticalFloatingToolbar`, a three-way dialog →
   `FloatingActionButtonMenu`, chips → `ButtonGroup`) into existing positions.
   Move the positions afterward and the swap is redone against a layout that
   no longer exists.
2. **Gate G is what produces the evidence.** Nobody has seen this app with a
   complete token layer on hardware. `map-redesign.md` already records one
   arrangement changed on reasoning and corrected twice on hardware; choosing
   what to rearrange before looking at the tuned version repeats that.
3. **The deferred findings are already layout-shaped** and have no other phase
   to belong to — see §4.

## 3. The prerequisite the planner has to price

**`AvailabilityScreen.kt` is what makes layout work expensive**, and this is
the constraint most likely to determine the phase's shape.

| Measure | Value | How measured |
|---|---|---|
| Lines | 4,830 | `wc -l` |
| Composables in this one file | 68 of the app's 115 | `grep -c '^@Composable'` |
| Entry composable signature | lines 347–484 | paren-depth scan |
| Top-level parameters | **60** | 4-space-indented `name:` lines in that span |
| …of which `on*` callbacks | **45** | same, filtered |
| Test files naming it | 15 | `grep -rl` over `app/src/test` |

> **Correction to earlier documents.** `understory-design-system.md`, PR #44's
> description and several commit messages on this branch said "45 parameters,
> 41 callbacks". That was an undercount: the measuring window stopped at line
> 430 and the signature runs to 484, missing 15 parameters. The design document
> is corrected as of `77b28b7`; the commit messages stand as written and are
> wrong on this point. The true figure is worse than the one previously cited,
> which strengthens rather than weakens the case below.

Steps 1–6 barely disturb this file — they change colours and specs and swap
components in place — which is exactly why tag 06 could be named and left alone
in the design document. A layout rearrangement is the opposite kind of work.

**This proposal does not decide whether the phase opens with a split.** That is
the planner's call. It asks only that the decision be explicit, because the
alternative is paying the tax silently on every task in the phase.

## 4. Findings already waiting for this phase

Three, all recorded, none actionable in any current step.

| Finding | Where recorded | Status |
|---|---|---|
| **Medium windows give 360dp of a 600dp window to a permanent drawer** — 60% of the screen to controls set less than once per search. Fix is a `NavigationRail` with the drawer becoming modal. | `map-redesign.md`, "Known defect in the untouched path" | Deferred by owner decision 2026-08-25. Binding reason: the project builds and tests on a phone with no emulator, so this is the change that most needs hardware and is least able to get it |
| **Where `EXPANDED`'s six destinations live.** List/Maps/Seasonal are a `SecondaryTabRow` over the content pane; Journal/Album/Settings are sticky footer rows *inside* the permanent drawer's search panel, and opening one replaces the search controls in place. | `map-redesign.md`, "Open question, EXPANDED" | Recorded as four unanswered questions. The deciding one: is the drawer carrying three destinations deliberate, or an artifact of the compact bottom-nav change never being carried across? |
| **Search is reachable only from the Maps tab** in compact — no quick-search affordance from List/Seasonal/Journal/Settings. | `map-redesign.md`, pre-existing | Flagged by the original author as "not decided here" |

## 5. Constraints a dispatch must respect

Settled decisions and standing rules that bound this phase. Reopening any is a
stop-and-ask, not a judgment call.

- **The map icon stack is fixed at exactly five icons** (`map-redesign.md` §3,
  owner decision). A sixth is a scope reopening.
- **`map-redesign.md`'s compact-only scope** explicitly rejects extending the
  redesign to all three width classes, with reasoning. Any medium/expanded work
  supersedes that decision and must say so.
- **Anything drawn over the map ships with a Robolectric long-press test.**
  `CLAUDE.md` carries this outright; the project has hit it three times (the
  compass strip, the return-to-vehicle row, a `horizontalScroll` at zero scroll
  range). No `Surface` over the map, no scroll modifier at any scroll range,
  and every container width-bounded unless full width is deliberate.
- **Room for data that relates, DataStore for flat settings** (`CLAUDE.md`).
- **A globally-unique value must be checked against the base's current state**
  (`CLAUDE.md`) — relevant if this phase touches schema or migration numbers.
- **Gate G** — the owner-run device gate in the design document — blocks step 5
  of Understory and therefore blocks this phase's start.

## 6. Questions for the planner, deliberately unanswered here

1. Does the phase open with splitting `AvailabilityScreen.kt`, absorb the cost,
   or split incrementally per task?
2. Is it compact-only (superseding nothing), or does it take the medium and
   expanded findings too (superseding `map-redesign.md`'s recorded scope)?
3. Does the medium-window rail get built without a medium-width device, given
   that this was the binding reason for deferring it?
4. Is "changing the design entirely" satisfied by a finish-and-tokens pass plus
   this phase, or is a genuine re-arrangement of compact wanted? The three
   findings in §4 do not by themselves justify moving the map, the bottom nav
   or the compass strip.
5. Does this phase wait for Understory to *merge*, or only for Gate G?

## 7. State of the branch this comes from

Landed and CI-green (run 136, `5b7023f`): the seven previously-unset colour
roles with `ThemeCompletenessTest`/`ThemeContrastTest`, and the map day/night
palettes with `MapPaletteTest`.

Pushed, CI not yet confirmed: the civil-twilight trigger and basemap dimming
(`02aafdf` — its own run was cancelled by a subsequent push; `77b28b7`'s run
covers the same code).

Not started: Understory steps 2 and 4 (the `1.5.0-alpha26` pin, the motion
rewrite), the long-press night override, and Gate G.

**`.github/workflows/ci.yml` carries two temporary trigger entries that must be
reverted before merge**, in the same shape and for the same reason as
`d3c4b97`.

## 8. What is unverified

- **Nothing in this proposal has been on hardware.** No layout claim here is
  backed by having looked at the app on a device; they are code reads.
- **The medium and expanded findings are unverified at their own widths.** The
  project has no emulator and builds on a phone. The 360dp figure is read from
  `DRAWER_PANEL_WIDTH`, not measured on a 600dp screen.
- **Whether the day marker palette is a real legibility problem is open.** It
  fails WCAG's 3:1 non-text bar against a modelled pale-topo reference
  (waypoint 1.79:1) but shipped and was hardware-tuned. `MapPaletteTest` pins
  it as a ratchet and blesses neither reading. Relevant here only because a
  layout phase may move markers onto different grounds.
- **This session could not compile or test locally.** The environment's network
  policy refuses `dl.google.com`, so every verification quoted above came from
  CI or from arithmetic, never from a local build.
