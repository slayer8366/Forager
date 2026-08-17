#!/usr/bin/env bash
# Verifies against the live Open-Meteo API that the fields the trip planner relies on
# actually exist and actually return data — at more than one location.
#
# This needs its own check because there are two *different* silent failure modes, and
# unit tests can only prove the app asks for a field, never that the API means anything
# by it:
#
#   1. Soil moisture and soil temperature are hourly-only. Requesting them as daily
#      variables is rejected outright, so a future "simplification" that moves them to
#      `daily=` fails loudly — that case is checked here so the rejection is on record.
#
#   2. Soil depth bands are model-specific and `timezone=auto` picks a different weather
#      model per location. A band the chosen model does not carry comes back as a
#      full-length array of nulls, with `hourly_units` reporting "undefined", under
#      HTTP 200. No error, no warning. This is the one that matters: hardcoding
#      soil_moisture_0_to_7cm ships a permanently empty soil signal across North America,
#      and hardcoding soil_moisture_0_to_10cm ships one across the UK. Neither band is
#      universal, which is why the app requests both and resolves the populated one.
#
# The comparison below is the point of the script. If Portland ever starts serving 0–7cm
# or London starts serving 0–10cm, that is fine — but if *neither* band serves anything at
# a location, the soil signal is dead there and the app should be saying so.
set -euo pipefail

API="https://api.open-meteo.com/v1/forecast"
PAST_DAYS=14
FORECAST_DAYS=7

DAILY="precipitation_sum,et0_fao_evapotranspiration"
HOURLY="soil_moisture_0_to_7cm,soil_moisture_0_to_10cm,\
soil_moisture_7_to_28cm,soil_moisture_10_to_40cm,\
soil_temperature_0_to_7cm,soil_temperature_0_to_10cm"

fail=0

# Reporting the measurement is this script's whole job, so an unreachable host must not
# abort the run via `set -e`. The failure is recorded and printed, not swallowed.
fetch() {
  curl -sS --max-time 45 "$1" 2>/dev/null || echo '{"error":true,"reason":"unreachable"}'
}

# --- 1. soil variables are hourly-only ------------------------------------------------
printf '%s\n' "--- daily endpoint must reject soil variables ---"
reject=$(fetch "$API?latitude=45.5&longitude=-122.6&daily=soil_moisture_0_to_7cm&forecast_days=1")
if printf '%s' "$reject" | grep -q '"error":true'; then
  printf '  daily=soil_moisture_0_to_7cm -> rejected, as expected\n'
  printf '  reason: %s\n' "$(printf '%s' "$reject" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("reason","?"))')"
else
  printf '  FAILED: the daily endpoint accepted a soil variable; the hourly-only premise no longer holds\n'
  fail=1
fi

# --- 2. per-location field availability -----------------------------------------------
printf '\n%s\n' "--- field availability by location (past_days=$PAST_DAYS forecast_days=$FORECAST_DAYS) ---"

check_location() {
  local label="$1" lat="$2" lng="$3"
  local url="$API?latitude=$lat&longitude=$lng&daily=$DAILY&hourly=$HOURLY"
  url="$url&past_days=$PAST_DAYS&forecast_days=$FORECAST_DAYS&timezone=auto"

  fetch "$url" | LABEL="$label" python3 -c '
import json, os, sys

label = os.environ["LABEL"]
d = json.load(sys.stdin)
if d.get("error"):
    print("  %-10s SKIPPED (%s)" % (label, d.get("reason", "?")))
    sys.exit(2)

print("  %s  tz=%s  utc_offset_seconds=%s" % (label, d.get("timezone"), d.get("utc_offset_seconds")))

problems = []

# Daily fields must be present and fully populated across the whole window.
daily = d.get("daily", {})
days = daily.get("time", [])
print("    daily window: %d days, %s -> %s" % (len(days), days[0], days[-1]))
for name in ("precipitation_sum", "et0_fao_evapotranspiration"):
    values = daily.get(name)
    if values is None:
        problems.append("daily %s absent" % name)
        continue
    served = sum(1 for v in values if v is not None)
    print("    daily %-28s %d/%d non-null" % (name, served, len(values)))
    if served != len(values):
        problems.append("daily %s incomplete" % name)

# Hourly soil: report which band actually served data, per family.
hourly = d.get("hourly", {})
units = d.get("hourly_units", {})
families = {
    "shallow moisture": ["soil_moisture_0_to_7cm", "soil_moisture_0_to_10cm"],
    "deeper moisture":  ["soil_moisture_7_to_28cm", "soil_moisture_10_to_40cm"],
    "soil temperature": ["soil_temperature_0_to_7cm", "soil_temperature_0_to_10cm"],
}
for family, names in families.items():
    resolved = None
    for name in names:
        values = hourly.get(name, [])
        served = sum(1 for v in values if v is not None)
        unit = units.get(name, "absent")
        print("    hourly %-30s %4d/%-4d non-null  units=%s"
              % (name, served, len(values), unit))
        if served and unit != "undefined" and resolved is None:
            resolved = name
    if resolved:
        print("    -> %s resolves to %s" % (family, resolved))
    else:
        problems.append("%s: no band served data" % family)
        print("    -> %s: NO BAND SERVED DATA" % family)

if problems:
    print("    FAILED: " + "; ".join(problems))
    sys.exit(1)
print("    OK")
' || return $?
}

for spec in \
  "Portland:45.5:-122.6" \
  "London:51.51:-0.13" \
  "Berlin:52.52:13.41" \
  "Sydney:-33.87:151.21"
do
  IFS=: read -r label lat lng <<< "$spec"
  check_location "$label" "$lat" "$lng" || fail=1
  printf '\n'
done

# --- 3. the past/future boundary ------------------------------------------------------
# The app splits observed days from forecast days by date. That is only correct if
# forecast_days=0 really does end at *yesterday* in the location's own timezone, and
# forecast_days=N really does append N days starting from today.
printf '%s\n' "--- past/future window boundary ---"
for spec in "Portland:45.5:-122.6" "London:51.51:-0.13"; do
  IFS=: read -r label lat lng <<< "$spec"
  base="$API?latitude=$lat&longitude=$lng&daily=precipitation_sum&past_days=$PAST_DAYS&timezone=auto"
  history=$(fetch "$base&forecast_days=0")
  full=$(fetch "$base&forecast_days=$FORECAST_DAYS")
  printf '%s\n%s\n' "$history" "$full" | LABEL="$label" PAST="$PAST_DAYS" FCAST="$FORECAST_DAYS" python3 -c '
import json, os, sys

label, past, fcast = os.environ["LABEL"], int(os.environ["PAST"]), int(os.environ["FCAST"])
lines = sys.stdin.read().strip().splitlines()
history, full = (json.loads(x) for x in lines)
if history.get("error") or full.get("error"):
    print("  %-10s SKIPPED (unreachable)" % label)
    sys.exit(2)

h, f = history["daily"]["time"], full["daily"]["time"]
print("  %-10s forecast_days=0 -> %d days, %s..%s" % (label, len(h), h[0], h[-1]))
print("  %-10s forecast_days=%d -> %d days, %s..%s" % ("", fcast, len(f), f[0], f[-1]))

problems = []
if len(h) != past:
    problems.append("history is %d days, expected %d" % (len(h), past))
if len(f) != past + fcast:
    problems.append("full window is %d days, expected %d" % (len(f), past + fcast))
if f[:past] != h:
    problems.append("the first %d days of the full window are not the history window" % past)
if problems:
    print("    FAILED: " + "; ".join(problems))
    sys.exit(1)
print("    OK: the observed portion is exactly the first %d entries, ending at %s" % (past, h[-1]))
' || fail=1
done

printf '\n'
if [ "$fail" -eq 0 ]; then
  echo "All Open-Meteo field checks passed."
else
  echo "One or more Open-Meteo field checks failed or were skipped (see above)."
fi
exit "$fail"
