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
# EXPECTED STATE WHILE THE DESIGN SYSTEM LANDS. Checks 3 and 4 fail until step 4 of that plan
# (the MotionTokens rewrite onto MotionScheme) lands. Check 2 now also catches tag 05 (`Bark`
# imported directly into AvailabilityScreen.kt) -- real, pre-existing, and named out of scope for
# this pass in the design doc's own Sort section, not something step 3 (MapPalette) was going to
# touch. That is the point of a check like this -- a check written after the fact, which passes
# the moment it is introduced, never demonstrated it could fail. Run it, read which checks fail,
# and expect the list to shrink as the steps land.
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
#    next twenty look reasonable ("tag 05").
hits=$(grep -rn "^import com\.forager\.app\.ui\.theme\." app/src/main --include=*.kt \
       | grep -vE "\.(ForagerTheme|MapPalette|Spacing|ForagerShapes|ForagerTypography)$" || true)
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
