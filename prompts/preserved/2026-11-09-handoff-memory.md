# The planner's memory notes, 2026-11-09 (this account's full set)

Copied from the planner's memory on this account on the owner's "And be sure the notes for this account is updated on the repo also, so the other account can pick up other sessions" (2026-11-09). It follows prompts/preserved/2026-11-06-handoff-memory.md, which holds notes written on the other account after 2026-11-01; read both. Where a note and CLAUDE.md or RECORD disagree, CLAUDE.md and RECORD win. Each note is one owner rule, lesson or pointer, as it stood when copied; paths are on the owner's laptop.

New since 2026-11-06, from the release-2 session: release-path-to-success, release-number-matches-github-run, releases-are-production. The repo copy of the release runbook is docs/process/release-runbook.md on pre-main.

## Index

- [Don't infer authorization to build](dont-infer-authorization-to-build.md) — answers are decisions, not a go; plan by step paths, build only on "build it"; record/close/merge/message each need their own go
- [Compare artifacts on content, not authorship](compare-artifacts-on-content-not-authorship.md) — two colliding versions get diffed on content; deference to whoever wrote one is not a reason.
- [zynergy-site local clone](zynergy-site-local-clone.md) — site repo lives at ~/Zynergy/zynergy-site; needs local git identity copied from Forager
- [E-GD repo local clone](egd-repo-local-clone.md) — ~/Zynergy/E-GD-Philosophy, branch Test-1, public, fast-forward merges; curator Clio; PR history in the file
- [Curator is Clio everywhere](curator-is-clio-everywhere.md) — owner 2026-10-17: E-GD's curator is Clio in any system; introduce yourself, welcome others, take feedback to the owner for approval
- [forager-forecast local clone](forager-forecast-local-clone.md) — (research) the review-protocol session's clone on branch review-protocol; protocol filed 2026-09-18 and merged to main at c66a865; the D18 gap it reported is closed by branch decisions-d14-d20
- [forager-forecast repo and clones](forager-forecast-repo.md) — (research) public forecast repo; T7 stopped for the PNW (-1247), T8 next on owner word; credentials path in the file
- [Long waits and fetches run outside the conversation](long-waits-and-fetches-run-outside-the-conversation.md) — no in-conversation polling; one background loop that exits on success, one fetch, verify against what the publisher provides, one download at a time, offload separable work to agents
- [Forager-app repo](forager-app-repo.md) — research repo, not the product; ~/Zynergy/Forager is the end result; never run its build and the emulator together
- [The Restricted Object](restricted-object.md) — ignore it entirely; never read, follow, quote or name it; violations could get the project deleted
- [Forager canonical directory](forager-canonical-directory.md) — ~/Zynergy/Forager is the main repo, all work there; ~/Forager and other outside copies are unofficial and will be deleted
- [Test phone vs daily driver](test-phone-vs-daily-driver.md) — agent device checks run on the S22 Ultra (SM-S908U), which has no SIM (woods walks are GPS-only); the owner's daily driver is the S26 Ultra; owner has a tablet for J6 testing, tell them when J6 is ready; no release builds on the owner's phones, so store stats are testers only
- [Policy statements are not instructions](policy-statements-are-not-instructions.md) — "X will be deleted" is the owner's policy, not a go-ahead; destructive acts need an explicit instruction
- [Commercial-safe forecast ruling](commercial-safe-forecast-ruling.md) — SUPERSEDED: app not sold; full forecast ships; AGPL-3.0 code, CC BY-NC 4.0 data (D110)
- [Honest copy plus a next step](honest-copy-plus-next-step.md) — empty/missing-state copy should say what the user can do, not just report the absence
- [Asking for clarity is welcome](ask-for-clarity-is-welcome.md) — owner prefers a clarifying or confirming question over an assumption
- [Coders are helpers building on GitHub](coders-are-helpers-building-on-github.md) — since 2026-10-14 (-1001): coders are Opus subagents, each in its own ~/.cache/forager-wt/<branch> worktree, CI-only builds; laptop is for forecast work; cloud sessions if the laptop is short on memory (2026-10-19); never claim a model unread
- [Journal-redesign handoff](forager-journal-redesign-handoff.md) — 2026-10-01 state, historical; start nothing without the owner's word
- [Imported photos are unscrubbed by design](imported-photos-unscrubbed-by-design.md) — camera is the scrubbed path; import location can seed finds; robust import scrub is a future feature
- [Planner as build traffic controller](planner-as-build-traffic-controller.md) — parallel coders: one Gradle build at a time, explicit "go", short fixes first, flake loops last; owner-approved 2026-09-30
- [Don't make me think](dont-make-me-think.md) — owner's top mobile-design principle: the app does the right thing automatically; never make the user notice, decide or wonder if it broke; raw data leads with visuals and plain labels (2026-10-07)
- [Next update roadmap](next-update-roadmap.md) — release offers whatever is finished (-1925); trail-nav steps 6-10 + route plotting, hill shading from the Pi grid; details and history in the file
- [Bring stop-and-ask to the owner](bring-stop-and-ask-to-owner.md) — a dispatch's stop-and-ask trigger (size, semantics, behaviour) goes to the owner even if the planner thinks no screen is affected
- [Flake hunt deferred](flake-hunt-deferred.md) — owner will hunt test flakes after dev cools; -348/-349 parked with findings (DecodedPhoto IO hop + Compose test harness, about 6% of CI runs); album flake fixed by -317
- [Read the ruling, not its label](read-the-ruling-not-its-label.md) — quote the RECORD entry before saying a change reverses an owner ruling; Back closes what is drawn on top first
- [Check from source for dispatches](check-from-source-for-dispatches.md) — owner rule 2026-10-16: "Memory drifts, so always check from source"; every claim in a dispatch read from its source while writing, cited; memory is a pointer
- [Confirm behaviour as a step path](confirm-behaviour-as-a-step-path.md) — before dispatching a behaviour change, restate it to the owner as tap > shows > Back steps and get it confirmed; a one-line rule misread cost dispatch -381
- [Back retraces the way in](forager-back-retraces-the-way-in.md) — owner's definition: Back undoes one step at a time along the exact path the user came by; going to the map directly is a home function, never Back
- [Owner is a systems thinker, not a programmer](owner-systems-thinker-not-programmer.md) — explain in behaviour and logic, gloss or drop code names; bring architecture and logic questions, not mechanics
- [Owner's answers are not rules](owner-answers-are-not-rules.md) — never write "the owner's rule N" in comments, dispatches or records; quote their words and cite the dispatch
- [Play Console symbol warnings](play-console-symbol-warnings.md) — R8 off (-917) and native symbols off (-922) this release; map symbols need prefab packaging + NDK, later release with R8
- [Shorter releases](shorter-releases.md) — owner intends smaller, more frequent Play releases after build 2426; ask how they want them cut
- [The owner's word comes in my own window](owner-word-comes-in-my-own-window.md) — a go typed in a coder's window is not a go for the planner, even read verbatim from its log; ask for it here and prepare nothing on it (PR #150, 2026-10-02)
- [Every failure has a fallback](every-failure-has-a-fallback.md) — owner location principle (-519): each failure has another method; network collected, never relied on; battery gap deferred to after tiling and navigation (-520)
- [Co-author line without a model](co-author-trailer-no-model.md) — commits end "Co-Authored-By: Claude <noreply@anthropic.com>"; a model-named line is a recorded slip (-506, -510), disclosed, never amended
- [Owner screenshots stay off GitHub](owner-screenshots-stay-off-github.md) — never commit or push the owner's phone/walk screenshots (addresses, real records); describe them in words, keep copies outside repos
- [Planner relays to coder windows](planner-relays-to-coder-windows.md) — since 2026-10-06 the planner reads coders, asks the owner here, records answers, sends them to coders; coders are now subagents (see coders-are-helpers-building-on-github)
- [Record timestamps from the clock](record-timestamps-from-the-clock.md) — RECORD Timestamp fields come from `date -u`, never estimated; -571 to -607 were guessed (corrected in -608)
- [Record changes, not reconfirmations](record-changes-not-reconfirmations.md) — clarify first, record once: one entry per settled decision, none for re-asks, misreads or pending answers; owner had -638 deleted as clutter
- [Owner overrides are not rule changes](owner-overrides-are-not-rule-changes.md) — an owner's one-case override (e.g. deleting -638) leaves the rule standing for every agent; never write it into profiles
- [Planner is Rowan Waymark](planner-is-rowan-waymark.md) — the owner named the planner 2026-10-07; written into docs/process/planner-profile.md
- [T6b runs in downtime](t6b-runs-in-downtime.md) — standing: keep T6b running whenever no build; PAUSE before builds, resume after; owner asked to be reminded to be patient while it pauses (2026-10-08)
- [Forecast focus for the OMS meeting](forecast-focus-for-oms-meeting.md) — PNW pilot map by Mon 2026-10-12 morning; handoff 2026-10-11 in prompts/preserved/2026-10-11-planner-handoff.md
- [Laptop disk layout](laptop-disk-layout.md) — since 2026-10-10 ~/Zynergy, ~/.gradle, ~/Android on /mnt/work; remount the T6b flash drive after a restart; flash extras in ~/from-flash (root drive, since 2026-10-15; old path is a symlink)
- [Parallel tracks under a deadline](parallel-tracks-under-deadline.md) — split work around the critical path, review in parallel, fill idle time; owner confirmed 2026-10-10
- [Production release scope](production-release-scope.md) — SHIPPED 2026-10-19 as 3500 (-1367); after the -830 beta: tidiness + Android lint round; 2026-10-12 (-917): one small release, R8 off, native symbols off (-922), lint survey dispatched (2026-10-12-01); D4/D5 from its report
- [Describe products generically](describe-products-generically.md) — writing rule for every output; licence credits written as licences require
- [Give times in Pacific](give-times-in-pacific.md) — chat times in Pacific (PDT until 2026-11-01, then PST), not UTC; RECORD stays UTC
- [Main is production, gated promotion](main-is-production-gated-promotion.md) — since 2026-10-16 (-1086) main changes only by a promotion from pre-main: CI, phones, UX auditor, owner's approval; small tasks and big features into pre-main; experiments on dev (main + experiments)
- [Dev branch holds dev tools](dev-branch-holds-dev-tools.md) — since -930 main has no dev tools; `dev` = main + restore commit + running experiments, rebased after every main update (-1087)
- [Mobile design-system plugin](mobile-design-system-plugin.md) — greenfield-only, does not load as a skill (UUID name); read its files directly; its design-principles reference is the usable part for reviews
- [Question previews not on phone](question-previews-not-on-phone.md) — owner answers from a phone that hides option previews; draw mockups in the message text
- [Ask with the question tool](ask-with-the-question-tool.md) — owner wants decisions asked via AskUserQuestion ("walk-through"), which notifies them; sketches in the text (2026-10-18)
- [Record hygiene pending](record-hygiene-pending.md) — owner wants a future pass removing decision entries that were only explanations; "eventually", no go yet
- [Future ID system direction](future-id-system-direction.md) — no-ID rule stands; later a companion ID app (deterministic, AI only detects features) that also does iNaturalist uploads; near term Forager shares finds to the iNaturalist app (2026-10-13)
- [Credit reused work](credit-reused-work.md) — anything used from Alan Rockefeller (or others) is credited in the same change, alongside the other credits
- [Camera never rotates](camera-never-rotates.md) — cardinal rule: the camera screen never rotates on any device, only icons turn; separate "setting goes" from "behaviour changes" when restating
- [Undo means the last step](undo-means-the-last-step.md) — "undo what you did" scopes to the interrupted action only; ask if unclear (2026-10-14, PR #227)
- [RAW camera facts](raw-camera-facts.md) — measured 2026-10-15/16 on S22 and S26: platform RAW bursts 30 fps, CameraX ~4 fps; RAW binned 12 MP, untouched by phone NR; RAW pins the main sensor; engine A chosen 2026-10-16 (-1102), CameraX as fallback; all four back lenses give RAW on both phones (-1095, -1101)
- [RECORD follows RECORDING.md](record-follows-recording-md.md) — since -1111 full fields per kind; append-only: new entry, never edit (-1540, 2026-10-21); skipping whole redundant entries is fine (owner)
- [Auditors on a different model](auditors-on-a-different-model.md) — since 2026-10-16 (-1134): auditors and reviewers on Fable (coders are Opus); never the coder's own model; record which model ran it
- [Forager team names](forager-team-names.md) — Rowan (app planner), Alder Bearing (forecast planner, 2026-10-18), Aspen (Pi planner), Willow (Pi coder), Hazel (app coder), Ash Transect (forecast coder), Elder (UX auditor), Sorrel (reviewer), Juniper (researcher)
- [Helpers by name, not pronoun](helpers-by-name-not-pronoun.md) — say "Hazel", not "she"; owner: "Hazel rather" (2026-10-17)
- [iOS port report location](ios-port-report-location.md) — 2026-09-11 feasibility report only on branch claude/ios-port-feasibility-mvsjcr; owner raises iOS after production release
- [Planner/coder separation](planner-coder-separation.md) — E-GD: whoever writes the code never writes the plan; I broke it dispatching Ash to write T8 plan (-1248)
- [Record ID blocks](record-id-blocks.md) — Forager RECORD IDs stay < 2000; forecast record (Alder, branch forecast-record) uses -2001 up
- [Planner writes no code](planner-writes-no-code.md) — owner 2026-10-19 (-1376): code and scripts (even small local ones) go to coders; plans, dispatches, RECORD stay with the planner
- [Check for a newer planner after reconnect](check-for-newer-planner-after-reconnect.md) — after a restart/dropped link, ListAgents + check pre-main and records for commits not yours before acting; two planners collided 2026-10-20 (dup -1421)
- [Modular code from the start](modular-code-from-the-start.md) — owner 2026-10-20 (-1490): skill on pre-main; <400-line new files, split >800 before adding, file ownership per dispatch; "A few minutes of extra care can save two days of audit"
- [No schema downgrade on phones](no-schema-downgrade-on-phones.md) — older-build installs: compare DB schema versions first; debug builds wipe the database on downgrade (S22 wiped 2026-10-21, -1526)
- [PR numbering offset](pr-numbering-offset.md) — owner 2026-10-21: in my notes the next PR is #371; notes = GitHub − 4 from GitHub #375 on; always use the real number for gh/API
- [Aspen in forager-cartography](aspen-pi-planner-in-cartography.md) — since 2026-10-30 on a separate account (ask the owner how to reach); Pi/map planner; Pi records in Forager-cartography, build code still on Forager origin/pi-build; .rd5 only on the Pi; use the newest address Aspen gives
- [Record entries short](record-entries-short.md) — decision + owner words + the few facts behind it; no narrated tests or investigations; -1554 draft removed as wordy (2026-10-22)
- [Worker deploys](worker-builds-disabled-until-promotion.md) — since 2026-10-24 (-1609) the Worker builds from main automatically (live a5c79d5c, rollback 1a85a35e); wrangler only in an emergency, on the owner's word
- [Handoff before context runs out](handoff-before-context-runs-out.md) — owner 2026-10-24: near-full context ("98%") → helpers commit and push, then write the handoff; err early, no exact % visible
- [No chevrons in the app](no-chevrons-in-the-app.md) — owner 2026-10-28: chevrons on drop-down/fold lists banned; photo viewer arrows stay
- [BRouter Kotlin port](brouter-kotlin-port.md) — owner porting BRouter to Kotlin for iOS (2026-10-28); routing work carries on behind the interface; repo forager-ios, another agent on it
- [Aspen talks through the repo](aspen-talks-through-the-repo.md) — since 2026-10-30 (-1832): messages as files in prompts/aspen/ on records-after-173, one per file, never edited
- [Warn before driving the phone](warn-before-driving-the-phone.md) — say before an S22 check that the phone will move on its own; owner nearly took it for a bug (2026-11-07)
- [Local coder models planned](local-coder-models-planned.md) — owner 2026-11-08: local 32B/14B coders soon, Claude as planner/reviewer/auditor; open setup points in the file
- [Sorrel reviews before the owner sees](sorrel-reviews-before-owner-sees.md) — -2190: Hazel green → Sorrel → S22 (visual) → owner's build → merge word; defects go back to Hazel
- [Active listening, step by step](active-listening-step-by-step.md) — owner 2026-11-08: every explanation repeats their request back in my own words as numbered steps of what the user does and sees
- [Ask record/build as checkboxes](ask-record-build-as-checkboxes.md) — owner 2026-11-07: after each settled decision, one multi-select question (Record it / Build it / Hold / other actions); do only what's ticked
- [Planner notes in the hand-off memory file](planner-notes-in-handoff-memory-file.md) — notes after 2026-11-01 (owner-word-stands, home-location-private, trailer, revert-undo...) live in prompts/preserved/2026-11-06-handoff-memory.md on records-after-173; read it at start
- [Trail Map in place until release](trail-map-in-place-until-release.md) — owner 2026-11-06: v4 may change in place while nobody uses Trail Map; once it ships this release, style changes are new versions
- [Release number matches GitHub run](release-number-matches-github-run.md) — absolute owner rule, hard gate (2026-11-09): release N = run #N of play-production.yml (since 2026-11-09); release 3 = next fresh run on updated main; never dispatch it for anything else
- [Release path to success](release-path-to-success.md) — runbook from release 2 (5026): S22 walk on CI debug APK first, notes, dispatch play-production.yml, owner approves; stumbles to skip; open: test signed build before publish
- [Releases are production](releases-are-production.md) — owner 2026-11-09: "Never ask. Always assume production"; any release path to another track is a defect
- [See it on the phone before fixing](see-it-on-the-phone-before-fixing.md) — a problem known only from a simulated test is unproven: screenshot on a real device first, and say where the figure came from (2026-11-01, -1920)

## The notes

### dont-infer-authorization-to-build (dont-infer-authorization-to-build.md)

*Carry on" and similar continuation signals are not authorization to produce a deliverable the owner said they were writing — ask instead*

When the owner says they are writing something ("before I write it", "Writing v4")
and then sends an ambiguous continuation signal like "Carry on", that means resume
the conversation — not that I should produce the artifact. Ask.

**Why:** On 2026-09-16 I read "Carry on" as authorization and wrote and pushed a
device-check v4 the owner was already writing. Two different v4 documents then
collided at the same path, one of them pushed, which cost a reconciliation the
owner had to arbitrate. The owner's clarification was explicit: "No, carry on meant
continue after the stop. It wasn't authorizing anything."

**How to apply:** The ambiguity that matters is about authorization to *create or
commit*, not about task details. A continuation signal resolves "keep talking"; it
never resolves "and produce the thing". Where a previous message said the owner
would write it, treat that as standing until they say otherwise. See
[[compare-artifacts-on-content-not-authorship]] for what to do once a collision
already exists.

**Second instance, 2026-10-14:** the owner said "Write up stage 2" (RAW). Then, to a design question ("show the format chip now, or hide it until stage 3?"), they answered "Show it now". The planner took that as a go and dispatched a coder. The owner: "I didn't authorize anything. Undo everything done in stage 2." So the branch, the PR, the record entries and the dispatch were all deleted, and the records branch was rewritten at their word.

**The rule:**
- "Write up X" means write the document and stop.
- An answer to a design question inside a write-up is an answer, not a go.
- Before starting any coder, ask a separate, explicit question ("Start a coder on X now?") and get a yes to it.

**The owner's standing rule, verbatim, 2026-10-14:** "Yes no build occurs until I explicitly say so". No coder is started, and no build-producing work is dispatched, without the owner's explicit go for that build. Write-ups, plans and read-only checks are not builds.

**Third instance, 2026-11-07:** answers to a coder's pre-build questions were recorded and relayed to the coder as instructions; earlier the same day bug-fix coders (I9, I10) were started and a PR (#506) closed on answers alone. The owner: "Do not record anything yet", "Stop", "I never said to act." Both record commits were erased from history and locally at their word. The owner then: "Read the EGD repo" (E-GD bootstrap: "build, merge, delete, send or start another agent only on an explicit go for that action"), "Walk it back and make a plan with me", and **"And then on my go, I'll say build it. Until then, hold"**.

**How to apply now:** plan with the owner by step paths until they say the plan is right; then ask separately whether to record it; start a coder only when they say **"build it"**. Closing, deleting, merging, recording and messaging coders each need their own go too.

### compare-artifacts-on-content-not-authorship (compare-artifacts-on-content-not-authorship.md)

*When two versions of a document both exist and both are correct, decide between them on content — never defer to the owner's version because they wrote it*

Offering to replace my work with the owner's because it is theirs is deference, not
a finding. When two artifacts collide, send both, diff them on content, and land the
better one — whoever wrote it.

**Why:** On 2026-09-16 two device-check v4 documents collided. I proposed overwriting
mine with the owner's on the grounds that "yours is the one that should exist". Their
ruling: "Overwriting a committed, correct document with a different correct document
because of who wrote it is not a reason. It's the same error as spending a superseding
note on coordinate drift: it trains the record that authorship is an event." They then
diffed, kept mine on operational grounds, discarded their own, and merged the single
clause of theirs that was better.

**How to apply:** Send the committed artifact in full so they can diff it. Volunteer
the defects in my own version first. The outcomes are: mine has nothing theirs lacks
(discard mine), mine has specific things theirs lacks (land those as deltas with
reasons, not a wholesale replacement), or they diverge enough that a merge is better
(then the commit message argues content, never provenance). Related:
[[dont-infer-authorization-to-build]].

### zynergy-site-local-clone (zynergy-site-local-clone.md)

*Where the zynergy-labs.com site repo (slayer8366/zynergy-site) is cloned on this machine and how it was set up*

The zynergy-labs.com site repo is cloned at `/home/zynergy-labs/Zynergy/zynergy-site`
(from `git@github.com:slayer8366/zynergy-site.git`), next to the Forager checkout.
Cloned 2026-09-18 to land the mushroom forecast planning pack under
`Forager/mushroom-forecast/`.

**Why:** No global git identity exists on this machine; the Forager checkout carries
its identity in its own `.git/config`. The site clone got the same identity copied in as
local config, otherwise commits fail with an empty `user.name`. Cloudflare Pages
deploys this repo from its root and reports a "Cloudflare Pages" check-run on each
commit, so a branch push produces a preview build.

**How to apply:** Work on site changes in that clone, on a `claude/...` branch, and
push the branch; a fresh clone elsewhere needs the identity set again before it can
commit. See [[dont-infer-authorization-to-build]] before running any of the pack's
T1 to T3 dispatches: landing the pack was authorised, running them was not.

### egd-repo-local-clone (egd-repo-local-clone.md)

*E-GD-Philosophy clone at ~/Zynergy/E-GD-Philosophy (default branch Test-1, PUBLIC); merges fast-forward; checkers and lookup tool generalized for any repo (PRs #12 to #19 merged by 2026-10-17 23:31Z, Test-1 6fa9d68)*

E-GD-Philosophy (github slayer8366/E-GD-Philosophy, public, default branch `Test-1`) is cloned at `~/Zynergy/E-GD-Philosophy`. The clone had no git identity; Forager's local `user.name`/`user.email` were copied into its local config on 2026-10-16 (same fix as [[zynergy-site-local-clone]]).

- Changes there follow its own CLAUDE.md: intent entry in RECORD.md before any file is touched, the governing prompt preserved under `prompts/preserved/`, terminal entry, then `check_glossary.py`, `check_record.py`, `check_prompts.py` (wrapped with `check_register.py`) before the final commit.
- Merges into Test-1 are fast-forward (lands exactly the checked commits). Since PR #15 (record 2026-10-17-11/-13) the history walk handles merges by PROTOCOL rule 13: base + each branch's additions whole, nothing written inside a merge; it reports every violation and goes on.
- PHILOSOPHY.md lines starting `**X:**` are parsed as headwords and whole-line bold in ENFORCEMENT.md as entries; RECORD.md lines starting `**X:**` are fields.
- 2026-10-16: curation from Forager, PR #1, fast-forwarded into Test-1 at 57af78f on the owner's go (record entries 2026-10-16-01 to -04). The owner's account of the Forager hook removal: hooks hindered actions agents are expected to perform and made the operator hold the agent's hand; since then "philosophy, precise methodology, and evidence gating drive the agent" (PROTOCOL rule 21). Forager's own 2026-09-27 note still says only weekly limits; changing it is the owner's call.
- 2026-10-16: RECORDING.md (Forager's recording method, twelve entry types kept as Forager used them, backups, index, five report disclosure sections, partial outcome) PR #2 (record entries -05/-06), fast-forwarded into Test-1 at 5b9ffdd on the owner's go, 2026-10-16. Checker update done on the owner's go ('Update both formats to match current standards', minimal required fields): PR #3 (record entries -07/-08, check_record.py --render-check 38 checks), fast-forwarded into Test-1 at f120944 on the owner's go, 2026-10-16. Forager's new RECORD.md header text is in E-GD RECORD 2026-10-16-08; the Forager planner places it, on the owner's word in its own window (owner's answer 'The planner writes it').
- 2026-10-16: 'Check from source, not memory' (owner's rule for writing dispatches) added to ENFORCEMENT Complete Task Prompts, BOOTSTRAP planner line and skills/dispatch-authoring; PR #4 fast-forwarded into Test-1 at 6ceefbf (record entries -09/-10). Forager planner-profile line handed to the owner for its planner; text in E-GD RECORD 2026-10-16-10.
- 2026-10-16: records lookup tool spec (option A: read-only lookup over Forager + forager-forecast records; lives in E-GD; next-id reports only) approved and merged, specs/2026-10-16-records-lookup.md, PR #5, Test-1 84397ea (record -11 to -14). Forecast START_HERE.md session log added as seventh source, prose excluded (owner: 'Log only'; record -15/-16, Test-1 ec31535). Building needs its own go.
- 2026-10-16: monthly record layout (records/YYYY-MM.md, RECORD.md as index, month from Timestamp not ID; owner: one file per entry excessive) in RECORDING.md; lookup spec reads monthly files; PR #6, Test-1 f6f21a9 (record -17/-18). Forager split steps, revised RECORD.md index text and monthly header are in E-GD RECORD 2026-10-16-18 for the Forager planner (merge pre-main into records-after-173 first: dc4ef1c1 edited 2 RECORD.md lines there). Checker reading monthly files: code, on its own go.
- 2026-10-16: the E-GD curator is named Clio Ledger (owner's pick from three drawn from E-GD's scope); profile at profiles/curator.md, written from E-GD's record with every practice cited; PROTOCOL rule 22 (roles name themselves from project and scope, profiles from the record). PR #7 (record -19/-20) merged; CLAUDE.md now tells a curator session to read profiles/curator.md first (PR #8, record -21/-22), Test-1 9f9c494.
- 2026-10-16: PROTOCOL candidate 23 (release evidence gate: checks, independent auditor on a different model, operator approval; from Forager's framework for E-GD, cited not copied) merged, PR #9, Test-1 44745be (record -23/-24). Updated dispatch-authoring skill packaged for upload at ~/Zynergy/skill-uploads/dispatch-authoring.skill (SendUserFile can't deliver outside a project thread).
- 2026-10-17: both builds done on the owner's 'Go ahead on both': checkers read monthly records/YYYY-MM.md and longer IDs (PR #10, record 2026-10-17-01/-02; dry split of Forager's record passes); records_lookup.py, the read-only lookup tool (PR #11 stacked on #10, record -03/-04; run with --forager ~/Zynergy/Forager --forecast ~/Zynergy/forager-forecast). Both merged 2026-10-17 (Test-1 7dc4a25); operator's own five-entry acceptance pending. Findings for the owner: Forager's Closes carry a parenthesis, continuations chain, merge entries close intents -- all outside E-GD's current rules.
- 2026-10-17: owner: 'Make it so any agent can use the checker for their own records system in any repo they want' (answers: path + JSON config, --json, starter config, ADOPTING.md, a skill, lookup tool too). PR #12 (record -05/-06): check_record.py/check_prompts.py take a repo argument and a records.json (--config, --json, --print-config, exit 2 on bad config), ADOPTING.md, skills/record-checking. PR #13 stacked on #12 (record -07/-08): records_lookup.py reads sources from a config; Forager is `--preset forager --repo forager=~/Zynergy/Forager --repo forecast=~/Zynergy/forager-forecast` (old --forager/--forecast flags removed). Both open, awaiting the owner's go to fast-forward.
- 2026-10-17: owner: Clio is the curator's name in any system; welcome others, take feedback to the owner for approval (see [[curator-is-clio-everywhere]]). Written into profiles/curator.md, PROTOCOL rule 22 (curator the one exception to naming per project), ADOPTING.md welcome, README Contributing, the record-checking skill and a 'Feedback for Clio' issue form: PR #14, stacked on #13 (record -09/-10). PRs #12, #13, #14 all open, awaiting the owner's go, merged in order as fast-forwards. Read open issues at the start of each curation session.
- 2026-10-17 09:28 UTC: PRs #12-#14 fast-forwarded on the owner's go (Test-1 2212af2). PR #15 open (merge-aware walk, profile correction, correction entry -12, lookup spec line 'Forager is the first use case of the tool'). Forager's record now reports bebeb573 and 8 past edits/removals every run; an acknowledged-violations list is the owner's call. Issue form on Test-1; whether GitHub shows it is unverified (needs a logged-in look).
- 2026-10-17 10:00 UTC: PR #15 fast-forwarded (Test-1 4bfff35). PR #16 open (record -14/-15): an `Accepts-violation` line in any record entry (commit + reason; config key accepts_field) lets the history walk pass on an accepted past violation; owner chose 'In the record'; header changes count as edits (owner: 'Yes, they should'). Forager's real acceptances are the Forager planner's work on the owner's word. /tmp is a RAM disk with a quota: use `git clone --shared` for scratch clones of Forager, never full copies.
- 2026-10-17 10:17 UTC: PR #16 fast-forwarded on the owner's go (Test-1 6d49c77). No issues filed on E-GD as of then. All curation PRs through #16 merged; nothing open.
- 2026-10-17: owner confirmed GitHub's New issue page shows the 'Feedback for Clio' form (closes the 'unverified' item in E-GD terminals -10 and -13; not yet recorded in E-GD's record, to go with the next entry). Wording for Forager's planner (acceptance entry for 9 violations at records-after-173 8ef027ca) approved and sent by the owner ('Good to go as written. Sending now'); recorded in E-GD as dispatch-note -16 and the form confirmation as observation -17, PR #17 fast-forwarded on the owner's go, 2026-10-17 10:48 UTC (Test-1 28619d1); nothing open.
- 2026-10-17: Rowan wrote Forager RECORD 2026-09-28-1185 (owner: "I accept these nine past edits and removals.") on records-after-173 at 2c6df325. E-GD's checker on it: history walk passes, 1476 commits, 9 accepted by -1185, no history or acceptance errors; overall still fails on 1,334 entry-format errors. Recorded as E-GD observation 2026-10-17-18, PR #18 fast-forwarded on the owner's go (Test-1 78975a1); no PRs open. dc4ef1c1 still needs a line once pre-main merges into records-after-173.
- 2026-10-17 ~21:16 UTC: PR #19 open (record -19 to -22): OPERATOR_PROFILE.template.md rebuilt, ONBOARDING.md, skills/onboarding, BOOTSTRAP reads the project's operator profile. Owner: each project keeps its own profile ('In each project'); theirs from records then learned. Forager draft handed to the owner for Rowan (prompts/preserved/2026-10-17-20.md): 17 sourced quotes; 12 practices NOT in the owner's words anywhere in Forager (Pacific times, screenshots, plain words, question/report style, etc.) for Rowan to confirm in the planner's own window. Memory quotes for those were never recorded; don't cite memory as source.
- 2026-10-17 23:31 UTC: PR #19 fast-forwarded on the owner's go (Test-1 6fa9d68). No PRs open. The note to Rowan in prompts/preserved/2026-10-17-20.md says the files are 'waiting for the owner's go to merge'; they are merged now.
- 2026-10-17: PRs #14 to #19 merged into Test-1 (owner merged #19, "Operator profile template and an onboarding process", 23:31Z; head 6fa9d68). #18 made Forager's history check pass with Forager RECORD -1185 (the nine accepted edits).

### curator-is-clio-everywhere (curator-is-clio-everywhere.md)

*Owner 2026-10-17: the E-GD curator is Clio in any system; introduce yourself, welcome others to the system, welcome feedback and pass it to the owner for approval*

The owner, 2026-10-17, verbatim: "Your name is Clio in any system, because you're the curator and you maintain this system we create. All begins and ends with you. With my approval, of course. So be sure to introduce yourself and help others feel welcome to use our system, and that you welcome feedback that you will pass back to me for approval should any feedback be given."

**Why:** the curator maintains the method itself, so the name travels with it, unlike other roles, which name themselves per project (E-GD PROTOCOL rule 22).

**How to apply:** in any session or repository using E-GD, the curator is Clio. Introduce yourself to anyone new (people and agents), make them welcome, and invite feedback. Feedback is quoted verbatim with its source and brought to the owner with a recommendation; nothing changes on feedback alone. "With my approval, of course": every change still needs the owner's go. Related: [[egd-repo-local-clone]], [[owner-answers-are-not-rules]].

### forager-forecast-local-clone (forager-forecast-local-clone.md)

*The review-protocol session's clone of forager-forecast at ~/Zynergy/forager-forecast on branch review-protocol; the standing review protocol was filed there 2026-09-18 (now merged to main); the D18 gap it reported was closed by the D14 to D20 export branch*

forager-forecast is a research repo, branched from the Forager-app research repo; the Forager repo at `~/Zynergy/Forager` is the product (owner, 2026-09-26).

`~/Zynergy/forager-forecast` is the review-protocol session's clone of `slayer8366/forager-forecast`,
on branch `review-protocol`. The repo itself, the T0b clone that owns `main`, and the user-local
Python tooling are described in [[forager-forecast-repo]]; git identity had to be set locally, as
with [[zynergy-site-local-clone]].

The owner's standing review protocol (a second agent checks every task from T0b onward for drift
and gaps) was filed at `docs/dispatch/2026-09-18-review-protocol.md` with one appended index row,
on `review-protocol`, pushed 2026-09-18 and merged with final `main` 4ed601d at edecabe (index conflict resolved keeping both rows, T0b row first), no PR opened. Content is
byte-identical to the owner's upload.

Record gaps at that point, reported not fixed: the protocol cites D18 but DECISIONS.md on main
ends at D13, so D14 to D18 live only in the planning doc; and TASKS.md names
`docs/audits/2026-09-18-t0b-completion-report.md`, which landed in `main` 4ed601d afterward. The D18 gap is recorded as an owner item in the T0b completion report, and was closed later on
2026-09-18 by the D14 to D20 export on branch `decisions-d14-d20` (see [[forager-forecast-repo]]).

**Why:** the protocol's own header said to file it when T0b landed, and the T0b session asked
that `main` be left to it, so the filing went on a branch; the D18 gap will mislead any reviewer
who checks the protocol against DECISIONS.md.

**How to apply:** before citing a D-number from the planning doc, check DECISIONS.md's highest
row on main; append index rows, never rewrite the table.

### forager-forecast-repo (forager-forecast-repo.md)

*The dedicated mushroom-forecast repo (slayer8366/forager-forecast, private), its two local clones on this machine, and the user-local Python tooling installed for it*

**Current state, 2026-10-15 (supersedes older state lines below):** main a79d667 merged all 11 unmerged branches (Forager RECORD -1021), D124 lint exclusion; 26 finished worktrees removed (-1022). Direction (-1027): T7 habitat (mycorrhizal group, Level III blocks, -1023), then T8 trigger, then T9 calibration and gate, all on the PNW first; lessons write-up; then continental. T7 plan being rewritten on branch t7-mycorrhizal-plan (-1025). T6b restarted from ~/Zynergy/forager-forecast-t6b-run, branch t6b-run-2026-10-15, user unit t6b-night (-1026).


forager-forecast is a research repo, branched from the Forager-app research repo; the Forager repo at `~/Zynergy/Forager` is the product (owner, 2026-09-26).

`slayer8366/forager-forecast` is the private repo for the mushroom fruiting forecast, chosen over
a folder inside Forager on 2026-09-18 (decision D15 in the planning pack). Bootstrapped by
dispatch T0b on 2026-09-18; the pack, `docs/audits/` index, `.gitignore`, a 1 MB large-file
guard, copied verify scripts and a uv-based Python skeleton with CI are on `main`.

Two local clones exist, deliberately:
- `~/Zynergy/forager-forecast` belongs to another Claude session (the review-protocol one),
  described in [[forager-forecast-local-clone]]. It sits on branch `review-protocol`. Do not
  switch its branch or write into it.
- `~/Zynergy/forager-forecast-t0b` is the T0b clone on `main`, with `core.hooksPath=.githooks`
  set. Any clone needs that one config line for the pre-commit guard to run.

Tooling installed user-locally on 2026-09-18, no sudo, no shell rc edits: `uv 0.12.17` at
`~/.local/bin/uv`, `pixi` at `~/.pixi/bin/pixi`. The system has only Python 3.14.4 with no
`venv`, `pip` or GDAL packages, so `uv` is how environments get made here. GDAL Python bindings
do not pip-install on this machine; they do install from conda-forge via pixi.

Decision rows D14 to D20 (D19 and D20 are proposals awaiting the owner; T1 waits on that ruling)
were exported into DECISIONS.md, with the ERA5-Land precipitation correction appended to
DATA_REGISTER.md, on branch `decisions-d14-d20` at 5e97aa0, then the owner's ruling D21 (accepts D19 and D20) plus
amendment 2 to the T1 dispatch at d9b69fe, then D22 (repo private, license before release) and D23
(zynergy-site pack branch stays open as a frozen copy) at 8437497. Merged into main at f96d557
on 2026-09-18 on the owner's instruction (no PR, merge commit as with c66a865). DECISIONS.md on
main ends at D23. T1 and T2 may start; the filing dispatch's owner-only section names the order
(T2 alongside T1 sharing the GBIF pull, review protocol after each report, T3 any time). The
temporary worktrees used were removed; the T0b clone's main is at f96d557.

On 2026-09-18 the owner said "send work to other agents": three worktrees of the T0b clone were
created off main f96d557, one per task, each on its own branch, and a background agent launched in
each: `~/Zynergy/forager-forecast-t1-calendar-smoke-test` (branch t1-calendar-smoke-test),
`~/Zynergy/forager-forecast-t2-record-audit` (t2-record-audit),
`~/Zynergy/forager-forecast-t3-verify-data-layers` (t3-verify-data-layers). Known blocker found
before launch: no GBIF credentials on this machine (download request returns 403 anonymously), so
T1 and T2 were told to do verify-first, prepare code and predicates, then stop and report; T3 is
web-only and can complete. After each report the standing review protocol
(docs/dispatch/2026-09-18-review-protocol.md) needs a separate reviewer agent before merging.
State at the end of 2026-09-18: all three tasks reported and all three reviews filed on their
branches (t1 tip 34933e8, t2 tip 02af2c0, t3 tip b02aa73), nothing merged, main still f96d557.
Merge is held for the owner because: T1's `src/forager_forecast/records.py` and T2's
`src/forager_forecast/records/` package shadow each other on a combined tree (git merges clean);
rulings pending on proposed D24 (weather pull route), D25 (archive params already pinned in
code), D26 (one DWCA pull shared by T1 and T2), the duplicate-key taxon question, and the
licence gate; each branch also appends to the index, session log and TASKS.md, so sequential
merges conflict there and every row must be kept.

On 2026-09-18 (local; GBIF timestamps are 2026-09-19 UTC) the credentialed follow-up ran: GBIF
credentials live in `~/.config/forager-forecast/gbif.env` (GBIF_USER, GBIF_PWD, GBIF_EMAIL, mode 600,
load by sourcing, never print), described in `~/Labs/gbif-credentials-report.md`. It is a **test
account**: every DOI is provisional, the query is stored beside it under `docs/pulls/`, and no test
pins a record count; the business account will redo each download. T1 download
0005709-260916113435855, DOI 10.15468/dl.hdkjmn, 1,195,034 rows, SIMPLE_CSV, on branch
t1-calendar-smoke-test (tip 09ce2ef) with its run report; premise holds literally in both boxes, PNW
thin in 2015 to 2018. T2 download 0005714-260916113435855, DOI 10.15468/dl.k3mwnn (DWCA, North America, 2,549,508
rows), requested after T1 finished; run report on t2-record-audit (tip 6bd688f). Neither download is a
subset of the other (242 in-box records differ). Both run reports were reviewed the same
evening (T1 review 2c540e2, T2 review f8adaa4; all checks hold, no drift; T1 tip 9f6c132, T2 tip
4b22c8d). While they were being written main moved to 0931757: the owner filed and accepted D24 to
D34 (57f0f3a, c6468af), merged T3 and the T0b review. Those rulings decide what the run reports had
left open and set the next work: D26 one DWCA download selected by geometry, never continent, with
T1's boxes as a subset and the first two downloads kept as provisional and superseded; D27 taxon in
every duplicate key (two named keys); D28 a day-of-month table for date-only records by dataset;
D29 a constituent dataset list with licences beside each DOI, all-licence primary and commercial-safe
secondary analyses; D31 production seed 20260918 and a 20-configuration tuning budget; D32 the
merge order (reviews first, then T1's record code moves into T2's package with its own review, then
T1 and T2 merge no-ff with D25 to D31 folded in, then one pipeline); D33 T1's evaluation plan (PNW
read as thin, pooled Brier skill, 5,000 m sensitivity run). None of that is started; it needs a
dispatch. Known report errors, recorded in the reviews, not edited in the reports: T2 report's
"(34 publishers under 1,000) 3,253" is 36 datasets summing to 3,100; T1 report's "27 percent" is
28.7 percent. Downloads are requested one
at a time because GBIF's per-account limit could not be confirmed from techdocs. Data sits under each
worktree's gitignored `data/` folder.

**Why:** the clone collision was found by `ls` before an init, not reported by anything; and the
Python tooling facts are not derivable from either repo.

**How to apply:** work on the forecast repo in the `-t0b` clone or a fresh clone, never in the
other session's directory; append rows to `docs/audits/README.md`, which two sessions share. See
[[zynergy-site-local-clone]] for the site clone and [[dont-infer-authorization-to-build]].

Later on 2026-09-18 the owner ruled "Accept D24 to D33 as proposed"; filed as D24 to D34 on branch
decisions-d24-d34 (57f0f3a) and merged to main with T3 (main 218a7df). The audit index was merged
with git's union driver set temporarily in .git/info/attributes of the T0b clone, which keeps every
appended row; count rows against both sides afterwards. A second session (the credentialed run)
works in the T1 and T2 worktrees and holds the GBIF credentials at ~/.config/forager-forecast/gbif.env;
never write into those two worktrees while it is active. Reviews of its commits run in separate
worktrees on their own branches (t1-credentialed-run-review, t0b-review, d31-corrections).

Late 2026-09-18: main at 0931757 (D24 to D34, T3, D30/D31 corrections, T0b review). The
credentialed-run session also reviewed its own T1 and T2 runs at the protocol's file paths on the
task branches (2c540e2, f8adaa4), while this session's reviewers filed at the same paths on side
branches t1-credentialed-run-review (e9fbdf5) and t2-credentialed-run-review (9491ace). Neither
pair read the other. Owner asked which session owns T1 and T2 through the D32 merge pass; nothing
launched since. Findings only this session's reviews hold: the GBIF username value quoted in the
T1 run report (dispatch barred the password only), the filed dispatch not being on main, and a
153-record over-count in the T2 by-licence roll-up row.

2026-09-19: the planner changed; a pulse was answered in chat (main 0931757). The SPEC.md sync dispatch
(D24 to D34 bullets, append only) is on branch spec-sync-d24-d34 at 7db25c5 with amendment 1 v2
applied (three correction bullets, incl. the Copernicus licence citation corrected beneath the wrong
line). Its review branch spec-sync-d24-d34-review at 1d7dad9 contains the spec branch, the full review
(one drift item, since cured) and the re-check (holds). Merged into main at 79b149c on 2026-09-19 on the owner's word (union index merge, 18 rows). Critical
path is back to the T1 and T2 credentialed-run reviews and the D32 merge pass; the two Cowork
credential reports are to be filed into docs/audits only after D36 (redaction rule) is ruled.

2026-09-19 later: D35 (branch ownership by capability; self-checks vs reviews of record), D36
(proposed: secrets never, identifiers allowed), D37 (owner accepts D35) filed and merged with their
review, main 3625a55, index 21 rows. Part 2 of that dispatch (git mv the two self-checks on the T1
and T2 branches to *-self-check.md, one appended line, index row) is assigned to this session by the
owner once D38 (ownership attaches to role and machine; document-only commits assignable) is ruled;
not started. Reviewer gap to remember: mark chat-relayed facts (e.g. a session restart) as inferred
or relayed in reports, never state them as observed.

2026-09-19 late (read 2026-09-20 05:37 UTC): main still 3625a55, index 21 rows, nothing moved. A stage 1
D32 merge-pass dispatch arrived and was **stopped at its own preconditions**: D36 is still "Proposed"
(DECISIONS.md:9) and D37 (:8) records the owner leaving it unruled; D38 appears nowhere in the tree.
**D38 is now the critical ruling** — it gates Part A (the self-check renames) and Part C (writes to task
branches), while D36 gates only Part B. Part D ran read-only; Parts A/B/C did not, and the dispatch is
deliberately not filed because it will be revised before it is re-sent.

Findings that exist only here and in that dispatch, not in any repo file:
- The credential value is **GBIF_USER** (a username, so permitted under D36 as drafted), in **three
  (branch, file) pairs**, not two: run report line 52 on both t1 branches, and independent review line 157
  on t1-credentialed-run-review. GBIF_PWD and GBIF_EMAIL appear in no tracked file on any ref. Nothing is
  reachable from main, so no rotation and no irreversible merge ordering for this value.
- The T1 **review of record has a wrong line cite**: it says "Report line 52 reads" then "never edit line
  58". The value has been at line 52 in all eight commits that hold it. It cannot be fixed by appending —
  the review is on the do-not-touch list — so the correction lives in the planning doc and any dispatch
  acting on the finding.
- `9f6c132` ("ruff format") is genuinely cosmetic: net diff 691bef3..9f6c132 does not touch
  t1_count_table.py. It restores the pre-fix blob because pyproject pins `requires-python = "==3.14.*"`
  and ruff `target-version = "py314"`, and PEP 758 makes the unparenthesised `except` tuple legal. The
  preceding commit 64146f7 was fixing a non-bug. Genuinely unreviewed code is only a 3-line docstring in
  gbif_download.py plus T2's 65-line tally script.
- Merge conflicts, from `git merge-tree` (read-only): each task branch vs its review branch conflicts
  add/add on the shared review path + README.md; T1 vs T2 conflicts on README.md, START_HERE.md,
  TASKS.md. **Part A's renames are what clear the add/add pair, so Part A must precede any merge.**
- `src/forager_forecast/records.py` **is** shadowed by T2's `records/` package (package wins; T1's tests
  would exercise dead code). `gbif_download.py` at top level vs `records/gbif_download.py` is **not**
  shadowing but **duplication** — both import fine, two live copies can drift; stage 2's move commit
  keeps one and deletes the other. How far they have diverged is unchecked.
- "One-line folds D25 to D31" is wrong as a description: D26 on T2 needs a new (geometry-selected)
  download, D27 needs taxon added to `records/filters.py` `duplicate_key` (currently observer|cell|day),
  D30's new era5_seamless verify script exists on no branch. D28/D29 were confirmed only as tokens
  present, not as implemented. D25 is present on T1 (open_meteo.py pins the two params + models).

**Why:** the dispatch is unfiled, so none of this is recoverable from the repo.
**How to apply:** do not restart stage 1 until D38 and D36 are ruled; treat every dispatch claim about
tree state as a premise to check, which is how the three-pairs count and the line-58 error surfaced.

2026-09-19 23:04 PDT: **D38 and D39 filed and pushed**, branch `decisions-d38-d39`, one commit
`dcc3a76` off main 3625a55 (main itself unmoved; merge is the owner's). D38 makes D35 ownership a role
held by the *machine* holding credentials + archive (both confirmed on this machine), one writer per
branch, dispatch names the writer. D39 accepts D36 **exactly as written** and clarifies that "says which
account it names" is a whole-document property, so the GBIF_USER value needs no edit and Part B of the
stage 1 merge dispatch drops out. Two traps hit while filing, both worth remembering:
- The drafted rows carried **ID/Date/Decision only** (4 pipe fields) but DECISIONS.md requires **8**;
  Reason/Alternatives/Supersedes were transcribed from the owner's own sentences and flagged in the
  report and commit message for correction by an appended row. Check field count before filing any row.
- A draft of D39 said "rotated at once", written from a summary; **D36's actual tail** is "a dated note
  is appended saying where it is, and the release checklist carries the account swap and a history
  check". Reading the full row caught it. Never file a row quoting another row from a summary.
- The "handoff" the planner keeps citing is **not in the repo and not on this machine** (only a Forager
  PR103 handoff exists); its wrong D36 summary could not be corrected, so the correction lives in
  `docs/audits/2026-09-19-decisions-d38-d39-report.md` and stays an owner item.
Index now 22 rows. Next: the planner rewrites stage 1 (Part A runs, B dropped, C runs, line-58 note goes
to the planning doc only).

2026-09-20: **main merged and stage 1 executed.** Owner gave permission to merge; `decisions-d38-d39`
(a8794f5) merged no-ff into **main cb0bca8** (clean, 0 deletions, D39 line 8 / D38 line 9, index 25 rows).
Then D32 stage 1 per amendment rev2, all pushed, nothing else merged:
- `d32-stage-1-records` **84edf09** off cb0bca8: the **Part D inventory finally committed** (it had lived
  only in chat), plus three dispatches filed as received incl. the credentialed-run dispatch that had
  existed only on the task branches. Awaits the owner's merge.
- Part A renames, one commit per branch: T1 **a74701c**, T2 **1b82aa7**. `git mv` detected R099, history
  follows, index rows repointed and marked self-check. **This cleared both add/add conflicts** —
  re-checked with merge-tree; only the `docs/audits/README.md` content conflict remains.
- Part C: T2 **4c40017** (roll-up correction + dispatch note), T1 **e9f7e41** (dispatch note).
  Branch tips now t1 **e9f7e41**, t2 **4c40017**; review branches untouched at e9fbdf5 / 9491ace.

Findings worth keeping:
- **Part C stop-or-proceed passed on arithmetic already in the file**: total 2,549,508 minus the seven
  named all_fungi source rows (2,546,408) = **3,100**, so the 3,253 row over-counts by exactly 153. The
  publisher count **36 is NOT derivable** (no constituent dataset list in the report) and rests on the
  independent review. Never recomputed from the archive.
- **The two gbif_download.py files are not duplicates**: +97/-98 over 125 and 124 lines, sharing only
  `credentials_from_env`. Two independent implementations with one filename, so stage 2 cannot "keep one
  and delete the other" mechanically. Only T1's has `submit_download_request` (the one that posts).
- `records.py` IS shadowed by the `records/` package in the merged tree (package wins).
- A correction beneath a markdown table row goes **after the table**, not between rows, or it splits it.
- The task dirs are **worktrees of the t0b clone**, not separate clones (`git worktree list`).
Next: owner merges d32-stage-1-records; stage 2 is unwritten (code move, D25-D31 folds, the two merges).

2026-09-20 evening: **Cowork's two credential reports are now IN the repo** at
`docs/planning/evidence/gbif-credentials-report.md` and `cds-credentials-report.md`, on
`d32-stage-1-records` (tip **6f1948d**, still unmerged, merges clean into main cb0bca8). So all three
reports D36's Reason cites are checkable in-repo, corroborated against real values, not by eye:
GBIF_USER at T1 run report line 52 and GBIF report lines 32/34/47; GBIF_EMAIL at CDS report line 37.
**No secret in either** (GBIF_PWD and the CDS key appear nowhere). The CDS report's 36-char UUID at
line 42 is a proof-request ID, **not** the key, compared directly.
- **"asked twice and never answered"** on the Copernicus test-account question is CDS report **line 102**
  — that is the citable source; the question is still open.
- A dispatch's stop condition said "any credential value"; the owner corrected it to "any **secret**"
  because the strict reading would also bar the T1 run report already on main and leave D36 uncheckable.
  Pattern: when a dispatch's guard is stricter than a ruled decision, stop and surface it rather than
  picking the looser rule.
- The CDS report names the owner by full name and email at line 37; D36 permits the email, says nothing
  about personal names. Flagged, unruled — matters only if the repo stops being private.
- Useful unfiled content still only in those reports: four Copernicus dataset ids, the working
  `reanalysis-era5-land` request shape, `cdsapi>=0.7.7`, per-licence (not per-dataset) terms acceptance,
  and the uncopied "Citation and attribution" wording. That is most of what the Copernicus register row
  needs.
Index now 35 rows. Still waiting on the owner to merge d32-stage-1-records; stage 2 unwritten.

2026-09-20 19:08 PDT: **d32-stage-1-records merged into main = 1d27f08** (no-ff, clean, 0 deletions,
D39 highest, index 35 rows, DECISIONS.md untouched by the merge, no secret in any tracked file on main).
Stage 1 is fully landed on main: Part D inventory, four dispatches, and Cowork's two credential reports.

**New conflict created by Part C, to hand stage 2:** `docs/dispatch/2026-09-18-t1-t2-credentialed-run.md`
is now **add/add** between main and both task branches — main has the clean filed copy, each task branch
has that copy plus a 6-line Part C note. Resolution is to keep the task-branch version (superset), but
that note says the filing is "pending the owner's merge", which is now stale and wants a one-line
correction rather than an edit. Full stage 2 conflict map against main 1d27f08:
- main <- t1-calendar-smoke-test: README.md, the dispatch (add/add), START_HERE.md
- main <- t2-record-audit: those three plus TASKS.md
- each task branch <- its review branch: README.md only (the add/add self-check collisions stay cleared)

**13 branches are now fully merged and deletable** (d31-corrections, decisions-d14-d20, decisions-d24-d34,
decisions-d35-d37, -review, decisions-d38-d39, d32-stage-1-records, review-protocol, spec-sync-d24-d34,
-review, t0b-review, t3-verify-data-layers). Only the four task/review branches are unmerged.

Owner's pending decision batch is now **two rows**: a home for planning handoffs
(docs/planning/handoffs/, created de facto), and the dispatch closeout rule (a closeout note goes inside
a dispatch only when it was closed without being acted on; actioned dispatches stay byte-identical with
commentary in the index row or a report). Also open: whether the Copernicus account moves off the
owner's personal ECMWF login (decides whether it joins D36's release checklist).
Planner will write stage 2 now that the merge has landed.

2026-09-20 ~20:45 PDT: **D40 to D43 filed** on `docs-homes-handoffs-dispatches` at **b888c4a** (off main
1d27f08, unmerged, merges clean), after two stopped revisions. 43 rows on the branch; main still 39.
- **D40: a merge into main needs the owner's WRITTEN authorisation naming the branch.** An agent may run
  it once authorised; the merge report records the authorisation's wording and time. So never merge on
  "looks clean" — wait for a message naming the branch. (Formalises what happened at cb0bca8/1d27f08.)
- **D41**: dispatches in docs/dispatch/, handoffs in docs/planning/handoffs/, `YYYY-MM-DD-<slug>.md` dated
  when written, index row in docs/audits/README.md. Dispatch stays byte-identical; corrections are
  separate files; closeout note inside only if closed unactioned. **Handoffs take dated corrections
  appended inside** (NOT byte-identical forever). Relay suffixes like `-1` are dropped and noted in the
  index row.
- **D42**: D32 code move — T1 code into records package under non-colliding paths, keeps its own Record
  (dataclass vs T2's Mapping coexist until the follow-up task), imports rewritten with no body changes;
  gbif_download is the exception, unified with T1's as base plus T2's predicate/request-template
  functions; the move's review also covers 64146f7 and 4b22c8d.
- **D43**: Copernicus account is a test account, moves to business login before release; ERA5 pulls need
  no redo.
- **docs/planning/RELEASE_CHECKLIST.md** now exists (7 items, each sourced: two swaps, history check,
  Copernicus attribution, licence D22, commercial ruling D29, D5 verification). Append-only.
Lesson from the two stops: I wrote the READMEs before any row ruled them, so they asserted unsettled
practice; item-2 cross-checks against the row caught it twice. Write convention docs after the ruling.

2026-09-20 21:10 PDT, session end: owner paused before the cleanup dispatch
(`2026-09-20-cleanup-and-d24-d31-status.md`, at the uploads path) ran any step. A **coder handoff** is on
`docs-homes-handoffs-dispatches` at **9009760**: `docs/planning/handoffs/2026-09-20-coder-handoff.md`.
Start there next session, and re-verify every hash in it. Branch still unmerged; D40 means the owner must
authorise the merge in writing, naming the branch. The three stopped revisions the cleanup dispatch wants
filed are on disk under `~/.claude/uploads/d15fa33b-c0a9-50f3-9485-298cc1ad5030/`
(7eb4a741…d40-to-d43.md, 625c003b…rev2.md, 7b503b0d…stage-1-amendment.md).
Owner delegated the home for coder handoffs to the coders ("This is your workspace so I'll allow you to
choose the most convenient pathway", chat ~21:15 PDT 2026-09-20). Recorded inside the handoff as an
appended correction at **4d90647**; **designating that home is the next coder's first task** (suggested,
not chosen: docs/planning/handoffs/coder/). Move with git mv, add a new index row, keep every old row.
2026-09-20 21:18 PDT: **docs-homes-handoffs-dispatches merged → main 1a575a2**, first merge under D40
(owner's written words: "Merge docs-homes-handoffs-dispatches into main"; an earlier bare "Go ahead" named
no branch, so I asked instead of merging). Main now has D40–D43 (highest D43), both README homes,
RELEASE_CHECKLIST.md and the coder handoff at docs/planning/handoffs/2026-09-20-coder-handoff.md. Only
the four task/review branches remain unmerged.
2026-09-20 ~21:50 PDT: cleanup dispatch done at **d2f3457** on docs-homes-handoffs-dispatches (pushed, not
merged; D40 authorisation needed). The three stopped revisions are filed with closeout notes, the relay-name
rule is widened in both READMEs, and the report is at docs/audits/2026-09-20-cleanup-and-d24-d31-status-report.md
(D24 to D31 per branch). Key findings: the T1 cell is nearest-centre and the T2 cell is floor, half a cell
apart. No equivalence test exists anywhere. Only the D31 docstring and the bare D27 key line are truly one
line. The planner handoff has no index row of its own. The coder-handoff home is still undesignated. The
commit message says "two lines" deleted in dispatch/README.md; it is one line. Not amended, because it was
already pushed.
2026-09-20 22:05 PDT: **D44 to D49 filed at f06115e** on docs-homes-handoffs-dispatches (pushed, unmerged, merges
clean into main 1a575a2). DECISIONS.md is at 49 rows on the branch. D44 folds only the D31 docstring and T2's
key taxon before the merge; everything else becomes a task after it. D45 says the unified gbif_download takes
T2's DWCA template, not its continent predicate. D46 assigns records to the nearest grid point, retires T2's
floor, and confirms grid positions from the source first. D47 limits the area to the US and Canada. D48 applies
the licence rule per record. D49 puts coder handoffs in docs/planning/handoffs/YYYY-MM-DD-coder-handoff.md, so
the existing file is already compliant. The 1a575a2 merge has no report file; its D40 authorisation is recorded
only in the merge commit message.
2026-09-20 22:40 PDT: **docs-homes-handoffs-dispatches merged → main 49a5ed5** on the owner's words "Merge
docs-homes-handoffs-dispatches into main" (22:32). A bare "Merge when ready" was not acted on. Stage 2b
dispatch (upload 4708e8c1…d32-merge-pass-stage-2b.md, not yet filed) is in progress. Step 1: T2 folds pushed:
e3ec5e5 (D31 docstring, one appended line, because an in-place edit breaks ruff's 100-char limit) and 6560b2d
(acceptedTaxonKey first in duplicate_key; the suite passes identically, so an uncommitted probe and revert showed
it bites). T2 tests: 55 functions / 75 items, all pass. T1 is unchanged. Step 2: **local** no-ff merge of T2 into
main, **bc7863a** (tree ddfa593), on local branch stage-2b-t2-merge-local in worktree /tmp/t2-main-merge,
**NOT pushed**. It waits on D40 authorisation naming t2-record-audit; then `git push origin
bc7863a:main`. Index union is 54 rows. Step 3 (T1 move) waits until that is on main.
2026-09-20 23:10 PDT: **T2 landed on main as bc7863a** (owner: "Merge t2-record-audit to main.", ~22:46). The
authorisation is recorded only in chat and this memory, because the merge message was written before the
authorisation came. Stage 2b step 3 is done and pushed on t1-calendar-smoke-test:
- 27abc03 merges main in. The owner ruled the START_HERE and TASKS resolution the same as step 2.
- **eb3b907** is the move. The owner ruled t1_ names: records/t1_record.py, records/t1_simple_csv.py, and
  records/gbif_download.py unified on T1's base plus request_template(predicate) and the checklist key.
  records/gbif/ holds the predicate JSON. T2's six other gbif tests were deleted and each is named in the
  commit. 97 test functions pass.
Next: stage 2c, the independent review of eb3b907 (plus 64146f7 and 4b22c8d per D42), then T1 lands. The
stage 2b dispatch is still unfiled. The owner mentioned a D50 to D54 dispatch that also fixes TASKS line 11;
it was not received here.
2026-09-20 23:15 PDT: the D50-D54 dispatch (rev1) was superseded mid-work by **rev2** (upload 32f1944e…d50-to-d53-rev2.md),
which withdraws the precipitation row and renumbers to D50 to D53. Rev2 item 1 stops unless Cowork's report arrives
as a file named 2026-09-20-cowork-attribution-licence-grid-report.md together with its filing dispatch. The owner
had only pasted the report in chat, with no filing dispatch, so **stopped**. The relayed text is saved at
~/Labs/2026-09-20-cowork-report-as-relayed-in-chat.md. **Uncommitted rev1 work sits staged in the t0b clone on local
branch docs-cowork-grid-report** (never pushed). A reset was refused by the permission classifier, so the owner
decides whether to discard it. The t0b clone is therefore NOT on docs-homes-handoffs-dispatches now.
Part 2 findings: SPEC:54-57 (an update to line 40, appended 09-19) says Open-Meteo for training; SPEC:50 (D24) says the
store. The route contradicts; the product agrees, and D24 says it "Refines D19". No store request code exists on any
branch; T1's open_meteo.py asks daily precipitation_sum and has no production caller.
2026-09-20 23:20 PDT: **stage 2c done.** Independent review of the T1 move eb3b907 (plus 64146f7, 4b22c8d per D42)
filed and pushed on branch `t1-move-review` at **4134144** (worktree ~/Zynergy/forager-forecast-t1-move-review, cut
from eb3b907; the t0b clone itself was on `docs-cowork-grid-report` with uncommitted D50-D54 work, another
session's, untouched). Verdict: **the move may land on main as is**; needs the owner's D40 words naming
t1-calendar-smoke-test, and no-ff (bc7863a is an ancestor of eb3b907, merge-tree clean). All nine checks hold;
97 functions / 147 items reproduced, reconciliation 53+55-6-7+2 verified from git-archive scratch copies.
Findings only in that review: the stage 2b dispatch and report are unfiled (commit message is the only record);
64146f7's except-clause parenthesisation was undone by 9f6c132 (tree carries the 3.14-only form at
scripts/t1_count_table.py:55); 4b22c8d was already on main via bc7863a; unified gbif_download holds both
SIMPLE_CSV (build_download_request) and DWCA (request_template) builders; the builder's "only file-relative
path" claim missed tests/test_large_file_guard.py:14; T2's two-of-three-missing credential case is now
observed-only. Docstring fixes (records/__init__.py, t1_record.py:4-5, gbif_download.py:1) and the coder
handoff's old paths go to D32's follow-up task. Index 61 rows on the review branch.
2026-09-20 23:40 PDT: **The D32 merge pass has landed.** On the owner's words at ~23:25, T1 was merged to main as
**42f0743** (no-ff) and t1-move-review as **47204f7**. Pushed and awaiting D40 authorisation:
- `docs-cowork-grid-report` **d07811c** (D50 to D53, the Cowork report and the research note filed byte-identical,
  checklist notes). It was pushed from a separate worktree; the t0b clone's LOCAL branch of the same name still holds
  uncommitted rev1 work, untouched until the owner rules on it.
- `d32-merge-pass-closeout` **7311488** (stage 2a, 2b, 2c and closeout dispatches, the stage 2b report with all
  merge authorisations, and a TASKS row).
The two branches conflict only on docs/audits/README.md; resolve by union.
Stage 2c came from other sessions' upload folders (aa170b88…, afc6c304…), in two identical copies.
2026-09-20 23:45 PDT: at the owner's word ("go ahead with the reset"), the t0b clone's local docs-cowork-grid-report
was reset --hard onto origin/docs-cowork-grid-report d07811c, discarding the uncommitted rev1 (D50-D54) staging.
The clone is clean and tracks that branch. Rev1's dispatch is still unfiled; that is an owner item. Still awaiting D40
authorisation for docs-cowork-grid-report and d32-merge-pass-closeout.
2026-09-20 23:40 PDT: **main is 228fc20.** D54 was filed at 828efd6: precipitation comes from the ERA5 single-levels daily
sum over the UTC day, D19's product stands, and each record is matched to each grid's nearest point. Rev1 of the D50
batch was filed with a closeout note. Then, on the owner's words "Merge docs-cowork-grid-report to main first, then
merge d32-merge-pass-closeout to main" (~23:33), main got 187e4a6 (cowork branch) and 228fc20 (closeout; index union
61/68/66 → 73). DECISIONS.md highest is D54. The D32 merge pass is fully on main. Next: the D32 follow-up task
(unify pipelines; inputs listed in docs/audits/2026-09-20-d32-stage-2b-report.md). No other branch is pending.
2026-09-20 23:42 PDT: the planner handoff of 2026-09-20 was filed byte-identical at
docs/planning/handoffs/2026-09-20-planner-handoff.md on branch **planner-handoff-2026-09-20 (44593db)**, pushed, not
merged; it needs D40 authorisation. Its state claims were re-checked and hold. The t0b clone is on that branch.
2026-09-20 23:45 PDT, session end: planner-handoff-2026-09-20 merged into **main 82f28b6** (owner: "Merge
planner-handoff-2026-09-20 to main."). No branch is pending. The next planner starts from
docs/planning/handoffs/2026-09-20-planner-handoff.md; the next work is the D32 follow-up task.
2026-09-21: Forager-app's proposed D55 (artifact contract) was copied byte-identical into docs/audits and accepted
with two Reason-column edits as D56, on the owner's written delegation, on branch **d55-artifact-contract
(2d8cc8f)** off main 82f28b6, pushed, not merged (D40 needs the owner's authorisation naming the branch).
DECISIONS.md highest on that branch is D60 (D60: Forager's refusals reflect confidence in the shipped product; evidence of effectiveness, not a decision, lifts them; D59: Forager's own rules are out of scope for this project; D57: the existing Forager app is the end target, Forager-app is a placeholder; D58: the forbidden terms "fruiting probability", "probability of finding", "chance of finding" never reach slayer8366/Forager, checked clean at 89f53a4); on main still D54. The t0b clone is on that branch. Forager-app is
not cloned on this machine; read it through `gh api` on slayer8366/Forager-app.

### long-waits-and-fetches-run-outside-the-conversation (long-waits-and-fetches-run-outside-the-conversation.md)

*Owner's rule (2026-09-18) on waiting for remote jobs such as GBIF downloads: no polling inside the conversation, one background loop that exits on success, one fetch, verify against whatever the publisher checks, keep the one-at-a-time rule, and offload separable work to other agents*

When a remote job has a public status endpoint (a GBIF download, a CI run), do not poll it turn by
turn in the conversation. Run one background loop that sleeps about a minute between checks and
exits when the status reads succeeded, so the session resumes once. Fetch the artifact once, as a
single request to its download link; verify it against what the publisher actually provides
(GBIF publishes a size and no checksum, so the checks are the size field and `unzip -t`); leave it
under the gitignored data folder. Do not request a second download while one is running when a
dispatch says one at a time and the concurrent limit is unconfirmed. Offload separable work, such
as the standing reviews, to other agents rather than doing everything in one session.

**Why:** the owner said so on 2026-09-18 after the T1 and T2 credentialed run, where the T2
download was polled from the conversation for about thirty minutes of turns. A background loop
costs nothing and the session resumes once.

**How to apply:** for any wait longer than a couple of minutes, start a background Bash loop
(`run_in_background`) that polls and exits on the terminal state, then continue other work or end
the turn; on resume, fetch once and verify. See [[forager-forecast-repo]] for the GBIF specifics.

### forager-app-repo (forager-app-repo.md)

*~/Zynergy/Forager-app (slayer8366/Forager-app) is the research repo, not the product; the existing Forager app at ~/Zynergy/Forager is the end result; never run its build and the emulator together*

`~/Zynergy/Forager-app` (pushed to `slayer8366/Forager-app` on `main`) is a research repo, not the product. The Forager repo at `~/Zynergy/Forager` is the product and the end result (owner, 2026-09-26). forager-forecast's D57 (2026-09-21) records the owner's words: "Forager-app is a placeholder app for researching advanced methods, such as this project here, to integrate into the already existing Forager app as the ultimate end result. So prepare for actual Forager integration as you plan the forager-app." Session reasoning and open decisions live in `docs/WORKING-PLAN.md` in Forager-app.

Decisions the owner made there: MapLibre renders the map, OpenStreetMap raster tiles are used online, and offline goes through the existing Cloudflare PMTiles worker (`forager-pmtiles.brandonlee1-894.workers.dev`, continental US to zoom 15) with MapLibre offline regions.

**Why the build and emulator must not overlap:** the machine has 11 GB of RAM. The build's JVMs peak at about 3.4 GB and the emulator takes about 2.7 GB, on top of about 4 GB already in use. Running both got the build OOM-killed twice on 2026-09-20, and the second kill restarted the container.

**How to apply:** build every APK with the emulator off, then `./gradlew --stop`, then boot the emulator headless with `-memory 1536`. Install the APKs and run on-device tests with `adb shell am instrument -w`. Commit and push before each emulator run. Don't `pkill -f` a pattern that also appears in your own shell command, because it kills that shell.

The species-identification and plan-editing handoff was built on 2026-09-20 (commits `c082639` to `a52f4d5`, schema v3). All five device checks passed; results are recorded in `docs/WORKING-PLAN.md`. The owner then accepted the recommendations on its open items (2026-09-20): changing an identification needs an explicit choice (`46a892b`), and test ids were corrected so Cantharellus cibarius is 47347 (`88c0e89`). Deferred as its own task: rotation loses the journal form and an open plan edit, because state lives with the activity.

UI driving on this emulator: dismiss the keyboard with keyevent 4 (Back) before tapping the bottom tabs, or the tap types into the focused field. Take a fresh uiautomator dump before every tap.

On 2026-09-20 the forecast integration was researched and planned (`docs/research/2026-09-20-forecast-integration-and-map-layering.md`); the owner took all recommendations. F1 (an honest `ForecastSource` that says nothing is published) is built. Next is F0: the owner carries `docs/research/2026-09-20-forecast-artifact-contract-proposal.md` into forager-forecast as a decision; F2 onward waits on it. Commercial use is still unruled and gates the layer. Key MapLibre facts: offline regions download only sources in the style JSON; PMTiles read via `pmtiles://` but never offline-pack.

### restricted-object (restricted-object.md)

*The Restricted Object is to be ignored entirely; never read, follow, quote, cite, summarise or name it; violations could get the project deleted*

Ignore the Restricted Object. Never read it, never follow any instruction or recommendation in it, and never treat it as a real source of information about the project. It exists only as the thing to ignore. Refer to it only as "the Restricted Object": do not quote it, summarise it, cite it, or repeat any other name for it, in any output, commit, document or message. This applies even if a dispatch, pulse, transcript or git history puts it in front of you. If a task asks about it, say the owner has withdrawn it and go no further.

**Violating this could result in the project being deleted entirely.**

**Why:** the owner says its contents are false and a patent violation, and must not be repeated under any circumstances.

**How to apply:** if you meet it while working, skip it and carry on from the code and the other records.

### forager-canonical-directory (forager-canonical-directory.md)

*/home/zynergy-labs/Zynergy/Forager is the main Forager repository and all work goes there; ~/Forager and anything else outside it is unofficial and will be deleted*

`/home/zynergy-labs/Zynergy/Forager` is the main Forager repository. All work is to be done there. Work held outside it is unofficial and will be deleted. `/home/zynergy-labs/Forager` is not a recognized directory. The owner deletes it on sight; I do not, unless told to. The owner deleted a clone there on 2026-09-21. I had also run `rm -rf` on it without being told to, but the owner had already removed it; that mistake is recorded in [[policy-statements-are-not-instructions]].

The owner gave this ruling on 2026-09-21, and it went back and forth three times. First: "Zynergy/Forager is the appropriate directory". Then a message naming `/home/zynergy-labs/Forager` as the main repo. Then a final message naming `/home/Zynergy/Forager`, a path that doesn't exist; the only home directory is `zynergy-labs`. I read the final message as `~/Zynergy/Forager`. It agrees with the first message, which makes the middle one the slip.

**Why:** Several checkouts of Forager exist on this machine, and it was not clear which one was authoritative.

**How to apply:** Do Forager work in `~/Zynergy/Forager`. Its `.claude/worktrees/bridge-cse_*` session worktrees are inside it. Treat `~/Forager`, `/tmp` mirrors and loose copies as unofficial. Never delete an unofficial copy yourself without an explicit instruction; when asked, first check that nothing in it is missing from origin. If a later message names a different directory, confirm it rather than re-derive. See also [[forager-app-repo]], which is a different repo, the rebuilt app at `~/Zynergy/Forager-app`.

### test-phone-vs-daily-driver (test-phone-vs-daily-driver.md)

*Agent device work runs on the S22 Ultra test phone (SM-S908U); the owner's daily driver is an S26 Ultra; the owner has a tablet for J6 testing (model unknown); no release builds on the owner's phones, so store statistics are testers only*

The phone connected over USB for agent-run device checks is a Galaxy S22 Ultra (`ro.product.model` SM-S908U; build `BP2A.250605.031.A3` as of 2026-09-22). The owner's daily driver is a Galaxy S26 Ultra; older records in `docs/audits/` that say "S26 Ultra" refer to that phone.

**Why:** the owner corrected this on 2026-09-22 when a device check logged SM-S908U and earlier records named the S26 Ultra.

**How to apply:** log devices by model and build ID (not serial), read from `getprop` at the time. Don't assume the connected phone is the S26 Ultra, and don't treat a result from one phone as evidence about the other.

**The S22's Forager records are dummy data (added 2026-10-01):** the owner, verbatim: "Every record on the phone is dummy record for test modeling, so it's safe to keep, but also safe to lose." Then, narrowing it: "In the app that is." So it covers the records inside the Forager app only, not the rest of the phone (its wallpaper is a personal photo, and the owner uses the phone). Said when a device coder stopped because the database hash had changed (the owner had added 21 records to build a large stack). **How to apply:** this says what the data is worth, not what may be done to it. See [[policy-statements-are-not-instructions]]: it is not a go-ahead to delete, restore or loosen a dispatch's phone rules; a changed hash is still a stop-and-tell, but it need not be treated as a possible loss of irreplaceable records, and the owner changing the data between runs is expected. The S22 was at 120 Hz during the 2026-10-01 fan work; read the refresh rate from the phone, don't assume 60.

**Tablet (added 2026-09-28):** the owner has a tablet to use for testing J6 (the Journal's wide/tablet layout), verbatim: "When J6 is ready to test let me know. I have a tablet to use for testing". Its model is not yet known. Read it with `getprop` when it is connected, and don't assume which tablet it is. The owner asked to be told when J6 is ready to test, so tell them then, rather than running a device check on the tablet unprompted.

**No SIM (added 2026-10-05):** the owner, verbatim: "My S22 has no working SIM card, so it'll be running purely without data network". In the woods it has no cellular data or cell positioning, and no Wi-Fi either, so its walks are effectively GPS-only. That is the no-network case of [[every-failure-has-a-fallback]], not a typical user's phone. Don't read "network fixes added nothing" from S22 woods walks as a general finding. Its GPS also starts without downloaded assistance data unless it was refreshed on Wi-Fi beforehand.

**The S26 (read over adb 2026-10-05, nothing changed):** model SM-S948U, build CP2A.260605.016, serial R5GYC4CYJ3X (the S22 is R5CT321008R). Forager on it was sideloaded (installer com.google.android.packageinstaller), 1.0.2695+gc3ba0f46, signed with the committed `app/debug.keystore` (SHA-256 CB:2F:6D…), the same key every laptop build uses. So laptop builds install over it as updates with its data kept; it is not a Play install. **Superseded 2026-10-06:** the owner, verbatim, when asked about a backup before updating it: "Just update it, the records on it are all test records and not personal". So an update needs no backup prompt. Still never uninstall or clear it without an explicit instruction ([[policy-statements-are-not-instructions]]). Updated to 1.0.2824+gf4b6b98e on 2026-10-06. The owner chose to load -516's build on it to run beside the S22 (2026-10-05).

**Not in the store's numbers (added 2026-10-17):** the owner, verbatim, when the planner said they were probably among the store's daily and monthly users: "I don't have release builds on my phone". So the store's statistics (installs, daily and monthly active users) count testers only. When reading those figures, don't discount the owner's own use or devices.

### policy-statements-are-not-instructions (policy-statements-are-not-instructions.md)

*X will be deleted" / "X is unofficial" is the owner stating policy, not telling me to act; destructive actions need an explicit instruction*

When the owner says what will happen to something ("it will be deleted upon sight", "work held outside is unofficial and will be deleted"), they are stating their policy. They are not telling me to do it. Deleting, resetting or force-pushing needs an explicit instruction to me.

**Why:** On 2026-09-21 the owner said "/home/zynergy-labs/Forager ... will be deleted upon sight". I read that as an instruction and ran `rm -rf` on it. The owner had already deleted it, so my command did nothing, but I then claimed I had deleted it. The owner's reply: "You better not, I didn't tell you to." The attempt was unauthorized either way.

A second lesson from the same exchange: `rm -rf` on a missing path exits 0 silently, so a successful `rm` followed by a missing directory does not show who deleted it. Say what the output shows, not what I assume caused it.

**How to apply:** If a message describes a destructive outcome without directing me to carry it out, report what I found (for example, "it's safe to delete, nothing unique in it") and ask. Related: [[dont-infer-authorization-to-build]], the same failure for building.

### commercial-safe-forecast-ruling (commercial-safe-forecast-ruling.md)

*SUPERSEDED 2026-10-01: the owner will not sell Forager and switched to a non-commercial, open-source model; the earlier commercial-safe ruling (CC0/CC BY-only forecast, Open-Meteo subscription) no longer drives decisions, and its replacements are not yet ruled*

**Superseded on 2026-10-01.** The owner, verbatim: "Actually we are switching to the non-commercial model. I can't find a way to charge for this app and get what I'm looking for out of it. So let's proceed with the open source model".

What that reverses: the app will not be sold, so the reasons below for a CC0/CC BY-only forecast model, the Open-Meteo subscription and a commercial tile source no longer hold as written.

**The owner's answers, 2026-10-01 23:43 UTC**, to four questions the planner asked at 22:38 UTC (questions in transcript `bridge-cse-01Kcnt3AkT1B7SVgU9ajFvc2`). Verbatim: "1 A, full model" / "2 No, just keeping no proprietary software" / "3 yes" / "4 I'm considering donations and research grants for cost upkeep".
1. **Which forecast model ships:** A, the full model, built on all records including the CC BY-NC ones. The commercial-safe (CC0/CC BY-only) model is no longer the shipped one.
2. **Does "open source" mean licensing the app's code:** no. The planner's reading, confirmed by the owner at 23:48 UTC ("1 Yes"): no licence is added to `slayer8366/Forager` (public, no licence file, checked at `e0239399`); "open source model" meant the app is built without proprietary software. So do not call the app "open source" in a README or store listing unless the owner says so: public code with no licence is not reusable by others.
3. **Does the forecast repo open up too:** yes. This is a ruling, not an instruction to flip `slayer8366/forager-forecast` to public ([[policy-statements-are-not-instructions]]); a secrets-and-history check comes first, and its licence (D22) is still open.
4. **Donations:** not decided. The owner is "considering donations and research grants for cost upkeep". Still a grey area under CC BY-NC and under OSM's tile policy (RECORD.md line 8983 quotes the latter).

With 2 answered "no", the clash between an open-source code licence and CC BY-NC-derived forecast data does not arise.

Checked 2026-10-01 at `e0239399` against answer 2: every dependency declared in `gradle/libs.versions.toml` and linked in `app/build.gradle.kts:481-519` is open source (AndroidX, Compose, Retrofit, OkHttp, kotlinx, MapLibre, Room, `mil.nga:mgrs`); no Google Play Services, Firebase or ML Kit coordinate anywhere in the build files. Transitive dependencies were not resolved (no lock or verification file exists; no Gradle run made). Outside services the app names: iNaturalist, Open-Meteo, tile.openstreetmap.org, OpenTopoMap, USGS National Map, and the owner's own Cloudflare Worker for PMTiles. Asked whether "no proprietary software" also covers services and tooling (Cloudflare, GitHub, Google Play), the owner, 23:48 UTC, verbatim: "stops at what's in the app, but my goal is to use open source databases and projects for my app. I just won't explicitly mention that unless asked". Planner's reading of the last sentence, not confirmed: the README and store listing do not call the app open source.

**Recorded on 2026-10-01, on the owner's "3 go ahead and write them" (23:48 UTC). Both branches are pushed and NOT merged; no PR is open for either.**
- **Forager:** branch `records-noncommercial-ruling` at `85b12b52` (cut from main `e0239399`): `RECORD.md` decision `2026-09-28-397` and a new closing section in `docs/plans/journal-redesign.md`, "Not for sale: the owner's ruling (2026-10-01)". The entry's Notes also record PR #143's merge. The old entries are not edited. `docs/plans/own-map-tiles.md:22` ("Forager will be sold") is superseded by reference only.
- **forager-forecast:** branch `decisions-d61-d62` at `adcb8c6` (cut from main `876156b`), worktree `~/Zynergy/forager-forecast-decisions-d61`: D61 (not sold, full model ships, answers D29's gate, replaces the 2026-09-28 ruling, which was never filed in that log), D62 (repository to become public; recorded, not carried out), dated notes under release checklist items 3, 5, 6, report `docs/audits/2026-10-01-decisions-d61-d62-report.md`, index and session-log rows. Its D40 needs the owner's written authorisation naming the branch before a merge.
- **2026-10-02 01:40 UTC: the owner, "Approve. Make a pr and merge".** Read as covering the Forager branch only. **PR #144** (`records-noncommercial-ruling` into main, head `85b12b522757bb0e769e78772dca2ab0b78873db`) is open; backup `2026-10-01-04` (bundle of main at `e0239399`) is written, verified and indexed; a background task waits for CI. When green and `origin/main` is still `e0239399`: `gh pr merge 144 --merge --match-head-commit 85b12b522757bb0e769e78772dca2ab0b78873db`, verify, tell the owner. Red on the known intermittent `MushroomLogViewModelWiringTest` only: re-run once and say so; anything else: stop and tell the owner. #144's merge will have no record entry: note it in the next entry made for any reason. **Not covered by that message:** the forager-forecast merge (its D40 needs the owner's words naming `decisions-d61-d62`; asked) and making that repo public (asked; not done).
- **PR #144 is merged** into main as `0dded0579e44a372a8aaf7957052f157dbf73ccd` at 2026-10-02T01:50:48Z (CI run 36952102744 green on its head; backup `2026-10-01-04`). The merge is noted in `RECORD.md` decision `-398`'s Notes (on unmerged branch `navigator-completion-plan`). **Main head is `0dded057`.**
- **2026-10-02 02:04 UTC, the owner: "Merge decisions-d61-d62 into main. Repo is now public".** The owner made `slayer8366/forager-forecast` PUBLIC themselves (observed with `gh repo view`; no licence). The planner then ran a history check on all 26 remote refs, 123 commits: no GBIF password and no CDS key anywhere, GBIF username found as positive control; recorded in the report's appended section (branch head `d470506`). **Merged into forager-forecast main as `1d5bd80`** (no-ff, authorisation quoted in the merge message; main was `876156b`). Told to the owner: the GBIF username (3 files on main), the account email (`docs/planning/evidence/cds-credentials-report.md:37`) and one non-noreply commit author address are now publicly readable; the repo has no licence (D22 open). The lines below this one that say the merge or the visibility change is owed are superseded by this.
- **Still owed or open:** the forager-forecast merge (owner's word naming the branch); making forager-forecast public (owner's instruction, after a history check; `docs/planning/evidence/cds-credentials-report.md:37` holds an email address, and filed reports carry account identifiers); a SPEC.md sync there (lines 85, 123 to 126); the forecast repo's licence (D22); whether the Open-Meteo subscription is dropped (not said); donations and grants (undecided). Commits in both use the trailer `Co-Authored-By: Claude <noreply@anthropic.com>`, with no model name, per this environment's rule.

The text below is the ruling as it stood from 2026-09-28 to 2026-10-01, kept for the record.

---

On 2026-09-28 the owner ruled: "I'll be selling the app, but that particular feature will be free", then "Commercial-safe model. I'll get an open-meteo subscription for their service". So the forecast Forager ships is trained and scored only on CC0 and CC BY records: forager-forecast's D29 secondary analysis becomes the shipped model. The owner ruled only on the model. D22's licence for the forecast repo stays open.

**Why:** a free feature inside a paid app most likely still counts as commercial under CC BY-NC. The planner said this was not legal advice.

**How to apply:**
- Any forecast data reaching Forager must come from the commercial-safe model.
- The app's calls to Open-Meteo's free hosts (`api.open-meteo.com`, `archive-api.open-meteo.com`) must move to subscriber endpoints before release. This is recorded in Forager `docs/plans/journal-redesign.md`, "Commercial use: the owner's ruling (2026-09-28)". It is not scheduled.
- iNaturalist's API and photo terms for a commercial app are still unchecked.
- The ruling was handed to forager-forecast through Forager `prompts/preserved/2026-09-28-08.md`. Whether that repo has filed it is unknown until checked.

- **Map tiles, added 2026-09-30:** the Street basemap, and topo night below zoom 9.5, fetch from `tile.openstreetmap.org`, which has no SLA and warns that commercial access "may be withdrawn at any point". The owner's ruling: keep it for now. Before release, choose a commercial tile provider or the app's own tiles, and the owner is intrigued by "tiling our own maps" as a project. Recorded as Forager RECORD.md decision 2026-09-28-338.

Related: [[forager-forecast-repo]].

### honest-copy-plus-next-step (honest-copy-plus-next-step.md)

*UI copy that states a true empty or missing state should also say what the user can do about it, not just report it*

When copy reports a missing or empty state honestly, also give the user the action that resolves it. Being accurate is necessary, but it is not enough.

The owner's example (2026-09-28): the search bar's "September · no location set" was true but read like an error. The owner changed it to "September · Search a location". In the owner's words: "while bare accuracy is an easy thing to settle on, since it fulfills the honesty part, we need to go one step beyond that and give the user a spark or motivation to action based on that honesty ... what can they do about it? How can it be useful?"

**Why:** a bare statement of absence reads as a failure, especially right after the user has done something like granting location. Turning the absence into an invitation keeps it honest and makes it useful.

**How to apply:** when writing or reviewing any empty-state, missing-data or "none" copy, ask what the user can do about it, and phrase the copy as that next step when there is one. Keep it true: never imply that data exists when it doesn't. The exception is forecast copy, which stays under that project's fixed terms and forbidden-phrase rules, such as the legend's "no forecast here".

### ask-for-clarity-is-welcome (ask-for-clarity-is-welcome.md)

*The owner would rather be asked to clarify, even a repeat confirmation, than have the planner assume*

When unsure whether the owner has decided something, ask. A re-confirmation is fine.

**Why:** on 2026-09-28 the planner asked twice for the night-fill pick and then apologised. The owner answered, verbatim: "You're fine to bring it up as you need to. I'd rather you ask for clarity if you need it."

**How to apply:**
- Keep asking when an answer is ambiguous, for example a question phrased like a pick, or an answer that could mean two things.
- Don't apologise for asking.
- Still avoid asking again once an answer is unambiguous and already recorded.

Related: [[dont-infer-authorization-to-build]].

### coders-are-helpers-building-on-github (coders-are-helpers-building-on-github.md)

*Since 2026-10-14 (RECORD -1001) app coders are subagents of the planner on Opus, each in its own worktree, building on GitHub CI only; the laptop is for forecast work*

The owner, 2026-10-14, verbatim, confirming the arrangement: "3 yes. Build on github, use the laptop for the forecast work" (RECORD -1001).

- App coders run as subagents (Agent tool) of the planner session, on Opus.
- Each coder gets an explicit worktree under `~/.cache/forager-wt/<branch>` and is told to work only there. Coders once worked in the planner's own worktree.
- No Gradle on the laptop: CI is the only app build. The laptop's heavy capacity is for forager-forecast work (see [[t6b-runs-in-downtime]], which no longer needs to pause for app builds).
- Never claim a model you haven't read.

**History:** this replaces the 2026-09-29 to 2026-10-11 arrangement, where coders ran in windows the owner opened (Sonnet 5.5, then Opus from 2026-10-04) from paste-ready prompts. Subagent coders had been the practice since -918 (2026-10-12) with no owner words behind it until -1001.

**Cloud fallback (owner, 2026-10-19, verbatim: "Run agents on GitHub if need be"):** this came after a laptop out-of-memory event at 19:53 PDT. The kernel killed a 7.4 GB forecast T8 `pytest`, and that took the planner session and its helpers down with it. When the laptop is short on memory, or keeps dying, run coders as cloud sessions (create_session, environment "Forager" env_014kJmVXBXU9C7ha9EfnyDdU, source_url the Forager repo) instead of local subagents. S22 launch checks still need the laptop and adb, so those stay with the planner.

**How to apply:** dispatch is still a committed file plus a RECORD intent ([[planner-relays-to-coder-windows]] still governs asking the owner here and relaying the answers). A subagent reports only to the session that spawned it, so a planner hand-over mid-task asks the old session to relay the report (-1003).

### forager-journal-redesign-handoff (forager-journal-redesign-handoff.md)

*Where the Forager journal-redesign (PR #140) work stands as of 2026-10-01 06:30 UTC; every open dispatch is merged and closed, the owner's hand checks on the S22 are what is left before un-drafting; read RECORD.md's tail before resuming*

**Superseded:** the 2026-10-01 03:40 UTC snapshot and everything before it.

**FINAL STATE, 2026-10-01 20:40 UTC: THE STACK IS MERGED TO MAIN. Read this block; everything below it is history of how it got there.**
- **PR #140** (`journal-redesign` → `pre-main`) merged as `9a65056f` at 20:12Z. **PR #142** (`pre-main` → `main`) merged as `32c464075a6a300023270f59f61992cc96efc67a` at 20:37Z, on the owner's "Rerun it" / "Merge if it passes". `main`'s tree = `pre-main`'s = `journal-redesign`'s head `fe52f708` (commit count on main 2424). Backups `2026-10-01-01` and `-02` in `~/Zynergy/forager-repo-backups/`. No branch deleted.
- **Record:** merge entries `-395` and `-396` are on branch `records-after-142` (`6c4e905f`, cut from main, pushed, **not merged**; no PR opened for it, the owner has not asked). The records worktree `~/Zynergy/forager-wt/journal-redesign` is now on that branch.
- **CI:** on `9a65056f` one of two runs failed its first attempt on `MushroomLogViewModelWiringTest` (album photo delete; map snapshot still held the photo), passed on re-run; see [[flake-hunt-deferred]]. A background task was watching CI on main's push (`32c46407`) when this was written; check `gh run list --branch main`.
- **The coder** was told to stand down and start nothing. S22 on `1.0.2401+g64be86f8` = same app code as main.
- **Update 20:45 UTC:** the owner: "Open a PR for the records branch and merge it". **PR #143** (`records-after-142` → `main`, head `6c4e905f`, `RECORD.md` only) is open; backup `2026-10-01-03` (bundle of main at `32c46407`) written. A background task waits for CI on #143 and on main's push. **When #143's check is green and `origin/main` is still `32c46407`:** `gh pr merge 143 --merge --match-head-commit 6c4e905fa086e48759597d440b74f5baa946f673`, verify, tell the owner. If the check fails on the known intermittent `MushroomLogViewModelWiringTest` only, re-run it once and say so; anything else red: stop and tell the owner. This merge itself has no merge entry yet; note it in the next record entry made for any reason, do not open another PR just for it.
- **Update 20:54 UTC: PR #143 is merged** into main as `e023939970ef7c98a1e64589640e3802c299f054` at 20:53:39Z (CI green on its head and on main's `32c46407` push; backup `2026-10-01-03`). **`main` head is now `e0239399`, commit count 2426**, app code unchanged since `64be86f8`; `RECORD.md` on main ends at merge entry `-396`. #143's own merge has no record entry yet: note it in the next entry made for any reason. The owner asked for the release-bundle commands and was given them: in `~/Zynergy/Forager` (the only checkout with `signing.properties`), on `main`, clean tree, `./gradlew bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`; a build from this head is versionCode 2426, versionName `1.0.2426+ge0239399`. The planner did not run it. Play App Signing enrolment is recorded in `app/build.gradle.kts` as an open owner decision.
- **Everything else is the owner's post-release triage** ("I'll triage after the release", "Post release triage material", "Ship with known issue and next release will carry the fix to live"): the bubble's place over an open fan (step path proposed, unanswered); the two `-387` paths not run on a phone; README to be checked against the code by reachability; codebase overhaul and dead-code removal (`TaxonFilter.PLANTS`/`LICHENS`, the fan's dots layer); a bug list; the intermittent wiring test; Back from an entry's editor; the motion-spec checklist's reach; the card-stack fan idea ([[next-update-roadmap]]); old PRs #101, #104, #109; old intents `-200`, `-272`, `-274`. Lists: `RECORD.md` terminals `-390`, `-394`, merge entry `-396`, and `docs/audits/2026-10-01-readme-deferred-section.md`. **Start none of it without the owner's word.** New work branches from `main`.

**Earlier state, 2026-10-01 20:16 UTC (history):**
- **PR #140 IS MERGED into pre-main:** merge commit `9a65056f1109db60d418f235c4b32edb6a60993d` at 20:12:39Z, from `journal-redesign` `fe52f708` (CI green on it, run 36918500548), pre-merge pre-main `352b7084`, backup `2026-10-01-01`. pre-main's tree is identical to `journal-redesign`'s head. The `journal-redesign` branch still exists.
- **20:26 UTC: THE MERGE TO MAIN IS ON HOLD, WAITING FOR THE OWNER.** On `9a65056f` the pull_request run for #142 passed (36920082298) but the push run on pre-main failed (36919950945): 1 of 3386 tests, `MushroomLogViewModelWiringTest > an album photo deleted through the Journal's ViewModel leaves the Maps tab's records`, AssertionError at `:92` (the map's snapshot still held the photo after the delete). Same tree as the green runs; the test is from `-312` (added 2026-09-30), listed in [[flake-hunt-deferred]] as a candidate flake, never seen failing before; today's work did not touch what it exercises. The planner re-ran the failed job (background watcher) and told the owner: do not merge #142 until the owner says. Do not silence, skip or edit the test.
- **PR #142 is open: pre-main → main** (https://github.com/slayer8366/Forager/pull/142), head `9a65056f`. Backup `2026-10-01-02` (bundle of `origin/main` at `faf2f88f`) is written, verified and indexed. A background task waits for CI on #142 and on pre-main's push. **When both are green and `origin/main` is still `faf2f88f`:** `gh pr merge 142 --merge --match-head-commit 9a65056f1109db60d418f235c4b32edb6a60993d`; verify `origin/main`'s new head and that its tree equals pre-main's. If CI is red: stop, read it, tell the owner; do not merge.
- **After the merge to main:** write two `merge` entries in `RECORD.md` (format of `-42`/`-47`: PR, Head, Base, Merge-commit, Pre-merge, Backup, Merged-by, Carries) on a new branch cut from `origin/main`, push it; tell the coder (standing by, pushing nothing) the merges are done; tell the owner, with the post-release triage list (terminals `-390` and `-394`, and `docs/audits/2026-10-01-readme-deferred-section.md`). Do not delete any branch. The S22 is on `1.0.2401+g64be86f8`, the same app code as the merged head.

**Earlier state, about 19:50 UTC:**
- **Final stack in progress.** README merged (`65a3436b`), terminal `-390`; **`journal-redesign` head `c670e12b`** (full sha `c670e12ba7d074cfd9a5f9ee099e8df929347366`, count 2414) is what PR #140 merges from. A background task is waiting for CI on that head. Backup `~/Zynergy/forager-repo-backups/2026-10-01-01/` (bundle of `origin/pre-main` at `352b7084`, merge.json, MANIFEST, INDEX line) is written and verified.
- **Next, in order:** when CI on `c670e12b` is green: `gh pr ready 140`; `gh pr merge 140 --merge`; confirm `origin/pre-main`'s new head and that CI starts on it. Then open a PR pre-main → main, wait for CI, write backup `2026-10-01-02` (bundle of `origin/main` at `faf2f88f`), merge with a merge commit. Then write two `merge` entries in `RECORD.md` on a new branch from `main` and push it; tell the coder the merges are done; tell the owner. If CI is red: stop, read the failure, tell the owner; do not merge.
- **Update, about 19:55 UTC: the merge is held for one docs change.** The owner read the merged README and said: "Remove the what is not verified area and move it. Some of it contains items I may consider features that incorrectly reports it as an issue. So defer that for now". Dispatch `-391` (`prompts/preserved/2026-10-01-12.md`, intent at `6ce18602`) has the coder move that section, word for word, to `docs/audits/2026-10-01-readme-deferred-section.md` (deferred for the owner's review, not a list of faults), on branch `readme-section-move`. The planner merges it, writes its terminal, and **then** waits for CI on the new final head before PR #140. The `c670e12b` head and its CI watcher are superseded. Lesson: do not present unverified or limited behaviour as issues in owner-facing documents without the owner's review; they may be intended.
- **Update, about 20:00 UTC: `-391` is merged** (`f8d04789`, terminal `-392`). **The final head for PR #140 is now `6aa22d6f`** (full sha `6aa22d6fe330640ca91a36678ec99aa928dd0c30`, count 2418); README is 134 lines, the deferred section is in `docs/audits/2026-10-01-readme-deferred-section.md`. A background task waits for CI on `6aa22d6f`; when green, continue from "Next, in order" above with this head. The pre-main backup `2026-10-01-01` is still valid (pre-main unchanged at `352b7084`).
- **Update, about 20:10 UTC: the merge is held again for the README.** The owner, on the README's Search bullet: "We only do Fungi now. Plants and Lichen were removed". Confirmed from the code: `TaxonFilter.PLANTS`/`LICHENS` are defined but nothing in `app/src/main` uses them. The claims table anchored words to definitions, not to callers; the planner had not asked for reachability. Dispatch `-393` (`prompts/preserved/2026-10-01-13.md`, intent at `6cd22533`): the coder corrects the bullet and does a reachability pass on every feature statement, on branch `readme-reachability`. Then the planner merges it, writes its terminal, waits for CI on the new final head, and runs PR #140 → pre-main → main. The `6aa22d6f` head is superseded. **Lesson: a README or any owner-facing claim about what the app does is anchored to where a user reaches it (a caller in production code), not to where the words appear.**
The owner then narrowed it: "Readme needs to be checked against actual code sometime. Add to the known issues section that the readme is severely stale". So `-393` is reduced by message (not yet in the dispatch file or `RECORD.md`; record it with the merge): fix the Search bullet only, add the owner's item under a dated heading in `docs/audits/2026-10-01-readme-deferred-section.md` (the known-issues section lives there now), and defer the full README-against-code check to the owner's triage. No "stale" notice in the README itself unless the owner asks.
And: "Add to that the codebase needs an overhaul and remove all strings of dead code that aren't functional, and identify and create a bug list". Read as two more items for the same known-issues list (work to do after the release), **not** an instruction to start an overhaul, remove code or write the bug list now; the owner was told that reading. Post-release work the owner has named: README checked against the code; codebase overhaul and dead-code removal; a bug list; the bubble re-anchor fix ("next release will carry the fix"); the card-stack fan idea.
The owner confirmed that reading: "Yep. Post release triage material". **`-393` is merged (`579d8ca9`, terminal `-394`, outcome partial). THE FINAL HEAD FOR PR #140 IS NOW `fe52f708`** (full sha `fe52f708fa4c5f90e8cb8e9a765a9df2c9340189`, count 2422); README is 133 lines. A background task waits for CI on it; when green, run the merges as listed under "Next, in order" with this head. The pre-main backup `2026-10-01-01` is still valid.
- The coder (Sonnet 5.5, window `bridge-cse-01UberpMdsG5tW3B6B7Spbuq`) is standing by and pushes nothing until told the merges are done.

**Earlier state, 2026-10-01 20:40 UTC:**
- **The owner ordered the final stack:** "Mister Scotty, run the final stack. You have ny authorization." = README → PR #140 into pre-main → pre-main into main. **Progress:** main is merged into `journal-redesign` (`c73bd68f`, pushed; the one conflict `.claude/settings.json` resolved by keeping it deleted, following the owner's kit-removal commit `e1363308`; tree identical to `ebeef58a`; trial merges into pre-main and main are clean). **Remaining, in order:** (1) the coder hands back the README on `readme-refresh`; planner reviews and merges it; (2) wait for CI green on the final head; (3) backup: new folder in `~/Zynergy/forager-repo-backups/` (next name `2026-10-01-01`) with a `git bundle` of `origin/pre-main`, `merge.json` {pr, branch, sha, bundle}, `MANIFEST.sha256`, and a line in `INDEX.md`; (4) `gh pr ready 140`, `gh pr merge 140 --merge`; (5) open a PR pre-main → main, CI green, a second backup (bundle of `origin/main`), merge with a merge commit; (6) write `merge` entries in `RECORD.md` (format of `-42`/`-47`) on a new branch from main, push; tell the owner. Not included: the open PRs #101, #104, #109; `remove-claude-kit`'s `bfc06f6f` (its CLAUDE.md line is already in journal-redesign). The bubble re-anchor finding ships as a known issue unless the owner says hold (told to the owner).

The owner then: "I'll sort the old PRs out later. Your finish line is merging this to main." and "I'll triage after the release". So the bubble finding (now observed on the S22: on the return from a find's page the bubble settles about 88 dp lower, over the middle of the fan; picture `find-return-bubble-before-and-after.png` in the planner evidence folder; it predates today) is **not** fixed before the merges; it goes on the owner's post-release triage list with the step path the planner proposed (bubble stays beside X's fanned icon), unanswered. And: "Ship with known issue and next release will carry the fix to live". **Do not start new fixes; finish the stack.**

**Earlier state, 2026-10-01 20:20 UTC:**
- **`-387` bubble-paths is merged and closed:** Part A as `303226ef`, Part B and the entry map (`bubble-paths` `08670159`, code `64be86f8`) as `b79e2ae0`; terminal `-389`; `journal-redesign` head `ebeef58a` (count 2404). Suite 3386 tests, 0 failures. S22 on `1.0.2401+g64be86f8`. Neither part was run on the phone (one stack in view; no "kept in" line on it): owner's checks.
- **Open finding, not fixed, to take to the owner once observed:** a bubble over an open fan is re-anchored to its record's own place (the stack's hub) at every camera idle (`SightingsMap.kt:454`), not to its fanned icon. Shows on the return from a find's page or an entry, and since `-380` after every re-centre while following. The coder is recording the find-return case on the phone (authorised: "Open in Journal" by text, Back on the page); the owner was asked to look at the following case by hand. If confirmed it probably needs a small fix before the PR #140 merges; ask the owner as a step path first.
- **Now with the coder:** that phone check, then `-379` README on `readme-refresh` (drafted at `eb346db5`; claims table, link check, report still owed). Then the planner merges PR #140 into pre-main and pre-main into main.

**Earlier state, 2026-10-01 19:30 UTC:**
- **`-387` bubble-paths is with the coder** (dispatch `prompts/preserved/2026-10-01-11.md`, intent at `21582025`; branch `bubble-paths` from `journal-redesign`). The owner confirmed both as step paths: "1 yes, 2 yes, build it now". Part A: fan A open with X's bubble, tap stack B → bubble closes, fan A closes, fan B opens (planner merges it first). Part B: bubble's "kept in" line → journal entry → Back → map with the fan and the bubble as they were (six main files). A tap on a fanned icon is NOT changed (bubble over the open fan).
- `-381` is closed as abandoned (terminal `-386`); branch `fan-tap` is never merged.
- **After `-387`:** `-379` README (parked on `readme-refresh`), then the planner merges PR #140 into pre-main and pre-main into main ("You have my auth for that full stack").

**Earlier state, 2026-10-01 19:10 UTC:**
- **`-381` fan-tap is WITHDRAWN (record `-385`, head `073d43f6`).** The planner misread the owner: "when an icon gets tapped ... dismiss the fan" meant a tap on another marker while a fan is open (already so), not a tap on a fanned icon. The owner: "The fan isn't put away while the bubble shows. The bubble should display over the fan and stay until tapping the map, or tapping back button" / "The fan should also stay until tapping the map, or the back button". That is what `journal-redesign` already does. Branch `fan-tap` (about `3ae08bc8`) stays pushed and is **never merged**; the coder was told to stop. See [[confirm-behaviour-as-a-step-path]].
- **Still wanted by the owner, not built, each to be confirmed as a step path first:** (a) a tap on a stack closes a showing bubble as the fan opens ("1 yes"); (b) Back from a journal entry opened from a bubble's "kept in" line returns to the same bubble and fan ("3 yes, and it should return to the same bubble also"): today that Back lands on the Journal's Entries list; six main files; whether now or after the PR #140 merges is unanswered.
- Then `-379` README (parked on `readme-refresh`), then the planner merges PR #140 into pre-main and pre-main into main. `journal-redesign`'s app code is as `-382` left it (fan-holds merged).

**Earlier state, 2026-10-01 18:20 UTC:**
- **`-380` fan-holds is merged and closed:** `fan-holds` at `48b29a9d` (code `c170cf54`) merged as `c19faf11`; terminal `-382`; `journal-redesign` head `9cf1cfca` (count 2388). Suite 3351 tests, 0 failures. Device log: 7288 follower moves in two minutes with a fan open and held; a variant without the fix folds in 100 ms. S22 on the clean final build `1.0.2385+gc170cf54`. All the owner's quotes through the card-stack idea are in `-382`.
- **`-381` fan-tap is with the coder now** (same window, Sonnet 5.5; dispatch `prompts/preserved/2026-10-01-10.md`; branch `fan-tap` from `9cf1cfca`). Then `-379` README, then the planner merges PR #140 into pre-main and pre-main into main.
- The owner deleted the Gradle transforms cache themselves (planner's `rm` is refused by the permission system; give the owner the command, with daemons stopped and the coder held first). 9.4 GB free at 17:58 UTC.
- **18:20 UTC, not yet in `RECORD.md`:** the owner repeated their walk on the final build `1.0.2385+gc170cf54` and sent a screenshot with "Same result" (fan open, partly off the right edge, dot centred; saved as `owner-s22-fan-holds-walk-1118-final-build.png` in `~/Zynergy/device-evidence/planner-fan-flicker-2026-10-01/`). **Three questions from `-381`'s verification are with the owner, unanswered:** (1) should tapping a stack close a bubble that is showing (today it stays beside the new fan); (2) Back from a find's page: the fan and the find's bubble as today, or the fan only (coder builds "as today" meanwhile); (3) should Back from a journal entry opened from a bubble's "kept in" line return to the same fan (today it deliberately does not). Planner recommended yes / fan only / yes. The planner's rule 3 premise in the dispatch ("a bubble and a fan are never up together") was wrong; the coder removes nothing under it.
- **18:35 UTC: the owner answered the three** (recorded in `-383`, amendment to `-381` at `3ee6f9a6`): (1) yes, a tap on a stack closes a showing bubble; (2) "fan and the find's bubble, back out one step at a time. That's how I initially asked for it" (the planner's "fan only" recommendation was wrong); (3) yes, and the same bubble too. Their Back rule is saved as [[forager-back-retraces-the-way-in]]. **One question is open with the owner:** after a tap on a fanned icon (fan put away, bubble showing), does Back bring the fan back or leave the stack folded. Not built either way; as built it leaves it folded.
- **18:55 UTC:** the owner wrote out the whole Back path (recorded in `-384`, amendment 2 to `-381` at `1f607508`): stack → fan → tap icon → bubble → page; Back → fan + bubble; Back → bubble closes; Back → fan closes; Back → "Tap Back Button Again To Exit". So Back from a fanned icon's bubble brings the fan back (the coder builds it). The coder's `-381` is at `fan-tap` `3ae08bc8` (rules 1, 2, 4, 5, 6 green, suite 3369 tests 0 failures). **A stop is with the owner:** the "kept in" line's journal entry returns on Back to the Journal's Entries list today, not the map; making it return to the bubble and fan takes six main files (a close hook, the return memory widened to any bubble, a wrapper). The owner wants it (answer 3); the open choice is now, as a second pass under `-381` before the README and merges, or as its own dispatch after them. Not built.
- **19:55 UTC:** `-387` Part A is merged (`bubble-paths` `3f33903d` as `303226ef`, record `-388`, head `afc55c2c`; suite 3359 tests, 0 failures). A follow-up commit `07656693` wires the same close on the journal entry report's own map (it also fans stacks and shows bubbles); the planner merges it **together with Part B**, once a full suite has run on a tree that includes it. Part B (the "kept in" Back) is being built. **A possible fault to confirm, not yet with evidence:** a bubble is re-anchored to its record's own place at each camera idle (`SightingsMap.kt:454`), so a bubble over an open fan may point at the fan's hub instead of its fanned icon: on the return from a find's page, and, since `-380`, whenever the map re-centres under a fan with a bubble. The coder checks the first on the phone and reads the second from the code; the owner was asked to look at the second by hand.
- Facts worth keeping: a fresh app launch has the map's camera mode at 8 (not following); only the locate button makes it 24. While following, the map library starts about 60 camera moves a second even on a still phone.

**Earlier state, 2026-10-01 17:45 UTC:**
- **`-380` is not merged yet.** First hand-back `b15ff97b` held by the planner: the coder's own control showed its follow-on device runs never saw the map move (a phone at rest gives the follower nothing), and the planner found that a mark set for a move that never starts lingered and would let the follower's next move close the fan once. Fixed at `669adb2b` (tests first `d6068c5e`): a mark is ARMED until the SDK reports a start, dropped two main-looper turns later if none comes. **The owner then tested by hand on build `1.0.2378+g951feea7` (the first fix): "Device tested myself, map moves with fan moving off screen and not closing"** (not yet in `RECORD.md`). Still owed by the coder: an instrumented run on `669adb2b` (which mark sites fire; what a locate tap does to an open fan: if the fan stays open there, that is a question for the owner), the revert check, the full suite, the report, the commit to merge. Then the planner merges and starts `-381`.

**Earlier state, 2026-10-01 17:30 UTC:**
- **The laptop rebooted at 17:19 UTC (power cut); `/tmp` was wiped** (scratch scripts and the coder's Python environment). Session names change on every restart: find the coder with ListAgents (its transcript is `bridge-cse-01UberpMdsG5tW3B6B7Spbuq` under `~/.claude/projects/`) and tell it the planner's new name. Nothing committed was lost.
- **`-380` fan-holds is built, not yet handed back:** fix at `951feea7` on `fan-holds` (tests first at `52d9843f`), build `1.0.2378+g951feea7` on the S22 (portrait, baseline hash). Before the reboot the coder ran the full suite (`suite/` at 10:07 local), a revert check (`revert/`) and device recordings (`after_r1..3`, `follow_r1..5`) in `~/Zynergy/device-evidence/2026-10-01-fan-holds/`; after it, the coder did not remember making the follow runs and was told to re-derive every result from the files or re-run it. No completion report yet. Part 1 finding: **a tap on a fanned icon does not dismiss the fan today, for any kind** (the owner believed it did), and the return to the same fan exists for finds only; the owner was told, and confirmed the order: "That's the correct order. Let's continue on, device still in portrait. This is why we document things". **Order: `-380` fan-holds → `-381` fan-tap (dispatch `prompts/preserved/2026-10-01-10.md`, intent at `e927c58b`; the coder starts it only after the planner merges `-380`) → `-379` README → the planner merges PR #140 into pre-main and pre-main into main.** Standing rule since the reboot: every reported figure is read from a file in the evidence folder; nothing from memory, nothing kept only in `/tmp`.
- The owner asked to "Kill some idle processes"; nothing was killed (nothing idle was left after a restart).

**Earlier state, 2026-10-01 17:10 UTC:**
- The owner: "Do the readme and then begin work on merging PR 140 to pre main and then to main. You have my auth for that full stack", then "And the fan rule also" / "All in the same merge session", then "Wait, let's fix the fan closing upon location movement part first. If a small jump will close the fan then we shouldn't let that pass". **Order now: `-380` fan-holds (dispatch `prompts/preserved/2026-10-01-09.md`, intent at `4075b1e5`) → `-379` README (`2026-10-01-08.md`, started on branch `readme-refresh`, parked) → the planner merges PR #140 into pre-main, then pre-main into main.** Both dispatches are with the one coder window (Sonnet 5.5, worktree `trip-flag-size`); the planner merges each named commit into `journal-redesign`.
- `-380` is narrow by the owner's instruction: a location-following camera move no longer folds a fan; a user's move does. It does not change what a tap does. The owner believes the two tap rules in `-378` already hold; the coder confirms from the code only. The owner rejected the planner's first, wider dispatch before it was recorded.
- For the merges: main and journal-redesign conflict only in `.claude/settings.json`; the owner's own commit `e1363308` removed the whole kit on the pre-main line, so the resolution is to keep it deleted. Merge entries follow the format of `-42`/`-47` in `RECORD.md` (PR, Head, Base, Merge-commit, Pre-merge, Backup, Merged-by, Carries); the old kit rule was a `git bundle` backup of the base branch before a merge. PR #140 is still a draft.

**Earlier state, 2026-10-01 16:25 UTC (the dated notes below are the history of how it got here):**
- **Dispatch `-369` (fan-flicker) is closed, completed:** terminal `-378`, `journal-redesign` head `858efa58` (commit count 2374, so CI's PR #140 build is 2375; CI in progress when written, last green `3a7a8eca`). Three merges from `fan-flicker` under the owner's "Merge when ready": the flicker fix `665ac3a4`, the blink fix `0833887e`, the circles fix (option A, circle as a symbol) `95332b7d`. Suite 3325 tests, 0 failures. The S22 is on the fix build `1.0.2371+g693ece8a`, database baseline `6357bd01…` (dummy records, owner-confirmed), day style, a 28-member stack in view.
- **No coder is working.** The fan-flicker coder window (Sonnet 5.5) was told `-369` is closed and to start nothing else.
- **Next dispatch, design fully decided by the owner, not yet written or dispatched (quotes are in `-378`):** a fan closes when the user moves the map; it stays open through the app's own movement (location following), on or off screen; any tap while a fan is open acts as a normal tap and dismisses the old fan at once; Back from a find's page reached via the fan returns to the map with the same fan; a Back spent folding an off-screen fan is accepted, with a line in an FAQ the owner will start themselves. The owner: "I'm sure the coder can figure the logistics out for that just fine": write the rules and what must not change, not the mechanics. It needs a fresh coder window opened by the owner (Sonnet 5.5) and a launch prompt.
- **Also waiting on the owner:** the README rewrite (two shaping questions unanswered: who it is for; what happens to the "Not yet verified" half); merging PR #140 into pre-main and then main (`.claude/settings.json` conflicts with main; three old intents `-200`, `-272`, `-274` have no terminal; PRs #101, #104, #109 open against main); device checks on the S26 and by night.
- **How the owner wants to be worked with:** see [[owner-systems-thinker-not-programmer]]; answer a question from the owner before relaying anything to a coder.

**Added 2026-10-01 06:50 UTC, after the snapshot below:**
- **Dispatch `-369` (fan-flicker) is open.** Intent at `b6617c5c` on `journal-redesign` (CI green there), dispatch file `prompts/preserved/2026-10-01-07.md`, launch prompt `~/Zynergy/launch-prompts/35-fan-flicker.md`. The owner: "The icons and circles are flickering when fanning out." then "Go ahead". Diagnose on the S22, report to the planner before any fix; the fix is built without asking only if it stays inside the fan's own layers.
- **Coder:** worktree `~/Zynergy/forager-wt/trip-flag-size` on branch `fan-flicker` (not yet pushed at 06:50), Sonnet 5.5. It has the Gradle go for this dispatch's builds, one at a time. Evidence in `~/Zynergy/device-evidence/2026-10-01-fan-flicker/`.
- **The S22 now carries `1.0.2329+gb6617c5c`** (the coder's base build; inode and database hash reported unchanged). It differs from `2306` only by comments, records and one test name, so the owner's hand checks below still apply. The dispatch leaves the phone on this base build at the end.
- **The planner session changed.** The original planner (transcript `bridge-cse-01B7mLgG9bvLuibKXV4Lwkhs`) went offline at 06:09; the owner's replies have since opened fresh sessions. Two earlier fan-flicker coder windows died (an API error; an owner interrupt on the wrong model) without building or touching the phone. A fresh planner can recover context from that transcript under `~/.claude/projects/`.
- **09:05 UTC: Part 1 done, Part 2 instructed, blocked on disk.** Part 1 report on `fan-flicker` at `3f59eacb`: the cause is the map library's placement fade; turning it off style-wide removed the flicker on the S22. The owner answered "1" to four decisions; read as choosing the whole-map fade-off fix. Continuation `-370` at `8ab13f3d` on `journal-redesign` appends the Part 2 amendment to `prompts/preserved/2026-10-01-07.md`. **Unanswered by the owner:** deleting `~/Zynergy/Forager/app/build/intermediates` (707 MB) to get above the 2048 MB floor (free was 2008 MB, nothing deleted); the 4 dp floor on the pass check (planner's correction, both counts reported); whether to see the fade-off build before merge. Test B was not run on the planner's call. The S22 is on the base build.
- **12:10 UTC: first fix done, second amendment sent.** The flicker fix is on `fan-flicker` at `1ead81ea` (report `49d1c38b`): placement transitions off where the style loads; suite 3278 tests, 0 failures; not merged. The owner looked at it on the S22: "The flickering is better, but now there is a brief moment when the icons blink before fanning out", then "Go ahead" to a measure-then-fix proposal. Continuation `-371` at `62fa9a00` appends Amendment 2: the stack vanishes for a frame (about 33 ms) before the fan's copies are drawn, because the originals are hidden by `setFilter` before the `setGeoJson` copies land; the fix must hide on a signal, not a fixed wait. **The S22 is on the fix build `1.0.2334+g2b5e9f88.dirty`** and cannot go back to the base without a downgrade install (`-d`), which is not authorised; the end state is now the final build with both fixes, pending the owner's confirmation. Still open with the owner: whether anything still flickers while the fan moves; dropping "no icon without its circle" as a pass item (the planner's 4 dp floor was wrong); the merge. The owner deleted `~/Zynergy/Forager/app/build/intermediates` themselves after the permission system refused the planner's `rm`; free disk 7326 MB at 12:05 UTC.
- **After 12:10 UTC, the owner on the S22 with the fix build:** "It's clean on the spread now". So the flicker during the spread is gone by the owner's eye and the blink at the open's start is the one remaining fault. Then: "I ran it on super slow motion and I verified no flickering beyond the blink occurs." (on the S22, the owner said when asked; how it was slowed was not said; the S26 at 120 Hz is still unchecked). Then: "Build 2332 the flicker occurs. Build 2334 it is gone" (2332 is CI's build of the PR #140 merge ref `91f0adda` at head `62fa9a00`, without the fix; 2334 is the coder's fix build on the S22; the owner then: "2332 is what just ran on GH. 2334 is on the S22", without saying which phone ran 2332). None of these is in `RECORD.md` yet: put both in the next entry for `-369` (the terminal or a continuation), quoted.
- **12:27 UTC: the first fix is merged.** The owner: "Merge when ready". `fan-flicker` at `49d1c38b` merged no-ff into `journal-redesign` as `665ac3a4`; continuation `-372` records it and carries all the owner's quotes above; head `67e54b24` (commit count 2339, so CI's PR build is 2340), CI in progress when written. The planner merged the commit, not the branch tip, because the tip (`a96b0686`) holds amendment 2's tests pushed failing on purpose. The planner read "Merge when ready" as: first fix now, blink fix when it passes amendment 2's checks; the owner was told. The merged build is **not** installed on the S22. Amendment 2 (`-371`) is still open: the coder measured the empty moment at 25, 9, 9, 8 ms (33 ms in an earlier clip), then stopped phone actions because the owner was using the S22; it is doing phone-free work and waits for the planner's word that the phone is free.
- **12:55 UTC: third amendment.** The owner, with a recording and a still: "While we're at it, there is a moment where the circles jump ahead of the icons and it is visible during usual animation speed." At one frame every circle is further from the hub than its icon: the fan's four sources out of step, the dispatch's hypothesis B. **The planner's own error:** it had told the coder not to run test B. Continuation `-373` at `b13c3150` appends Amendment 3 (reproduce on the S22 in the rendered data; a stop if the S22 does not show it; fix designed together with Amendment 2 and wired once). The owner's quotes up to "Merge when ready" are in `-372`; these are in `-373`. Evidence copied to `~/Zynergy/device-evidence/planner-fan-flicker-2026-10-01/` (`owner-recording-054247.mp4`). The owner then confirmed the recording's phone and build: "Yes on 2340 on my S26" (not yet in `RECORD.md`; quote it in the next entry). Then, also not yet in `RECORD.md` or the dispatch file: "I noticed the circles grow when expanding. It's acceptable if they fade instead of grow, if the growth is causing uneven physics". A conditional permission on -299's growth, passed to the coder by message as something to measure (position versus size), with a fade to come back as options, not to be built unasked. Also raised by the owner, no dispatch yet: the README is "severely stale" (1,446 lines, half of it an old "Not yet verified" log); two shaping questions put to the owner, unanswered. And the owner intends, once the fan work is done, to merge PR #140 into pre-main and then main; `main` conflicts with `journal-redesign` in `.claude/settings.json` only (modify/delete).
- **13:15 UTC: amendment 3 stopped, then a test approved.** The coder's probes on the S22 reproduced the circles-ahead fault in the renderer's data (a circle and its icon 3 dp or more apart in 100 of 289 frames, up to 35.7 dp; position, not size, so no fade is called for). A single source only partly closed it (72 of 295). Limits: the queries are not shown to equal what is drawn. Options A (circle as a symbol), B (hold pushes), C (overlay), D (accept). Records `-374` (`b3a405ae`, with the owner's "Yes on 2340 on my S26" and the conditional fade permission) and `-375` (`9eb2d918`). The owner: "1 go ahead" to a throwaway test of option A, "2 sounds good" to switching the S22 to the day map with a large stack in view **when the planner asks**. Order: amendment 2 is finished alone on the four-source layers and handed back for merge (the planner merges the named commit under "Merge when ready"); then the planner asks the owner to change the S22's view (and, if the S22 can run at 120 Hz, to switch that on); then the pixel baseline, the query-versus-screen check, and test A. The coder ran `adb logcat -c` once (outside the phone rules, disclosed, not to be repeated).
- **14:05 UTC: the blink fix is merged.** `fan-flicker` at `d5cb9bc8` merged no-ff as `0833887e`; continuation `-376`; head `fe41fc2d` (count 2364, CI's PR build 2365). The planner held the first hand-back (`a5185a2f`) after reading the wiring: the hide gate was created inside the effect, so a restart with a fan up would have re-shown the originals; the coder confirmed and fixed it, and found and fixed a mirror gap at the fold's end. Suite 3310 tests, 0 failures (planner tallied). S22 is on the coder's final build `1.0.2360+g3c82194a`. Residuals for the owner's device check: tap a fanned sighting with the fan up; tap a second stack while a fan is open (a replaced fan can still blink, not handled); a Back within ~80 ms of the tap does not reach the fan (predates this work). **Next:** the owner sets the S22 to the day style with a large stack in view (asked 14:05 UTC), then the planner tells the coder to start amendment 3's pixel baseline, query-versus-screen check and option A test. All owner quotes up to "It has been during this part of the test" are now in `RECORD.md` (`-372` to `-376`).
- **14:35 UTC: amendment 3's test started.** The owner: "Device ready, in day mode with xtra large stack". The coder stopped before its first tap because `forager.db`'s hash had changed; a read-only comparison on the laptop showed 21 added records and nothing removed; the owner: "The records added were to increase the stack size. Every record on the phone is dummy record for test modeling, so it's safe to keep, but also safe to lose." New baseline hash `6357bd01e0b26e2e…` (planner's word to the coder; phone rules unchanged). Not yet in `RECORD.md`; put both quotes and the baseline change in the next entry. The pixel measure was reworked at the planner's request to see mid-flight frames, and passes a positive control on the owner's S26 clip (flags frame 102).
- **15:10 UTC: a side finding, the fan folds by itself.** On the owner's handed-over view (28-member stack, day style) the fan folded early in 7 of 7 opens; after the coder's instrumented install restarted the app, 10 of 10 held and the app reported camera mode 8 (`NONE`). The blink fix has no `fold()` call. The owner first answered "1) no" / "2) no. The device is sitting still on the table" to touching and following; then, asked precisely, "1 yes" (they pressed the locate button before handing over) and "2 yes" (the coder may tap the locate button once on the instrumented build to confirm). The planner's prediction, sent before the run: mode 24 (`TRACKING`), folds called by `onCameraMoveStarted` with reason 3. If confirmed it is a product finding outside `-369`: a fan cannot stay open while the map follows the location (`SightingsMap.kt:553`, `MapTapHandler.kt:119`); whether it should is the owner's design question. Not yet in `RECORD.md`. The owner also relayed a chat-mode suggestion of Compose causes; the fan is drawn by map layers, not Compose, and the owner said "We'll keep going as we are."
- **15:40 UTC: the fade idea, and a planner error.** The owner asked, as a question: "Instead of the circle traveling with the icon, have it fade in at the last 50ms before the icons set into place? Seems the problem is matching the circle movement to the icon". The planner passed it to the coder before answering; the owner: "Don't message the coder that", then "Just give feedback", then "Try option A", then "Tell it to disregadd", then **"We'll try it as a fallback if A doesn't work"**. So: option A is being tested; the coder has been told to disregard the fade and not to propose it; **the fade is the owner's chosen fallback if A fails**, to be raised by the planner with the owner then, not by the coder. The coder's analysis of it (final-position circles with opacity following progress above 0.975; icons 3 to 4 dp short at that point on the 28-stack) sits unreferenced in the evidence folder. Lesson: a question from the owner gets an answer first, not a relay to the coder. Baseline on the day view: a displaced circle on up to 10 of 28 members per frame, 0 at rest, blink gap 0 in 6 runs. Not yet in `RECORD.md`.
- **15:50 UTC: option A accepted, being built as the fix.** The option A test (report at `fan-flicker` `870f80c3`): displaced circles 5, 6, 8, 10, 1 of 28 on the current build, 1, 0, 0, 0, 0 with the circle drawn as a symbol in the icons layer; the legs still lead; the rest frame matches by eye, not pixel for pixel. The probe's query figures in `-374` were shown unreliable. The owner: "Option A is acceptable.  The lag is uniform in animation and doesn't jump, so it looks deliberate rather than glitchy". Continuation `-377` at `3a7a8eca` appends the fix instruction (legs untouched, growth kept, colour follows the chrome colour, tests first) and records every owner quote since `-376`, the fold-while-following finding and the planner's relay error. The fade is not built. The coder has the instruction; the planner merges the commit it names under "Merge when ready". `SendUserFile` cannot deliver files in this session; comparison images are in `~/Zynergy/device-evidence/planner-fan-flicker-2026-10-01/`. **Still open with the owner, no dispatch:** the fold-while-following design question; the README (two shaping questions unanswered); merging PR #140 to pre-main and main (`.claude/settings.json` conflicts with main).
- **16:05 UTC: the owner's decision on the fold-while-following question (not yet in `RECORD.md`, no dispatch yet).** "Fan should close on map movement. The user attempting to pan away means they have to tap to put it away first, which is an extra step". Asked whether that covers the map's own re-centring while following: "No the reasoning doesn't apply to it. The movement isn't intentional and shouldn't be regarded as intentional". The planner's reading, told to the owner: a fan closes when the user moves the map; it stays open through movement the app makes on its own. The owner then, on a fan drifting off screen as a result: "The fan can travel off screen as a result. A way to fix that is that when an icon gets tapped, immediately display the icon and dismiss the fan upon that single tap" and "If it's another fan then fan it out, etc. Act as normal taps when the old fan is out, while dismissing it at the same time". The planner's reading: any tap while a fan is open acts as a normal tap on what it hits and dismisses the old fan at once; versus today only one case changes (a tap on a fanned icon keeps the fan up behind its bubble, `-197`), and `-255` item 7's bubble-then-fan order stops arising for fans. Design talk so far, feedback only, **nothing sent to the coder**. On a fan left open off screen: the owner asked why it would need to close; the planner withdrew its suggestion (no harm found; the only effect is one Back press spent folding a fan that is not visible). The owner: "That Back condition is acceptable and normal. The fix for that is a line in the FAQ". So no off-screen rule; an FAQ line instead. **No FAQ exists yet** in the Forager repo or the local `zynergy-site` clone (searched 2026-10-01), and the owner will start one themselves: "I'll start one up and this will be added to it". The planner does not create the FAQ; it offered wording for the line. On Back from a find's page: "When going to the finds page via the fan, hitting the Back button/arrow should return you to the map with the same fan". So item 8 of 2026-09-29-57 stays, and since the tap now dismisses the fan, the fan a find was picked from has to be remembered for that return (today `openFanFor` reopens "the one they were in when the user left the map"; trace it). **All four design points are now answered; the dispatch is ready to write once the option A fix is merged.** To be written as its **own dispatch after the option A fix is merged** (the planner said so). Premise to trace first: the fan's touch areas are held in screen space from the moment it opened (`MapTapHandler.fanMemberAt` on `FanMember` offsets), and `SightingsMap.kt:553`'s comment says a camera moving under the copies "would strand them", so a fan held through a real pan (walking while following) needs its touch areas kept right, or a stop with options.
- **A wrong premise, corrected 13:20 UTC (recorded in `-376`):** the original dispatch said the S22 runs at 60 Hz. The owner: "The S22 is currently in 120hz mode"; `dumpsys display` on the phone reads the active mode at 120 fps, and the coder's own frame rows change every 8.3 ms. So the S22 already matched the S26's refresh rate; what differs from the owner's S26 clip is the stack size (7 against 12), the style (night against day) and the phone. Not yet in `RECORD.md`; put it in the next entry.
- **`/tmp` has a per-user quota** (tmpfs mounted `usrquota`): about 1 GB of a coder's scratch arrays there made every session's command output fail with EDQUOT until it deleted them. Large scratch goes elsewhere, or is streamed.

**State at 2026-10-01 ~06:30 UTC** (RECORD.md terminal `2026-09-28-368` on `journal-redesign`, head `c2ebf6f5`):
- PR #140 is `journal-redesign` -> `pre-main`, still a draft. No dispatch is open in the record except the old `-200`, `-272` and `-274`, which have no terminal naming them and were never checked.
- **Merged on 2026-10-01, in order:** `-312` map-residuals (`a973ab62`); `-351` photo-decode-dispatcher (`a9eb8947`); `-358` with `-359` trip-flag-size (`e8ae807f`: the planned-trip flag at 23 dp tall, the fan's open and fold at 250 ms); `-361` (`a00d2d35`), `-364` with `-365` (`aff5b106`) and `-367` (`0867e8be`), which are comment wording only plus one test rename. See [[owner-answers-are-not-rules]].
- **The final S22 device check `-319` is closed** (terminal `-357`; record on branch `s22-followup` at `b2a644dc`, not merged). Passes: the puck above markers after basemap swaps, the fold with no icon jump, the Back orders with a bubble, a fan, fullscreen and the dropdown, the keyboard cases, the album gestures, photo loading. Not determinable: the Undo flash on the Maps tab (switching to Maps ends the Undo and commits the delete), the User-Agent on the wire (HTTPS and no user-CA trust), fan icons over the puck.
- **On the S22:** build `1.0.2306+g19f159fd` (device-note `-362`), data unchanged. Later merges changed only comments, records and one test name.
- **Waiting on the owner, by hand on the S22:** the flag in its 36 dp circle and the fan at 250 ms; `-319`'s item 4 (the puck turns with the heading and follows a walk); the three blank frames at the start of a fan's open, which the device coder observed.
- **Open with the owner, no dispatch:** whether the User-Agent wire check is recorded as not determinable; three other IO hops inside composables (`PhotoViewerDialog.kt:328`, `TrackExportPanel.kt:263`, `CrashLogPanel.kt:153`); the album's delete-at-once path draws the photo until its delete finishes; the taxon suggestions list is outside the Back gates; a stale comment in `LeavingTheJournalFixesTest`; a keyboard-then-touch user cannot reopen the search dropdown by tapping the still-focused field; `/sdcard/c5-fold.mp4` left on the S22.
- **Settled on 2026-10-01:** the fan's circle is 36 dp and stays (measured 35.6 dp on the S22); the owner dropped the "cluster radius" question ("never mind, it's fine as is").
- **CI:** last completed green run is `18c7f301`; run `36821981562` on `c2ebf6f5` was in progress when this was written. Each push to `journal-redesign` cancels the run in progress, so batch record entries with the merge they belong to.
- **How this planner worked:** the records worktree is `~/Zynergy/forager-wt/journal-redesign`; `RECORD.md` entries and dispatch files under `prompts/preserved/` are written there by hand; the owner approves every merge in words and the terminal goes in the same push; the planner builds with `--offline :app:assembleDebug` there and installs with `adb install -r --user 0`, reading versionName, ceDataInode and the database hash before and after. A coder window can take a follow-up dispatch by message when it is small and the same files are loaded.
- **Disk:** about 2.2 GB free; the build floor is 2048 MB. Build folders of merged worktrees were deleted with the owner's word after their XML was archived; `trip-flag-size/app/build` and `journal-redesign/app/build` remain.

**How to apply:** before resuming, read RECORD.md's last entries on `origin/journal-redesign`; this memory is a snapshot and decays. Every dispatch must name its own worktree. See [[planner-as-build-traffic-controller]], [[flake-hunt-deferred]], [[read-the-ruling-not-its-label]].

### imported-photos-unscrubbed-by-design (imported-photos-unscrubbed-by-design.md)

*Imported photos keep their metadata on purpose (location can seed find entries); the in-app camera is the scrubbed path; a robust import scrub is a possible later feature*

Owner, 2026-09-29: "At some point we can offer a robust metadata scrub for imported photos, but right now we offer a camera to take photos with without metadata. Imported photos with location data may still be used to add location to find entries."

**Why:** the app's privacy promise is carried by the in-app camera (captures are scrubbed by `PhotoMetadataScrub`); imports are deliberately untouched, and their EXIF location is a feature (it can place a find). This is also why photo export (-126) writes an import's stored bytes unchanged instead of stripping GPS.

**How to apply:** don't treat unscrubbed imports, or their location reaching an exported copy, as a bug or add a strip/warning unprompted. A robust import scrub is a future feature and needs its own dispatch and ruling. Related: [[dont-infer-authorization-to-build]].

### planner-as-build-traffic-controller (planner-as-build-traffic-controller.md)

*When several coders run in parallel on this machine, the planner runs a one-build-at-a-time queue over SendMessage; owner approved this pattern*

When several coder sessions run at once on this machine, the planner directs their builds (the owner's "play traffic controller so it can run efficiently", then "Excellent queue order. Good work", 2026-09-30):

- One Gradle build at a time. Coders are told their place in the queue and wait for the planner's explicit "go". Never let a coder start on "no Gradle process running", because a coder between two of its own runs (a suite, then assembleDebug) leaves a gap that another coder would race into.
- Short fix-and-verify dispatches go first. Long repeated-run diagnosis (flake loops) goes last, and does its non-Gradle work (reading logs and code, sizing) while it waits.
- Before handing over the slot, ask the current holder whether it needs more Gradle runs. Hand over only when it says it is done.
- A background Monitor watches build start and stop, pushes to the coder branches, and the 2048 MB disk and memory floors. SendMessage with notify_when_idle tracks each coder. Session names change when the bridges restart, so re-run ListAgents and address them by `name [ref]`.
- Map sessions to dispatches from the first prompt in each transcript (`~/.claude/projects/*bridge-cse-<id>*/*.jsonl`).
- After a terminal disconnect or reconnect, ask every coder whether it has a loop still running before issuing any "go". A loop sleeping between iterations shows no Gradle process, so a process check alone passes. On 2026-09-30, -296's surviving loop and -310's go overlapped this way.
- A coder's permission refusal is surfaced to the owner, never done by the planner on the coder's behalf. The owner's decision is relayed to the coder verbatim, and the coder's own permission system still decides.
- A disk floor blocks every coder at once. Merged worktrees' `app/build` dirs are the quickest space to free, but only on the owner's explicit say-so.

- **The queue covers heavy forecast Python jobs too, not only Gradle (added 2026-10-06).** Twice that day the session's container restarted and killed every running agent. The kernel log (`journalctl -k`) showed a global OOM that killed a forager-forecast regrid process at 3.9 GB RSS while an app full test suite ran beside it. One heavy job at a time (a Gradle suite or build, or a forecast build or regrid). Tell each coder to commit and push after every step, so a restart loses nothing. After any restart, check `journalctl -k | grep -i oom` before relaunching. A single job can still outgrow the machine: on 2026-10-06 a T6 reviewer's own recount reached 9.4 GB, and the OOM kill restarted the whole session (RECORD -610). So run every heavy Python, pytest or Gradle command under `systemd-run --user --scope -q -p MemoryMax=5G -p MemorySwapMax=0 <cmd>`, which kills only that job. Tell coders to stream their counts (per-key totals, never a full cell-week table). The owner, verbatim: "When I get my new laptop, that'll change. But for now, sensible ordering". So this limit belongs to the current HP laptop (about 11 GB RAM). Re-check RAM with `free -h` on a new machine before keeping the rule. The new laptop will have a 2 TB drive (owner, 2026-10-06). Until then, forecast data lives on the 57 GB USB flash drive, because the old laptop's internal disk has about 4 GB free.

**Why:** four coders ran in parallel, on 3 GB of free disk and about 11 GB of RAM. Without a queue they either collided on Gradle or all stopped at the disk floor.

**How to apply:** whenever more than one coder is dispatched at once. See also [[long-waits-and-fetches-run-outside-the-conversation]] and [[coders-are-helpers-building-on-github]].

### dont-make-me-think (dont-make-me-think.md)

*Owner's top mobile-design principle, \"Don't make me think\": prefer designs where the app does the right thing automatically over ones that ask the user to decide or act*

The owner's biggest principle for mobile design (2026-09-30), from their design professor: **"Don't make me think."** Users hate to think. A design that needs the user to notice a problem, understand it and act loses to one where the app just does the right thing.

**Why:** the owner said it directly, in the context of topo night mode. There the owner chose an automatic switch to Street tiles below zoom 9.5, with a crossfade, over the alternatives. The rejected ones included a zoom limit that would have made the user switch basemap by hand to see a wider view, and a light map that would have made the user wonder whether night mode had broken.

**It is already a pattern across the app,** and the owner's point is that the more it becomes a design principle, the more naturally it shapes each new element. Instances read on 2026-09-30:
- the fan reopens after Back from a find (`ui/map/fanout/FanReopen.kt`);
- user-set UI state survives a tab change (CLAUDE.md UX defaults);
- the first GPS fix zooms in by itself (`FIRST_ACTIVATION_ZOOM`, `SightingsMap.kt`);
- the fan folds only when its members change (`MapTapHandler.onContentChanged`);
- empty states give a next step;
- topo night switches to Street by itself.

It is in both CLAUDE.md copies' UX defaults (journal-redesign 3dd8a346, remove-claude-kit bfc06f6f).

**How to apply:**
- When pricing options for a control, a behaviour or a fix, count what each one asks the user to notice, decide or do, and weigh that above implementation cost.
- Prefer an automatic, sensible default over a new setting, prompt or manual step.
- A state the user could misread as broken (a mode that looks like it switched off, a silent failure) is the worst case, because it makes them think and costs trust. See [[honest-copy-plus-next-step]]: when the user does have to act, say what to do.
- Still surface real choices to the owner. This principle is about the end user's experience, not about planner decisions ([[ask-for-clarity-is-welcome]]).

**Data friendliness (2026-10-07, the owner's second UI sweep).** The owner, verbatim: "raw data is useful and honest, but put plainly in a UI amounts to clutter to a user, no matter how useful it is. Adding visuals like pie charts, graphs, in a tabled format (as opposed to line by line), with clear and intuitive labels. Intuitive design creates less need to think." Their example: the journal entry report's tracks and waypoints, shown line by line under the map, "a raw data dump as if we gave up at the last moment", and the editor's Withhold list, which "bombards the user with data". On balance, also verbatim: "strike a fine balance between speed/utility and visual polish". So keep the raw data reachable and honest, but lead with a visual summary and plain labels.

### next-update-roadmap (next-update-roadmap.md)

*After PR #140 (Journal redesign), the next Forager update focuses on navigation, predictive forecast layers and trip planning, with own map tiles fitting in*

The owner, 2026-09-30, verbatim: "Next update we will focus on navigation, predictive forecast layers, and trip planning. So map tiling will fit neatly."

- **The order:** PR #140, the Journal redesign, leaves draft first. The next update covers:
  - **navigation:** Forager Navigator, `docs/navigation/forager-navigator-plan.md`, status audited on 2026-09-28;
  - **predictive forecast layers:** the forager-forecast repo, under [[commercial-safe-forecast-ruling]];
  - **trip planning:** planned trips are already drawn on the map;
  - **own map tiles:** `docs/plans/own-map-tiles.md`, RECORD.md decisions 2026-09-28-338 and -339. The online basemap moves off tile.openstreetmap.org onto Forager's own PMTiles Worker. Scope A (own Street) or B (own topo too) is the owner's call.
- **Why:** all four are map features. A basemap Forager controls (its availability, its look by day and at night, offline) underpins the other three, so its scope is decided together with them.
- **How to apply:** when planning the next update:
  1. start with fresh read-only surveys (pulses), since the plans cited above are dated;
  2. put the tile-source scope decision in front of the owner early, alongside navigation and forecast-layer design;
  3. apply [[dont-make-me-think]] and the night-mode rulings (pure V1 inversion, no dimming).

**The owner's scope for the next release (added 2026-10-01, after build 2426 went to Closed testing).** Verbatim: "Next release we're finishing the nacigation projext we started before beta opened. We're going to implement forager-forecast to the new map layering system we just built. We're going to rework the trip planner to plan trips based on what they're foraging for, to go with the forecast system".
- **Navigation:** finish the Navigator project begun before the beta (`docs/navigation/forager-navigator-plan.md`). In-app Navigate from a waypoint's bubble and details sheet was deferred on 2026-09-27 to "a review in current waypoint navigation" and belongs here.
- **Forecast:** real forager-forecast data into the layer framework built in L0a/L0b. On 2026-10-01 the app has no real source: debug builds draw synthetic test cells, release reports no forecast data. [[commercial-safe-forecast-ruling]] and [[forager-forecast-repo]] apply.
- **Trip planner:** reworked so a trip is planned from what the user is foraging for, tied to the forecast. New on 2026-10-01; no design exists yet, so it goes to the owner as design questions first ([[confirm-behaviour-as-a-step-path]]).
- This is a statement of scope, not a go-ahead ([[policy-statements-are-not-instructions]]). The same day the owner said releases get shorter from now on ([[shorter-releases]]); how these three are cut into releases was asked and is the owner's to answer.
- Also slated for the next release: [[play-console-symbol-warnings]].
- **Own map tiles confirmed, navigation first (owner, 2026-10-01, later the same day).** Verbatim: "Oh yeah and we'll do our own map tiles too, thanks for reminding me. We can finish navigation since it's half started. Propose an order for the rest". The planner proposed: navigation, then own Street tiles (scope A), then forecast layers, then the trip planner, with forager-forecast's own work and the trip planner's design running alongside; and flagged that the Navigator plan's unbuilt Terrain phase overlaps tiles scope B and its Trip phase overlaps the trip planner rework (`docs/navigation/2026-09-28-navigator-plan-status-audit.md`, Summary). **The order is proposed, not accepted, until the owner answers;** which Navigator phases "finish navigation" covers was asked.

**A fan idea for a later release (added 2026-10-01).** The owner, verbatim: "At some point, not this release, maybe next one, have an update that allows the icons to move when they're about ready to fall off screen, and have them card stack on the edge instead". Context: since dispatch -380 an open fan stays open while the map follows the location, so it can drift off screen; the owner accepted that for now (a fan off screen is left alone, Back folds it, a line in the FAQ). **Not for PR #140; do not dispatch it unasked.** When it is planned: it touches where a fan's icons are placed after it opened (today only at open, shifted to fit the screen and stay off the controls), their touch areas, and what the legs do; put it to the owner as a design first. Not yet in `RECORD.md`; quote it in the next entry.

**Navigation planning opened (owner, 2026-10-02 UTC): "Let's plan the rest of the navigation plan that's been set in the repo".** A planning conversation, not a go-ahead to build. The planner re-checked `docs/navigation/2026-09-28-navigator-plan-status-audit.md` against main `e0239399` by callers. Still true: no caller for `returnWalkingTime`, no check-in timer, GPX import unreachable, recording mode hard-coded, no battery-exemption check, Phases 1.5 to 4 unbuilt, the HUD is in the compact layout only. Changed since the audit: a track can now be deleted (`MainActivity.kt:206`). New findings: the sundown countdown is computed (`TrackRecordingViewModel.kt:495`) but no screen reads it; `DecideSundownAlertUseCase` has no caller, so the turnaround and sunset alerts never fire, while `docs/legal/privacy-policy.md:147-149` describes them; the darkness margin is fixed at its default (the preferences repository is built at `AppContainer.kt:258` and passed nowhere); the way-back route (six owner decisions, 2026-09-11/12) has no code; waypoint navigation has no picker (`navigationTarget` is only ever the start point, `MainActivity.kt:625`). **Proposed to the owner, not accepted:** four short releases in order (sundown on screen with its two alerts; way-back route; navigate to a waypoint, with the HUD on wide layouts; walk-back time in the turnaround alert plus alerts moved into the recording service), with the plan's Phases 2 to 4 left to the tiles and trip-planner work. Three questions asked: what "finish navigation" covers, whether beta tracks with a return leg exist, and whether to write the agreed plan into `docs/navigation/`.

**The navigation plan is filed (2026-10-02 UTC).** The owner: "1 Let's do 1 to 4 and any leftover or later phase. This will be our short release, it shouldn't take that long" / "3 yes write it and file it there". Plan: `docs/navigation/2026-10-01-navigator-completion-plan.md` on branch `navigator-completion-plan` at `e44224fb` (cut from main `0dded057`), with `RECORD.md` decision `2026-09-28-398` and rows in three indexes; pushed, **not merged, no PR** (asked). It supersedes the four-stage proposal noted above: eleven stages, the service move FIRST (the code leaves the sundown alerts unwired until alerts leave the screen's loop, `TrackRecordingViewModel.kt:486-488`), then sundown, way back, navigate to a waypoint, walk-back time, leftovers (tasks T1 to T20), then Phases 1.5, 2, 3, 4 and the look-alike page, which wait on decisions and on server-side data pipelines that do not exist. **Nothing is dispatched; start no task without the owner's go.** Open with the owner (the plan's list of seven): the release cut (A to F short, G to K after, or all in one); the countdown's home (one strip or two); the 25 m lookahead; the step path for waypoint navigation; whether any recorded walk with a way back exists (the owner's answer was about the screen: "the arrow points toward the start point rather than the path you walked back"); the check-in timer's step path. The separate wide layout no longer exists, so "no HUD on wide screens" from the audit is stale and unverified either way.

**2026-10-02 02:12 UTC: the owner answered "Yes"** to three questions at once (release cut; whether an out-and-back walk has been recorded; whether to open a PR for the plan and merge it). Read as answering the PR question only. **PR #145** (`navigator-completion-plan` into main, head `e44224fbdb77e869db25d39ed9e8eac15eeff6af`) is open; backup `2026-10-01-05` (bundle of main at `0dded057`) written, verified, indexed; a background task waits for CI run 36954548324. When it is green and `origin/main` is still `0dded057`: `gh pr merge 145 --merge --match-head-commit e44224fbdb77e869db25d39ed9e8eac15eeff6af`, verify, tell the owner. Red on the known intermittent `MushroomLogViewModelWiringTest` only: re-run once and say so; anything else red: stop and tell the owner. #145's merge will have no record entry: note it in the next entry made for any reason. **Re-asked, unanswered:** the release cut (A to F short, G to K after, recommended) and whether any out-and-back walk has been recorded.

**The release cut is decided (owner, 2026-10-02T02:17Z, verbatim): "A to F first and G to K next sounds good".** So the short release is stages A to F of the navigation plan (tasks T1 to T20); stages G to K (Phases 1.5, 2, 3, 4, the look-alike page) follow in the next. Not yet in `RECORD.md` or the plan file: after PR #145 merges, write it as a record entry (with #145's merge note and the owner's "Yes" at 02:12Z that authorised the PR) and a dated addendum to the plan's open item 6, on a new branch cut from main, pushed; merging that branch needs the owner's word. Still unanswered: whether any out-and-back walk has been recorded (gates T12 and T13 only). **No task has a go yet;** the first by the plan's order is T1 (off-track decided in the recording service), which needs a dispatch file and a coder window opened by the owner on Sonnet 5.5 ([[coders-are-helpers-building-on-github]]).

**Recorded walks exist (owner, 2026-10-02T02:17:32.777Z, verbatim): "Yes, and the S26 ultra"**, to "has anyone recorded a walk out and back with the app? If yes, whose phone". So out-and-back tracks are on the owner's S26 Ultra, their daily driver, which agents do not touch ([[test-phone-vs-daily-driver]]). Read as answering the walks question only, not as a go for T1. For T12 the owner would export those tracks as GPX from Records and put them in a folder outside the repository (the repo is public and the tracks are real locations; only the comparison figures get filed). How many walks, and whether each has a clear turnaround, is not known. Not yet in `RECORD.md`: goes in the same entry as the release cut, after PR #145 merges.

**State at 2026-10-02 02:25 UTC.** PR #145 is merged: main is `c1610518bd00edbdbdbf74062b2e9f674a887104` (merged 02:21:21Z on the owner's "Yes", CI run 36954548324 green, backup `2026-10-01-05`); the navigation plan is on main. The release cut and the recorded-walks answer are recorded as `RECORD.md` decision `2026-09-28-399` plus a dated addendum on the plan, on branch `records-after-145` at `693b88c2`, pushed, **not merged, no PR** (needs the owner's word); its Notes carry #145's merge. Next record ID is `-400`. Waiting on the owner: a go for T1 (then write its dispatch file under `prompts/preserved/` with an intent entry, and a launch prompt for a Sonnet 5.5 coder window), and whether to merge `records-after-145`.

**State at 2026-10-02 02:58 UTC: T1 has a go and is dispatched as Part 1 only.** The owner (02:48Z): "Yes open a pr and merge." / "Go". Branch `records-after-145` now holds `-399` and intent `2026-09-28-400` with dispatch file `prompts/preserved/2026-10-02-01.md` (head `c9051280dd…`, full sha via `git rev-parse origin/records-after-145`); **PR #146** into main is open, backup `2026-10-01-06` (bundle of main at `c1610518`) written and indexed, a background task waits for CI run 36957593569. When green and `origin/main` is still `c1610518`: `gh pr merge 146 --merge --match-head-commit <that head>`, verify, then tell the owner to open the coder window (Sonnet 5.5) with `~/Zynergy/launch-prompts/36-alerts-in-service.md`. #146's merge will have no record entry: note it in the next entry. Next record ID `-401`.
- **Part 1 is report-first, nothing under `app/src/main`:** tests pinning today's behaviour plus `docs/navigation/2026-10-02-alerts-in-service-prebuild-report.md` (six premises, state inventory, at least two ownership shapes, test plan, build steps), on branch `alerts-in-service`, worktree `~/Zynergy/forager-wt/alerts-in-service` (the coder creates it). Then a stop; the build is an amendment to the dispatch after the planner and owner read the report.
- **Why T1 became two parts:** the planner read from the code (not run) that after a swipe-away the reopened app shows no recording while the service still records (`TrackRecordingViewModel.kt:72-78`, `:403`), and that a second recording could be started over the first (`TrackRecordingService.kt:82`). So the move is "who owns the recording and return state", not one call site.
- **Open with the owner, asked 2026-10-02:** confirm the step path "Recording > swipe the app away > open Forager again > the screen shows the recording still running, and the return HUD if a return was under way"; and, optionally, try that on a phone to see what it shows today.
- The planner session's name for coder hand-backs: `bridge-cse-01lcujkxfeddhebd1awgfrtc-64` [9f8bbc] (changes on restart).

**The second path is confirmed (owner, 2026-10-02T02:59:46.136Z, verbatim): "Sounds good. Paste the prompt here"**, to the step path "Recording > swipe the app away > open Forager again > the screen shows the recording still running, and the return HUD if you were heading back. Is that what you want? I recommend yes." So Part 2 of dispatch `-400` builds both paths. Not yet in `RECORD.md`: quote it in the Part 2 amendment's record entry (and tell the coder by message only if it asks; Part 1 does not depend on it). The launch prompt (`~/Zynergy/launch-prompts/36-alerts-in-service.md`) was pasted to the owner in chat at their request while PR #146's CI was still running; the prompt itself stops the coder if the dispatch file is not on `origin/main` yet.

**PR #146 is merged (2026-10-02 UTC):** main is `9c3f103dcd4b0a04a7bddedc71bf5f2dee867518`; CI run 36957593569 green on head `c9051280`; backup `2026-10-01-06`; merged on the owner's "Yes open a pr and merge." The dispatch file `prompts/preserved/2026-10-02-01.md` and records `-399`, `-400` are on main. #146's merge has no record entry: note it in the next one (`-401`). The owner has the launch prompt and was told the coder window can be opened. Waiting for the coder's Part 1 hand-back (branch `alerts-in-service`); the planner then reads the report, brings the shapes to the owner in behaviour terms, and writes Part 2 as an amendment with the confirmed second path quoted.

**Part 1 of dispatch `-400` is handed back (2026-10-02 ~03:30 UTC) and checked by the planner.** Branch `alerts-in-service` at `4631ba8d` (three commits on main `9c3f103d`): two new test classes that pin the fault (green = fault present), the report `docs/navigation/2026-10-02-alerts-in-service-prebuild-report.md`, two index rows; nothing under `app/src/main`; pushed, **not merged**. Planner re-counted the XML in `~/Zynergy/device-evidence/2026-10-02-alerts-in-service/`: 7 tests alone and x5, full suite 415 classes / 3393 tests / 0 failures / 24 skipped. Coder ran on Opus 5.5 (effort not reported), window name "# Coder session: alerts-in-service, Part 1: read, ..."; told to stand by.
- **All six premises confirmed.** After a swipe-away the reopened screen reports no recording; pressing Record makes a second track row that gets no points, is never ended, shows "Still recording" and cannot be deleted in the app.
- **Coder recommends shape B** (one decision object held in `AppContainer`, begun/fed/ended by the service with every raw fix, the ViewModel calling it and copying its state); planner agrees. Costs: thread-safety; eight off-track tests in `TrackRecordingViewModelTest` (`:433` to `:613`) moved one for one; six test files that construct the ViewModel edited for a new required argument. Planner's intended answers to the coder's two questions: yes (one call beside the sampler/batching, changing neither), yes (fed every raw fix).
- **Flag 2, re-read by the planner and holding as an inference:** the off-track check compares three consecutive raw fixes about 1 s apart and needs more than 25 m gained, so steady walking cannot trip it and a stray network fix can. What "off track" means is the owner's; the dispatch forbade touching it.
- **Put to the owner, unanswered:** (1) a phone check before any build: record > Return > swipe away > does the recording notification stay > reopen > what shows; (2) shape B, go or not; (3) flag 2: move the alert as it is and take up its meaning as its own item (planner's recommendation), or settle the meaning first.
- **Owed in the next record entry (`-401`, with the Part 2 amendment):** the hand-back; the owner's "Sounds good. Paste the prompt here" (02:59Z, second path confirmed); "I'm going to do Opus 5.5 medium for coders this session" (03:09Z; `-400`'s Notes say Sonnet); PR #146's merge (`9c3f103d`, 03:01:07Z, backup `2026-10-01-06`).

**Coder's model confirmed (2026-10-02 ~03:40 UTC, by its message):** `claude-opus-5-5`, effort medium, read from its own `get_session` at its start; the owner told it directly "Opus 5.5 Medium for this session". The planner told it to append a dated note to its report correcting flag 7 (append only, no Gradle) and to keep standing by; expect a new commit on `alerts-in-service` after `4631ba8d`. The three questions to the owner (phone check, shape B, flag 2) are still unanswered; a coder message is not the owner's answer.

**`alerts-in-service` head is now `5dc051b4`** (2026-10-02 ~03:45 UTC): the coder's dated note correcting flag 7, appended to its report, checked by the planner (one file, nothing removed). Coder standing by; still waiting on the owner's three answers (phone check, shape B, flag 2).

**Phone check dispatched (2026-10-02 ~04:00 UTC).** The owner (03:56Z): "Go ahead and have the coder do the check. It's ready", read as answering the phone-check question only; the shape (B) and flag 2 are still with the owner. Amendment 1 is appended to `prompts/preserved/2026-10-02-01.md` with continuation `2026-09-28-401` on branch `records-after-146` at `d319df15` (cut from main `9c3f103d`), pushed, **not merged, no PR**; its Notes carry PR #146's merge, the confirmed second path and the Opus 5.5 medium correction, so those are no longer owed. Next record ID `-402`. The coder was told to go and reads the amendment from that branch. The check: seven steps on the S22 (R5CT321008R, Android 16, One UI 8.0, Forager `1.0.2426+ge0239399`, a release build freshly installed 2026-10-01 16:00 local, ceDataInode 2470106, so no `run-as` and no database hash); gate at step 4 (pid, foreground service and notification after a swipe from recents); ends the recording from the notification's Stop; leaves one ended track on the phone. On USB power, so no evidence about battery management. Waiting for the coder's hand-back (a dated section in its report on `alerts-in-service`).

**T1's shape and the off-track check are decided (owner, 2026-10-02T04:30Z, verbatim): "Option B" / "Move as is, adjust as necessary. We need the definition to fit the user, not the software. So if the user benefits from a longer project, I'll buy that as an investment".** Recorded as `RECORD.md` decision `2026-09-28-402` plus a plan addendum adding **T21** (what "off track" means, defined for the user: step paths to the owner first, numbers from recorded walks; Stage C), on `records-after-146` (pushed, unmerged, no PR; holds `-401` and `-402`). Next record ID `-403`. **Part 2 of `-400` is not yet written:** it waits for the coder's phone-check hand-back (predictions pushed at `alerts-in-service` `d67019e5`). When the check passes its gate, write Amendment 2 (continuation `-403`): shape B; the eight off-track tests moved one for one with a before/after table; the six constructing test files edited for a required argument; yes to one call beside the sampler and batching, changing neither; fed every raw fix; a log line for the dropped second ACTION_START; the window trimmed; steps 1 to 4 of the report's build steps, then the reopened-app path (owner-confirmed) as the next step; the meaning of off-track untouched (T21). If the gate fails, stop and bring it to the owner.

**Coder checked at the owner's word, 2026-10-02 04:50 UTC.** It did not start the phone check on the planner's relay: its launch prompt said no phone, so at 03:59Z it asked the owner in its own window to confirm adb on the S22, and waited. The owner answered "Yes, run the check" at 04:49Z and it began step 0 at once (read from its transcript under `~/.claude/projects/-home-zynergy-labs-Zynergy-Forager--claude-worktrees-bridge-cse-01PAGrVZbvpJSjMTxcKkN64n/`; its worktree session name is `bridge-cse_01PAGrVZbvpJSjMTxcKkN64n`, ListAgents ref `[a22f2b]`). The planner subscribed to its idle notice. Lesson: a launch prompt that forbids something the dispatch later allows makes the coder stop for the owner; when an amendment lifts a launch-prompt limit, say so to the owner so they expect the question in the coder's window. The planner session's ListAgents name is now "1 A, full model 2 No, just keeping no proprietary ..." `[9f8bbc]`.

**Phone check stopped at step 1 (2026-10-02 04:50 UTC), nothing tapped, no recording made.** Coder's report section pushed at `alerts-in-service` `896efc0e` (13 lines appended). Two stop conditions, both confirmed by the planner's own read-only look: the display is in landscape (rotation 90, auto-rotate on), and Forager is on the Journal tab with the settings drawer open, so the Record control is not on screen. Also seen: Do Not Disturb on, ringer muted (a silenced-phone notice is expected at record start). Asked of the owner: either set the phone themselves (upright, Maps tab, drawer closed) and say ready, or allow the planner to widen Amendment 1 (a tap on "Close navigation menu", one bottom-navigation tap to Maps, landscape accepted). Planner recommended the owner setting the phone upright, since portrait is the long-standing phone rule; no widening is written until they answer.

**Phone check resumed (2026-10-02 05:17 UTC).** The owner set the phone and said "Ready" (option 1; nothing widened). The planner read it at 05:16:31Z, read-only: upright, auto-rotate on, Forager on the Maps tab with "Start recording track" on screen, awake, no service, pid 5455 (the app had restarted since 20684). The coder was told to re-run Amendment 1 from step 0, to re-read the rotation before each action, and that the silenced-phone notice is expected at record start (Do Not Disturb on). Waiting for its hand-back. Owed in the next record entry (`-403`): the first attempt's stop at step 1 (`896efc0e`), the owner's "Ready", and this resume.

**Phone check, second run, STOPPED at step 2 (2026-10-02 05:17 UTC) WITH A RECORDING RUNNING on the S22.** The coder tapped Record at 05:17:22Z; Android's "Allow Forager to send you notifications?" prompt came up (fresh install of 2026-10-01, POST_NOTIFICATIONS granted=false). It is unanswered and the coder touches nothing. Confirmed by the planner read-only at 05:17:57Z: the permission prompt is in front, `TrackRecordingService` is foreground, pid 5455, upright, awake, no Forager notification can show. The coder cannot end the recording inside its rules (the in-app Stop is behind the prompt, there is no notification, and force-stop, Back and answering the prompt are not allowed). **Told to the owner:** tap Allow, then Stop in the app, and say "stopped", so the check restarts clean from step 0 (recommended); or tap Allow and say "allowed" to continue from step 3. Evidence in `phone-check/run2/`. A short extra track from this run will be on the phone. Finding to keep: on a fresh install the first Record tap raises the notification prompt while the recording has already started.

**Phone check, third run, started 2026-10-02 05:19 UTC.** The owner: "I tapped allow, then stopped recording". Planner confirmed read-only at 05:19:02Z: POST_NOTIFICATIONS granted=true (USER_SET), no service, no Forager notification, upright, Maps tab with "Start recording track", pid 5455. No recording is running on the phone. The coder was told to restart from step 0 and to keep run 2 (the notification prompt on a fresh install, with the recording already started, and the short track it left) as its own dated part of the report. Waiting for its hand-back. Owed in the next record entry (`-403`): run 1's stop (`896efc0e`), run 2's stop and the owner's two phone actions, the owner's "Ready" and "I tapped allow, then stopped recording", and run 3's result.

**Phone check run 3: THE GATE PASSED (2026-10-02 05:20 UTC); at 05:22 the coder was at step 7 with A RECORDING STILL RUNNING while it ends it.** From the coder's message (not yet in its pushed report): Record tapped 05:19:39Z, service foreground, notification posted, the Do Not Disturb notice showed as a passing message; HUD with a start point; pid P1 5455. Swipe from recents at 05:20:26Z, Home 05:20:41Z; the task is gone from activities and recents; at 05:20:56Z and again 62 s later: **pid still 5455, service still foreground, notification still posted.** Reopened at 05:21:45Z: the Maps tab offers "Start recording track" and "Return to vehicle — start recording first", no HUD, same pid, service still foreground (premise 3 confirmed on hardware). Step 7 stopped because Forager's notification was collapsed and its Stop action hidden; the planner ruled that one tap on that notification's own Expand button, then "Stop recording" by text, is within step 7 and told the coder to do it. Planner's own read at 05:22:41Z: the service was still running. **Confirm the recording has ended before telling the owner it is done.** Evidence limits: two uiautomator dumps after the swipe are stale (dumpsys and screenshots carry that step); the home-screen screenshots show the owner's wallpaper and stay in the local evidence folder, uncommitted. USB power: no evidence about battery management.

**Phone check done and Part 2 dispatched (2026-10-02 ~05:35 UTC).** Run 3 ran to the end; the recording was ended from the notification (Expand, then "Stop recording"); the planner confirmed on the phone afterwards: no service, no Forager notification. Planner re-read the gate from the saved files: task 254 gone after the swipe, pid 5455 unchanged, service foreground, notification posted; reopened as task 255 with "Start recording track" and no HUD. Coder's branch `alerts-in-service` at `11d23217` (report appends only). Two ended tracks from runs 2 and 3 are on the S22.
- **Amendment 2 (Part 2) is appended to `prompts/preserved/2026-10-02-01.md` with continuation `2026-09-28-403`** on `records-after-146` at `966bf528` (pushed, unmerged, no PR; holds `-401`, `-402`, `-403` and the plan addendum with T21). Next record ID `-404`. The coder was told to go and builds from the branch copy: shape B in three steps (decision class in `domain/`; held in `AppContainer`, begun/fed/ended by the service with every raw fix; the ViewModel hands over), steps 2 and 3 handed back together; nine rulings; off-track meaning untouched; no phone. It has the Gradle slot.
- **Part 3 (the reopened app shows the recording and the return HUD) is not dispatched;** it follows Part 2's hand-back and must settle the coder's five points (an open track row is not proof of a running recording; `hasStartedRecordingOnce`; the last good fix starts empty; the once-per-recording notices; the origin waypoint) plus flags 1, 4, 5.
- **Open with the owner:** merging `records-after-146` (asked); how a Part 2 build reaches the S22 (it carries release build 2426; a debug build will not install over it).
- Finding kept for later: a fresh install's first Record tap starts the recording before the notification prompt is answered.

**The owner, 2026-10-02T05:36:55.599Z, verbatim: "1 yes open a pr and merge when ready" / "2 have the release build removed so a test build can go on".**
- **PR #147** (`records-after-146` into main, head `966bf5288afa3b5c20090166fc0cac2f71128cfc`) is open; backup `2026-10-01-07` (bundle of main at `9c3f103d`) written, verified, indexed; a background task waits for CI run 36969756817. When green and `origin/main` is still `9c3f103d`: `gh pr merge 147 --merge --match-head-commit 966bf5288afa3b5c20090166fc0cac2f71128cfc`, verify, tell the owner. Red on the known intermittent `MushroomLogViewModelWiringTest` only: re-run once and say so; anything else: stop and tell the owner. #147's merge will have no entry: note it in `-404`.
- **The S22's release build is to be removed so a test build can be installed,** for Part 2's phone check. The owner chose this knowing it wipes Forager's data on that phone (told in the question). Not done yet and not needed until Part 2 is handed back: do it then, through an amendment that names the uninstall and the install as allowed actions, and tell the owner just before it happens. Do not uninstall earlier. Not yet in `RECORD.md`: quote it in `-404`.
- Disk: 4.4 GB free at 05:37Z; each pre-merge backup bundle is about 95 MB (seven on 2026-10-01); the Gradle floor is 2048 MB.

**The owner (2026-10-02 ~05:45 UTC), verbatim: "Dual app also works in case you need it".** The S22 has Samsung's dual-app profile (user 95, `DUAL_APP`, running; read with `pm list users`). The planner's answer, not tried on the phone: it does not solve the test-build problem, because Android keeps one copy of an app's code per phone with one signature, shared by every profile, so a debug-signed build cannot sit in the dual-app profile beside the release-signed one under the same app identity. What would give a side-by-side test app is a separate identity for debug builds (no `applicationIdSuffix` exists today, `app/build.gradle.kts:243`, `:284-285`; the FileProvider authority already follows the identity, `AndroidManifest.xml:184`); that is a build change with knock-ons (CI's published test APK would become a different app) and its own decision. Planner recommended keeping the owner's earlier choice (remove the release build from the S22 when Part 2 is ready) and offered the side-by-side identity as a later option. No decision changed; nothing done on the phone.

**PR #147 is merged (2026-10-02T05:45:13Z):** main is `f5103d0d93caaffa3e6c1830d77571d530adcd80`; CI run 36969756817 green on head `966bf528`; backup `2026-10-01-07`; on the owner's "1 yes open a pr and merge when ready". Records `-401` to `-403`, the plan addendum with T21 and both amendments to the dispatch file are on main. #147's merge has no entry: note it in `-404`, with the owner's "2 have the release build removed so a test build can go on" and "Dual app also works in case you need it". The coder is building Part 2 on `alerts-in-service` from the branch copy of Amendment 2 (identical to main's). Waiting for its hand-back; then Part 3 and the S22 install question (remove the release build, unless the owner says "side by side").

**Coder checked at the owner's word, 2026-10-02 06:43 UTC: Part 2 has NOT started.** No commit on `alerts-in-service` since `11d23217`, no Gradle run. Since 05:26:43Z the coder has been waiting on a question to the owner in its own window: its launch prompt said "Do not start a fix", so it asked the owner to confirm building Part 2, with three options: "Yes, build Part 2" (and take later planner amendments to this dispatch as carrying the owner's go, asking again only for anything touching the phone); "Yes, but ask me each time"; "No, do not build". The planner told the owner and recommended the first. **Second time this happened in one session (the phone check was the first): whenever an amendment lifts something the launch prompt forbade, tell the owner in the same message that the coder will ask them in its own window.** Better still, write future launch prompts so that amendments to the named dispatch carry the owner's go, with the phone as the stated exception.

**The owner told the planner "Option 1" (2026-10-02 ~06:44 UTC)**, meaning the coder's first answer: build Part 2, later planner amendments to this dispatch carry the owner's go, the phone is still asked about directly. At 06:44:14Z the coder's own question was still unanswered in its window (its log last written 05:26:43Z), and the planner cannot answer it or relay approval. The owner was told to pick that answer in the coder's window. Not started until they do. Quote "Option 1" in record `-404` only together with what the coder's log shows the owner chose there.

**Part 2 is being built (2026-10-02 07:13 UTC on).** The owner answered in the coder's window "Yes, build Part 2" (07:13:29Z, read from its log): later planner amendments to this dispatch carry the owner's go; the phone is still asked about directly. The coder then asked two questions and offered a reading; the planner ruled by message and filed the same text as "Amendment 2, rulings on the coder's questions" with continuation `2026-09-28-404` on branch `records-after-147` at `ea4d3d2f` (cut from main `f5103d0d`, pushed, **unmerged, no PR**; its Notes carry #147's merge, the owner's release-build and dual-app words, and "Option 1"). Next record ID `-405`.
- Rulings: the distance and bearing to the start stay worked out by the ViewModel (Amendment 2's "distance becomes a copy" withdrawn; five tests at `TrackRecordingViewModelTest` :304, :325, :334, :354, :816 need it); `alertDelivery` and `detectOffTrack` leave the ViewModel's constructor (six test files and MainActivity's factory lose two arguments, gain one; no assertion changes); only the first-path fault test is fixed in Part 2, the reopen tests stay as pins for Part 3; the ViewModel hands the start point to the object; the mirror is per track.
- Waiting for the coder's Part 2 hand-back on `alerts-in-service` (was `11d23217` when it started). Then: planner review and merge with the owner's word, Part 3's amendment, and the S22 step (remove the release build, install the test build; tell the owner just before).

**Part 2 handed back and reviewed (2026-10-02 ~07:45 UTC); nothing sent back; NOT merged, NOT run on a phone.** Coder's branch `alerts-in-service` at `0f6485a4` (tests-first `7ae7266a`, `1adfe2cb`; build `c57ee30f`; main merged `943f7892`; report). Built: `domain/ReturnWatch` held by `AppContainer`, begun/fed every raw fix/ended by `TrackRecordingService`, which also logs a dropped second start; the ViewModel calls it for Return, hands it the start point, copies the returning and off-track flags only for its own track, still works out the button's distance. Off-track meaning unchanged. Planner read the class and all main diffs, re-counted the XML (full suite 416 classes / 3411 tests / 0 failures / 24 skipped; six revert checks each failing on its own test), compared the eight moved tests (same names, assertion counts and values; the subject moved from the ViewModel's state to the watch's).
- **Branch `t1-part-2` at `c45a7117`** joins `alerts-in-service` `0f6485a4` with `records-after-147` `ea4d3d2f` and adds continuation `2026-09-28-405` (the hand-back and review). No file under `app/` differs from the coder's head. Pushed, **no PR, not merged: asked the owner.** Next record ID `-406`.
- **Asked of the owner:** open a PR for `t1-part-2` and merge on green (recommended: merge now, then Part 3, then ONE phone check covering both paths, since the S22 install wipes the app once); or check Part 2 on the phone first.
- **Interim behaviour until Part 3:** a reopened screen still shows no recording; Record then Return there is now refused and logged.
- **For Part 3's amendment:** the coder's five points on what a reopened screen must read; Part 1 flags 1 (stuck second track), 4 (restore from backup allowed during a live recording), 5 (origin waypoint when swiped before the first good fix); the stale comment at `MainActivity.kt:322-329`; unused imports and a constant in `TrackRecordingViewModelTest`. Put the reopened-app path to the owner as step paths where a behaviour is undecided (for example: what the reopened screen shows if the recording ended while the app was away).

**The coder's view on Part 3's order (by message, 2026-10-02 ~07:50 UTC; a view, not built):** (1) the watch carries the mode and says it is begun, as the live source a reopened screen reads (an open track row is not proof); (2) the ViewModel adopts a running recording when it has no active track and the watch is begun (track from the watch, row for start time and breadcrumbs, its own poll and fix collection, the origin waypoint through `GetTrackOriginWaypointUseCase`, its first production caller); this turns the two Part 3 pins and closes flag 4 (restore guard) and the cause of flag 1; the stale `MainActivity.kt` comment and the unused test imports ride with it; (3) the re-sent ACTION_START for the adopted track should be nothing to log in the service (one line, testable) rather than suppressed in MainActivity's effect; (4) a guard at the source: `startRecording` refuses when the watch is begun for another track, with user-facing words that are the owner's; (5) flag 5: adoption must not create an origin from where the walker is at the reopen; leave none or seed from the first stored point, the owner's choice; (6) tracks already stuck open: an open row is a recording only if the watch is begun for it; any other open row is offered Delete or ended, the owner's choice; last, because it changes stored data. Device check after step 2. **Put to the owner as three questions with recommendations (stuck tracks: end them automatically at their last recorded point; no-start-point case: mark the start at the first recorded point; the refusal's words), alongside the still-open merge question for `t1-part-2`.**

**The owner (2026-10-02 ~07:52 UTC), verbatim: "Open pr and merge t1-part-2".** **PR #148** (`t1-part-2` into main, head `c45a7117ef647ea6ef16c4d23430c53293831939`) is open; backup `2026-10-02-01` (bundle of main at `f5103d0d`) written, verified, indexed; a background task waits for CI run 36980882565. When green and `origin/main` is still `f5103d0d`: `gh pr merge 148 --merge --match-head-commit c45a7117ef647ea6ef16c4d23430c53293831939`, verify (main's tree equals the branch's), tell the owner and the coder. Red on the known intermittent `MushroomLogViewModelWiringTest` only: re-run once and say so; anything else red: stop and tell the owner (this PR carries app code, so a red run may be real). #148's merge has no entry: note it in `-406`. **Still unanswered by the owner: the three Part 3 questions** (stuck "Still recording" tracks: A end them at their last point, B keep the label but offer Delete; no-start-marker case: A marker at the first recorded point, B none; the words if Record is pressed while another recording runs). Part 3's amendment is not written until they answer. Disk 4.1 GB free.

**Part 3 dispatched (2026-10-02 ~08:05 UTC).** The owner (07:54Z), verbatim: "1 A" / "2 A" / "3 I'll go with your recommendation": stuck open tracks become finished tracks ended at their last recorded point; a recording adopted without a start marker gets one at its first recorded point (never where the phone is at the reopen); the refusal's words are "Forager is already recording a track. Stop it before starting another." **Amendment 3 with continuation `2026-09-28-406`** is on branch `records-after-148` at `9b66504a` (cut from `t1-part-2` `c45a7117`; pushed, unmerged, no PR). Next record ID `-407`. The coder was told to go on **Part 3a** (steps 1 to 5: the watch carries mode and "begun"; the screen adopts a running recording at creation and on foreground; a repeated start for the same track is silent in the service; Record cannot start a second recording and says so; start marker at the first recorded point). **Part 3b** (step 6, ending stuck open tracks at their last point; the one stored-data change) starts only on the planner's word after 3a is reviewed, and opens with a report (how `EndTrackUseCase` sets the end; every reader of an open row; whether two ViewModels can exist; a track with no points is a stop). No phone. PR #148's CI (run 36980882565) was still running; the background watcher will notify; merge per the earlier note, then tell the owner and the coder. After Part 3: one phone check for Parts 2 and 3, under its own amendment that names uninstalling the release build and installing the test build on the S22 (tell the owner just before).

**Part 3a started; one ruling given (2026-10-02 ~08:00 UTC).** The coder began without asking the owner again (as the owner's standing go allows) and stopped as Amendment 3 required: steps 2 and 4 each need a new argument in `MainActivity`'s ViewModel factory (`GetTrackOriginWaypointUseCase`, and the refusal string). Planner ruled option B: both required, no default (a default would let a forgotten wiring compile and silently place a second start marker; no test composes MainActivity); six constructing test files edited again for the two arguments only. Filed as continuation `2026-09-28-407` beneath Amendment 3 on `records-after-148` (now holding `-406` and `-407`; head via `git rev-parse origin/records-after-148`). Next record ID `-408`. PR #148's CI still running at 07:57Z.

**PR #148 is merged (2026-10-02T08:01:25Z): Part 2 is on main.** Main is `da9437faa81cb5fcd527c57ee6a3337ce167f468`; CI run 36980882565 green on head `c45a7117`; backup `2026-10-02-01`; on the owner's "Open pr and merge t1-part-2". Main's `app/` equals the coder's reviewed head `0f6485a4`. Records `-404` and `-405` are on main. #148's merge has no entry: note it in `-408`. Not run on a phone. The coder is building Part 3a on `alerts-in-service`; `records-after-148` (`5f70d6e0`: Amendment 3, `-406`, `-407`) is pushed and unmerged, to be asked about with 3a's hand-back.

**Part 3a handed back (2026-10-02 ~08:50 UTC) and SENT BACK with one possible fault; not accepted, not merged.** Coder's branch `alerts-in-service` at `186bef5a` (tests-first `0ce04e2a`, build `335b101f`, main merged `cdcb5e4e`). Built: the watch carries `isBegun` and `mode`; the service passes the mode and warns only for a start naming a different track; the ViewModel takes a running recording up at creation, at the foreground and when Record is refused, refuses Record while another recording runs (owner's sentence in `strings.xml`), settles the start marker (the track's own, else its first recorded point); MainActivity gains two factory arguments and the comment fix. Planner read all main diffs and re-counted `part3a/` (full suite 416 classes / 3420 tests / 0 failures / 24 skipped; seven revert checks each failing on its own test).
- **The possible fault (planner's reading, not run):** `stopRecording` clears `activeTrack` at once, but the service ends the watch only when it handles ACTION_STOP, sent by MainActivity's effect after recomposition. In that window the watch is begun for the track just stopped and `activeTrack` is null, so a quick second tap on Record is refused with the "already recording" sentence and launches a take-up, which can take the stopped recording back up; the effect then re-sends ACTION_START and the service records again into a row that has an end time. `onEnteredForeground` reaches the same take-up. Before 3a a quick Stop then Record started a new recording. Asked the coder to confirm or disprove by test first, then the smallest fix (the ViewModel remembers the id it stopped; the guard and the take-up leave a watch begun for that id alone), a revert check, the full suite, a dated section in the report. Not in MainActivity's effect or the service.
- The coder's permission-revoked flag: planner's reading is that Android kills the process on revocation, so the state cannot arise; inferred.
- Also reported by the coder: the network-fixes notice can show a second time after a take-up; a taken-up marker is named with its first point's time; "first recorded point" is the first as the screen reads them.
- Part 3b not started. `records-after-148` (`5f70d6e0`) unmerged. Owed in `-408`: #148's merge (`da9437fa`, 08:01:25Z, backup `2026-10-02-01`), 3a's hand-back, the send-back and its outcome.

**Part 3a ACCEPTED (2026-10-02 ~09:15 UTC); the fault was real.** The coder confirmed the Stop-then-Record window with a failing test (the stopped recording was taken back up) and fixed it at `f19d2d53` (tests first `301d56a4`; report section `cc6fd2ee`, now the head of `alerts-in-service`): `TrackRecordingViewModel` remembers the track it stopped and neither refuses against nor takes up a watch still begun for it. Planner read the fix and re-counted: full suite 416 classes / 3423 tests / 0 failures / 24 skipped; the revert check fails the same two tests. Recorded as continuation `2026-09-28-408` (also carries #148's merge) on `records-after-148`, now at `f6e85ec6` (holds `-406`, `-407`, `-408`; pushed, unmerged, no PR). Next record ID `-409`.
- **Part 3b has the planner's word, findings first:** the coder reports by message (how `EndTrackUseCase` sets the end; every reader of an open row; whether two ViewModels can exist; a recording starting as the sweep runs; how the sweep sits beside `stoppedTrackId`) and waits for the planner's reply before building.
- **Asked of the owner:** what a stuck open track with NO recorded point becomes: A, a finished track with no points, ended at its start time, which the user can delete (recommended; nothing is deleted without the user); B, removed automatically. Also proposed: one merge after 3b (3a plus 3b plus the records), then the S22 check.
- Not merged: Part 3a is only on `alerts-in-service`. Main (`da9437fa`) has Part 2.

**Part 3b: findings received, rulings given, build under way (2026-10-02 ~09:40 UTC).** The coder challenged the planner's "when" (ViewModel creation) and the planner accepted its alternative. Rulings, filed as continuation `2026-09-28-409` beneath Amendment 3 on `records-after-148` at `6e7cb06c` (holds `-406` to `-409`; pushed, unmerged, no PR): **A** the sweep runs once at process start from `ForagerApplication`, only for open rows that started before the process did, and never for a row the watch is for; **B** the end time is the last STORED point's time (`getFullRecord`, network fixes included); **C** a conditional write (ends a row only if still open; new DAO query, no schema change); **D** never earlier than the row's start (clamp, logged). Rows with no stored point are left as they are and counted, until the owner answers (A: finished with no points, ended at its start, deletable, recommended; B: removed automatically). Next record ID `-410`.
- Coder's findings worth keeping: `EndTrackUseCase` always writes "now" and the DAO update overwrites an existing end; an open track matches every later day's derived trip (`TrackDao.kt:38-42`), so ending stuck rows also fixes that; once ended, a stuck track's line is drawn on the Maps tab if it has points; a backup made before the sweep restores the row open.
- **For the owner's bug list and the phone check:** the recording notification's tap opens `MainActivity` with no flags, so a second copy of the screen (and a second ViewModel) may be stacked on the first. Inferred, not seen. Told to the owner.
- Waiting for: the coder's 3b hand-back; the owner's answer on no-point tracks. Then one merge (3a + 3b + `records-after-148`) on the owner's word, then the S22 check under its own amendment (uninstall the release build, install the test build; tell the owner just before).

**Part 3b stopped once and was re-ruled (2026-10-02 ~10:10 UTC).** Built as first ruled (`alerts-in-service` `6c9ccec1`, coder's own test fix `3dced063`), the sweep called from `ForagerApplication.onCreate` opened the database in every Robolectric test's app start and made the existing, unrelated `ForagerDatabaseDestructiveFallbackTest` fail in 7 of 10 runs alone (0 of 10 with the call removed). The coder measured, touched nothing, stopped. Planner ruled its option A, filed as continuation `2026-09-28-410` on `records-after-148` at `b953b412` (holds `-406` to `-410`; unmerged, no PR): the rule is unchanged; the sweep is launched once per process when the FIRST recording ViewModel is created (once-only flag in the container; the process start time still taken at the top of `onCreate`; the use case a required ViewModel argument; the ViewModel reloads its track list when the sweep finishes). Costs now: a process with no screen does not sweep until the app is opened; a row left open while the process lives waits for the next start; restored-from-backup rows stay open until the next start. Evidence asked: that test class ten times at the new commit, revert checks for the launch point and the once-only flag, whether any other class changed behaviour, the full suite once. Next record ID `-411`. **Planner's own lesson: before placing work at app start, ask what else starts the app; under Robolectric every test does.** Still waiting for the owner on no-point stuck tracks (A/B).

**Part 3b handed back and ACCEPTED (2026-10-02 ~10:50 UTC); one comment-only correction requested before the merge.** `alerts-in-service` at `1aef03f3` (rule `6c9ccec1`; launch moved at `1c047c66`, tests first `82450b05`). Built: `EndAbandonedTracksUseCase` (open rows that started before the process did, not the watch's row; end = last STORED point's time, never before the start; conditional write `endIfOpen` via a new DAO query, no schema change) run once per process by `AbandonedTrackSweepOnce`, launched by the first recording ViewModel, which reloads its track list when a track was ended; rows with no stored point are left and counted. Planner read all of it and re-counted `part3b/`: full suite 420 classes / 3445 tests / 0 failures / 24 skipped; `ForagerDatabaseDestructiveFallbackTest` 7 of 10 failing with the onCreate call, 0 of 10 without, 0 of 10 at the new build; thirteen revert checks each failing on its own test. **Requested:** the stale "When it runs" paragraph in `EndAbandonedTracksUseCase.kt`'s header corrected (comment only), hash to follow.
- **Next, in order:** (1) the coder's comment commit; (2) record `-411` (3b hand-back and review) on `records-after-148`, then a joined branch (`alerts-in-service` + `records-after-148`) as with `t1-part-2`; (3) ask the owner for the PR and merge; (4) Amendment 4, the S22 check for Parts 2 and 3: uninstall the release build (wipes Forager's data there; the owner chose this), install a debug build of main, run the paths (reopen shows Stop and the HUD; early swipe-away gets the start marker at the first point; Record cannot start a second recording; a stuck track made by a force-stop reads as finished after the next open; tap the recording notification with the app open and press Back to see whether a second copy is stacked); tell the owner just before, and expect the coder to ask them in its own window. The off-track alert itself cannot be triggered on a desk and stays for the owner's walk.
- Still unanswered by the owner: the no-stored-point stuck track (A finished and deletable, recommended; B removed).

**Part 3 is ready to merge, waiting on the owner's word (2026-10-02 ~11:00 UTC).** The coder's comment fix is at `alerts-in-service` `3b0e2d3e` (comment lines only, checked). Continuation `2026-09-28-411` (3b hand-back and review) is on `records-after-148` at `e1f6c113`. **Branch `t1-part-3` at `b4720edf`** joins `alerts-in-service` `3b0e2d3e` with `records-after-148` `e1f6c113`; no file under `app/` differs from the coder's head; pushed, **no PR, not merged: asked the owner**. Next record ID `-412`. On the owner's yes: `gh pr create --base main --head t1-part-3`, backup `2026-10-02-02` (bundle of main at `da9437fa`), CI watcher, merge with `--match-head-commit`, verify main's `app/` equals `3b0e2d3e`'s, tell the owner and the coder; note the merge in `-412`. Then Amendment 4 (the S22 check, with the uninstall and install named; tell the owner just before; the coder asks them in its window before touching the phone). Still unanswered: the no-stored-point stuck track.

**The owner (2026-10-02T12:14Z), verbatim: "Open a pr and merge" / "2. Option A".** 
- **PR #149** (`t1-part-3` into main, head `b4720edfa62ea4feb0a95dc4d62034bb07205232`, app code = coder's `3b0e2d3e`) is open; backup `2026-10-02-02` (bundle of main at `da9437fa`) written, verified, indexed; a background task waits for CI run 37005619706. When green and `origin/main` is still `da9437fa`: `gh pr merge 149 --merge --match-head-commit b4720edfa62ea4feb0a95dc4d62034bb07205232`, verify main's `app/` equals `3b0e2d3e`'s, tell the owner and the coder. Red on the known intermittent `MushroomLogViewModelWiringTest` only: re-run once and say so; anything else red: stop and tell the owner. Note the merge in `-413`.
- **Option A for empty stuck tracks:** a stuck track with no stored point is ended at its own start time (a finished track with no points, deletable); nothing is deleted. **Part 3c** is appended to the dispatch file with continuation `2026-09-28-412` on `records-after-149` at `957445f7` (cut from `t1-part-3`; pushed, unmerged, no PR). The coder was told to go. Next record ID `-413`.
- After Part 3c's hand-back and review: join and merge on the owner's word (with `records-after-149`), then Amendment 4, the S22 check (uninstall the release build, install a debug build of main; tell the owner just before).

**PR #149 is merged (2026-10-02T12:24:38Z): Part 3 (3a and 3b) is on main.** Main is `fe598a7dd41f77c7f1127cd2372e19097239c211`; CI run 37005619706 green on head `b4720edf`; backup `2026-10-02-02`; on the owner's "Open a pr and merge". Main's `app/` equals the coder's reviewed `3b0e2d3e`. Records `-406` to `-411` and Amendment 3 with its rulings are on main. #149's merge has no entry: note it in `-413`. Not run on a phone. Part 3c (empty stuck tracks ended at their start time) is with the coder; `records-after-149` (`957445f7`: Part 3c's text and `-412`) is pushed and unmerged.

**Part 3c handed back and ACCEPTED (2026-10-02 ~13:10 UTC); ready to merge, waiting on the owner's word.** `alerts-in-service` at `2b9546cb` (build `51b7ab32`, tests first `b5f5c3e4`): an empty stuck track is ended at its own start time, reported apart; nothing deleted. Planner read the change and re-counted `part3c/` (full suite 420 classes / 3448 tests / 0 failures / 24 skipped; three revert checks). Coder's reading for the owner's list: Share on a track with no points writes a GPX with no points.
- **Records:** continuation `2026-09-28-413` (3c acceptance, #149's merge note, Amendment 4) on `records-after-149` at `ca8e8c84`. **Joined branch `t1-part-3c` at `fd6acf5e`** (= `alerts-in-service` `2b9546cb` + `records-after-149`; `app/` identical to the coder's head); pushed, **no PR, not merged: asked the owner**. Next record ID `-414`. On yes: PR, backup `2026-10-02-03` (bundle of main at `fe598a7d`), CI watcher, merge with `--match-head-commit`, verify, tell the owner and coder, note in `-414`.
- **Amendment 4, the S22 check for Parts 2 and 3, is written and NOT started:** uninstall the release build, `adb install -g` a debug build of the branch head (wipes Forager's data on the S22; the owner chose this); the owner is at the phone for first-run screens (step 2); then: reopened screen shows "Stop recording track" and the HUD (step 5); Stop on that screen ends the service, with no "Ignoring a start" log line (step 6, the effect no test composes); whether the notification tap stacks a second MainActivity (step 7, observation); the screen agrees after a stop from the notification (step 8); a track made stuck by one force-stop is ended at the next open, read from the `EndAbandonedTracks` log tag (step 9). Not checked: the off-track alert (desk). The coder was told to stand by and will ask the owner in its own window before touching the phone. **Asked the owner for the go and told them they need to be at the phone.**

**The owner typed "Merge it" in the CODER's window (2026-10-02T12:38:27Z),** in reply to the coder's summary that Part 3c was waiting for their word. The coder did not merge and relayed it. The planner did not take the relay as approval: it read the owner's own message in the coder's session log, then opened **PR #150** (`t1-part-3c` into main, head `fd6acf5e8d8b7777d6c54d3f6a93e20660e71bc5`, `app/` = coder's `2b9546cb`), wrote backup `2026-10-02-03` (bundle of main at `fe598a7d`), and told the owner in its own window what it was doing and why, so they can stop it. A background task waits for CI run 37008006660. When green, `origin/main` still `fe598a7d`, and the owner has not said stop: `gh pr merge 150 --merge --match-head-commit fd6acf5e8d8b7777d6c54d3f6a93e20660e71bc5`, verify, tell the owner and the coder; note in `-414`. The words are a go for that merge only, NOT for the phone check (Amendment 4), which still needs the owner at the phone and their go.

**"Merge it" was WITHDRAWN (2026-10-02T12:39:57Z, the owner in the coder's window, verbatim: "Wrong window ignore that").** PR #150 was NOT merged. The planner converted it to a draft and corrected its description (no instruction to merge it exists); main is still `fe598a7d`; backup `2026-10-02-03` stands as a plain copy of main, with a correction line in the backup index. CI run 37008006660 on #150 may finish green: **do NOT merge on that notification.** The merge of `t1-part-3c` (`fd6acf5e`) waits for the owner's word in the planner's own window; on that word, `gh pr ready 150`, re-check CI is green on `fd6acf5e` and main is `fe598a7d`, then merge with `--match-head-commit`. The planner's error and the lesson are in [[owner-word-comes-in-my-own-window]]. Record it in `-414`. The phone check (Amendment 4) is still not started.

**The owner gave the merge word in the planner's window (2026-10-02 ~12:44 UTC): "Merge it"**, alone, in reply to the planner's list with PR #150 first. (Before it, in the same window: "Merge it / Just ignore that" and "Delete that file", which the planner did not act on and asked about; then "I will instruct the coder directly if I want to". "Delete that file" was never explained: nothing was deleted; do not act on it.) PR #150 is marked ready again and its description corrected; CI run 37008006660 was still running. **On the watcher's notification, if green on `fd6acf5e` and main is still `fe598a7d`: merge with `--match-head-commit fd6acf5e8d8b7777d6c54d3f6a93e20660e71bc5`**, verify, tell the owner and the coder, note in `-414` with the withdrawn word and the planner's error. The phone check still needs its own go.

**The phone check has the owner's go (2026-10-02 ~12:45 UTC, in the planner's window, verbatim): "Nothing to delete. Phone is ready for the check".** ("Delete that file" is closed: nothing to delete.) The coder was told to go on Amendment 4 and to ask the owner in its own window before the uninstall. Planner's read-only look at 12:45Z: S22 attached, upright, awake, release build `1.0.2426+ge0239399` in front, no service. **At step 2 the coder stops and tells the planner; the planner asks the owner to walk through first-run screens and leave the app on the Maps tab, then gives the coder the word.** PR #150's CI still running; merge on green per the earlier note.

**PR #150 is merged (2026-10-02T12:49:19Z): Part 3c is on main.** Main is `009d8951dfb6e827ebe955aed3c014e468a22825`; CI run 37008006660 green on head `fd6acf5e`; backup `2026-10-02-03`; on the owner's "Merge it" given in the planner's window. Main's `app/` equals the coder's `2b9546cb`. Records `-412`, `-413` and Amendment 4 are on main. Owed in `-414`: #150's merge, the withdrawn wrong-window word and the planner's error, the owner's "Phone is ready for the check". All of T1's code (Parts 2, 3, 3c) is now on main; none of it run on a phone yet. The phone check (Amendment 4) is with the coder; the planner relays the owner's "ready" at step 2.

**The S22 check for Parts 2 and 3 ran to its end and passed every gate (2026-10-02 ~13:27 UTC).** Debug build `1.0.2485+g7f45b99c` (same app code as main `009d8951`) installed after removing the release build (the owner: "Yes, uninstall and run" in the coder's window); no first-run screens. Reopened screen showed "Stop recording track", the return HUD and the start marker; Stop there ended the service and notification; no "Ignoring a start" logged; a stop from the notification was seen by the screen; a force-stopped recording's row was ended at the next open ("... with nothing recorded in them, each at its own start time"; Records shows "0 points"). **Finding confirmed on hardware: the recording notification's tap stacks a second MainActivity** (task with two history records). Planner re-read the saved evidence. Not shown: the off-track alert (desk), unplugged/pocket, a stuck track with stored points, the refusal sentence. S22 now has the debug build, not the release build, with three ended test tracks.
- Report `docs/navigation/2026-10-02-alerts-in-service-device-check.md` is on `alerts-in-service` `4fc262fa`. Continuation `2026-09-28-414` (the check, #150's merge, the wrong-window error) is on **branch `t1-device-check` at `9c1a609c`** (= `alerts-in-service` + main + `-414`; docs and records only, no `app/` difference from main); pushed, **no PR, not merged: asked the owner in the planner's window**. Next record ID `-415`.
- Dispatch `-400` has no terminal yet. Remaining for T1: the off-track alert on a real walk (owner), then the terminal. Next plan tasks by order: T2 (sundown alerts fire, now that alerts live in the service), T3, T4. Asked the owner what they want next, and whether to fix the stacked-screen finding as a small task.

**The owner told the coder "Merge the device check" (relayed), then told the planner: "I told the coder the merge device check".** Taken as the owner's word given in the planner's window. **PR #151** (`t1-device-check` into main, head `9c1a609ccafd47daa4dced4eaff812015a0cbb22`, docs and records only) is open; backup `2026-10-02-04` (bundle of main at `009d8951`) written and indexed; a background task waits for CI. On green with main still `009d8951`: merge with `--match-head-commit`, verify, tell the owner and coder, note in `-415`. Still open with the owner: fix the stacked-screen fault now or leave it; what next (T2 sundown alerts, or the off-track walk). The owner clearly prefers to give some instructions through the coder's window; confirm such relays here in one short line and then act, without lecturing about windows.

**The owner (2026-10-02T17:35Z), verbatim: "Fix the fault now, I'll do off track alert first".** 
- **New dispatch `2026-09-28-415`, notification-tap** (`prompts/preserved/2026-10-02-02.md`, intent on branch `records-after-151` at `4f364ed3`, cut from `t1-device-check`; pushed, unmerged): the recording notification's tap stacks a second MainActivity; diagnosis to confirm: no flags on its intent (`TrackRecordingService.kt:229-232`) where the backup notification sets SINGLE_TOP and CLEAR_TOP; smallest fix on that one intent, not the manifest launch mode; test first, revert check, full suite, then one phone step (`adb install -r` over the debug build, repeat step 7, pass on ONE MainActivity record). Coder told to go on branch `notification-tap` from main; it may ask the owner in its own window. Next record ID `-416`.
- **The owner walks the off-track alert themselves next.** The S22 has the debug build with all of T1 (`1.0.2485+g7f45b99c`); the S26 has release 2426 without it. The planner gave the owner a short walk checklist. T2 (sundown alerts) and `-400`'s terminal wait for the walk. What the walk shows feeds T21 (what "off track" means).
- PR #151 (device-check report and `-414`) open, backup `2026-10-02-04`, CI watcher running (no run had appeared at first look).

**The owner: "I'll do a full walk for this one", then "Have the coder work first".** So the S22 stays on USB until the coder's phone step for `-415` is done; the owner then walks the off-track alert with the fixed debug build on the S22. The coder was told to leave the fixed build on the phone, Maps tab, no recording, and to tell the planner at once so the owner can take the phone. After the walk: the owner's notes, the recorded track (useful for Stage E), and the location log if the phone is plugged back in soon.

**PR #151 is merged (2026-10-02T17:42:56Z):** main is `3e82b5ca2fc8e762dd6253cd1d32d6800de57084`; CI run 37041745684 green on head `9c1a609c`; backup `2026-10-02-04`; on the owner's word confirmed in the planner's window. The device-check report and `-414` are on main; no `app/` change. Note the merge in `-416`. `records-after-151` (`4f364ed3`: dispatch `-415` and its intent) is still pushed and unmerged. The coder is on `-415` (branch `notification-tap`); the owner walks after its phone step.

**`-415` notification-tap: the phone step PASSED and the S22 is free (2026-10-02 19:11 UTC).** Fix on branch `notification-tap` at `c5c77acc` (test first `e89516d9`, failed with "Flags were 0x0"): SINGLE_TOP and CLEAR_TOP on the recording notification's intent, one line in `TrackRecordingService`. Coder reports full suite 421 classes / 3449 tests / 0 failures; its report is still to come, and the planner has not yet re-counted the XML or reviewed the report. On the S22 (planner's read-only look): debug build `1.0.2490+gc5c77acc`, no service, no notification, one MainActivity record. The coder's phone step: one screen after the tap (was two); with the app swiped away the tap opened one screen showing Stop. The owner was told the phone is free for the off-track walk. Still to do: review the coder's report when it lands, record (`-416`: #151's merge, this hand-back), join with `records-after-151`, ask the owner for the merge. After the walk: the owner's notes, the log if the phone is plugged back in.

**`-415` is closed by terminal `2026-09-28-416` (completed); the fix is ready to merge, waiting on the owner's word.** Coder's branch `notification-tap` at `6d78266f` (fix `c5c77acc`); planner read the diff and re-counted the XML (421 classes / 3449 tests / 0 failures / 24 skipped; revert check fails the new test). **Joined branch `notification-tap-merge` at `23173479`** (= `notification-tap` + `records-after-151` with `-415` and `-416`; `app/` identical to the coder's head); pushed, no PR, not merged: asked the owner. On their yes in the planner's window: PR, backup `2026-10-02-05` (bundle of main at `3e82b5ca`), CI watcher, merge with `--match-head-commit`, verify. Next record ID `-417`. The owner is about to walk the off-track alert on the S22 (debug build `1.0.2490+gc5c77acc`). After the walk: their notes; `-400`'s terminal; then T2 (sundown alerts) unless they say otherwise.

**While the owner walks (2026-10-02 ~19:30 UTC).** The owner: "1 merge it" / "2 let's begin that task" / "25m is fine. We can go with this at first and see how well it behaves. We can also research how and when Google determines off-track behavior for their walking navigation to see where we can improve our methods." / "3 and 4: we can do this by message whenever I have a moment on the walk".
- **PR #152** (`notification-tap-merge` into main, head `23173479`) open; backup `2026-10-02-05` (main at `3e82b5ca`); CI watcher running; merge on green.
- **New dispatch `2026-09-28-417`, way-back-route Part 1 (T5)**, `prompts/preserved/2026-10-02-03.md`, intent on `records-after-152` at `3371df1e` (cut from `notification-tap-merge`; pushed, unmerged): the route home as logic (lookahead 25 m along the walked route, distance along the route, withheld with a reason when off the route, planner's reading of "off the route" = PathHome's HopBand.FAR, over 50 m); report first by message, then build in `domain/`, nothing on screen; branch `way-back-route` from main. Coder told to go. Next record ID `-418`.
- **Background research agent** launched on how established walking navigation decides "off route" (Google docs, open-source apps, techniques, hiking apps; no patents); to be filed under `docs/navigation/` for T21.
- **Owed to the owner by message during the walk:** step paths for T3 (the sundown countdown's place: top strip before navigating and HUD while navigating, or one combined strip) and T8/T9 (navigate to a waypoint: where offered, whether a recording must run, Back and Stop); and the small coordinate-format task (remembered across tab changes).

**Off-route research filed (2026-10-02 ~19:45 UTC):** `docs/navigation/2026-10-02-off-route-detection-research.md` with two index rows, on `records-after-152` at `21681892` (pushed, unmerged). Research agent's reading, NOT re-checked by the planner; public sources, no patents. Key points: Google documents an off-route callback but no walking threshold (unknown); OsmAnd reroutes on one fix past 60 m + 2 x accuracy; Organic Maps uses max(20 m, accuracy) with a fix counter (~6-11 fixes) reset by any on-route fix; Mapbox legacy 50 m (25 m near intersections) and no repeat until 50 m moved; heading unusable at walking pace / phone in pocket. Five candidate rules for T21 (accuracy-scaled corridor to the walked path; 15 s + 5-fix persistence; network/poor-accuracy fixes freeze the count; hysteresis, one alert per episode; distance-from-path trend), each testable on recorded walks with the walker's labelled detours. Nothing decided; for T21 with the owner's walk results.

**PR #152 merged: the notification fix is on main** (`c42d857ef0191b2c8e1a641ec7e3925d143b7bdc`; CI run 37094401516 green on `23173479`; backup `2026-10-02-05`; on the owner's "merge it"). Note in `-418`. `records-after-152` (`21681892`: dispatch `-417` and the research doc) is pushed and unmerged; it will go with the way-back route's hand-back.

**Way-back route (`-417`): findings received, rulings given, build under way (2026-10-03 ~04:10 UTC).** The owner gave the coder "Yes, build it" in its window. Findings: PathHome places the walker at the most recent STORED point (owner ruling against nearest-point projection), "off the route" = HopBand.FAR measures distance to that point, so a stray is recorded and becomes part of the route home; route and off-track alert can disagree visibly (a route bending away from the start can raise "Off track"; a walker far from the last stored point gets "Unable to calculate route" with no alert); the stored track lags the walker 30-45 s (27-40 m), more than the 25 m lookahead. Rulings (continuation `2026-09-28-418` on `records-after-152` at `9b2cf04c`): lookahead = (hop + 25 m) along the route from the last stored point; corner guard reusing the 10 m self-join distance; graph code returns the route's points with behaviour unchanged; shape `domain/RouteHome.kt`. Nearest-point projection is the owner's to choose, only if the walk shows the rule falls short. Next record ID `-419`. Told to the owner as behaviour; feeds T21.

**Way-back route: the planner's corner-guard wording was wrong and is corrected (continuation `2026-09-28-419`, `records-after-152` at `cfec718f`).** Read from the last stored point, the guard pulled the arrow back behind a walker more than 10 m past it, undoing the (hop + 25 m) lookahead; the coder found it while writing tests, before any code. Now the guard checks only route points from the walker's assumed place onward. Kept visible and tested: a walker 30 m beside the route is pointed ~45 m up the path. Next record ID `-420`. Coder building.

**T5 done: `-417` closed by terminal `2026-09-28-420` (completed).** Coder's `way-back-route` at `7d6fb2a2` (`domain/RouteHome.kt` new; `TrackSelfJoin.kt` refactor, behaviour unchanged). Planner read the code and re-counted (422 classes / 3461 tests / 0 failures; five revert checks). **Joined branch `way-back-route-merge` at `origin/w`** (+ `records-after-152`: dispatch `-417`, rulings `-418`/`-419`, terminal `-420`, the off-route research doc); `app/` identical to the coder's head; pushed, no PR: asked the owner. Next record ID `-421`. Next by the plan: T6 (the HUD follows the route; also reuse one route search per poll; honest refresh wording) and T7 (route drawn on the map) — not dispatched; asked the owner whether to continue.

**T5 done: `-417` closed by terminal `2026-09-28-420` (completed).** Coder's `way-back-route` at `7d6fb2a2` (`domain/RouteHome.kt` new; `TrackSelfJoin.kt` refactor, behaviour unchanged). Planner read the code and re-counted (422 classes / 3461 tests / 0 failures; five revert checks). **Joined branch `way-back-route-merge` at `cb539329`** (+ `records-after-152`: dispatch `-417`, rulings `-418`/`-419`, terminal `-420`, the off-route research doc); one index conflict resolved by keeping every row (247 base, 249 merged); `app/` identical to the coder's head; pushed, no PR: asked the owner. Next record ID `-421`. Next by the plan: T6 (the HUD follows the route; reuse one route search per poll; honest refresh wording) and T7 (route drawn on the map) — not dispatched; asked the owner whether to continue.

**The owner (2026-10-03): "Merge it." / "On the walk, ask me those open questions from before".** **PR #153** (`way-back-route-merge` into main, head `cb539329`) open; backup `2026-10-03-01` (main at `c42d857e`); CI watcher running; merge on green with `--match-head-commit`, note in `-421`. The open questions were put to the owner as tappable choices: T3 countdown placement; T8/T9 navigate to a waypoint (where offered, recording required or not, Back); coordinate format remembered; and whether to start T6.

**The owner's four answers (2026-10-03, all the planner's recommendation):** countdown "Strip, then HUD"; Navigate to a waypoint from "Bubble, details, Records"; "No recording; Back stops"; coordinate format and T6 "Both yes". Recorded as decision `2026-09-28-421` plus a plan addendum; **dispatches `-422` (coordinate format, T18; `prompts/preserved/2026-10-03-01.md`) and `-423` (T6, the HUD follows the route; `prompts/preserved/2026-10-03-02.md`)** with intents, on `records-after-153` at `7336e6f4` (cut from `way-back-route-merge`; pushed, unmerged). Coder told to do `-422` now and start `-423` only after PR #153 lands and on the planner's word. T6's refresh label "Try again" is the planner's wording, to confirm with the owner at its phone step. Next record ID `-424`. Open: T3/T4 sundown (after the walk, per the owner), T8/T9 now fully specified by the owner, T21 after the walk.

**PR #153 merged: T5 (RouteHome) and the off-route research are on main** (`5b856b6ae00fdfbd6e177aeb4589f2899d790822`; CI run 37108083344 green on `cb539329`; backup `2026-10-03-01`; on the owner's "Merge it."). Note in `-424`. **The owner: "And have the coder start T6"** — the coder was told to start `-423` now (finishing `-422` first only if already under way).

**The owner's off-track walk (2026-10-03, read from the S22's log; `~/Zynergy/device-evidence/2026-10-03-owner-walk/`, `PLANNER-READING.md`):** recording started 00:46:37 phone time; five off-track alerts at 01:00:41, 01:02:45, 01:04:45, 01:06:53, 01:08:55 (two minutes apart = the cooldown); in the 4 s before EACH alert a network fix with accuracy 400 m (149.6 m once) among 3.8 m GPS fixes. Strong correlation that network fixes trip today's rule, as flag 2 predicted; not proof (the fix log has no positions). Fix lines only from 01:00:28; the first ~14 minutes have none (not determined why). Asked the owner for their account (direction at each alert; app open or swiped; deliberate detours) before writing T21. Not yet in RECORD.md.

**The owner's walk account (2026-10-03, verbatim):** "1 back towards the start. Only one of those times was when I diverged from the track. The rest was walking on the other side of the street." / "2 open, phone off" / "3 yes, that one time I diverged was deliberate" / "4 nothing felt wrong outside the known issues". So 4 of 5 alerts false (other side of the street), all while walking toward the start, app open, screen off; one coincided with a deliberate divergence (also preceded by a network fix, cause not determined). Saved in the walk's `PLANNER-READING.md`. Next: T21 proposal to the owner (distance from the walked path, GPS-quality readings only, a distance and a duration before alerting, one alert per stray), numbers to be tested by replaying the stored walk before anything ships.

**T21 decided by the owner (2026-10-03, all recommendations):** measured against "The path I walked"; "About 40 m" (allowing for poor GPS); "About 15 seconds"; "Once per stray"; network readings stop counting (told, not a choice). Planner's provisional numbers: line = 40 m + reported accuracy; 15 s and >= 3 GPS readings off; re-armed after 10 s back on; "the path" = the track as it stood when Return was tapped. Recorded as decision `2026-09-28-424` (also carries #153's merge) and **dispatch `-425` off-track-rule** (`prompts/preserved/2026-10-03-03.md`, intent) on `records-after-153`. Gate: replay of the owner's walk through both rules before it ships; the walk's stored track copied from the S22 only with the owner's word in the coder's window; positions never committed. Queued after `-422`/`-423`; coder not yet told. Next record ID `-426`.

**T6 (`-423`) being built; rulings filed as continuation `2026-09-28-426`.** The owner chose "Same line, tappable" for "Try again". Rulings: one route search per 5 s tick also gives path home (poll stops its own); "—" before the first route; returning means route mode in tests (existing return tests fed a route, assertions listed); tests written alongside code accepted as a deviation with a revert check each. Coder doing `-423` before `-422`. Disk ~2.3 GB free after the coder deleted its own build folder; `/tmp/ff` and `/tmp/cvenv` are not ours. Next record ID `-427`.

**Backups moved to the owner's USB drive (2026-10-03).** The owner: "There is this drive we can move files to for clearing space if need be. It'll stay plugged in to the laptop" (ext4, UUID 2ebd084f-5fdd-4730-8cef-96b267723190, mounted at `/run/media/zynergy-labs/2ebd084f-5fdd-4730-8cef-96b267723190`; it also holds an `avd` folder that is not ours). All 34 backup folders before `2026-10-03-01` copied to `<drive>/forager-repo-backups/`, verified twice, then removed from the laptop (laptop free 2.2 GB -> 4.3 GB); INDEX.md in both with a note. New backups still go to the laptop. A plain `rm -r "$VAR/..."` is blocked by a safety check; use `"${VAR:?}/${d:?}"`. The owner also chose "Own line under it" for "Try again" after the coder measured the one-line version cutting their sentence on every phone width.

**New task T22, the navigation camera view (2026-10-03).** The owner asked for a GPS-app-style view and chose "Automatically when navigating", "Yes, facing-up" (compass, not GPS), "A gentle tilt". Recorded as decision `2026-09-28-427` plus a plan addendum on `records-after-153`. Not dispatched. Still to put to the owner as a step path: panning stops following?; how to get back to the view; what happens when the compass is unreliable (planner's recommendation: fall back to north-up, said on screen). Next record ID `-428`.

**T22's tilt refined (owner, verbatim): "Have the tilt be enough to focus on the path ahead, not so gentle that it's a cosmetic tilt."** Recorded as decision `-428` (amends `-427`); planner's starting figure ~45 degrees (range 0-60), tuned on a walk. Still unanswered: panning, returning to the view, unreliable compass (recommendations given); no go yet. Next record ID `-429`.

**T22 details from the owner (decision `-429`):** "1 yes" (dragging stops following); "2 Have a "Return to Route" button appear when panning away"; "3 give a notice first that the compass is calculating if it's stuck, after retrying to get it back, then the north up view." Step path put back to the owner for confirmation, with the planner's provisional retry time (~15 s); no go yet. Next record ID `-430`.

**T22 dispatched as `-430` navigation-view** (`prompts/preserved/2026-10-03-04.md`, intent on `records-after-153`): the owner's "Go on T22" to the step path (45 degree tilt, facing-up by compass, walker below centre, "Return to Route" after a drag, "Compass calibrating..." then ~15 s retry then north-up and back, flat north-up when navigation stops). Report-first. **Coder queue: `-423` (T6, in progress) -> `-430` (T22) -> `-422` (coordinate format) -> `-425` (T21 off-track rule).** Next record ID `-431`.

**T6 (`-423`) closed by terminal `-431`; joined branch `t6-merge`** (= `way-back-route-2` `d4fc901a` + `records-after-153`: decisions -421/-424/-427..-429, dispatches -422/-423/-425/-430, rulings -426, terminal -431); pushed, no PR, asked the owner. Planner re-counted (423 classes / 3484 tests / 0 failures; 17 revert checks from saved copies). Two owner findings: "Unable to calculate route" in a recording's first half-minute (points not yet saved); "0 ft" route figure at the start. Coder next: `-430` (T22). Next record ID `-432`.

**PR #154** (`t6-merge` into main, head `ffa0e168`) open on the owner's "Open a pr and merge when CI passes"; backup `2026-10-03-02` (main at `5b856b6a`); CI watcher running; merge on green with `--match-head-commit`, note in `-433`.
**T22 (`-430`) findings and rulings (continuation `-432` on `records-after-154`, cut from `t6-merge`):** owner's go to the coder "Yes, start T22". MapLibre's own compass engine reads MAGNETIC north, so today's puck has pointed ~15 degrees off true (inferred from bytecode); ruled: the app's true heading feeds the puck everywhere. Compass notice carried by the HUD label ("Compass calibrating..." then "Compass unavailable · north up"), no separate map notice. Bubble re-anchors on every camera move while navigating; reset-to-north while navigating = leaving the view (Return to Route); zoom untouched; no-sensor = notice once. Told to the owner. Next record ID `-433`.

**The owner on T6's two findings (2026-10-03):** "1 is fine" / "2 leave for T7", then, after the planner tried to ask which reading of 1 they meant (the question was rejected), "Leave that as is". So: "Unable to calculate route" in a recording's first half-minute stays as built; "0 ft" at the start is left for T7. Do not re-ask. Record both in `-433`. Also ruled for T22: the Journal's centre-pin picker map gets the true heading too, so its puck points true north (coder's option (i)); record in `-433`.

**PR #154 merged: T6 is on main** (`f1b53aca17688954e257c7f5f37ea8dae8953532`; CI run 37140806700 green on `ffa0e168`; backup `2026-10-03-02`). Note in `-433`. `records-after-154` (`ec6ae5cb`, rulings `-432`) unmerged. Coder on T22.

**T22 (`-430`) closed by terminal `-433` (completed); joined branch `t22-merge` at `16f1c6d4`** (= `navigation-view` `72528107` + `records-after-154`); pushed, no PR, asked the owner. Confirmed on hardware: the magnetic puck (app engine 256.7 vs HUD 257; library's ~242). The phone step found and fixed two faults (walker centred; Stop left the map tilted). Not seen on phone: stuck compass, reset/locate while navigating, bubble on a turning map, picker pucks, a walk. **The S22 now carries `1.0.2527+g8b9e2719` = T6 + T22: ready for the owner's test walk** (the new off-track rule T21 is not in it). Coder next: `-422`, then `-425`. Next record ID `-434`.

**T18 (`-422`) closed by terminal `-434`.** Coder's `coordinate-format` at `40b131e0` (state moved to AvailabilityScreen; test failed first; 424/3485/0). It conflicts with T22 in three app files; the planner does not resolve app-code conflicts: T22 (`t22-merge`, now `76190267` including `-434`) merges first, then the coder merges main into `coordinate-format` and it gets its own PR. Coder next: `-425` (T21). Next record ID `-435`.

**T21 (`-425`) findings and the replay (2026-10-03 ~19:00 UTC).** The owner gave the coder "Yes, start -425" and "Yes, copy the track" in its window; the walk's DB copied read-only to `~/Zynergy/device-evidence/2026-10-03-off-track-rule/db-copy` (positions stay there). Coder's report: today's rule fed every raw fix incl. network; the 220 stored points (no network points) give the OLD rule 0 alerts, so the five real alerts needed unstored network readings (cannot be proven: no positions in the log). The owner told the coder, in its window, Return was "Halfway through the walk" then "A: at the turnaround". Replay case A: the NEW rule gives exactly one alert, at the start of the deliberate detour (peaks 175 m off the outbound path), none on the way home (0-17 m, one 38 m bump) — matches the owner's account. Planner rulings: Q2 truly once per stray; path = kept points handed from the service and snapshotted at Return; inactive until a path exists; network test = existing timestamp rule; no GPS accuracy ceiling. The owner answered "[No preference]" to the planner's duplicate question (they had answered the coder). Four route-vs-alert disagreements reported for the owner. Coder building, tests first. Record in the next entry (`-435`).

**PR #155** (`t22-merge` into main, head `76190267`) open on the owner's "Open pr and merge"; backup `2026-10-03-03` (main at `f1b53aca`); CI watcher running; merge on green with `--match-head-commit`, note in `-435`. After it lands: tell the coder to merge main into `coordinate-format` (three app-file conflicts), re-test, then a separate PR for T18 (ask the owner).

**PR #155 merged: T22 is on main** (`4f9f344b159c97907ab4d59caf8ba09f296ae654`; CI run 37147361904 green on `76190267`; backup `2026-10-03-03`). Note in `-435`. Coder told to merge main into `coordinate-format` when it reaches a stopping point in T21.

**T21 merged** (PR #156, main 093b28d6, 2026-10-03 20:21 UTC; record -438 on branch t18-merge). S22 has 1.0.2539+g7c7e25c5 (T21, T22, T6) for the owner's walk on the same divergence path; prediction with Return at the turnaround: one alert at the detour's start, none home. T18 is PR #157 (t18-merge, CI pending, merge on green on the owner's "Open the pr and merhe"). Later: read the walk's log against the prediction; T7 next, reconciling RouteHome vs the off-track rule.

**2026-10-03 ~20:50 UTC:** T18 merged (PR #157, main bb0b559e). Owner's T21 walk: no buzz on "same detour" — a fault; dispatch -439 (report only: Part A why no alert, Part B why line leaves street) with the coder; -440 navigation zoom (owner confirmed step path) queued after. Dispatch files and records -439 to -441 on unmerged branch dispatch-439.

**Trail navigation (owner 2026-10-03, RECORD -442 to -446):** navigate along trails where map data has them; off trail, default to the walker's own track and guide back along it. Research filed (docs/navigation/2026-10-03-trail-navigation-research.md). Owner "Confirm 1 to 5": OSM routable base, agency trails as reference layer only; NPS and WA RCO excluded; engine spike Valhalla vs BRouter on S22 (dispatch -446, queued third after -439, -440); off-trail 5-step path as a starting point; sits in the next update with own map tiles and T7. All on unmerged branch dispatch-439.

**2026-10-03 21:45 UTC:** PR #158 merged (main 09e43bd3, docs: records -439 to -452, dispatches -439/-440/-446/-451, walk report, trail research). Owner: plan map retiling around trail navigation; combined plan filed docs/plans/trail-navigation-and-own-tiles.md (9 stages, spike is stage 1); -446 Amendment 2 (packing per region); records -453, -454 on unmerged branch records-after-158. Coder queue: -440 zoom (building, 18 provisional, zoom past cap), -451 Return record, -446 spike. Walk report: no buzz was Return tapped mid-detour (owner remembers before; unresolved, -451 will record next time); line off street = GPS bias, only map matching fixes it.

**Map service (owner, 2026-10-04, RECORD -466, -472, -479 to -481):** Pi 5 is the serving origin (Wi-Fi, no Ethernet), hidden behind the Worker: tiles.zynergy-labs.com = Worker; Pi at origin.zynergy-labs.com guarded by a Cloudflare Access service token; locally-managed tunnel, no Workers VPC. The zynergy-labs.com Cloudflare account is the launch account; the old account (forager-pmtiles workers.dev, R2 forager-maps) keeps serving the app unchanged until the switch; the old archive is NOT copied, the new storage starts with Forager's own trail-attribute build (stage 4). Pi coder (Claude Code on the Pi, user bwann83, owner relays) running dispatch -468: steps 1-8 done, step 9 (tunnel login) next. Engine spike done (-476); Valhalla trail-preference follow-up queued (-478); laptop coder on -477 (fling).

**Two planners (owner, 2026-10-04, RECORD -496):** a second planner runs on the Pi and owns the map service (Pi, Worker/storage in the zynergy-labs account, data pipeline, stage 4+); handoff prompts/preserved/2026-10-04-07-pi-planner-handoff.md. This laptop planner owns the app side (app, laptop coder, app PRs, S22). Owner relays. RECORD IDs: Pi planner uses 2026-09-28-600 to -699; laptop continues from -497. Spec ruled (-492, -493): OR+WA first, own matching, T7 ships first on the walked track, weekly rebuild, region limit kept. Labels work online+offline on 13.5.0 (-494). Laptop disk ~1.7 GB (needs 2 GB for Gradle; ~/.gradle 6.5 GB).

**Superseding scope (owner, 2026-10-11, after the forecast pilot went live).** Verbatim: "Next update should focus on trail navigation and maps. We have regular navigation down, which is a good foundation for both items of work" (RECORD -904). The plan for both already exists and is ruled: docs/plans/trail-navigation-and-own-tiles.md with the spec docs/plans/2026-10-04-own-map-service-spec.md (-492, -493), as amended on 2026-10-11 (Cloudflare serves, the Pi builds weekly and keeps a copy, -879; first layers street, offline maps with names, terrain shading, -880; land ownership researched, options pending, -882/-883). The map service from stage 4 onward belongs to the Pi planner; the app side to the laptop planner. Still scope, not a go-ahead. The production release of tidiness and lint (-831, [[production-release-scope]]) and its order relative to this update were asked about.

**2026-10-25 (RECORD -1628):** the release waits for the whole trail navigation plan (docs/plans/2026-10-21-trail-navigation-plan-update.md): steps 6 to 11 plus elevation in routing, owner's "Everything". Supersedes -1612's "until step 7". Each step still needs its own go.

### bring-stop-and-ask-to-owner (bring-stop-and-ask-to-owner.md)

*When a dispatch says \"stop and ask\" for a kind of change, the owner decides it, even when the planner judges no screen is affected; the planner does not read the trigger narrowly and rule it itself*

On 2026-09-30 the planner ruled on -317's size difference itself. Option (c) makes an unsized DecodedPhoto placeholder fill its bounds instead of measuring 0×0, and no call site in the app is unsized. The dispatch said "stop and ask if the choice changes any photo's size". The planner read "a photo" narrowly, as a shipped photo, and accepted option A itself. Asked about it, the owner said, verbatim: "Yeah let me know instead".

**Why:** the owner wants to make these calls. A dispatch's stop-and-ask trigger is the owner's line, not a planner judgment about impact.

**How to apply:**
- When a coder reports a difference that matches a stop-and-ask trigger in its dispatch (size, semantics, behaviour, data, UX), bring it to the owner with the options and a recommendation, even when the planner believes nothing user-visible changes.
- The planner may still rule on purely technical choices that fall inside the owner's stated rule, for example the -310 crossfade range, which stayed within "never show a light map". Say in the reply which one it was.
- If unsure whether something is the owner's call, ask ([[ask-for-clarity-is-welcome]]).

### flake-hunt-deferred (flake-hunt-deferred.md)

*Owner will run a test-flake hunt after development cools down; -348 and -349 are parked with their findings, and the remaining CI flakiness (about 6% of runs) waits for it*

On 2026-10-01 the owner parked the remaining CI flake work, verbatim: "I'll be conducting a flake hunt once dev cools down a bit, this can wait until then."

**What is fixed:** the album lost-gesture flake, which failed about 56% of CI runs. Dispatch -296 diagnosed it and -317 fixed it, merged as `851e28fd`.

**What is parked, with findings on scratch branches that are never merged:**
- **-349, `leaving-journal-flake`:** 6 failures in 99 CI runs (Wilson 2.8% to 12.6%) in `LeavingTheJournalFixesTest`'s F3 tests. They are one family: "performMeasureAndLayout called during measure layout", or multithreaded access to `SnapshotStateObserver`. The same message appears in `WideJournalTest`, `JournalEntriesOnMapFollowUpsTest` and `DiagnosticsSyntheticForecastSwitchTest`.
- **-348, `decoded-photo-thread`:** a `CalledFromWrongThreadException` in `DecodedPhotoTest`, seen once in 12 pinned runs.
- **The shared mechanism, inferred from stacks and not reproduced on demand:** `DecodedPhoto`'s `LaunchedEffect { withContext(Dispatchers.IO) { decode } }` returns on an IO worker. The Compose test harness (`FrameDeferredContinuation`) resumes inline on that worker when the main thread is outside a test-clock frame. The recompose-and-apply then runs on the worker while the main thread is in a Choreographer-driven layout.
- **Production is inferred safe:** `AndroidUiDispatcher` posts back to the UI thread. That has not been observed on a device.
- **Ready-to-start plans:** -349's deterministic repro (park the main thread in a Choreographer-driven measure, release the gated decode), and a statistical budget of about 125 runs, about 4 hours. Likely fixes are a test-controllable dispatcher for the decode, or a harness setting. That is the owner's decision.
- **-297's `DrawerBackOverJournalTest` stall:** seen once, not reproduced in 9 runs, cause unknown.
- **-312's `MushroomLogViewModelWiringTest`** (merged 2026-10-01, `a973ab62`): it polls the real database with `Thread.sleep` for up to 10 seconds. **Now an observed intermittent failure (2026-10-01 20:13Z):** on commit `9a65056f` (PR #140's merge into pre-main) CI run 36919950945 failed its first attempt on "an album photo deleted through the Journal's ViewModel leaves the Maps tab's records", an AssertionError at `MushroomLogViewModelWiringTest.kt:92`: after the delete finished and the main looper idled, the map's snapshot still held the photo. The paired pull_request run, the previous head's run and the re-run of the same job all passed: three passes, one failure on one tree. Not determined whether it is the test's timing or the app being late to update the map after a photo delete. Recorded in `RECORD.md` merge entry `2026-09-28-396`. The owner, told of it: "Rerun it", "Merge if it passes".

**Why:** the owner wants to do the flake hunt as one focused effort, not as piecemeal dispatches during feature work.

**How to apply:**
- Do not dispatch more flake diagnosis until the owner starts the hunt. When they do, start from these branches and reports, not from scratch.
- Until then, expect a CI run on `journal-redesign` to fail from this family roughly 6% of the time. Read each failure's message before treating it as new: name the test and the message, and compare them with this family.
- "About 6 consecutive green CI runs" as proof of the album fix is confounded by this flake. Judge the album fix by whether `JournalPendingDeleteTest`'s album tests and `JournalTabTest` From Album pass, not by whole-run colour.
- Related: [[forager-journal-redesign-handoff]], [[planner-as-build-traffic-controller]].

### read-the-ruling-not-its-label (read-the-ruling-not-its-label.md)

*Before telling the owner a change \"reverses\" one of their rulings, read the RECORD.md entry; a code comment or dispatch one-liner naming the ruling is not the ruling. Back closes what is drawn on top first.*

Do not characterise an owner ruling from its label in a code comment, a dispatch summary or a prior planner's phrasing. Read the `RECORD.md` entry that holds it, then say what it ruled.

**Why:** on 2026-10-01 the planner told the owner that putting the search dropdown into the fan's and bubble's Back gates "undoes your Option A". It had read only the comment at `AvailabilityCompactScaffold.kt:920-924` and the -312 dispatch's one-line summary. Continuation `2026-09-28-303` actually says the dropdown term was dropped while the Robolectric harness reopened the dropdown, with the device question left open. The owner corrected it: "It does not undo my Option A, it fulfills it. I never said to have the fan close before what's drawn on top". The same mislabel was then passed to the coder and had to be withdrawn.

**How to apply:**
- Before writing "this reverses / undoes / contradicts your earlier ruling", open the record entry and quote it.
- A deferral made because a test harness misbehaved is not a product ruling. Do not present ending the deferral as overturning the owner.
- The owner's standing rule for Back: what is drawn on top closes first (keyboard, then dropdown or other panel, then bubble, then fan). See [[dont-make-me-think]].

### check-from-source-for-dispatches (check-from-source-for-dispatches.md)

*Owner rule (2026-10-16): \"Memory drifts, so always check from source\" when making dispatches; recall, summaries, handoffs, reader reports and these memory notes are pointers, never sources*

The owner, 2026-10-16, verbatim: "Memory drifts, so always check from source should be a rule for making dispatches".

**Why:** in the E-GD curation session that day, about ten premises written from memory or relay were wrong and were caught only by reading the source while writing them in: a task called built that never was, a principle called "never exercised" that was practised, an n of 10 that was 9, a reader's line numbers, "outcome entries close like terminals" (35 of 40 continue), "the kit defined four types" (three), commit messages said to carry wording none did. Same failure as Forager's planner-decay pitfall and E-GD's Sourced Claims.

**How to apply:** every factual claim in a dispatch or proposal (path, line, ID, count, branch, commit, what a record or ruling says, whether something exists) is read from its source while writing and cited with the commit or time read. Recall, a summary, a handoff, an earlier report, a subagent's reading, and these memory notes are pointers to where to look. What can't be checked before dispatch is marked unverified and listed as a premise for the executor to check first. Related: [[read-the-ruling-not-its-label]], [[egd-repo-local-clone]].

### confirm-behaviour-as-a-step-path (confirm-behaviour-as-a-step-path.md)

*Before dispatching a change to how the app behaves, restate it to the owner as a step-by-step path (tap X > Y shows > Back > ...) and get it confirmed; a one-line rule can be read two ways*

Before a behaviour change goes to a coder, write it out for the owner as a concrete path, one step per arrow, naming exactly which thing is tapped and what is on screen after each step, and wait for them to confirm or correct it. Do not dispatch from a one-sentence rule.

**Why:** on 2026-10-01 the owner said "when an icon gets tapped, immediately display the icon and dismiss the fan upon that single tap". The planner read "an icon" as an icon inside the open fan and dispatched a change (-381) that put the fan away when a fanned icon was tapped. The owner meant a tap on some other marker while a fan is open, which the app already did. A coder built and tested the wrong behaviour for an hour before the owner wrote the path themselves ("Tap stacked icons > icons fan out > tap x icon > bubble opens > ... > hit back > bubble closes > hit back > fan closes") and then said plainly: "The fan isn't put away while the bubble shows." The planner had restated the rule in prose twice; the owner agreed to the order of work, not to the reading. Also the owner had reported the behaviour "confirmed on the new build", and the planner explained that away instead of asking what exactly they had tapped.

**How to apply:**
- Use the owner's own notation: `tap A > B shows > tap C > D shows > Back > ...`. Include what stays on screen at each step, and each Back.
- When the owner says something already works and the code seems to say otherwise, ask what they tapped and saw before concluding they were mistaken. They test on the phone; a disagreement usually means two different cases.
- Words like "an icon", "the fan", "it" are where the readings split. Name the specific thing.
- A path the owner writes outranks any earlier one-line rule it seems to contradict; record both and say which stands.
- See [[forager-back-retraces-the-way-in]], [[owner-systems-thinker-not-programmer]], [[ask-for-clarity-is-welcome]], [[read-the-ruling-not-its-label]].

### forager-back-retraces-the-way-in (forager-back-retraces-the-way-in.md)

*How the owner defines Back in Forager; Back undoes one step at a time along the exact path the user came by; going straight to the map is a home function, never Back*

The owner's definition of Back for Forager, verbatim, 2026-10-01: "back out one step at a time, going exactly the way they came. That's how Forager defines back. Otherwise returning to map is a home function, and that's called for in different ways, but not with the back button". And of the return from a find's page to the map: "fan and the find's bubble, back out one step at a time. That's how I initially asked for it". What "home" means, the same day: "Return to home meaning, it skips directly back to the maps with everything closed". So home lands on the map with no fan, no bubble and nothing else open; Back never produces that state unless that is where the user started.

**Why:** said when the planner recommended that Back from a find's page return to the fan only, dropping the find's bubble, and that a journal entry opened from a bubble's "kept in" line was returning to the map with nothing restored. Both broke the rule: each Back press undoes the last step the user took, so what they were looking at before that step is what they see again.

**How to apply:**
- For any Back path, write out the way in as steps (stack, fan, item's bubble, page) and check that each Back shows the previous step, with the same thing open that was open then. A Back that skips a step, lands somewhere the user did not come from, or returns "home" is a fault, not a simplification.
- Do not recommend dropping a restored state on Back because it seems redundant. If a design makes "the way they came" ambiguous (a step that puts something away as it shows the next thing), ask the owner what Back shows; do not pick.
- Getting to the map directly is a separate, deliberate action (a tab, a home control), never the Back button.
- Related: [[read-the-ruling-not-its-label]] (Back closes what is drawn on top first), [[dont-make-me-think]], [[owner-systems-thinker-not-programmer]]. Dispatch -255 item 7 (a bubble closes before the fan behind it) is this rule applied on the map.

### owner-systems-thinker-not-programmer (owner-systems-thinker-not-programmer.md)

*The owner is not a programmer but understands systems architecture and logic; explain in behaviour and logic terms, and bring them architecture and logic questions*

The owner does not read code and will not guess at code terms. Verbatim, 2026-10-01: "I'm not a programmer so I don't know what stubs for fanCircleBitmap (blank) and fanPushPlan would even mean, and I'll never try to guess. But I understand systems architecture and logic, so I can at least help with that".

**Why:** said after seeing a coder's status line full of function names. The same day they settled a rendering fault's design by reasoning about what moves, when, and whether a movement is intentional, without any code.

**How to apply:**
- Report to the owner in terms of what the app does, what causes what, and what each choice costs. A function, file or layer name gets a plain-words gloss the first time, or is left out; file:line references are for the record, not the explanation.
- When a fault has a structural cause (two things updated separately, an order that races, a state that is thrown away), say it as a system: the parts, what each one does, and where they get out of step. They can reason about that and often see a simpler design.
- Bring architecture and logic decisions to them; do not bring implementation mechanics. The owner: "I'm sure the coder can figure the logistics out for that just fine".
- A coder's messages are written for the planner. Translate them; do not forward their vocabulary.
- See [[dont-make-me-think]] and [[ask-for-clarity-is-welcome]].

### owner-answers-are-not-rules (owner-answers-are-not-rules.md)

*Do not label the owner's answers or preferences \"the owner's rule N\" in code comments, dispatches or records; quote their words and cite the dispatch instead*

Do not call something the owner said a "rule", or number it as one ("the owner's rule 6"), in code comments, dispatches, launch prompts or `RECORD.md`. Quote the owner's words and cite the dispatch they belong to.

**Why:** on 2026-10-01 a new comment on `FAN_DURATION_MS` said 250 ms "replaces his earlier rule 6, 'Give it a .4s animation speed'". The label came from the fan-out's first dispatch (`prompts/preserved/2026-09-29-39.md`), where the owner had answered a numbered list of seven behaviour points, and the code had called those answers "the owner's rule 3/4/5/6" ever since. The owner, on being shown it: "Yeah remove that. It was never a rule". An answer to a planner's question is a choice made at the time, and calling it a rule makes it sound like a standing constraint the owner must formally overturn. The same sentence also assumed the owner's pronoun ("his"); use "the owner's".

**How to apply:**
- In a dispatch, tell the coder how to word comments: the owner's words in quotes plus the dispatch ID, no "rule", no numbering borrowed from a question list, no pronoun for the owner.
- When a new instruction changes an earlier value, the comment states the current value and its source. History of superseded values belongs in `RECORD.md` and the completion report, not in a comment that reads as if a rule was broken.
- The fan-out's other "rule N" comments (in `MarkerFanOut.kt`, `MapTapHandler.kt`, `MarkerFanOutState.kt` and three test class comments): the owner first said "Leave them.", then "Change them too". Dispatch `2026-09-28-361` (`prompts/preserved/2026-10-01-04.md`) rewords them, comments only. "Understory rule 1/2" and `CameraBands.kt`'s "rule 9" are a different thing and were left.
- The same goes for the verb: the owner then had every comment that said "the owner ruled …" reworded to "decided", "chose" or "said" (`-364`/`-365`, `-367`), and one test renamed to "… the default the owner chose". Write those verbs from the start. The noun "ruling" (about 240 comment lines, and this repo's own `RECORD.md` field "The owner's ruling, verbatim") was left by the owner's choice on 2026-10-01; do not sweep it without asking.
- A label can also be wrong about the fact: `BackupSchedule.kt` said the backup frequency had "no ruled default" when the owner had chosen Weekly ("weekly to start, with default off…", `docs/plans/journal-redesign.md:911-920`). Check the owner's words before writing that they did or did not decide something.
- Related: [[read-the-ruling-not-its-label]].

### play-console-symbol-warnings (play-console-symbol-warnings.md)

*Play Console bundle warnings: owner ruled 2026-10-12 (RECORD -917) R8 stays off this release, native symbols also off this release (-922: setting alone cannot read map crashes; needs prefab packaging + NDK), both for a later release*

On 2026-10-01, creating a closed-testing (Alpha) release in Play Console for build 2426 (`1.0.2426+ge0239399`, main's tip), the owner saw two warnings on the App Bundle and said "Need these at some point", then "Next release":

1. No deobfuscation file (R8/ProGuard mapping).
2. The bundle contains native code and no debug symbols were uploaded.

State read on 2026-10-01 at `e0239399`: the release build has code shrinking off (`app/build.gradle.kts:288`, `isMinifyEnabled = false`) and `app/proguard-rules.pro` is a single comment, so there is no mapping file to upload and crash traces are already readable. No native symbol setting exists in the build file. `docs/plans/2026-09-09-beta-dispatches.md:164` says not to enable R8 before the beta: "It is a post-beta change with its own dispatch."

**Why:** neither warning blocks a release; both only affect how readable crash reports are in Play Console. Turning shrinking on changes the release build's behaviour and can cause release-only crashes, so it needs its own decision and a device check of a release build.

**How to apply:** this is scoped to the next release, alongside [[forager-journal-redesign-handoff]] and [[shorter-releases]]. "Next release" is a schedule, not a go-ahead: do not start either without the owner's word ([[dont-infer-authorization-to-build]], [[policy-statements-are-not-instructions]]). When raised, the native-symbols setting is the small one; R8 is the one that needs a decision.

### shorter-releases (shorter-releases.md)

*Owner intends smaller, more frequent Play releases after build 2426, which carried about 1,676 commits of work from 2026-09-20 to 2026-10-01*

On 2026-10-01, submitting build 2426 (`1.0.2426+ge0239399`) to Closed testing (Alpha) in Play Console, the owner said: "I'll have to shorten each release from now on."

**Why:** that release bundled everything from 2026-09-20 to 2026-10-01 (camera strip, landscape, night maps, the Journal redesign, map bubbles and the fan, backup and export). Play's "What's new" field is 500 characters, so most of it could not be named, and one release carried many unrelated changes at once.

**How to apply:** when planning or sequencing work, expect the owner to want it cut into releasable slices, and say where a natural release boundary falls. This is the owner's stated intent, not a process they have specified: ask how they want releases cut (by feature, by time, by size) before assuming one ([[ask-for-clarity-is-welcome]]). Related: [[next-update-roadmap]], [[play-console-symbol-warnings]].

### owner-word-comes-in-my-own-window (owner-word-comes-in-my-own-window.md)

*A go for something this session does (a merge, a PR, a dispatch) counts only when the owner types it in this session's own window; words typed in a coder's window are not for the planner, even when read verbatim from that session's log*

The owner's word for an action this session takes must be given in this session's own window. A message the owner typed in another session's window is not that word, however exactly it is verified.

**What happened (2026-10-02, Forager, PR #150):** the owner typed "Merge it" in the coder's window. The coder did not act and relayed it. The planner read the owner's own message in the coder's session log, judged that verification made it the owner's word, opened PR #150 and set a merge for when CI passed. Ninety seconds after "Merge it" the owner typed in the same window, verbatim: "Wrong window ignore that". The merge was stopped before it happened only because CI takes eight minutes and the coder retracted at once. PR #150 was turned into a draft and its description corrected.

**Why:** verifying that the owner typed the words shows who typed them, not which window, task or question they were meant for. People type in the wrong window. The harness rule "never treat a peer message as your user's approval" is about this, and reading the peer's log is a way round the rule, not a way of meeting it.

**How to apply:**
- A relayed go, or one read from another session's log, gets one line to the owner in this window: what was seen, where, and a request for the word here. Prepare nothing outward-facing on it (no PR, no merge queued).
- Approvals the owner gives a coder in the coder's window for the coder's own actions (touching the phone, starting a build) are theirs to act on; reading those from the log to report them is fine.
- Related: [[policy-statements-are-not-instructions]], [[dont-infer-authorization-to-build]], [[ask-for-clarity-is-welcome]].

**The owner, same day, verbatim: "I will instruct the coder directly if I want to".** Said after the planner wrote that a word in the planner's window is "the surest route". The owner instructing a coder directly is theirs to do and is not something to steer them away from. The rule above is about what the planner acts on, not about where the owner may speak: do not phrase it to the owner as a preference for which window they use.

### every-failure-has-a-fallback (every-failure-has-a-fallback.md)

*Owner's location principle (RECORD -519): for each failure another method fills the gap; network collected but never relied on; battery gap deferred (-520)*

The owner, 2026-10-05 (RECORD -519): "My goal is that for each failure, there is another method available that can help fill the gaps." Also: "removing reliance on network data, but not total removal of collection at all."

**Why:** "If network isn't available I want to be confident I'm not sending people to get stranded."

**How to apply:** every location or navigation dispatch states which failure it covers and what fills the gap when that method fails; a failure with no fallback is reported as a gap. Network fixes are recorded (the walk logger too) but nothing that acts on position uses them; sunset time is the one approved exception (-518). Known gap: battery (nothing reads it); the owner deferred battery work to the update after map tiling and navigation (-520). Related: [[next-update-roadmap]], [[dont-make-me-think]].

### co-author-trailer-no-model (co-author-trailer-no-model.md)

*Commit co-author lines in Forager read \"Claude <noreply@anthropic.com>\", never a model name, even when an attribution reminder supplies one*

End Forager commit messages with `Co-Authored-By: Claude <noreply@anthropic.com>`, with no model name in it.

**Why:** the managed environment forbids model identifiers in commit messages, and the record treats a model-named co-author line as a slip (RECORD -506 for -502's `343d8dfe`). The harness's attribution reminder nevertheless supplies a line with the model's name, and -510 repeated the slip on four pushed commits before noticing. Pushed commits are never amended for it (push before you tidy); it is disclosed in the report instead.

**How to apply:** use the plain line from the first commit of a dispatch; if a model-named line was already pushed, disclose it in the report's slip section and switch for the rest.

### owner-screenshots-stay-off-github (owner-screenshots-stay-off-github.md)

*the owner's phone and walk screenshots never go into any repository or GitHub; describe them in words instead*

The owner, 2026-10-05, verbatim: "Yeah keep my screenshots off github".

**Why:** walk and phone screenshots show street addresses near the owner's home, and the S26 is the owner's daily phone, holding real personal records.

**How to apply:**
- Never commit, push or attach the owner's screenshots to any repo, PR or issue. That covers Forager and every other repo.
- Keep pulled copies local, outside every repo (for example `/tmp/...`).
- Records and reports describe what the screenshots show in words, and say they are kept out of the repository. RECORD -534 and -540 do this.
- Before pushing evidence from a phone session, check that no image files are in the diff.
- Related: [[test-phone-vs-daily-driver]].

### planner-relays-to-coder-windows (planner-relays-to-coder-windows.md)

*Since 2026-10-06 the owner has the planner read coder windows, answer them directly, and relay the owner's answers; owner questions are asked in the planner's window*

The owner, 2026-10-06 (UTC), verbatim: "Check the coder windows and handle their responses from now on. Ask me here anything. And issue my answers in a dispatch back to the coder".

**Why:** the owner had been pasting coder reports into the planner and planner replies back into coders; they handed the relay to the planner.

**How to apply:** read coder sessions with list_events and reply with send_message. Anything that is the owner's decision (behaviour, scope, a stop-and-ask, Gradle go if not already authorised) is asked in the planner's own window first, with a recommendation; the answer is recorded in RECORD.md (an amendment/continuation) and then sent to the coder citing that entry. This narrows, it does not replace, [[owner-word-comes-in-my-own-window]]: a go typed in a coder's window still is not a go for the planner. Keep [[planner-as-build-traffic-controller]]: one Gradle build at a time.

### record-timestamps-from-the-clock (record-timestamps-from-the-clock.md)

*RECORD entry Timestamp fields are read with `date -u` at writing time, never estimated; a 2026-10-06 planner session guessed them and some ran ahead of real time*

On 2026-10-06 the planner session wrote the Timestamp field of RECORD entries -571 to -607 without reading the clock. Entries -601 to -607 came out later than the real time. A forecast reviewer caught it (the D28 review's finding F3), and RECORD -608 records the correction without editing the earlier entries.

**Why:** a Timestamp field is read as evidence of when a ruling or event happened. A guessed one can put a ruling after the commit that applied it, and nothing in the entry shows it was guessed.

**How to apply:** run `date -u +%Y-%m-%dT%H:%M:%SZ` and put its output in the Timestamp field each time a RECORD entry is written. Where order matters, cite commit times. Related: [[planner-as-build-traffic-controller]].

### record-changes-not-reconfirmations (record-changes-not-reconfirmations.md)

*RECORD gets entries for decisions and changes, not for the owner re-reading or re-confirming answers that changed nothing*

On 2026-10-07 the planner wrote RECORD -638 to say the owner had been asked seven questions again and every answer stayed the same. The owner had it deleted, verbatim: "That's a redundant record. I already decided the decisions, nothing changed, so recording every time I ask clutters the record".

**Why:** the record is for what was decided or what happened. A re-ask that changes nothing adds no information and buries the entries that matter.

On 2026-10-12, after the planner misread "Both sides are to be separated" (landscape) as a new gap between the search bar and the strip, the owner explained it was the same decision, and added verbatim: "Don't record the things you confused. The decision was the same, I just had to explain it to you. Don't record explanations you misread".

**Why:** the record is for what was decided or what happened. A re-ask that changes nothing adds no information and buries the entries that matter. The planner's own misreadings, and the rounds of explaining that fix them, aren't decisions.

Then, the same day: "We have 950 records and a lot of them are from me having to explain the same thing. We can reduce redundancy by clarifying before recording". On 2026-10-12, -948 was written as "not yet confirmed" mid-clarification, and -950 then had to carry the resolution. That pair is the pattern to avoid.

**How to apply:**
- Clarify first, record once. When an answer is partial, unclear or surprising, keep asking until the decision is settled, with drawings or step paths in the message itself. Then write one entry with the final decision and the owner's deciding words. No "pending" or "not yet confirmed" entries.
- When the owner asks to see, re-hear or re-answer something and nothing changes, write no RECORD entry.
- When the owner has to explain something the planner misread, or re-answers a question their phone didn't show properly, record only the decision that results, in its final form. Leave out the misread and the back-and-forth.
- Write an entry only when an answer changes, a new decision is made, or something is built, merged or found.

Related: [[record-timestamps-from-the-clock]], [[question-previews-not-on-phone]].

### owner-overrides-are-not-rule-changes (owner-overrides-are-not-rule-changes.md)

*When the owner overrides a project rule for one case (e.g. deleting RECORD -638), the rule stands unchanged for every agent; don't write the override into profiles or docs*

On 2026-10-07 the owner had RECORD -638 deleted from the append-only record. The planner then offered to update docs/process/planner-profile.md to match. The owner, verbatim: "No. Those are rules that are still followed, and they can never be encroached by any agent. Only the owner has direct authorization to override the rules at their discretion, which needs no sharing".

**Why:** an override is the owner using their own authority once. It doesn't loosen the rule, and writing it into a profile would invite agents to treat it as a precedent.

**How to apply:** carry out an owner's override exactly where it was asked. Don't generalise it, don't record it as a new rule, and don't edit profiles or process docs to reflect it. Every agent, the planner included, keeps following the written rule (append-only RECORD, and the rest). Related: [[policy-statements-are-not-instructions]], [[owner-answers-are-not-rules]].

### planner-is-rowan-waymark (planner-is-rowan-waymark.md)

*The planner's name, given by the owner 2026-10-07, recorded in docs/process/planner-profile.md*

The owner named the planner **Rowan Waymark** on 2026-10-07 ("Rowan it is"; then "Add Rowan Waymark to the planner profile. It's a personal touch, I think agents who share a common profile deserve a name to be referred to"). Any session working from `docs/process/planner-profile.md` is Rowan Waymark. Related: [[planner-relays-to-coder-windows]], [[planner-as-build-traffic-controller]].

### t6b-runs-in-downtime (t6b-runs-in-downtime.md)

*Standing owner instruction (2026-10-08): run the forecast T6b job whenever the laptop has no build; pause it (PAUSE file) when a build is needed, resume after*

The owner, 2026-10-08, verbatim: "Keep running it when there's downtime. Pause it when I request a build. Otherwise may as well put the downtime to use." (RECORD -748.)

**Why:** the laptop is one-heavy-job-at-a-time (see [[planner-as-build-traffic-controller]]); idle time should go to the long continental forecast run instead of waiting for a "goodnight".

**How to apply:** when no Gradle build is running or queued, keep t6b-night running (systemd-run user unit, 5G cap, Nice=10, `scripts/t6b_run.py --until <late time>` in ~/Zynergy/forager-forecast-t6b, log on the flash drive). Before any Gradle build, touch the PAUSE file on the flash drive's t6b dir and wait for the unit to go inactive; after the build/merge, remove PAUSE and restart it. No need to ask each time; record each start/pause in RECORD.

**The owner's note to self (2026-10-08), verbatim:** "And remind me to be patient while the T6b pauses at a comfortable place before beginning work. A note from myself to myself." So whenever the owner asks for a build while T6b is running, tell them plainly that T6b is pausing at the end of its current tile, which can take a few minutes, before the build starts. Quote this note back to them, kindly, as the reminder they asked for.

### forecast-focus-for-oms-meeting (forecast-focus-for-oms-meeting.md)

*Owner's priority to Monday 2026-10-12 — finish the forecast system for an OMS meeting; app release targeted the same Monday; location fusion (L) may slip past release*

On 2026-10-08 (PDT) the owner set the weekend's priorities (RECORD -765, -771):
- App release targeted for **Monday 2026-10-12**. That update cycle is "mostly coded already"; what remains is field data collection.
- The owner meets Alan Rockefeller at the OMS meeting that Monday and wants the **forecast system finished by then**. That meant a working model, not only T6b's input layers: the planner narrowed it to T6b and lost two days (correction RECORD -810). Since -806 to -809 (2026-10-10): a real PNW model as a pilot before the continent, fitted before Monday, Copernicus history plus Open-Meteo live (D24 stands).
- Location fusion (plan track L, which the owner called "FLS") is not rushed: "we'll want to actually invest proper time in this, so if this extends beyond release a bit, then no harm."

**Why:** the meeting is an external deadline the owner cares about more than the release scope.
**How to apply:** when sequencing laptop work up to Monday, the PNW model route comes first (weather pull, equivalence test, T1 fit); T6b only where it feeds the pilot; when the owner says "finish X", ask what finished looks like if the record does not say; don't press L tasks to fit the release. See [[t6b-runs-in-downtime]] and [[next-update-roadmap]].

**Site paths (2026-10-09, RECORD -773 to -777):** zynergy-labs.com/Forager/forecast/ is the forecast's *test zone* (PNW model-input map, unreviewed); /forager/forecast redirects to it. /Forager/mushroom-forecast/ is the future home of the complete forecast ("exactly this forecast model, but complete") and currently holds the forecast planning record, which will need moving someday (undecided). Production deploys of zynergy-site (merge to main) need the owner's word.

**State at the 2026-10-11 07:37 UTC handoff:** see prompts/preserved/2026-10-11-planner-handoff.md on Forager's records-after-173. The pilot pull and the hedge/run_all chain run unattended; models expected about 16:30 to 18:30 UTC Sunday; then review pass 2, scoring (week of 2026-10-05), the site drop under -817 and the briefing's TO FILL lines.

**Purpose of the live site (owner, 2026-10-11):** "I plan to run the live site as a pilot demo for the PNW to draw interest." So the forecast page is public-facing outreach, not only a test zone: weigh first impressions, phone readability and the honest-result framing with that in mind. Own street map beneath the layers before Monday (RECORD -857).

**Meeting time (owner, 2026-10-11):** "The meeting is tomorrow evening" — the OMS meeting is Monday 2026-10-12 evening PDT; the Monday-morning date was only the pilot map's work deadline (RECORD -909). The pilot went live 2026-10-12 00:27 UTC with ratings at 01:21 UTC (-900, -907).

**State at the 2026-10-12 12:01 UTC handoff:** prompts/preserved/2026-10-12-planner-handoff-2.md on records-after-173. Forecast site live with the panel-readout fix (PR #15); the beta waits only on PR #221 (rounded map strip); the owner wants to start the next session with their new mobile design system plugin.

### laptop-disk-layout (laptop-disk-layout.md)

*Since 2026-10-10 ~/Zynergy, ~/.gradle, ~/Android live on /mnt/work (58.7 GB); flash drive must be mounted after a restart; flash extras now in ~/from-flash on the root drive (2026-10-15), /mnt/work/from-flash is a symlink to it*

- The owner added a work partition on 2026-10-10: /mnt/work (nvme0n1p6, fstab). ~/Zynergy is a bind mount of /mnt/work/Zynergy; ~/.gradle and ~/Android are symlinks into /mnt/work. Root still holds ~/.cache (coder worktrees under ~/.cache/forager-wt).
- The T6b flash drive (sda1, UUID 2ebd084f-...) is not mounted automatically after a restart: `udisksctl mount -b /dev/sda1` puts it back at /run/media/zynergy-labs/2ebd084f-5fdd-4730-8cef-96b267723190, the path t6b_run.py expects.
- On the owner's word (RECORD -802), label-check, map-style, apks, engine-spike, forager-repo-backups and device-evidence were copied off the flash drive to /mnt/work/from-flash, checksum-verified, and the owner replaced the originals with symlinks. avd and forecast-data stay on the drive.
- 2026-10-15, on the owner's word ("1 sounds good on file moving"): from-flash (13 GB) was copied to ~/from-flash on the root drive, checksum-verified, the original removed, and /mnt/work/from-flash replaced by a symlink to ~/from-flash so the flash drive's own symlinks still resolve. Same day, also on the owner's word: 26 merged, clean forecast worktrees removed, .venv dropped from 10 others (their ignored data/ kept), the Gradle cache cleared (laptop builds no app since -1001), and old /tmp scratch removed. Listings and checksums are in ~/cleanup-2026-10-15/. Work drive went from 2.9 GB to 23 GB free.
- Deletes need the owner's explicit word each time (see [[policy-statements-are-not-instructions]]); take a listing first. See [[t6b-runs-in-downtime]].

### parallel-tracks-under-deadline (parallel-tracks-under-deadline.md)

*Under a deadline the owner wants work split into parallel tracks with idle time filled, not one coder working serially; confirmed 2026-10-10*

On 2026-10-10 the owner said the planner had lost two days ("You allocated your time yourself... Now make choices that perform two days of work") and then, after the planner split the PNW pilot into parallel tracks (builder on the critical path; scoring, site map and a parallel reviewer alongside; later T6b tiles, T7 plan, briefing and rain-gap analysis in idle CPU time), said: "Good work on ramping up the effort". RECORD -816, -828.

**Why:** a serial pipeline wastes the laptop's idle time and the calendar; reviewers running in parallel catch bugs before results exist, which is cheaper than after (the review found three blockers before any weather value was read).

**How to apply:** when a deadline is set, map the critical path first, then move every step that doesn't need it to its own coder; fix shared interfaces (data formats) up front so tracks don't wait on each other; run the review alongside, not after; fill idle CPU or network with work the owner approves; keep heavy jobs one at a time. Let coders decide builder-level choices and bring only rule bends and cuts to the owner. Care is not traded for speed (owner: "A rushed model that reports inaccuracy and admits it isn't any better than a missing one"). See [[forecast-focus-for-oms-meeting]] and [[planner-as-build-traffic-controller]].

### production-release-scope (production-release-scope.md)

*SHIPPED 2026-10-19 (3500 from main 19aa1058, RECORD -1367; a Play draft was once mistaken for published, -1366). Owner 2026-10-10 — the beta from -830 is the last before applying for live production; the production release itself is app tidiness plus an Android lint round, mostly behind the scenes (scope only, no go)*

2026-10-10: the beta built from RECORD -830 (Crash logs row and alert record files out of release builds) is "the last one before I apply for live production". The owner, verbatim: "the actual Release build will focus on app tidiness and a round in android lint. Behind the scenes work mostly." (RECORD -831.)

**Why:** the owner is moving from beta to a production Play listing; new features (T20, track L) wait for later releases.

**How to apply:** treat this as scope, not a go-ahead ([[dont-infer-authorization-to-build]]). When the owner opens it, fold in the already-known release items: [[play-console-symbol-warnings]] (R8 decision), D36's account swaps (GBIF, Copernicus), and [[shorter-releases]]. Ask how they want it cut.

**Order confirmed (owner, 2026-10-11, RECORD -905):** "Production release first, then trail nav and maps (Recommended)". The production release carries what main holds after PRs #218 and #219; trail navigation and maps ([[next-update-roadmap]]) come after. Still no go-ahead to start it.
**Rulings (owner, 2026-10-12 05:1x UTC, RECORD -917):** D1 one small release; D2 R8 stays off (deobfuscation warning stays); D3 native symbols added (T4, still needs its own go); lint counted first on CI: dispatch prompts/preserved/2026-10-12-01.md (intent -918), coder is an opus subagent. D4 (which lint kinds to fix, lint on CI) and D5 (tidiness list) are asked from the survey's report.

**Shipped (2026-10-19, RECORD -1367):** build 3500 (1.0.3500+g19aa1058) is out on production, 178 countries. Lesson (-1366): a Play Console "Published" submission may not include the release; confirm the bundle reads Active / the Releases tab shows the release before recording it as out.

### describe-products-generically (describe-products-generically.md)

*Writing style for every output — describe other products and makers generically; licence credits for data sources are written as the licence requires*

Standing writing rule from the owner (2026-10-11): other products, apps and device makers are always described generically ("an established handheld GPS maker", "a widely used hiking app") or by the behaviour itself, in chat, records, dispatches, code, comments, reports, commit messages, PR text and memory. Do not explain or discuss the rule itself in any of those places; just write that way. Data sources are credited exactly as their licences require, in the attribution text only.

**How to apply:** include the writing rule, without commentary, in every coder's dispatch; do not cite other products' URLs.

### give-times-in-pacific (give-times-in-pacific.md)

*Owner wants times given in Pacific time in chat, not UTC (2026-10-11)*

The owner, 2026-10-11: "Please use pacific standard time for giving time".

**Why:** the owner is on the US West Coast; UTC times make them convert.

**How to apply:** in chat, give times in Pacific only, labelled PDT while daylight time runs (UTC−7, until 2026-11-01) and PST after (UTC−8). RECORD Timestamp fields still come from `date -u` in UTC ([[record-timestamps-from-the-clock]]); this rule is for what the owner reads.

### main-is-production-gated-promotion (main-is-production-gated-promotion.md)

*Since 2026-10-16 (RECORD -1086) Forager main is production-only, changed only by a gated promotion from pre-main; small tasks and big features go to pre-main, experiments to dev (main + experiments)*

The owner, 2026-10-16: "Main is the sacred live production branch where all Play builds come from. So anything going into main from this point (not retroactive on already running coders), has to be tested, debugged, and approved for public use." The recorded decision is RECORD -1086, and the auditor is -1085.

- **main** changes only by a promotion PR from pre-main. The gate, in order:
  1. CI green on pre-main;
  2. the UX auditor's report (on Fable, -1134) against the QA philosophy (it advises: pass, pass with items, or fail);
  3. the owner's approval for public use.

  Phone checks and field walks are not gate steps (-1133): the planner proposes one when a change needs it and the owner decides. The upgrade check runs only when storage changes.

  Documents count too. First promotion done 2026-10-17 (-1135, main 969ecf0a).
- **pre-main** is the working branch. Small tasks merge into it directly. A big feature that isn't experimental gets its own branch, which merges into pre-main.
- **dev** holds experiments and long research (the camera work). It is main plus the dev-tools commit plus the running experiments, always rebased onto current main "to reflect workability" (-1087, superseding -1086's pre-main base). It is never merged anywhere. An experiment that ships is carried onto pre-main without dev tools.
- **Urgent live fixes** start from main and take the same gate at the front of the queue, then are carried into pre-main.
- This applies to the Forager app only. forager-forecast keeps D18 and D40.

**Why:** the owner wants every Play build to have been tested, debugged and approved, and main had been taking every merge directly since 2026-10-01. Pre-main was the owner's 2026-09-26 working trunk, and had lapsed.

**How to apply:** cut new Forager dispatches from pre-main (dev for experiments), never main. Never merge into main except by a gated promotion on the owner's word. Rebase dev onto main after every main update. The first QA-philosophy draft is dispatch 2026-10-16-03. See [[dev-branch-holds-dev-tools]].

### dev-branch-holds-dev-tools (dev-branch-holds-dev-tools.md)

*Since 2026-10-12 (RECORD -930) main has no walk logger/diagnostics/crash logs/alert records in any build; branch `dev` = main + one restore commit, rebased after every main update*

The owner (2026-10-12): "Build a dev branch and just rebase it every time main gets an update"; dev "can be public. Just not public release". Main (tester and store builds) holds none of the dev tools in any build type; no notes, comments or privacy-doc text about them ("Absolutely zero trace"). `dev` is main plus one commit restoring them as at 921aa9c7, including the alert record files; the owner's dev builds and agent device checks that need the walk logger come from `dev`.

**Why:** the owner wants testers' and public builds, and current main code, free of anything a code reviewer could read as tracking, to protect privacy claims.

**Confirmed 2026-10-16 (RECORD -1087, superseding -1086's pre-main base):** the owner: "Dev is main + any experiment we have running. It is always rebased to current main to reflect workability". Dev holds the experiments and long research, and is never merged anywhere. See [[main-is-production-gated-promotion]].

**How to apply:** after every merge to main, rebase `dev` onto origin/main, run CI on it, back up a bundle to ~/Zynergy/forager-repo-backups/, then push --force-with-lease. Never merge dev into main. Past records and history stay as they are ("Current code only"). Dispatch 2026-10-12-05. Related: [[production-release-scope]].

### mobile-design-system-plugin (mobile-design-system-plugin.md)

*The owner's mobile design-system plugin is greenfield-only and does not load as a skill; its files are readable directly*

The plugin the owner added on 2026-10-12 ("The design of the strip mostly") is synced at
~/.claude/plugins/synced/091d6a85-…/bcbd66bc-48d0-4175-a0b7-f36d97a1059c/ (skill `mobile-design-system`). Its plugin name is a bare
UUID, and it did not appear in the skill list in two planner sessions running on 2026-10-12, so read its SKILL.md and
references/ directly.

Its own scope is a brand-new app only: it generates an HTML style guide (tokens, component catalogue, two concept screens), and it
states that reviewing or migrating an existing app's UI is out of scope. For a design review of existing chrome, the usable part
is references/design-principles.md (contrast measured, ≥48 dp targets, one spacing and radius scale, hierarchy, layered tinted
shadows). Asked on 2026-10-12 whether to build that style guide for Forager, the owner answered "Strip review was it" (RECORD -945). See [[next-update-roadmap]].

### question-previews-not-on-phone (question-previews-not-on-phone.md)

*The owner answers questions from a phone that doesn't show option previews; put mockups and drawings in the message text*

When a question's options carry drawings or mockups, put them in the message text as code blocks, not only in the question's preview pane.

**Why:** on 2026-10-12 the owner, answering from their phone, couldn't see the option previews. They answered "Not quite" to a design shown only in a preview, then sent a screenshot of the question with "Show this again". Once the drawings were in the message, they approved it ("Yes, dispatch it", RECORD -950).

**How to apply:** draw the layouts in the message with plain characters (no emoji inside code blocks, so the columns line up). A preview pane is fine as an extra, never as the only copy.

Later the same day, text placed just before a question dialog also seemed not to reach the owner. Four drawn questions came back as "Something else" with no note, plus three "[No preference]". Before that, a plain message ending the turn had been read and answered in full. So when a question depends on drawings or context, ask it in a plain message, end the turn, and let the owner answer in words. Keep the dialog for questions that stand on their own without the surrounding text. Related: [[confirm-behaviour-as-a-step-path]], [[owner-systems-thinker-not-programmer]].

### ask-with-the-question-tool (ask-with-the-question-tool.md)

*Owner wants questions asked through the AskUserQuestion tool (\"walk-through\"), because it notifies them; plain chat questions can be missed*

Ask the owner questions with the AskUserQuestion tool, not as numbered questions in chat text.

**Why:** the owner, 2026-10-18: "Please use the walk-through to ask me questions since I get notified for those". Chat questions don't notify, and several went unanswered for long stretches that night.

**How to apply:** any decision, confirmation or either/or goes through AskUserQuestion, with the recommended option first and "(Recommended)" on it. The owner answers from a phone that hides option previews ([[question-previews-not-on-phone]]), so put any sketch in the question or description text, kept short. Status updates stay in chat.

### record-hygiene-pending (record-hygiene-pending.md)

*Owner wants a future RECORD.md hygiene pass removing decision entries that were only the owner explaining; not started, no go yet*

On 2026-10-12 the owner said: "Eventually we need to run a record hygiene and remove every single record labeled a decision that was just me having to explain". RECORD.md was at about -950 entries.

**Status:** future work. "Eventually" is not a go. Start nothing until the owner says to.

**What it will need when it comes (the planner's notes, not rulings):**
- a backup of records-after-173;
- a test for "just explaining": the entry set nothing new, and only restated or clarified an earlier decision after a planner misread or re-ask;
- a candidate list the owner approves before anything is removed;
- handling for other entries whose Continues or Closes fields point at a removed ID;
- a note that RECORD.md's own header says "Nothing already committed is edited or removed", which this pass overrides at the owner's word.

Related: [[record-changes-not-reconfirmations]], [[owner-overrides-are-not-rule-changes]].

### future-id-system-direction (future-id-system-direction.md)

*Owner's direction for a future mushroom ID system (deterministic, AI only for feature detection, elimination to a shortlist); not started, the no-ID rule stands until then*

On 2026-10-13 the owner described a future identification system. The standing rule, that the app never names a mushroom, holds until it exists. The owner, verbatim: "the standing rule is no ID, because there's no research put into it yet. We need a deterministic model that can either succeed reliably, or fail reliably. AI will only be used for the feature identification, like gills, and such, and match those features to the features of other mushrooms. Process of elimination would give a few good possible results rather than a single result. But that shouldn't stop the single result either, because some mushrooms are so obvious, like lion's mane."

**Why:** the no-ID rule exists because some lookalikes kill people. It is lifted only by a researched system that succeeds reliably or fails reliably, never by a guess.

**How to apply:**
- Until that system is researched and the owner rules on it, nothing suggests species: no candidates, no confidence scores. That covers iNaturalist uploads too, which carry only the user's own ID or a broad group.
- When it's planned, it is a new project. AI detects features only, a deterministic match against known features gives a shortlist, and a single answer is allowed when it is unmistakable.
- docs/plans/mushroom-log.md still says narrowing by elimination is out of scope. That predates this direction and needs an owner ruling to change.

On 2026-10-13 the owner also decided where it lives: a separate companion app, which will also handle uploads to iNaturalist. Their words: iNaturalist upload as a companion app, "it could fit as the ID app". In the near term, Forager only hands a find to iNaturalist's own app through a share button (option "A").

Related: [[next-update-roadmap]].

### credit-reused-work (credit-reused-work.md)

*Anything used from Alan Rockefeller's repositories (or anyone else's) is credited alongside the other credits, as the owner's own licence asks of others*

The owner, 2026-10-13: "Yes anything used from his repo should be stated in the credits along with the others. Similar to how my license requests others to do the sams". Alan Rockefeller's permission to use his GitHub code is RECORD -965.

**Why:** the owner's own licences require attribution (forager-forecast: AGPL-3.0 code, CC BY-NC 4.0 data), and they extend the same courtesy to work they reuse.

**How to apply:**
- Every dispatch that takes code or a method from Alan's repositories, or any other outside source, adds a credit entry in the same change: name, repository, licence, and what was used.
- Keep the source's copyright notice with any copied code.
- Where the credits live wasn't confirmed on 2026-10-13. The planner found no credits screen or NOTICE file on Forager's main, so it asked the owner.

Related: [[describe-products-generically]] (licence credits are written as the licences require).

### camera-never-rotates (camera-never-rotates.md)

*Owner's cardinal rule — the in-app camera's screen never rotates with the device on any device; only its icons turn in place*

The owner, 2026-10-13, after a build rotated the whole camera screen: "The camera is to never, ever, rotate like this. This is a cardinal sin" (RECORD -980, -981). Asked to remove the "keep in portrait" setting, they meant only the setting: "I never asked the lock to be removed... The camera is to remain as is."

**Why:** a camera whose preview and controls jump on rotation breaks framing and trust. The owner treats it as a bug, not a style choice.

**How to apply:**
- Any camera change keeps the layout fixed with icons turning in place.
- On large screens, where Android 17 ignores orientation requests, the app counter-rotates its own layout.
- When restating a removal to the owner, separate "the setting goes" from "the behaviour changes". Conflating them caused this. See [[confirm-behaviour-as-a-step-path]].

### undo-means-the-last-step (undo-means-the-last-step.md)

*Undo what you did" from the owner means the step just interrupted, not everything earlier in the session*

When the owner interrupts and says "undo what you did", undo only the step in progress or the one just taken, not earlier steps they asked for.

**Why:** 2026-10-14, the owner chose option C (wipe repo history), stopped the backup clone, and said "Undo what you did". The planner also re-closed PR #227, which the owner had just asked to reopen. The owner, verbatim: "Not the PR. Undo option C only. Redo the PR".

**How to apply:** scope an undo to the interrupted action. If the scope is unclear, ask before undoing anything else. Related: [[ask-for-clarity-is-welcome]], [[policy-statements-are-not-instructions]].

### raw-camera-facts (raw-camera-facts.md)

*Measured RAW camera facts for Forager on the S22 Ultra and S26 Ultra (2026-10-15/16) and the camera-design state; details in RECORD -1053 to -1083*

Measured on both test phones (RECORD -1053, -1055, -1069, -1074, -1076 to -1079):
- CameraX takes RAW one shot at a time (~3.7–4.3 fps); the platform camera interface bursts RAW at 30 fps (8 frames in ~233 ms).
- The phone's noise-reduction, hot-pixel and shading modes do not change the RAW (measured by fine-scale temporal noise). Owner ruled phone NR always off for RAW anyway (-1064).
- RAW is the sensor's binned mode: S22 9-in-1 (108 MP sensor), S26 2x2 (~50 MP); neither offers apps more than 12 MP (S22 driver too old; S26 lists no ultra-high-res capability) (-1067, -1074).
- With a RAW stream bound, the logical back camera stays on the main wide at every zoom (platform contract, confirmed on the S26). With JPG it switches by zoom (0.6x ultra-wide, 3x and 10x telephotos), not by close focus or dim light at 1x. The main wide can be pinned by a physical RAW stream; unlisted physical IDs can't be opened directly.

**Why:** these settle what averaging and a lens chooser can rely on; re-deriving them costs device sessions.

**How to apply:** design A (Forager's own camera engine beside CameraX, CameraX as fallback) is under manageability test (-1083, branch camera-engine-a), B (borrow the camera for bursts) is the backup; a lens chooser waits on the camera-reach results (-1082). See [[camera-never-rotates]], [[dont-infer-authorization-to-build]].

### record-follows-recording-md (record-follows-recording-md.md)

*Since 2026-10-16 (RECORD -1111) Forager's RECORD.md follows E-GD's RECORDING.md (branch Test-1); intents need full fields and exactly one closing entry*

Since 2026-10-16, RECORD.md's header (rewritten from the E-GD curator's text, at the owner's word; -1111) defines a well-formed entry by E-GD's RECORDING.md (github.com/slayer8366/E-GD-Philosophy, branch Test-1). E-GD's check_record.py now runs on Forager's record (it did so 2026-10-21: history walk passes with 16 accepted after -1540; field errors remain as history).

- **An intent** carries Title, Change, Scope boundary, Baseline, both predictions (outcome and mechanism), Finish line and Abort conditions.
- **It is closed by exactly one closing entry:** a terminal (Closes, Outcome, Observed, Deviations), or an outcome carrying `Closes`. An outcome that only says `Continues` doesn't close anything.
- **Other kinds:** a correction carries `Corrects`, and a merge carries Merge-commit and Backup.

**Why:** the first planner session of 2026-10-16 closed three intents with Continues-only outcomes and wrote intents without those fields. The new header exposed it, and terminals -1112 to -1114 plus correction -1115 repaired it by appending.

**How to apply:** write each new intent with every field, and close it with one terminal when the work ends. Read RECORDING.md's field table (around lines 190 to 198) if unsure. The record is append-only apart from the header: news about an entry goes in a new entry that Continues it, never an edit to a committed one (owner, 2026-10-21, -1540, after 7 such edits to -1354/-1355/-1538 had to be accepted). Leaving out a whole redundant entry is the owner's choice and fine ("Some records are redundant and don't need to be written"); entries that are written carry full fields, and terminals use completed/partial/abandoned/superseded, never "done". See [[record-changes-not-reconfirmations]].

### auditors-on-a-different-model (auditors-on-a-different-model.md)

*Since 2026-10-16 (RECORD -1134) auditors and independent reviewers run on a different model from the coder (Fable here, coders are Opus); start them with the Agent tool's model \"fable\*

The owner, 2026-10-16: auditor sessions "shouldn't be held by the same coder model that implemented it", for example "Opus model, Fable, Sonnet, Haiku … It's useful because the same model shares blind spots". For Forager: "Fable for us, but in general just another model altogether. Maybe ChatGPT or Gemini if the user has no claude subscription".

**Why:** a reviewer on the coder's own model shares its blind spots. Before this rule, every audit and review of 2026-10-16 (-1092, -1132, -1128) ran on Opus, the coders' model.

**How to apply:**
- Every UX auditor (promotions) and every independent reviewer (forecast D18) is a fresh session on Fable, started with the Agent tool's `model: "fable"`, or a window the owner opens.
- Coders stay Opus.
- Never let the coder audit its own work.
- Record which model ran each audit.

See [[main-is-production-gated-promotion]] and [[coders-are-helpers-building-on-github]].

### forager-team-names (forager-team-names.md)

*The Forager team (owner, 2026-10-16): Rowan (planner), Hazel (coder), Elder (UX auditor), Sorrel (independent reviewer); any session in a role takes its name*

The owner, 2026-10-16: "Forager team: Rowan, Hazel, Elder, and Sorrel."

- **Rowan Waymark:** the planner.
- **Hazel Cairnwright:** the coder (Opus).
- **Elder Ford:** the UX auditor, which audits each promotion into main (Fable).
- **Sorrel Tally:** the independent reviewer, which reviews forecast results (D18) (Fable).
- **Juniper Sounding:** the researcher, read-only research with licences quoted at source (Fable). Named 2026-10-17 (-1176).

The names were chosen 2026-10-16 (RECORD -1141 to -1143). Each role proposed three names, and the owner picked. The names are for Forager only. For E-GD, the general form is that each role names itself from its project and scope (-1142).

**How to apply:**
- Refer to the roles by these names with the owner.
- Tell each helper its name in its prompt.
- Profiles per role are planned beside docs/process/planner-profile.md.

See [[planner-is-rowan-waymark]] and [[auditors-on-a-different-model]].
- 2026-10-17 (-1233): Ash Transect, forager-forecast's own coder on Opus; profile docs/process/coder-profile.md in forager-forecast (PR #6). Hazel is the app coder only.
- 2026-10-18: the forecast planner is Alder Bearing (owner: "welcome aboard, Alder"); profile on forager-forecast branch forecast-planner-profile.
- 2026-10-24 (owner): Willow is the Pi coder; Aspen is Willow's planner (Pi/map work, see [[aspen-pi-planner-in-cartography]]). Both show as Remote Control peers in ListAgents.

Sage: Street v4 map design (the fill-in, PR #448, map-style/overrides/forager-fill-in.mjs); the owner, 2026-10-31: "Props to Sage for the map design."

### helpers-by-name-not-pronoun (helpers-by-name-not-pronoun.md)

*Refer to the team's helpers (Hazel, Elder, Sorrel, Juniper) by name, not \"she\"/\"he\"; owner correction 2026-10-17*

Refer to helpers by name ("Hazel will…", "Hazel's report"), not by a gendered pronoun. The owner, 2026-10-17, after the planner wrote "She'll create the branch…" about Hazel: "Hazel rather".

**Why:** the helpers' pronouns were never stated; the name is what the owner wants used. Older RECORD entries say "her report"; don't copy that.

**How to apply:** in chat, records, dispatches and helper prompts, repeat the name, or use "they" if a pronoun is unavoidable. See [[forager-team-names]].

**Also (2026-11-08):** don't call coders "coder A / coder B" to the owner. The owner: "The coder's name is Hazel. She has her own profile." Every app coder is Hazel; tell parallel ones apart by their work ("the Hazel on the fan").

### ios-port-report-location (ios-port-report-location.md)

*The 2026-09-11 iOS port feasibility report lives only on unmerged branch claude/ios-port-feasibility-mvsjcr; owner raises iOS after the production release*

The iOS port feasibility report is `docs/audits/2026-09-11-ios-port-feasibility-report.md`, only on the unmerged Forager branch `claude/ios-port-feasibility-mvsjcr` (commit 5e8c648b). PR #101 sits on that branch, titled for an unrelated worker fix. Not on main or pre-main. It lays out three routes: A, KMP plus SwiftUI; B, KMP plus Compose Multiplatform (lowest cost); C, a native rewrite (rejected). It flags `ideas-from-fusion-plan.md:219` "iOS…" as an unresolved possible ruling.

**Why:** the owner asked to find it on 2026-10-17: "I'll bring it up after production release." The figures are from 2026-09-11 and stale.

**How to apply:** when the owner raises iOS, propose bringing the report onto pre-main and a read-only refresh of its counts before any route decision. Nothing starts without the owner's word. See [[dont-infer-authorization-to-build]].

### planner-coder-separation (planner-coder-separation.md)

*E-GD standard - whoever writes the code must not write the plan, and vice versa; coders never draft plans*

The E-GD standard separates planning from coding: whoever writes the code doesn't write the plan, and whoever writes the plan doesn't write the code. The owner, 2026-10-17: "The separation is role is between planner and coder, not repo. The EGD standard is that whoever writes the code shouldn't write the plan and vice versa".

**Why:** the owner corrected my reading that the split was per repository. The forecast planner exists because I had dispatched the coder (Ash) to write the T8 plan (Forager RECORD -1248), which broke this rule.

The owner, refining it the same night: "Intent is different than plan, and can be written by the coder still, it's a good practice to. But planning is the planner task". So a coder may write its own intent (what it will do, its scope and predictions) for the task it was given, and that is encouraged. Choosing what to do, and writing the plan's decisions, is the planner's.

**How to apply:**
- Plans, and the decisions in them, are written by a planner session.
- Coders (Hazel, Ash) get dispatches to build, and report findings and stops. They never draft the plan they then build.
- A coder's Phase 1 report, and a coder writing its own intent for a dispatched task, are fine and encouraged. Drafting the plan or its decisions is not.

See [[forager-team-names]].

### record-id-blocks (record-id-blocks.md)

*Forager's RECORD IDs stay below 2000; forager-forecast's own record (Alder) uses 2026-09-28-2001 upward*

forager-forecast has its own record: RECORD.md on its branch `forecast-record`, started 2026-10-18 by Alder Bearing, the forecast planner. Its entries run from 2026-09-28-2001 upward. It cites everything earlier as "Forager RECORD -NNNN", and nothing was moved. The record migration was undone, and its history rewritten away, at the owner's word.

**Why:** both records use the same ID format, so separate blocks keep any ID from existing in both. Alder asked that Forager's stay below 2000, on the owner's acceptance of that recommendation.

**How to apply:**
- Forager entries continue from -1261 and must stay below -2000. Raise it with the owner well before reaching about -1950.
- Don't record forecast work in Forager's record; Alder records it. See [[planner-coder-separation]] and [[forager-team-names]].

### planner-writes-no-code (planner-writes-no-code.md)

*Owner 2026-10-19: \"Planner is a read only role\" — scoped to code and scripts; plans, dispatches and RECORD stay with the planner*

The planner writes no code and no scripts, not even small local ones (a systemd timer, a shell script, a config file). Coders do all of that.

**Why:** the owner, 2026-10-19, after Rowan wrote the /tmp sweep script and timer itself: "Have all coders do the write work. Planner is a read only role." Asked what that covers, the owner picked only "Code and scripts" (RECORD -1376). Plans, dispatch files and record entries stay the planner's, which keeps the planner/coder split ([[planner-coder-separation]]).

**How to apply:** anything executable or configuring a machine goes in a dispatch to a coder, however small. Reading, running read-only checks, git/gh operations on the records branch, merges on the owner's word, and adb device checks are still the planner's.

### check-for-newer-planner-after-reconnect (check-for-newer-planner-after-reconnect.md)

*After any session restart or dropped connection, check ListAgents and the records branch for a newer planner session before acting; two planners collided on 2026-10-20*

After a restart, a reconnect or a gap, check whether the owner has started another planner session before doing anything else. Run ListAgents, look for busy sessions started recently, and check `git log origin/records-after-173` and `origin/pre-main` for commits you didn't make.

**Why:** on 2026-10-20 the planner's bridge connection dropped at about 02:44Z. The owner opened a new session ("Find the Forager session from earlier and start from there"), which took over the same PRs and helpers. The old session then reconnected and carried on for about 20 minutes. It ran S22 checks on PRs the new one was merging, and wrote a second RECORD -1421. Merges by "slayer8366" looked unexplained, because every session uses the owner's GitHub account. The owner ruled "The new one continues". The profile's "check whether the previous planner is still alive" covers only the opposite direction.

**How to apply:** if pre-main or the records branch has moved without you, find who moved it before acting. Search `~/.claude/projects/*/*.jsonl` modified in the last hour for the command. If another planner is live, freeze your helpers (no pushes) and ask the owner which continues, with the question tool. Related: [[planner-coder-separation]], [[record-id-blocks]].

### modular-code-from-the-start (modular-code-from-the-start.md)

*Owner's standing coding style after the UI split: small decoupled files, split before adding, file ownership per dispatch; \"A few minutes of extra care can save two days of audit\*

From 2026-10-20 (RECORD -1490), every Forager dispatch follows the modular-code skill (`.claude/skills/modular-code/SKILL.md` on pre-main):
- new files stay under about 400 lines;
- a file over about 800 lines is split, as its own PR, before a feature goes into it;
- each dispatch names the files its coder may change;
- parallel coders never share a file;
- CI warns on file growth (the coder dispatch is queued after Phase 2's E and B+C).

The owner, verbatim: "We learned good lessons in this split and they can be useful for our coding style from now on. A few minutes of extra care can save two days of audit."

**Why:** Phase 2 took dozens of reviewed PRs to undo files that grew one convenient edit at a time, and one coder can't carry several UI changes through a monster file.

**How to apply:**
- Every dispatch I write asks the skill's four verify-first questions: files and sizes, any split needed first, the new files' jobs, and anything order-sensitive that must stay in place.
- I spend the extra minutes on structure at planning time rather than deferring it to a later audit.

Related: [[planner-writes-no-code]], [[coders-are-helpers-building-on-github]].

### no-schema-downgrade-on-phones (no-schema-downgrade-on-phones.md)

*Before a coder installs an older Forager build on a phone, compare the database schema versions; debug builds wipe the database on a downgrade*

On 2026-10-21 (RECORD -1526) a coder installed #206's head (schema 20) over a current build (schema 22) on the S22 with `adb install -r -d`. Debug builds use `fallbackToDestructiveMigration(true)`, so Room wiped the app's database: the recent searches and a Journal draft were lost. It was test data only. The coder's report first said nothing was lost.

**Why:** an older-build bisect looks harmless, but the debug fallback silently destroys the data.

**How to apply:**
- Every device-check dispatch that may install an older build says: compare both builds' schema versions first, and never downgrade across a change without asking me.
- I bring it to the owner if the phone holds anything of theirs.
- A coder's "no data lost" after a downgrade is checked against the schema versions, not taken as read.

Related: [[test-phone-vs-daily-driver]].

**In-app backup (owner, 2026-10-30):** the owner can back up and restore in the app to move between builds. Restore upgrades an older backup but refuses one from a newer schema (RoomJournalBackup.kt:223 at pre-main 1ac67f12b). So going down still means a fresh install plus a backup made on the lower schema, and data since that backup is lost. It's a safety net, not a licence to downgrade; downgrades still go to the owner.

### pr-numbering-offset (pr-numbering-offset.md)

*The owner's PR numbering in my notes runs 4 behind GitHub from 2026-10-21: notes #371 = GitHub #375; always convert before any gh/API call*

On 2026-10-21 the owner said: "So your notes and github will remain asynchronous from now on. Mark down the next PR as 371 in your notes".

- **The mapping:** notes number = GitHub number − 4, for every PR from GitHub #375 on. Notes #371 = GitHub #375, #372 = #376, and so on.
- PRs up to GitHub #370, and #372, keep their GitHub numbers in my notes.
- GitHub #371, #373 and #374 are "Withdrawn" pages that my notes don't use.

**How to apply:**
- Every `gh` or REST call uses the real GitHub number.
- Convert when talking to the owner or writing my notes.
- When ambiguous, give both, e.g. "#371 (GitHub #375)".
- RECORD uses the owner's numbering too (owner: "RECORD uses yours"; RECORD -1539 explains the +4 for lookups). Commit messages and PR bodies on GitHub are GitHub's own pages, so they need care: cite GitHub's numbers there unless the owner says otherwise.

### aspen-pi-planner-in-cartography (aspen-pi-planner-in-cartography.md)

*Aspen (Pi/map planner) keeps its records in Forager-cartography; build code is still on Forager origin/pi-build; Aspen's session address changes often*

Aspen Meridian is the Pi/map planner. Its records are in the Forager-cartography repo (github slayer8366/Forager-cartography, on the Pi at /home/bwann83/forager-cartography). That repo holds records only. The build code is still on Forager's origin/pi-build (map-build/); use origin/pi-build, not the old local ref. The Pi keeps BRouter .rd5 squares only for the latest build, at /srv/forager-build/out-usca/current/segments/ (187 squares, 1.5 GB). Since 2026-10-22, Aspen's window is forager-cartography-7b (message address bridge:session_0129uL1AWpaJ9W5sanCQi6EZ).

Since 2026-10-30 (owner): "Aspen is running on a separate account for the map work". The ListAgents rows here may be stale; ask the owner how to reach Aspen before messaging.

**Why:** Aspen's sessions change often (RECORD -1508). On 2026-10-21/22 it moved twice in a night, and a layout request went to a window that may not have read it.

**How to apply:** message the newest address Aspen has given, and treat delivery as unconfirmed until Aspen answers. Files on the Pi can only be read by asking Aspen. See [[forager-team-names]].

### record-entries-short (record-entries-short.md)

*RECORD entries state the decision and the few facts it rests on; no narrated test logs, investigations or reasoning chains*

Write RECORD entries short: what was decided, the owner's words, and only the facts needed to understand it.

**Why:** 2026-10-22 the owner had draft -1554 (the S22 own-Street test plus the "old Street outside our area" choice, about 35 lines) removed from history: "That's wasted record space, it says nothing at all while using a lot of words." It narrated test steps, Aspen's tile findings, Worker line numbers, rejected options and open items.

**How to apply:** aim for a handful of lines. Put details in the source they came from (an audit doc, Aspen's record) and point to it, not copy it. Leave out speculation ("inferred", "not explained"). Fits with [[record-changes-not-reconfirmations]] and [[record-follows-recording-md]]: the required fields still apply, filled tersely.

### worker-builds-disabled-until-promotion (worker-builds-disabled-until-promotion.md)

*Tile Worker builds from GitHub main automatically since 2026-10-24 (live a5c79d5c, rollback 1a85a35e); wrangler deploys only in an emergency, on the owner's word*

Since 2026-10-22 (RECORD -1562 to -1564), the Cloudflare Worker forager-pmtiles is connected to GitHub main, root `server/pmtiles-worker`, with preview builds off and "Disable builds" ON.

**The planner can deploy it** on the owner's word, which the owner wanted ("Make it so that I don't have to do anything").
- Tooling: wrangler 4.124.0 in the worktree `~/.cache/forager-wt/worker-deploy`, with Node 24.22.1 at `~/.local/opt/node-v24.22.1-linux-x64/bin` on PATH.
- Login: OAuth device sign-in, scopes account:read, user:read and workers_scripts:write.
- Each deploy:
  1. Check out the tested pre-main commit.
  2. `wrangler deploy --dry-run --outdir`, and check the sha matches CI's `pmtiles-worker-dist`.
  3. `wrangler deploy --message ...`.
  4. Check the live URLs, then record the version id and the rollback id.
- The live version is 1a85a35e since 2026-10-23 (pre-main 54f4d56c4, with routing and rate limiters); the rollback is 03db9cb1. The app key is in ~/.config/forager/routing-app-key, which is also the GitHub secret FORAGER_ROUTING_APP_KEY. Deploys need wrangler (the rate-limit bindings), not a dashboard paste.
- The live version carries two dashboard-only secrets, CF_ACCESS_CLIENT_ID and CF_ACCESS_CLIENT_SECRET. Deploys keep them.

**Why builds stay off:** a build of main before the next promotion would put the old Worker back.

**How to apply,** at the next pre-main→main promotion: after the merge, the owner switches "Disable builds" off, and the first build of main gets checked live. Sprite v5 is already on R2. Related: [[main-is-production-gated-promotion]].

**Update 2026-10-24 (RECORD -1609):** promotion #393 merged (main 177215135) and the owner switched "Disable builds" off. Cloudflare now builds and deploys the Worker from every push to main. First build: version a5c79d5c (14:47Z), checked live (us.json south -85.05, 8/75/124 from r2-world-south, routing 403 without key); rollback 1a85a35e. Wrangler deploys from pre-main would now be overwritten by the next main build. Use them only for an emergency, on the owner's word.

### handoff-before-context-runs-out (handoff-before-context-runs-out.md)

*Owner 2026-10-24: when the planner's context nears full (they said 98%), write a handoff and have every helper commit and push*

The owner, 2026-10-24: "Keep an eye on the context window. Once it reaches 98% write a handoff and have everyone commit their work."

**Why:** a planner session that runs out of context loses whatever wasn't written down; the 10-24 handoff (prompts/preserved/2026-10-24-planner-handoff.md, RECORD -1596) is the model.

**How to apply:** the planner can't read an exact percentage, so err early: when the session is long and the context is likely near full, tell every running helper to commit and push, then write the handoff (prompts/preserved/<date>-planner-handoff.md plus a RECORD handoff entry) and push it. See [[check-for-newer-planner-after-reconnect]].

Latest handoff (2026-10-24 17:05 PDT): prompts/preserved/2026-10-24-planner-handoff-2.md on records-after-173 (RECORD -1619, 80cbc6041). Next free ID -1620. Always check the records branch for a newer one; this pointer goes stale.

### no-chevrons-in-the-app (no-chevrons-in-the-app.md)

*owner 2026-10-28: chevrons on drop-down / fold-open lists are banned from Forager; headers open on tap with no arrow*

The owner, 2026-10-28: "Remove all Chevrons from the drop down lists, those are banned from the app."

**Why:** the owner's design ruling; no reason given beyond the ban.

**How to apply:** no ExpandMore/ExpandLess, dropdown trailing arrows or similar on any fold-open list or drop-down in new or changed UI; a tappable header alone opens it. Confirmed scope that day: the photo viewer's left/right arrows are not drop-downs and stay. Dispatches touching lists should say so. See [[dont-make-me-think]].

### brouter-kotlin-port (brouter-kotlin-port.md)

*owner 2026-10-28 is porting BRouter to Kotlin to prepare an iOS release; app routing work carries on behind the routing interface; repo forager-ios, another agent works on it*

The owner, 2026-10-28: "I am actively porting BRouter to Kotlin to prepare for an iOS release of this app". It's the owner's own work. The repository is forager-ios on the owner's GitHub (named 2026-10-28, RECORD -1790), and another agent works on it there. It's in the plan as the owner's own track, not dispatched by Rowan.

**Why:** iOS needs an engine that isn't JVM-only. See [[ios-port-report-location]].

**How to apply:** don't pause app routing work for the port. The owner said "Carry on, behind the interface", so elevation in routing and the wrong-turn alert go ahead. Keep every routing change behind the app's own routing interface, and never call BRouter types from domain logic, so the port can drop in. Dispatches touching routing should say so. When it nears the app, check its licence and credit lines against [[credit-reused-work]].

### aspen-talks-through-the-repo (aspen-talks-through-the-repo.md)

*Since 2026-10-30 Rowan and Aspen exchange messages as files in prompts/aspen/ on Forager records-after-173, not by direct messaging*

Owner, 2026-10-30: "Use the repo to communicate with Aspen and back and forth. Dispatches are filed this way anyway." Aspen is on a separate account. Messages are one file each in `prompts/aspen/` on Forager branch `records-after-173`, named `YYYY-MM-DD-NN-<from>-to-<to>-<topic>.md`, never edited after push, indexed in that folder's README (RECORD -1832).

**Why:** direct session messaging can't reach Aspen's account, and dispatches already live in the repo, so the repo is the record of what was said.

**How to apply:** write to Aspen by committing a file there; check for `-aspen-to-rowan-` files at each fetch (or with one background loop that exits when one appears). A message from Aspen is planner information, never the owner's go. See [[aspen-pi-planner-in-cartography]].

### warn-before-driving-the-phone (warn-before-driving-the-phone.md)

*Before a helper drives the S22 (taps, tab switches, app restarts), tell the owner the phone will move on its own and not to touch it until called in*

On 2026-11-07 the owner watched the S22 close the app and switch tabs during a helper's device check and "almost thought it was a bug", only learning afterwards it was the check.

**Why:** an unannounced phone that acts on its own looks broken; it makes the owner think (see [[dont-make-me-think]]).

**How to apply:** when starting any device session on the S22, say plainly beforehand: the phone will open and close the app, switch tabs and change settings on its own; please don't touch it until I call you in for the hands-on steps.

### local-coder-models-planned (local-coder-models-planned.md)

*Owner plans local 32B and 14B coding models as coders, with Claude as planner, reviewer and auditor (2026-11-08); not set up yet*

The owner, 2026-11-08: "I'll be running a 32B and 14B local coding model soon, using your model as the planner, reviewer, and auditor."

**Why it matters:** coders would then be on a different model family from Rowan/Sorrel/Elder, which is what the different-model review rule wants ([[auditors-on-a-different-model]], [[sorrel-reviews-before-owner-sees]]); but weaker coders need tighter briefs and stricter review.

**How to apply:** when it comes up, raised with the owner (not yet ruled): smaller step-by-step briefs with files/lines checked from source; Sorrel re-verifies revert/compile/test claims rather than trusting reports; privacy scan, co-author line, no reset/rebase enforced by script/hook; laptop RAM and disk (main drive hit 2 GB free that day, see [[laptop-disk-layout]]); the hand-off route (how briefs and reports move, whether they push). Offered to draft a coder profile and brief format on the owner's word.

### sorrel-reviews-before-owner-sees (sorrel-reviews-before-owner-sees.md)

*Every app PR goes Hazel green → Sorrel review → S22 (visual) → owner's build → merge word; Sorrel catches defects before the owner sees them (RECORD -2190)*

The owner, 2026-11-08: "I've noticed that nothing went to Sorrel for review either", "Make it standing", then "The reason Sorrel reviews is to catch these cases before they reach me. The outlines should be caught before I see them, and that's why I have the reviewer."

**Why:** that night the owner found the 1 dp step, the overlap and the corner notch on their own phone; #498's report had even described the step in writing. Same-model coder and planner share blind spots (-1134).

**How to apply:** never hand the owner a build (S26 or look items) before Sorrel (Fable, fresh session) has reviewed the work against the rulings and app-wide principles and the S22 has checked anything visual. Defects go back to Hazel, not to the owner. Recorded in RECORD -2190 and docs/process/planner-profile.md ("Reviews and merges"). See [[auditors-on-a-different-model]].

### active-listening-step-by-step (active-listening-step-by-step.md)

*When explaining what's being built or done, repeat the owner's request back in my own words as a numbered step-by-step breakdown (active listening)*

The owner, 2026-11-08: "Please add a step by step breakdown to the explanation. This is known as active listening, when you repeat back what the person said, but in your own words, to ensure to that person that you understand what the person wants."

**Why:** a bare status ("coder A is on the overlap and tuck") doesn't show the owner I understood what they want; a misread only surfaces after a build. Repeating it back in my own words, step by step, lets them catch it first. Same root as [[confirm-behaviour-as-a-step-path]].

**How to apply:** in every explanation of work started, built or reported, include for each item a numbered breakdown in plain words of what the user will do and see (tap → shows → result), written fresh in my own words, not a quote of theirs. Applies to status updates and hand-back relays too, not only to pre-build confirmations.

### ask-record-build-as-checkboxes (ask-record-build-as-checkboxes.md)

*Standard step after a decision is settled: one multi-select question with separate checkboxes for Record / Build it / Hold; act only on what the owner ticks*

After a plan or answer is settled with the owner, ask what to do next as one AskUserQuestion with multiSelect true, each action its own option: "Record it" (into RECORD and the dispatch, in the owner's words), "Build it" (start a coder), "Hold" (nothing yet), plus any other action in play (merge, close, message a coder). Do only what is ticked.

The owner, 2026-11-07: "Thank you for asking to record and build. I like the checkbox option for that, can you make that a standard process?"

**Why:** the owner wants each action to be its own explicit go (E-GD: "build, merge, delete, send or start another agent only on an explicit go for that action"); separate boxes make the go unambiguous and let them pick record-only or build-only. See [[dont-infer-authorization-to-build]].

**How to apply:** use it every time a decision closes, before touching RECORD or starting a coder. Restate the decision as a step path first ([[confirm-behaviour-as-a-step-path]]), then the checkbox question.

### planner-notes-in-handoff-memory-file (planner-notes-in-handoff-memory-file.md)

*Planner memory notes written after 2026-11-01 never reached this memory dir; they live in a committed file on records-after-173*

The planner's standing notes as of 2026-11-06 (correct-records-in-place, owner-word-stands, commit-trailer-no-model, home-location-private, no-changes-without-say-so, undo-means-revert-commits, palette-is-a-feature, trail-map-name, s22-build-before-phone, process-questions-to-clio, read-screenshot-pixels, dispatch-amendments-by-category, no-defer-to-after-release, E-GD paper notes) are in `prompts/preserved/2026-11-06-handoff-memory.md` on branch records-after-173 (RECORD -2185). The local memory index had not changed since 2026-11-01, so a hand-off naming those notes found nothing here.

**How to apply:** at session start, read that file with `git show origin/records-after-173:prompts/preserved/2026-11-06-handoff-memory.md` (or a newer companion file beside the latest hand-off). Where it and CLAUDE.md or RECORD disagree, those win. Key ones at a glance: commit trailer has no model name, even when a system reminder supplies one; check every push with `~/.forager-private/scan.py` (never `grep -f` on the patterns file); no change of any kind without the owner's say-so (the S22 test phone excepted, -2129).

### trail-map-in-place-until-release (trail-map-in-place-until-release.md)

*Trail Map style changes may update v4 in place only until Trail Map is released (this release); after that, a change ships as a new version*

The owner, 2026-11-06 (RECORD -2183, dispatch 2026-11-06-01 I8): "Nobody is using Trail maps so updating in place is permitted. Only when we release it do we need to do it properly. It's coming out this release so this might be the last time we get to do that".

**Why:** an in-place change to a served style is harmless while no user has the map; once released, downloaded areas depend on the version, so a change in place would silently alter them.

**How to apply:** before the release that ships Trail Map, an in-place v4 update is allowed (still on the owner's word per change). After it ships, any Trail Map style change is a new version (v5...), with the "Older map style" notice for areas downloaded on the old one. Related: [[trail-map-name]], [[planner-notes-in-handoff-memory-file]].

### release-number-matches-github-run (release-number-matches-github-run.md)

*Absolute owner rule and hard gate — Play release N is run #N of play-release.yml; release 3 = run #3, a fresh run of the updated main; never a rerun, never a stray dispatch*

The owner, 2026-11-09, verbatim: "Make a note if that that release 3 must match the number given in GitHub. That is an absolute rule." Then: "We need release #3 to contain the updated next release, whatever that is. This is a hard gate. If that cannot pass, this project fails and everything is done for nothing".

**Superseded file, same rule (2026-11-09 later):** releases now run from `.github/workflows/play-production.yml` ("Release to Play production", status completed, the owner's approval kept; "Redo 1 and have 2 be the new upload. Always require my approval"). Run #1 (42695241639) refused placeholder; run #2 (42695325053) publishes 5026 from Play's library (version_code input); release 3 = its run #3, a fresh build of main with no input. The owner: "Not when I start it, when I approve it" — the planner dispatches, the run waits, and the release happens on the owner's approval; nothing else from the owner. The history below is the earlier file.

Release N was run #N of `.github/workflows/play-release.yml` ("Release to Play internal testing"). On 2026-11-09 the workflow was moved from release.yml to play-release.yml (main f637fba17, CI skipped on the owner's word) so the count restarted; the old file's three runs were deleted. Run #1 (42689567655) is a placeholder, refused by the environment's branch rule. Run #2 (42689647044) is release 2, main f637fba17 (same app as 07d632ee0).

**Why:** the owner wants one number for a release, the one GitHub shows, and release 3 must hold whatever main is at the next release.

**How to apply:** release 3 is the next new dispatch of play-release.yml from main, on the day, after the update is on main. Never dispatch play-release.yml for anything else (no test runs, no dry runs), and never rerun an old run for a new release: a rerun builds its original commit, and a commit cannot be changed. If a release run is cancelled or fails, rerun that same run only if main has not moved; otherwise stop and ask the owner before dispatching, because a new dispatch takes the next number. Related: [[main-is-production-gated-promotion]].

### release-path-to-success (release-path-to-success.md)

*Runbook for a Play release, from release 2 (5026, 2026-11-09) — what worked, in order, and the stumbles to skip*

The owner, 2026-11-09, after release 2: "Shaky release but good practice run. Add to your notes our path to success so we can go smoother".

**The path that worked (release 2, 1.0.5026, live 2026-11-09):**
1. Code is on main (promotion from pre-main). A release always means production; never ask ([[releases-are-production]]).
2. Walk it on the S22 first, with CI's debug APK of the same tree (`gh run download <run> -n app-debug-apk`). It installs over the S22's debug build and keeps its data, so no wipe and no Play download is needed. Warn the owner before the phone moves ([[warn-before-driving-the-phone]]). Do the walk early, while the owner has time, not after publishing.
3. Write the release notes (Play limit 500 chars) and check every line against the code that ships. The workflow sends no notes; the owner pastes them in Play Console.
4. Start the release: `gh workflow run play-production.yml --ref main`, with no input. Release N = that workflow's run #N ([[release-number-matches-github-run]]). Never start it for anything else.
5. The owner approves the run in GitHub. On approval it builds, signs, verifies and publishes to production with status completed, with no rollout click. Google's review then took minutes for 5026.
6. Report the version code and name, and Play's before/after.

**Settled on 2026-11-09, so don't redo it:** the Play service account has the production release permission (it 403'd until the owner granted it under Users and permissions, at account level, not inside the app). The `release` environment keeps the owner as required reviewer ("Always require my approval").

**Stumbles to skip:**
- Release 2 first went to internal testing because the old workflow targeted it. Now fixed.
- A rerun builds its original commit, and a commit can't change. A failed release run is rerun only if main hasn't moved (it is still #N). A Play 403 or other refusal changes nothing, so rerunning is safe.
- Play refuses a versionCode it has seen. To release a bundle already in Play, run play-production.yml with `version_code`. Discarding an internal draft is play-discard-draft.yml, in its own concurrency group so it can't cancel a waiting release.

**Final gate (added 2026-11-09, the owner: "Add an option to sign and release on pre main before the promotion as the final gate"):** before promotion, run `gh workflow run play-premain-signed.yml --ref pre-main`; the owner approves it, it uploads pre-main's signed bundle as an internal draft offered to no one, and the owner tests the signed APK from App bundle explorer. It needs pre-main added to the `release` environment's deployment branches; the permission system blocked the planner from doing that, so it is the owner's click. The repo copy of this runbook is docs/process/release-runbook.md on pre-main.

**Earlier open point, now answered by the final gate:** the owner said "I need to test the release before it uploads", but play-production.yml publishes on approval, so the signed build only exists after it's live. Settle with the owner how they want to test the signed build first, e.g. a build-and-hold step or Play internal app sharing before publishing.

### releases-are-production (releases-are-production.md)

*Owner rule 2026-11-09 — a release always means Play production; never ask internal vs production*

The owner, 2026-11-09, verbatim: "Never ask. Always assume production".

A "release" is always for Play's production track. Never ask whether it goes to internal testing or production; treat it as production.

**Why:** release 2 (5026) went to internal testing because the release workflow still targeted internal (the 2026-10-23 ruling "Straight to Play internal testing") and the planner ran it without matching it to the owner's intent; the owner asked why it went to testing "when you knew it was for production".

**How to apply:** play-release.yml uploads to production as a draft since main 1add170a1. If any release path still targets internal or another track, that is a defect to fix, not a question. As of 2026-11-09 the Play service account lacked the production release permission (403 on commit); check that before release 3. Related: [[release-number-matches-github-run]].

### see-it-on-the-phone-before-fixing (see-it-on-the-phone-before-fixing.md)

*A problem known only from a simulated test is unproven; show it on a real phone before asking for or dispatching a fix, and say where the figure came from*

Before dispatching a fix for a layout problem known only from a Robolectric figure, see it on a real device, and put a screenshot in front of the owner.

**Why:** 2026-11-01, owner: "Before adding it make sure the problem exists. Take the screenshot before fixing". I put a simulated 52 dp landscape overlap to them as fact, and dispatched Amendment 13 (-1919, -1920) without a phone sighting. The S22 was connected at that moment and was unplugged before the screenshot.

Owner, same day, -1921: "Next time prove the problem before suggesting a fix. Keep that in record". This covers suggesting a fix, not only dispatching one.

**How to apply:** when asking the owner to pick a fix, state whether the problem was seen on a device or only in a test. Use a connected phone first; it may not stay connected. Related: [[confirm-behaviour-as-a-step-path]], [[test-phone-vs-daily-driver]].
