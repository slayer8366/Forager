#!/usr/bin/env bash
# Verifies against the live Open-Meteo historical archive API that the fields the seasonal
# fruiting-lag visualizer relies on actually exist and actually return data.
#
# Follows scripts/verify-open-meteo-fields.sh's shape, for the archive endpoint instead of the
# forecast one. A prior session concluded this endpoint was blocked by an organization network
# policy and shipped no verification at all. That was a misread of a *rate limit* as a policy
# denial: archive-api.open-meteo.com returns HTTP 429 ("Daily API request limit exceeded") on a
# shared egress IP under load, not a connection failure, and a later attempt with backoff got
# through. This script backs off and retries on 429 rather than concluding the host is
# unreachable — see the retry loop below.
#
# What this checks, and why each one matters:
#
#   1. The request actually returns data for the parameters
#      OpenMeteoHistoricalWeatherProvider sends: latitude, longitude, start_date, end_date,
#      daily=precipitation_sum, timezone=auto. Unlike the forecast endpoint, the archive one
#      takes an explicit date range rather than past_days/forecast_days.
#
#   2. The response shape matches what HistoricalPrecipitationResponseDto expects:
#      utc_offset_seconds, daily.time, daily.precipitation_sum.
#
#   3. A single request can span years, not just days — GetSeasonalPatternUseCase fetches one
#      request per search covering the full range from the earliest sighting (padded backward by
#      FruitingPatternAssumptions.FRUITING_LAG_DAYS.last) through the latest, which for a species
#      with sightings spread across many years can be a multi-year window. If the API silently
#      truncated a long span, the provider would need to chunk requests — a capability it does
#      not have and does not silently pretend to.
#
# What this script could NOT establish, the last time it was run (2026-08-17): whether the most
# recent few days before "today" are populated or come back null — every attempt to check that
# specific case hit the same daily rate limit before a request got through. This is not assumed
# either way: OpenMeteoHistoricalWeatherProvider treats a missing/null precipitation value on any
# day the same way OpenMeteoWeatherProvider already does for the forecast endpoint — the day is
# dropped from the series rather than defaulted to zero, so an unpopulated recent day degrades to
# "not counted" rather than reading as "confirmed dry."
set -euo pipefail

API="https://archive-api.open-meteo.com/v1/archive"
DAILY="precipitation_sum"

# Rate-limit backoff. The observed failure mode is HTTP 429 with
# {"error":true,"reason":"Daily API request limit exceeded..."} — a shared-IP throttle, not a
# policy block (curl exits 0 with a normal HTTP response either way). A curl-level failure
# (timeout, DNS, refused) is a different, genuine unreachability signal and is not retried the
# same way; it is reported once and treated as a real failure.
MAX_ATTEMPTS=6
BACKOFF_SECONDS=8

fail=0

# Fetches $1, retrying on the rate-limit response body. Writes the response body to stdout on
# success. On exhausted retries or a curl-level failure, writes an {"error":true} sentinel so
# callers can detect it the same way scripts/verify-open-meteo-fields.sh does, and prints what
# was actually observed rather than silently giving up.
fetch_with_backoff() {
  local url="$1" attempt body
  for attempt in $(seq 1 "$MAX_ATTEMPTS"); do
    if body=$(curl -sS --max-time 45 "$url" 2>/dev/null); then
      if printf '%s' "$body" | grep -q '"error":true'; then
        printf '  attempt %d/%d: rate-limited (%s), backing off %ds\n' \
          "$attempt" "$MAX_ATTEMPTS" "$(printf '%s' "$body" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("reason","?"))' 2>/dev/null || echo '?')" \
          "$BACKOFF_SECONDS" >&2
        sleep "$BACKOFF_SECONDS"
        continue
      fi
      printf '%s' "$body"
      return 0
    fi
    printf '  attempt %d/%d: curl failed (network/timeout, not a rate limit)\n' "$attempt" "$MAX_ATTEMPTS" >&2
    sleep "$BACKOFF_SECONDS"
  done
  echo '{"error":true,"reason":"exhausted retries"}'
  return 1
}

# --- 1 & 2. field names and response shape, at one location ---------------------------------
printf '%s\n' "--- request shape and response fields (10-day window) ---"
short_url="$API?latitude=45.5&longitude=-122.6&start_date=2024-01-01&end_date=2024-01-10&daily=$DAILY&timezone=auto"
short_body=$(fetch_with_backoff "$short_url") || fail=1
printf '%s' "$short_body" | python3 -c '
import json, sys
d = json.load(sys.stdin)
if d.get("error"):
    print("  FAILED: could not reach the archive API:", d.get("reason"))
    sys.exit(1)

problems = []
for field in ("utc_offset_seconds", "timezone", "daily"):
    if field not in d:
        problems.append("top-level %s missing" % field)
daily = d.get("daily", {})
for field in ("time", "precipitation_sum"):
    if field not in daily:
        problems.append("daily.%s missing" % field)

print("  utc_offset_seconds=%s timezone=%s" % (d.get("utc_offset_seconds"), d.get("timezone")))
time = daily.get("time", [])
precip = daily.get("precipitation_sum", [])
print("  daily window: %d days, %s -> %s" % (len(time), time[0] if time else "?", time[-1] if time else "?"))
print("  precipitation_sum: %d entries, %d non-null" % (len(precip), sum(1 for v in precip if v is not None)))
if len(time) != 10 or len(precip) != 10:
    problems.append("expected 10 days for a 10-day start_date/end_date span, got %d/%d" % (len(time), len(precip)))

if problems:
    print("  FAILED: " + "; ".join(problems))
    sys.exit(1)
print("  OK")
' || fail=1

# --- 3. a multi-year span in one request -----------------------------------------------------
printf '\n%s\n' "--- a single request spanning several years (2016-01-01 to 2024-12-31, 3288 days) ---"
long_url="$API?latitude=45.5&longitude=-122.6&start_date=2016-01-01&end_date=2024-12-31&daily=$DAILY&timezone=auto"
long_body=$(fetch_with_backoff "$long_url") || fail=1
printf '%s' "$long_body" | python3 -c '
import json, sys
d = json.load(sys.stdin)
if d.get("error"):
    print("  FAILED: could not reach the archive API:", d.get("reason"))
    sys.exit(1)

daily = d.get("daily", {})
time = daily.get("time", [])
precip = daily.get("precipitation_sum", [])
expected_days = 3288  # 2016-01-01 .. 2024-12-31 inclusive, across three leap years
non_null = sum(1 for v in precip if v is not None)
print("  %d days returned (expected %d), %s -> %s" % (len(time), expected_days, time[0] if time else "?", time[-1] if time else "?"))
print("  %d/%d non-null" % (non_null, len(precip)))

problems = []
if len(time) != expected_days:
    problems.append("expected %d days, got %d — the endpoint may cap or paginate a long span" % (expected_days, len(time)))
if non_null != len(precip):
    problems.append("some days in a fully-historical span came back null")
if problems:
    print("  FAILED: " + "; ".join(problems))
    sys.exit(1)
print("  OK: one request served the full multi-year span with no truncation and no gaps")
' || fail=1

printf '\n'
if [ "$fail" -eq 0 ]; then
  echo "All Open-Meteo historical-archive field checks passed."
else
  echo "One or more Open-Meteo historical-archive field checks failed or could not complete (see above)."
fi
exit "$fail"
