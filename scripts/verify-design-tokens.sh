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
# EXPECTED STATE WHILE THE DESIGN SYSTEM LANDS. Checks 3 and 4 passed once step 4 of that plan
# (the MotionTokens rewrite onto MotionScheme, docs/adr/0002-motion-scheme-adoption.md) landed.
# Check 2 still fails, but not on MapPalette -- that landed as the hand-authored day/night palette
# (R9 overriding the original "derive from ColorScheme" plan), and its own import is excluded
# below. The remaining failures are Spacing imported broadly by design (step 3), plus one
# pre-existing, separately tracked defect ("tag 05": Bark imported into AvailabilityScreen.kt).
# That is the point -- a check written after the fact, which passes the moment it is introduced,
# never demonstrated it could fail. Run it, read which checks fail, and expect the list to shrink
# as the steps land.
set -uo pipefail

cd "$(dirname "$0")/.."

UI=app/src/main/java/com/forager/app/ui
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
#    doc comment for why it exists), not a colour or a palette constant.
hits=$(grep -rn "^import com\.forager\.app\.ui\.theme\." app/src/main --include=*.kt \
       | grep -vE "\.(ForagerTheme|LocalForagerDarkTheme|MapPalette|MapIconBarAccent)$" || true)
report "no palette constant imported outside the theme package" "$hits"

# 3. Motion comes from MaterialTheme.motionScheme. A tween at a call site is the tween-only rule
#    growing back, one animation at a time (ADR-0002).
hits=$(grep -rn "tween(" $UI --include=*.kt || true)
report "no tween( in ui/" "$hits"

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
