# Coder task: Mushroom Log Phase 2 — iNaturalist upload

Planning doc from the EGD planner session — a task spec for the coder, not a
replacement for the repo's real `CLAUDE.md`, whose standing principles govern
everything below.

**Do not dispatch or start this until `feature/mushroom-log` (Phase 1) has
merged to `main`.** This phase extends the `LogEntry`/sync-state model Phase
1 defines; building against an unmerged, still-shifting shape risks rework.
See `docs/plans/README.md` for current status.

**Also blocked, independently, on an owner action**: registering an
iNaturalist OAuth application. See "Owner action required" below. The
plumbing in this doc (auth flow, token storage, orchestration, tests against
a fake) can be built without it; a live, verified upload cannot.

## What this is

The second half of the mushroom log (see Phase 1's own plan doc, wherever it
landed on `main` after merge, for the field-record feature this extends):
uploading a completed log entry to iNaturalist as a real observation, with
its recorded characteristics attached.

Re-verify the shape Phase 1 actually shipped before starting — this doc
was written before Phase 1 merged and describes the *intended* sync-state
shape from the original plan, not confirmed final code.

## Auth: OAuth2 + PKCE, and a two-step token exchange

**Verified live this session** against `https://api.inaturalist.org/v1/swagger.json`
(reachable from this environment) and corroborating search results —
`https://www.inaturalist.org` itself is blocked by this environment's egress
proxy, so the exact registration-form UI was not seen directly and should be
confirmed by whoever completes step 1 below.

- iNaturalist supports OAuth2 Authorization Code with **PKCE** — the correct
  flow for a native app: **no client secret may ship in the APK.**
- **The OAuth token is not used directly against the API.** It must be
  exchanged for a **JWT** via an authenticated request to
  `https://www.inaturalist.org/users/api_token`. That JWT — not the raw OAuth
  token — is what goes in the `Authorization` header on every
  `api.inaturalist.org` call. This is a real second leg, not a detail to
  skip: build both the PKCE handshake and the token exchange.
- The auth flow itself must run against `https://www.inaturalist.org`
  (browser-based, via Custom Tabs); data calls go to `https://api.inaturalist.org`.
  Two different hosts — do not conflate them in the HTTP client config.
- `androidx.security-crypto` (for `EncryptedSharedPreferences` or equivalent)
  and `androidx.browser` (for Custom Tabs) are **not currently dependencies**
  of this project — checked `gradle/libs.versions.toml` and
  `app/build.gradle.kts` directly, neither is present. Add both, pinned to
  exact versions per `CLAUDE.md`, not open ranges.
- Behind an owned `INaturalistAuth` interface, so domain code never sees a
  token, a `Uri`, or a Custom Tabs intent — per `CLAUDE.md`'s wrap-external-
  integrations rule. Signed-out is a normal state: the log works fully
  offline with no account; upload is the only thing gated on auth.

### Owner action required (blocks live testing only, not the plumbing)

**Not yet done as of this writing.** The project owner needs to:

1. Log into iNaturalist, go to `https://www.inaturalist.org/oauth/applications/new`
   (unverified UI — the page itself couldn't be fetched from this
   environment; confirm the exact fields when you get there).
2. Register an application. This is a native app using PKCE — it needs no
   client secret. If the form issues one anyway, it must never be embedded
   in the APK; only the client ID is used.
3. Register a redirect URI using a custom scheme dedicated to this app —
   e.g. `org.forager.app://oauth-callback`. **The coder implementing this
   phase should pin the exact string** (it must match the
   `AndroidManifest.xml` intent filter exactly) and communicate it back to
   the owner if it differs from what they already registered.
4. Hand the resulting client ID (and confirmed redirect URI) to whoever
   picks up this task.

The log itself (record, photos, edit, browse — all of Phase 1) does not
depend on this and ships without it. Treat upload as fully separable:
buildable and unit-testable now, live-verifiable only once the owner
completes the above.

## Sync state

Per entry, from the original Phase 1 plan (re-verify Phase 1's actual code
uses this shape):

```kotlin
sealed interface LogSyncState {
    data object Draft : LogSyncState
    data class Uploading(val progress: Float) : LogSyncState
    data class Uploaded(val remoteObservationId: Long, val uploadedAt: Instant) : LogSyncState
    data class Failed(val reason: String, val remoteObservationId: Long?) : LogSyncState
}
```

The nullable `remoteObservationId` on `Failed` is what makes resumption
safe — see "partial failure" below. `Draft` is the default; there is no
auto-upload. Uploading requires an explicit user action.

## Upload orchestration

- **One entry ≈ many requests.** 1 observation + N photos + M field values.
  A find with 5 photos and 15 recorded features is roughly 21 requests. At
  the documented rate limit (~1 req/sec, ~10k/day — from the swagger spec
  and prior verification), that's ~21 seconds per entry. **This must be a
  resumable background operation with explicit per-entry progress, not a
  foreground call the UI blocks on.** Do not parallelize past the rate
  limit to make it feel faster.
- **Persist the returned remote observation id immediately** after the
  `POST /observations` call succeeds, before any photo or field-value
  request. If photo 3 of 5 then fails, a retry must resume against the
  existing remote observation, never re-`POST` a new one. A duplicate
  upload to a public citizen-science dataset is real harm to other people's
  data, not just a local bug — this is not optional.
- Partial success is reported as partial, never as success. If observation +
  3/5 photos + 10/15 field values succeeded before a failure, the entry's
  state reflects that truthfully (`Failed` with the remote id set so retry
  resumes cleanly), never silently marked `Uploaded`.
- Consider `WorkManager` for the background/resumable piece — not currently
  a dependency; check before assuming it's available, and pin its version if
  added.

## Observation field mapping: look up real IDs, never invent them

Mapping mushroom characteristics (cap shape, gill attachment, spore print
color, etc.) onto iNaturalist's `POST /observation_field_values` requires
real `observation_field_id` values — these are community-created and must be
looked up against the live API, matched to characteristics that genuinely
mean what this app means by them. **Where no well-established field exists,
put the characteristic in the observation's `description` text instead and
say so explicitly** — never guess an id, and never map onto a
field that means something adjacent-but-different. Document which
characteristics map to real fields and which fall back to prose, in a doc
comment next to the mapping code.

## Verification — this is not optional per CLAUDE.md

The swagger spec at `api.inaturalist.org/v1/swagger.json` is incomplete —
confirmed this session: `POST /observations`' documented schema
(`PostObservation`) lists only `species_guess`, `taxon_id`, `description`,
none of `observed_on`/`latitude`/`longitude`, which the real API almost
certainly accepts anyway (the Rails API is broader than its own spec). The
original Phase 1 planning session further found that iNaturalist **silently
ignores parameters it doesn't recognize** — a wrong field name produces a
successful-looking upload that quietly drops data.

Therefore: `scripts/verify-inaturalist-upload.sh` must POST a real
observation against a **test account** (not production data), read it back
via `GET /v1/observations/{id}`, and assert every field sent actually
persisted — comparing values, not checking for HTTP 200. Per `CLAUDE.md`:
assert on actual output, never a proxy like a status code. If this script
cannot be run (no test account, no owner-registered app yet), the upload
path is reported as unverified — explicitly, in the PR and in README's "Not
yet verified" — never implied to be working because the code compiles and
unit tests pass against a fake.

## Tests required

- `INaturalistAuth` behind a fake: the PKCE flow and the OAuth→JWT exchange
  as two distinct, separately-testable steps.
- Upload orchestration against a fake API: resumption after a mid-upload
  failure reuses the stored remote id and does not re-create the
  observation; rate limiting is respected; partial success reports as
  partial, never success; a `Draft` entry never uploads without an explicit
  user action.
- Token storage: written through `EncryptedSharedPreferences` (or
  equivalent), never plain `SharedPreferences` — a test asserting this is
  worth writing given it's a real, easy-to-regress security property, not
  just descriptive prose.
- **Not verifiable headlessly, report as such**: the live upload round-trip
  (covered only by the verify script + a test account), the OAuth browser
  hand-off via Custom Tabs on a real device. Add to README's "Not yet
  verified".

## Delivery

1. Confirm Phase 1 is merged and re-read its actual `LogEntry`/sync-state
   code before starting — this doc describes the intended shape, not
   confirmed final code.
2. Branch off current `main`.
3. Build the plumbing (auth interface + implementation, token storage,
   upload orchestration, field mapping) fully unit-testable against fakes,
   independent of whether the owner has registered an app yet.
4. If a client ID/redirect URI has been provided, wire it in (via
   `BuildConfig` or a gradle property — not hardcoded, and never a secret
   committed to the repo) and run `scripts/verify-inaturalist-upload.sh`
   against a test account; report actual results.
5. If no client ID is available yet, say so plainly in the PR: plumbing is
   done and unit-tested, live verification is blocked on the owner action
   described above, added to README's "Not yet verified".
6. `./gradlew testDebugUnitTest` and `./gradlew assembleDebug`; report
   actual pass/fail counts.
7. Update README: "How it works" entry for upload, "Not yet verified" as
   applicable.
