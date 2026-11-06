#!/usr/bin/env bash
# Guards the design-token boundary described in docs/plans/understory-design-system.md §4S:
# colours come from the theme, motion comes from the motion scheme, and no call site invents
# either. Same shape and same reason as scripts/verify-codeowners-placeholders.sh -- it greps the
# real tree rather than asserting against a copy of it, and fails loudly.
#
# Deliberately NOT wired into .github/workflows/ci.yml, matching the precedent .github/CODEOWNERS
# records for verify-codeowners-placeholders.sh: that pipeline gates every PR in this repo, and a
# violation here should not block unrelated changes from merging.
#
# STATE AS OF 2026-10-07 (dispatch 2026-09-28-658, scout items G1/F1), replacing an older header
# that no longer matched. Check 2 had searched for imports under com.forager.app.ui.theme, the
# package root from before the rename to com.zynergylabs.forager.app, so it found nothing and passed
# whatever the code imported: a check that never saw the data that could fail it. It now searches
# the current root, and fails on palette constants imported into feature packages (74 at first;
# the owner then allowed Spacing and navigationBarContainerColor, RECORD -661, and three were
# fixed in files that dispatch changed, leaving 11; none was changed just to make it pass). Check 1 fails on one colour
# literal in ui/log, and check 3 fails on real tween( calls (its pattern also matched
# "metersBetween(" until RECORD -660 anchored it to a word boundary). Check 4 passes. A check that passes the moment it is introduced
# never demonstrated it could fail; check 2 was shown failing on a planted import before this
# header was written. Run it, read which checks fail, and expect the list to shrink as the steps
# land.
set -uo pipefail

cd "$(dirname "$0")/.."

UI=app/src/main/java/com/zynergylabs/forager/app/ui
THEME=$UI/theme
failed=0

report() { # name, violations
  local name="$1" hits="$2"
  if [ -z "$hits" ]; then
    printf '  PASS  %s\n' "$name"
  else
    printf '  FAIL  %s\n' "$name"
    printf '%s\n' "$hits" | sed 's/^/          /'
    failed=1
  fi
}

echo "verify-design-tokens.sh"

# 1. Raw colour literals belong to the theme package. Anywhere else they bypass the colour-role
#    indirection entirely, which is how a palette drifts one call site at a time.
hits=$(grep -rnE "Color\(0x" $UI --include=*.kt | grep -v "^$THEME/" || true)
report "no Color(0x literal outside ui/theme/" "$hits"

# 2. Palette constants are the theme's own vocabulary; the rest of the UI names roles, not
#    colours. Importing Bark into a screen is one import, but it is the precedent that makes the
#    next twenty look reasonable ("tag 05"). MapIconBarAccent is excluded for the same reason
#    MapPalette already is: a component whose colours are deliberately not derived from the
#    ambient ColorScheme (see that type's own doc comment) is an owned type in ui/theme/, not raw
#    palette literals reaching into a feature package. LocalForagerDarkTheme is excluded for the
#    same reason ForagerTheme already is: it is the theme-resolution primitive itself (see its own
#    doc comment for why it exists), not a colour or a palette constant. mapChromeContentColor is
#    excluded for MapIconBarAccent's reason: an owned accessor in ui/theme/ for chrome keyed off the
#    map's night/day, added (RECORD -660) so feature files stop importing Bark for it.
#    Spacing is allowed: it is the design scale, meant to be imported everywhere (RECORD -661).
#    navigationBarContainerColor is allowed: the one chrome-colour token of the owner's C1 ruling (RECORD -661).
hits=$(grep -rn "^import com\.zynergylabs\.forager\.app\.ui\.theme\." app/src/main --include=*.kt \
       | grep -vE "\.(ForagerTheme|LocalForagerDarkTheme|MapPalette|MapIconBarAccent|mapChromeContentColor|Spacing|navigationBarContainerColor)$" || true)
report "no palette constant imported outside the theme package" "$hits"

# 3. Motion comes from MaterialTheme.motionScheme. A tween at a call site is the tween-only rule
#    growing back, one animation at a time (ADR-0002). The name must start at a word boundary:
#    the bare "tween(" also matched "metersBetween(" (RECORD -660 fixed that; shown before and after
#    on a planted file holding one of each).
#    One exception, and only one call site (motion Part 2, Amendment 1, RECORD -672; the owner: "Allow one
#    exception"): MotionTokens.navigationViewChromeSpec, a tween exactly as long as the map's navigation tilt
#    (NAVIGATION_VIEW_TRANSITION_MILLIS), which a spring cannot match. Excluded by its file and its exact
#    text, so a second tween in that file, or this one copied anywhere else, still fails. ADR-0002 records it.
#    (Merged 2026-10-07: -660's word-boundary pattern applied to both greps below, -672's exception kept.)
#    Typed tweens too (dispatch 2026-09-28-685, fix 5; the owner, RECORD -655: "Fix and prove they bite
#    (Recommended)"): the pattern was blind to explicit type arguments, so `tween<Float>(300)` passed (found by
#    motion Part 2). It now takes an optional <...> between the name and the bracket, with no bracket inside it.
#    Shown failing on a planted `tween<Float>(300)` and restored from a saved copy; see that dispatch's report.
TWEEN_CALL="(^|[^[:alnum:]_])tween(<[^()]*>)?\\("
hits=$(grep -rnE "$TWEEN_CALL" $UI --include=*.kt \
       | grep -vF "$UI/motion/MotionTokens.kt:" \
       || true)
allowed=$(grep -HnE "$TWEEN_CALL" $UI/motion/MotionTokens.kt \
       | grep -vF "fun <T> navigationViewChromeSpec(): FiniteAnimationSpec<T> = tween(durationMillis = NAVIGATION_VIEW_TRANSITION_MILLIS.toInt(), easing = FastOutSlowInEasing)" \
       || true)
hits=$(printf '%s\n%s' "$hits" "$allowed" | sed '/^$/d')
report "no tween( in ui/ (one allowed: navigationViewChromeSpec)" "$hits"

# 4. R1 in the design plan: a critically damped effects spring cannot overshoot on its own path,
#    so interruption is safe -- but RETARGETING to an intermediate value is not, and alpha is only
#    clamped at 0 and 1. Every animated alpha in this app targets a bound today; this keeps it
#    that way rather than trusting it. fadeIn/fadeOut naming initialAlpha/targetAlpha explicitly
#    is the only way to introduce an intermediate target without new machinery.
hits=$(grep -rnE "(initialAlpha|targetAlpha)\s*=" $UI --include=*.kt \
       | grep -vE "(initialAlpha|targetAlpha)\s*=\s*(0f|1f|0\.0f|1\.0f)" || true)
report "no effects animation retargets alpha to a non-bound value" "$hits"

echo
if [ $failed -ne 0 ]; then
  echo "FAILED. See the header for which checks are expected to fail until which step lands."
  exit 1
fi
echo "All design-token checks passed."
