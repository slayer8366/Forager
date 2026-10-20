#!/usr/bin/env bash
# A fixed set of screenshots from the S22 for a phone pass (dispatch 2026-10-19-27, RECORD -1435).
#
# Usage: scripts/s22-screenshot-set.sh OUTPUT_DIR
#   OUTPUT_DIR must be outside every git repository and empty (or not yet exist). The screenshots are
#   the owner's: they can show real places and records, so they never go into a repository.
#   Environment: S22_SERIAL (default R5CT321008R) to aim at the S22 under another serial.
#
# What it does, in order, stopping at the first problem with "FAIL [step]: ..." and exit 1:
#   1. checks the device attached under the serial is an SM-S908U with the app installed, and prints
#      the installed versionName;
#   2. wakes the screen (fails if a lock screen is showing; it never unlocks), brings the app to the
#      front with `am start` (never force-stop, so a recording in progress is not killed), and turns
#      auto-rotate off;
#   3. in portrait and then landscape (`settings put system user_rotation`), visits, in order:
#      Maps tab, map fullscreen, search open, Tools drawer, Settings, Journal, List, Seasonal; each is
#      checked from a fresh `uiautomator dump` before its picture is taken, and the picture is
#      refused unless the app's own window has focus;
#   4. saves <screen>_<orientation>_<versionName>.png per screen and orientation, and index.txt.
# On exit, success or failure, auto-rotate and user_rotation are put back to what they were, and the
# map is taken out of fullscreen if this script put it in.
#
# How it moves. Every tap is at the centre of a node found by its exact text or content description
# in a fresh dump, from a fixed allowlist: the five bottom-nav labels, "Fullscreen", "Exit
# fullscreen", the search bar, "Settings", and "Close navigation menu". No tap is at a hard-coded
# coordinate. Closing the search panel and Settings uses the Back key. Nothing here records,
# deletes, downloads, shares, starts navigation or changes a setting beyond the two rotation keys.
# Fullscreen is the app's own persisted UI state; it is entered for one picture and left at once.
set -uo pipefail

SERIAL="${S22_SERIAL:-R5CT321008R}"
PKG=com.zynergylabs.forager.app
ACTIVITY="$PKG/.MainActivity"
ADB=(adb -s "$SERIAL")
NAV_LABELS=(List Seasonal Maps Journal Tools)
SETTLE="${SETTLE_SECONDS:-1.5}"

STEP="setup"
fail() { echo "FAIL [$STEP]: $*" >&2; exit 1; }

OUT="${1:-}"
[ -n "$OUT" ] || fail "usage: $0 OUTPUT_DIR (a folder outside every git repository)"

# --- Output folder: outside every repository, empty. -------------------------------------------
STEP="output folder"
probe="$OUT"
while [ ! -d "$probe" ]; do probe="$(dirname "$probe")"; done
if git -C "$probe" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  fail "$OUT is inside the git repository at $(git -C "$probe" rev-parse --show-toplevel); screenshots never go in a repository"
fi
mkdir -p "$OUT" || fail "could not create $OUT"
[ -z "$(ls -A "$OUT")" ] || fail "$OUT is not empty; give an empty folder so a run's pictures are never mixed with another's"
OUT="$(cd "$OUT" && pwd)"

TMP="$(mktemp -d)"
DUMP="$TMP/window.xml"

# --- 1. Device and install. -------------------------------------------------------------------
STEP="device"
attached="$(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')"
command grep -qx -- "$SERIAL" <<<"$attached" || fail "no device attached as $SERIAL (attached: ${attached:-none})"
model="$("${ADB[@]}" shell getprop ro.product.model | tr -d '\r')"
[ "$model" = "SM-S908U" ] || fail "device $SERIAL is '$model', not the S22 (SM-S908U)"

STEP="install"
pkg_dump="$("${ADB[@]}" shell dumpsys package "$PKG" | tr -d '\r')"
VERSION="$(command grep -m1 -o 'versionName=[^ ]*' <<<"$pkg_dump" | cut -d= -f2)"
[ -n "$VERSION" ] || fail "$PKG is not installed on $SERIAL"
SAFE_VERSION="$(tr -c 'A-Za-z0-9._+-' '_' <<<"$VERSION" | sed 's/_$//')"
echo "Device: $model ($SERIAL)"
echo "Installed: $PKG versionName=$VERSION"

# --- Rotation: saved now, restored on every exit. ---------------------------------------------
STEP="save rotation"
ORIG_ACCEL="$("${ADB[@]}" shell settings get system accelerometer_rotation | tr -d '\r')"
ORIG_USER="$("${ADB[@]}" shell settings get system user_rotation | tr -d '\r')"
echo "Rotation before: accelerometer_rotation=$ORIG_ACCEL user_rotation=$ORIG_USER"
ENTERED_FULLSCREEN=0

restore_setting() { # key original
  if [ "$2" = "null" ] || [ -z "$2" ]; then
    "${ADB[@]}" shell settings delete system "$1" >/dev/null
  else
    "${ADB[@]}" shell settings put system "$1" "$2"
  fi
}
cleanup() {
  status=$?
  if [ "$ENTERED_FULLSCREEN" -eq 1 ]; then
    if dump 2>/dev/null && tap_desc "Exit fullscreen" 2>/dev/null; then
      echo "Cleanup: left fullscreen"
    else
      echo "WARNING: the map may still be in fullscreen; this script entered it and could not leave it" >&2
      status=1
    fi
  fi
  restore_setting user_rotation "$ORIG_USER"
  restore_setting accelerometer_rotation "$ORIG_ACCEL"
  now_accel="$("${ADB[@]}" shell settings get system accelerometer_rotation | tr -d '\r')"
  now_user="$("${ADB[@]}" shell settings get system user_rotation | tr -d '\r')"
  echo "Rotation after: accelerometer_rotation=$now_accel user_rotation=$now_user"
  if [ "$now_accel" != "$ORIG_ACCEL" ] || [ "$now_user" != "$ORIG_USER" ]; then
    echo "FAIL [restore rotation]: rotation not restored (wanted accelerometer_rotation=$ORIG_ACCEL user_rotation=$ORIG_USER)" >&2
    status=1
  fi
  rm -rf "$TMP"
  exit "$status"
}
trap cleanup EXIT

# --- Helpers over a fresh uiautomator dump. ---------------------------------------------------
dump() {
  "${ADB[@]}" shell uiautomator dump /sdcard/window_dump.xml >/dev/null 2>&1 || return 1
  "${ADB[@]}" exec-out cat /sdcard/window_dump.xml >"$DUMP" 2>/dev/null || return 1
  [ -s "$DUMP" ]
}

# query MODE ARG: prints a result from the last dump, exit 1 if not found. Modes:
#   text T / desc D        centre "x y" of the first node whose text / content-desc equals T / D
#   textsuffix T           centre of the first node whose text ends with T
#   nav LABEL              centre of LABEL inside the node holding all five nav labels
#   navselected LABEL      succeeds if LABEL's nav item is selected
#   landscape / portrait   succeeds if the dump's root is wider / taller than it is high
query() {
  python3 -I - "$DUMP" "$1" "${2:-}" "${NAV_LABELS[@]}" <<'PY'
import re, sys
import xml.etree.ElementTree as ET
path, mode, arg, *nav = sys.argv[1:]
root = ET.parse(path).getroot()
def centre(n):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
    return (x1 + x2) // 2, (y1 + y2) // 2
def nodes(top):
    return [n for n in top.iter("node")]
def first(pred, top=root):
    return next((n for n in nodes(top) if pred(n)), None)
if mode in ("landscape", "portrait"):
    top = first(lambda n: True)
    if top is None: sys.exit(1)
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", top.get("bounds")))
    sys.exit(0 if ((x2 - x1) > (y2 - y1)) == (mode == "landscape") else 1)
if mode in ("text", "desc", "textsuffix"):
    key = "content-desc" if mode == "desc" else "text"
    n = first(lambda n: n.get(key, "").endswith(arg) if mode == "textsuffix" else n.get(key) == arg)
    if n is None: sys.exit(1)
    print(*centre(n)); sys.exit(0)
if mode in ("nav", "navselected"):
    # The nav container: the deepest node whose subtree holds every nav label as an exact text.
    best = None
    for n in nodes(root):
        texts = {m.get("text") for m in n.iter("node")}
        if all(l in texts for l in nav):
            best = n  # iter() is depth-first, so later matches are deeper
    if best is None: sys.exit(1)
    parents = {c: p for p in best.iter("node") for c in p}
    label = first(lambda n: n.get("text") == arg, best)
    if label is None: sys.exit(1)
    if mode == "nav":
        print(*centre(label)); sys.exit(0)
    n = label
    while n is not None:
        if n.get("selected") == "true": sys.exit(0)
        n = parents.get(n)
    sys.exit(1)
sys.exit(2)
PY
}

tap_xy() { "${ADB[@]}" shell input tap "$1" "$2"; sleep "$SETTLE"; }
tap_by() { # mode arg: tap the node's centre from a fresh dump
  local xy
  dump || return 1
  xy="$(query "$1" "$2")" || return 1
  # shellcheck disable=SC2086
  tap_xy $xy
}
tap_desc() { tap_by desc "$1"; }

need() { # mode arg description: the last dump must contain it
  query "$1" "${2:-}" >/dev/null || fail "${3:-expected $1 '$2' on screen}, not found"
}

app_focused() {
  "${ADB[@]}" shell dumpsys window | tr -d '\r' | command grep -E 'mCurrentFocus=' | command grep -qF "$PKG/"
}

INDEX="$OUT/index.txt"
COUNT=0
shoot() { # screen orientation
  local f="$OUT/${1}_${2}_${SAFE_VERSION}.png"
  app_focused || fail "the app's window does not have focus; refusing to save a picture of something else"
  "${ADB[@]}" exec-out screencap -p >"$f" || fail "screencap failed"
  local size
  size="$(python3 -I - "$f" "$2" <<'PY'
import struct, sys
data = open(sys.argv[1], "rb").read(32)
if data[:8] != b"\x89PNG\r\n\x1a\n": sys.exit(1)
w, h = struct.unpack(">II", data[16:24])
if (w > h) != (sys.argv[2] == "landscape"): sys.exit(1)
print(f"{w}x{h}")
PY
)" || { rm -f "$f"; fail "the saved file is not a $2 PNG"; }
  printf '%s\t%s\t%s\t%s\t%s\n' "$(basename "$f")" "$1" "$2" "$size" "$VERSION" >>"$INDEX"
  COUNT=$((COUNT + 1))
  echo "  saved $(basename "$f") ($size)"
}

back_until_gone() { # mode arg description: Back, up to three times, until the node is gone
  local i
  for i in 1 2 3; do
    "${ADB[@]}" shell input keyevent KEYCODE_BACK; sleep "$SETTLE"
    dump || fail "uiautomator dump failed"
    query "$1" "$2" >/dev/null || return 0
  done
  fail "$3 is still open after three Back presses"
}

go_tab() { # label
  tap_by nav "$1" || fail "bottom-nav '$1' not found (is the app in front, on its main screen?)"
  dump || fail "uiautomator dump failed"
  need navselected "$1" "the '$1' tab selected"
}

set_rotation() { # 0 or 1
  "${ADB[@]}" shell settings put system user_rotation "$1"
  local want=portrait i
  [ "$1" = 1 ] && want=landscape
  for i in $(seq 1 10); do
    sleep 0.5
    dump && query "$want" >/dev/null && { sleep "$SETTLE"; return 0; }
  done
  fail "the screen did not turn $want after user_rotation=$1"
}

# --- 2. Wake, bring the app to the front, auto-rotate off. ------------------------------------
STEP="wake"
"${ADB[@]}" shell input keyevent KEYCODE_WAKEUP; sleep 1
if "${ADB[@]}" shell dumpsys window | tr -d '\r' | command grep -q 'isKeyguardShowing=true'; then
  fail "the lock screen is showing; unlock the phone and run again (this script never unlocks it)"
fi

STEP="launch"
"${ADB[@]}" shell am start -W -n "$ACTIVITY" >/dev/null 2>&1 || fail "am start failed"
sleep 2
app_focused || fail "the app did not come to the front"

STEP="auto-rotate off"
"${ADB[@]}" shell settings put system accelerometer_rotation 0

# Out of any drawer or search panel left open, without pressing Back blindly (a Back on the main
# screen can arm the app's exit).
STEP="start state"
dump || fail "uiautomator dump failed"
if query desc "Close navigation menu" >/dev/null; then tap_desc "Close navigation menu" || fail "could not close the open drawer"; dump; fi
if query text "Set on map" >/dev/null; then back_until_gone text "Set on map" "the search panel"; fi
if query desc "Exit fullscreen" >/dev/null; then fail "the map was already in fullscreen; leave it and run again, so this script never changes it"; fi

printf 'file\tscreen\torientation\tpixels\tversionName\n' >"$INDEX"

# --- 3. The screens, per orientation. ---------------------------------------------------------
for orientation in portrait landscape; do
  rot=0; [ "$orientation" = landscape ] && rot=1
  STEP="$orientation: rotate"
  echo "$orientation:"
  set_rotation "$rot"

  STEP="$orientation: maps"
  go_tab Maps
  need desc "Fullscreen" "the map's Fullscreen button"
  shoot maps "$orientation"

  STEP="$orientation: map fullscreen"
  tap_desc "Fullscreen" || fail "Fullscreen button not found"
  ENTERED_FULLSCREEN=1
  dump || fail "uiautomator dump failed"
  need desc "Exit fullscreen" "the Exit fullscreen button"
  shoot map-fullscreen "$orientation"
  tap_desc "Exit fullscreen" || fail "Exit fullscreen button not found"
  dump || fail "uiautomator dump failed"
  need desc "Fullscreen" "the map out of fullscreen"
  ENTERED_FULLSCREEN=0

  STEP="$orientation: search"
  tap_by textsuffix "Search a location" || fail "the search bar ('<month> · Search a location') not found"
  dump || fail "uiautomator dump failed"
  # "Set on map" (only checked, never tapped) rather than "Search coordinates": in landscape the
  # panel opens scrolled, with the coordinates rows above its top edge (first run, 2026-10-20).
  need text "Set on map" "the open search panel"
  shoot search "$orientation"
  back_until_gone text "Set on map" "the search panel"

  STEP="$orientation: tools"
  tap_by nav Tools || fail "bottom-nav 'Tools' not found"
  dump || fail "uiautomator dump failed"
  need desc "Close navigation menu" "the Tools drawer"
  need text "Settings" "the drawer's Settings row"
  shoot tools "$orientation"

  STEP="$orientation: settings"
  tap_by text "Settings" || fail "Settings row not found"
  dump || fail "uiautomator dump failed"
  need text "Units" "the Settings page"
  shoot settings "$orientation"
  back_until_gone desc "Close navigation menu" "the Tools drawer"

  STEP="$orientation: journal"
  go_tab Journal
  need text "Entries" "the Journal's Entries | Records switch"
  shoot journal "$orientation"

  STEP="$orientation: list"
  go_tab List
  shoot list "$orientation"

  STEP="$orientation: seasonal"
  go_tab Seasonal
  shoot seasonal "$orientation"

  STEP="$orientation: back to maps"
  go_tab Maps
done

STEP="done"
echo "PASS: $COUNT screenshots of $VERSION in $OUT (index: $INDEX)"
