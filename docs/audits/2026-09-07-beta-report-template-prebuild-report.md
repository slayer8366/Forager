# Pre-build report: a beta report template, before there are testers

**Dispatch:** "A beta report template, before there are testers to fill it in" (planner,
owner-directed). Documentation only. **Base confirmed: `main` at `49b65c7`** (the merge of PR #71);
branch `claude/new-session-102gri` restarted from it, working tree clean. **Nothing is built in this
commit** — the four report-before-building points, the GPX wording, the negative-result shape and
the length question are all answered below and stop there.

**Suite as found:** 1178 tests, 0 failed, 24 skipped, skip set byte-identical to the CI allowlist —
the last full run this session, on the tree that became `49b65c7` (the only commits after that run
were documentation). Nothing here touches code; the count is re-run and reported again when the
template lands.

---

## 1. Where it lives

**What the `docs/` tree already distinguishes** (read, not assumed):

- `docs/audits/` — dated, point-in-time records, "not a maintained document" by its own README; a
  later audit supersedes rather than edits. A template is the opposite: undated and maintained.
- `docs/qc/` — dispatch/pulse cycles for work on a task branch, corrected in place.
- `docs/plans/`, `docs/adr/` — engineering intent and decisions.
- `docs/*.md` at the root — the standing, maintained specs (`error-presentation-spec.md`,
  `motion-spec.md`, `phase-stack.md`).
- There is no tester-facing document anywhere in the repository today, and no template other than
  `.github/PULL_REQUEST_TEMPLATE.md`.

**Proposal: a new `docs/beta/` folder** — `README.md` (what the beta asks of a tester, how to get and
return a report, the privacy statement once), `trip-report.md`, and `device-report.md` if the owner
takes two templates (§3). A new class of document gets a new subject folder, which is the convention
`plans/`, `adr/` and `qc/` each followed; a maintained document does not go in `audits/`. **Index:**
`docs/audits/README.md` gets a row for *this* report (it already has one per dispatch report); the
template itself does **not** belong in that index — it is not an audit — and `docs/beta/README.md`
is its own index, the way `docs/qc/README.md` and `docs/plans/README.md` are theirs. The rejected
alternative is `docs/beta-report-template.md` at the root beside the specs: fine for one file, wrong
the moment there are two plus a how-to, and tester-facing prose does not belong shoulder to shoulder
with the motion spec.

## 2. The format

**Markdown is right for the repository and wrong for the thumb.** A tester on a phone will not
edit a `.md` file; they will paste something into a message. Tables are unreadable and uneditable
on a phone, checkbox syntax only works when rendered, and headings cost lines.

**Proposal: a Markdown file whose body is one fenced plain-text block**, so "copy the block" yields
exactly what gets pasted into an email, Signal, WhatsApp or a Google Form's free-text field and
filled in inline — one question per line, a short blank after a colon, no tables, no markdown
syntax inside the block, and each block short enough to fit a phone screen or two:

```
Battery at start: ___%   at end: ___%
Trip length: ___ h ___ min
Recording on? yes / no      Navigating back (arrow screen)? yes / no
```

That shape is usable on a phone because the tester never leaves their messaging app, and it is
usable for the owner because every report has the same lines in the same order. Yes/no questions
are written as `yes / no / didn't notice` so deleting two words is the whole answer.

## 3. One template or two

**Two, joined by a handle — proposed; the owner decides.**

- **Device report, once**: name or handle, phone make and model, Android version, whether it lives
  in a case with a magnet or clips to a magnetic mount (compass), whether Do Not Disturb is normally
  scheduled. Five lines, sent once.
- **Trip report, per trip**: handle, then the sections in §6. Device model asked on every trip is
  friction and invites "same as before", which is useless as data.

*Trade-off stated:* two documents means two things to find and a join key the owner has to keep
(a handle on every trip report). One document means the device block is either repeated per trip
or marked "first time only", which testers skip both ways. With a handful of closed-beta testers
the join is a list the owner already has. If the owner prefers one file, the device block goes at
the **bottom**, headed "first report only", and the trip block stays first so a returning tester
never scrolls past it.

## 4. How a tester gets it and returns it — options, no decision

The repository is **public** (checked against the GitHub API: `private: false`), issues are enabled,
and CI publishes the debug APK as a workflow artifact, not a release. That shapes the options:

1. **A copy-paste block sent with the beta invitation**, returned on the same channel (email,
   Signal, whatever the owner already uses with these people). Cheapest; private by construction;
   the template file in the repo is then the canonical source the owner copies from, not something
   testers visit.
2. **A file in the repo, linked from the invitation.** Works because the repo is public; but a
   tester opening GitHub on a phone to copy a block from a rendered Markdown page is friction, and
   it puts the tester-facing document next to the engineering tree, which is fine for the owner and
   odd for the tester.
3. **A GitHub issue form** (`.github/ISSUE_TEMPLATE/*.yml`): genuinely phone-friendly (dropdowns,
   yes/no, text areas), structured on arrival, and free. **But every report would be public on a
   public repository, and needs a GitHub account.** Terrain descriptions and battery figures are not
   sensitive; a tester's free text may be, and "closed beta, public reports" is a decision the owner
   must make deliberately, not one this template should slide into.
4. **A form service** (Google Forms or similar): phone-friendly, private, structured, and an
   external service handling testers' text — out of character for an app whose pitch is that nothing
   leaves the device, even though this is a person choosing to type into a form. The owner's call
   on whether that distinction survives contact with a tester's expectations.
5. **Carrying the template with the APK** (a text file beside the artifact, or release notes if
   the owner ever cuts releases): puts it where the tester already is, but the artifact is a CI
   download today, not a store page.

**Recommendation, if one is wanted:** option 1 as the channel and option 2 as the source of truth —
the file lives in `docs/beta/`, the owner pastes the block into the invite, reports come back on the
invite's channel. Stop.

## 5. The GPX wording — proposed, and stopping

Where the trip report invites a track file (only in the location-quality section, only when the
tester reports a spike or starburst, and marked optional):

> **Optional — a track file.** If the track looked wrong, the file the app can share for it is the
> most useful thing you could send. Before you do: that file (a `.gpx`) contains **the exact position
> of every point along your route, with the time of each**, and elevation. It shows precisely where
> you walked. Send it only if you are comfortable with that. A report without it is still useful —
> "spike, under fir canopy, standing still" tells us most of what we need.
>
> To share it: Journal → Records → Recorded Tracks → the share button on that track, then send it
> to us the same way you send this report.

Deliberately not said: that the file is "anonymous" (it is not), that the app "removes" anything
from it (it does not — the app strips EXIF from *photos*; the GPX carries everything a track has
except the accuracy figure), or any suggestion that sending is expected.

## 6. What the template asks — every dispatch question placed, with the negative-result shape

Preamble, once, in `docs/beta/README.md` and one line at the top of the trip block: *Forager sends
nothing anywhere. Everything in this report is something you type and choose to send. Please don't
tell us where you were — describe the ground and the sky instead ("dense fir canopy", "open
ridge", "car park").* And, one line: ***"No" and "didn't notice" are answers we need. They are not
wasted lines.***

**Battery** (all four owner fields; the negative result asked for by name):
`Battery at start / at end`, `Trip length`, `Recording on?`, `Navigating back (arrow screen open)?`,
and: `Did the battery bother you? no, nothing noticeable / yes — say what`. The wording makes "no,
nothing noticeable" a complete, valued answer rather than an empty one: the line exists to be
answered "no", and the percentages beside it turn that "no" into a number.

**Location** — `How often did you see "Last fix … ago" or "Location services unavailable"? never /
once or twice / often — and what was overhead (open sky / trees / buildings / in a vehicle)`;
`While it said the fix was old, did the blue marker on the map keep moving? yes / no / didn't look`;
`Did the recorded track show spikes or a starburst? no / yes — standing still or moving? what was
overhead?` and the GPX paragraph from §5 under the "yes".

**Compass** — `Did "Compass unreliable" ever appear? no / yes — what was near you (vehicle, fence,
power line, building, the phone's own case or mount)?`; `Did the heading ever seem plainly wrong
*without* that message? no / yes — describe`. The second is flagged in the template as the more
useful answer, in one clause.

**Off-track alert** — `Did the off-track alert fire? never navigated back / navigating but never
strayed / fired / strayed and it did NOT fire`. Only if it fired or should have: `Phone in a pocket
with the screen off? yes / no`; `Phone silenced? yes / no — felt the vibration anyway? yes / no`;
`Do Not Disturb on? no / yes — which mode (Priority / Alarms only / Total silence)`; `Roughly how long
after you strayed did it arrive?` **Deliberately without the "about twenty seconds" expectation**:
telling a tester the expected delay produces reports of the expected delay. The expectation stays in
the owner's reading notes, not in the template — flagged in §8 as a departure from the dispatch's
phrasing.

**Offline maps** — first line, the caveat the section cannot be read without: `Had you ever looked at
this area in Forager while you had signal? yes / no / not sure` with one clause of why (*a phone
that has had signal keeps map pieces it already showed, so "the map worked offline" can mean the
download or that leftover — we need to know which*). Then `Did you download a region? no / yes —
roughly how large (the km or mi on the slider)`; `Did the map show the detail you wanted when zoomed
in? yes / no`; `Anything blurred or stretched? no / yes — at what kind of zoom (whole valley / a
few streets / close in)`.

**Anything else** — `Did the app crash or vanish? no / yes — what were you doing`; `Was a recording
running when the app was killed or the phone restarted? no / yes — did it resume? (we expect no)`;
free text, with the dispatch's own sentence: *the most useful reports contain something nobody
thought to ask about.*

**Every question in the dispatch is placed.** None is dropped. Two are reworded away from
engineering vocabulary ("HUD" → "the arrow screen for navigating back"; "position marker" → "the
blue marker on the map"), and one loses its expected value (the twenty seconds).

## 7. Length — the thing that carries more weight

Counted from §6: **the full trip report is 19 questions**, plus the handle. Written as one line
each, that is about two phone screens — long enough that a tester who did a plain walk will skip
it, **unless the sections are conditional**, which is how §6 is built: four of the six blocks open
with a gate line (`never navigated back`, `didn't download`, `no crash`, `no spikes`) after which the
rest of the block is skipped. A plain walk with recording on answers **battery (5 lines), location
(3), compass (2), the three gate lines, and free text — about 13 short answers, most of them one
word** — three to four minutes. A trip where everything happened answers all 19, and that tester has
something to say.

**If the owner wants it shorter still, the cut order I would propose:** (1) drop the offline-maps
block from the per-trip report into a one-off "the first time you use offline maps" mini-report,
since most trips will not download anything; (2) fold the compass pair into one line with two
blanks; (3) keep battery and the location trio untouched — they are the reason the template exists.
Below about ten lines the report stops answering the questions that motivated it, and I would say
so rather than cut further.

**Owner addition (after this report was filed):** `docs/beta/README.md` must say that this cut
order exists and where it is, so whoever shortens the template later does not reinvent the
priorities. Taken: the README will carry the cut order itself, in three lines, not a pointer to an
audit — the person shortening the template will be reading the README, not the audits index.

## 8. Decisions this dispatch does not make — flagged, not taken

- **The expected off-track delay is left out of the tester-facing text** (§6). The dispatch says
  "about twenty seconds is expected and correct"; I read that as guidance for reading reports, and
  putting it in the question biases the answer. If the owner wants it in, it goes in.
- **"Device model" on a per-trip report** — resolved by §3's two-template proposal; if one template
  is chosen, it is asked once at the bottom.
- **Whether reports may be public** (option 3 in §4) is the owner's alone.
- **A "handle" as the join key** is a name the tester chooses; the template will say so, so nobody
  feels asked for a real name.
- **Fix-logging instructions**: not in the template, per the dispatch. I agree, with one note: the
  trip report's location block will produce exactly the reports that make a tester's fix log worth
  requesting, so the logging branch's instruction should point back at this template's terrain
  vocabulary rather than invent its own. Nothing added here.

## Required disclosure

**Confirmed:** the `docs/` tree's four conventions and the absence of any tester-facing document or
template (read and listed); the repository is public with issues enabled (GitHub API); CI publishes
the APK as a workflow artifact (`.github/workflows/ci.yml`); the app's track export goes through the
share sheet from Records (`ui/track/TrackExportPanel.kt`); the GPX carries positions, times and
elevation and no accuracy (`domain/GpxCodec.kt`, and the two findings documents already on `main`);
the suite figure is from this session's last full run.

**Inferred:** that testers will return reports by message rather than by editing a file — general
experience, not a measurement of these testers; the three-to-four-minute estimate.

**Could not determine:** how many testers, on what channel the owner already talks to them, and
whether any of them would object to a GitHub account or a form service — all of which decide §4.

**Premises in this dispatch that were wrong:** none found. One softened: "asking for the device
model on every submission is friction" is true and small (one line); the stronger reason for two
templates is that a repeated device line invites "same as before".

**Decided without cover:** nothing built; the departures in §8 are proposals awaiting the owner.
