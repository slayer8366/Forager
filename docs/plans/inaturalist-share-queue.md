# Spec: share finds to iNaturalist, one at a time, from a review queue

Written by the planner (Rowan Waymark), 2026-10-13. Decisions: RECORD -963 and -964 (branch `records-after-173`). Evidence: the read-only check of iNaturalist's apps (dispatch `prompts/preserved/2026-10-13-01.md`; its report is quoted in -964). Base: main `497423d6`.

**Status: spec for the owner's review. Not dispatched.**

## Why

After the 2026-10-12 OMS meeting, the owner reported "a good point about needing more observations for the forecast". Finds that reach iNaturalist and are confirmed by its community ("research grade") flow on to the shared biodiversity database the forecast reads. That flow is inferred, not checked against the forecast's input code.

## What the owner decided

- Forager **hands finds to iNaturalist's own app**. It doesn't upload them itself. Uploading inside an app comes later, in a separate companion app that will also hold the future identification system ("A and B, it could fit as the ID app").
- Several finds can be selected and shared **one after another from a queue**. Each one is reviewed, edited or confirmed, then shared ("then it moves to the next one").
- **"Include location" starts on, and is offered for every find** ("Start with on. Offer each time").
- A find can also be reached **from the map: tap the find, choose "Open in journal", then "Share to iNaturalist"**.
- **No identification is passed** to iNaturalist ("A - skip it"). The no-ID rule is unchanged: Forager never names a mushroom.

This replaces `mushroom-log-phase2-inaturalist-upload.md` (in-app upload over OAuth) for the near term. That plan's OAuth, rate-limit, observation-field and remote-id parts don't apply to a share.

## Step path

1. **Journal:** long-press a find to start selecting, and tap more finds to add them. A button reads **"Share to iNaturalist (3)"**. From a single find (including one reached from the map), the same button shares just that find.
2. **Review:** Forager shows the first find: its photos, date and time, and notes. Anything edited saves to the journal entry. A switch reads **"Include location"**, starting **on**. Buttons: **Share**, **Skip**, **Stop**.
3. **Share:** iNaturalist's app opens with that find's photos as **one new observation**, carrying the find's date and time, and its location if the switch was on. The person sets its privacy and name there, and saves.
4. **Back in Forager,** the queue moves to the next find: **"Find 2 of 3"**. Pressing Back from the review screen asks whether to stop the queue.
5. **Done:** each shared find shows **"Sent to iNaturalist"** with the date it was sent. "Sent", not "posted": Forager can't see whether posting finished, and iNaturalist uploads in the background.

```
 Review — Find 2 of 3
 +--------------------------------------+
 | [photo] [photo] [photo]              |
 | Oct 11, 2026  4:12 PM                |
 | Notes: under Douglas fir, ...        |
 |                                      |
 | Include location            [ on  ]  |
 |                                      |
 |   [ Skip ]   [ Stop ]   [ Share ]    |
 +--------------------------------------+
```

## The planner's calls (correctable by the owner)

1. **Dates always go with the share.** Forager's camera strips the date from saved photos along with the location (`PhotoMetadataScrub.kt:56`), and iNaturalist reads the observation's date from the photo. Without it the observation arrives undated. Each share uses a temporary copy of the photos with the find's date and time written in. GPS and its accuracy are written into the first photo only when "Include location" is on. The stored originals are never changed.
2. **iNaturalist opens in its own task, and the queue position is saved.** After saving, iNaturalist's current app closes the whole task it was opened into (`ObservationListActivity.java:813-815`). Launched the usual way, that would close Forager too. Opening it separately prevents that. Saving the queue means a restart still resumes at the right find.
3. **"Sent to iNaturalist" is a new state on the entry, with its date.** Forager never learns an iNaturalist observation id through a share, so the old `Uploaded` / `Failed` states don't fit. Re-sharing a sent find is allowed. The review screen says **"Sent before on <date>"** so a duplicate is a choice, not an accident.
4. **When iNaturalist's app isn't installed,** the button stays visible. Tapping it says so plainly and offers to open its Play page, consistent with the empty-state rule (honest copy plus a next step). Forager already declares the classic app's package (`AndroidManifest.xml:75`).

## What it does not do

- No identification, suggestion or name of any kind goes to iNaturalist.
- No account, sign-in or API upload inside Forager.
- No automatic or background sharing; every share is a tap.
- No change to the stored photos or to the camera's scrubbing.

## Known risks

- **iNaturalist's replacement app** (now in closed testing) treats several shared photos as separate observations until the person groups them. When it ships, multi-photo finds need a grouping step there. The spec targets the current Play app (`org.inaturalist.android`).
- **iNaturalist blocks some apps from sharing,** ones whose photos aren't evidence of the person's own encounter or have stripped metadata (iNaturalistAndroid issue #811). Forager isn't on the list. Share copies carry the date, and the location when on, to stay real field evidence.
- **The first share shows iNaturalist's own permission prompts** (photos, photo location). Denying the storage prompt cancels that share inside iNaturalist.

## Device checks (the owner)

1. Share one find with location on: one observation with all its photos, the right date and time, location and accuracy.
2. Location off: no location, and no current-position fill.
3. After saving in iNaturalist, Forager is still open on the queue's next find, not restarted.
4. A three-find queue: Skip, Stop, Back, and resuming after force-closing Forager mid-queue.
5. With iNaturalist's app uninstalled: the plain message and the Play link.

## Not yet checked

- iNaturalist's terms-of-use pages could not be read from this environment.
- Nothing in this spec has been tried on a phone.
- That shared finds reach the forecast is inferred.
