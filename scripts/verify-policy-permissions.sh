#!/usr/bin/env bash
# Guards the two ways docs/legal/privacy-policy.md can disagree with the code it describes.
#
# Google Play compares the Data safety declaration against the privacy policy the listing links
# to. A policy that names a permission the manifest does not declare is a mismatch a reviewer can
# act on, and it is the failure this script exists to catch: a draft of this policy claimed
# background location kept a recording alive while ACCESS_BACKGROUND_LOCATION appeared nowhere in
# the manifest -- avoiding that permission is the largest Play-policy hazard an app of this shape
# gets to sidestep, and the policy was quietly giving it away. The same draft named a stale
# applicationId. Both defects were mechanically detectable and neither was mechanically detected.
#
# Same shape and same reason as scripts/verify-codeowners-placeholders.sh -- it greps the real
# tree rather than asserting against a copy of it, and fails loudly.
#
# Deliberately NOT wired into .github/workflows/ci.yml, matching this repo's existing
# scripts/verify-*.sh convention (README.md) of standalone, manually-run checks.
#
# SCOPE. This checks the source-of-truth document only. The user-facing page published at
# https://zynergy-labs.com/privacy lives in a separate repository and is generated from this file.
# When a check here fails, the page needs regenerating too; passing here does not mean the
# published page agrees.
set -euo pipefail

POLICY="docs/legal/privacy-policy.md"
MANIFEST="app/src/main/AndroidManifest.xml"
GRADLE="app/build.gradle.kts"

for f in "$POLICY" "$MANIFEST" "$GRADLE"; do
  if [ ! -f "$f" ]; then
    echo "FAILED: $f not found. Run this from the repository root."
    exit 1
  fi
done

fail=0

# Permissions the policy describes in prose rather than by constant. Each entry is a deliberate
# decision that the user-facing wording covers it; adding to this list is a claim that a reader
# learns what the permission does without seeing its Android name.
#   INTERNET             -> "Internet: the requests listed above"
#   POST_NOTIFICATIONS   -> "Notifications, vibrate, foreground service"
#   VIBRATE              -> same line
#   FOREGROUND_SERVICE   -> same line
# CAMERA was in this list until 2026-09-10. The app no longer declares android.permission.CAMERA:
# capture goes through ACTION_IMAGE_CAPTURE, which the user's camera app services under its own
# permission. If the policy still lists a camera entry that is a prose question for the owner, not
# a manifest mismatch -- this script compares the manifest against permission *constants* named in
# the policy, and the policy names none in that form.
PROSE_COVERED="INTERNET POST_NOTIFICATIONS VIBRATE FOREGROUND_SERVICE"

# Only <uses-permission> declarations count, not every occurrence of the string in the file. Until
# 2026-09-10 this grepped the raw manifest text, so a *comment* naming a permission was read as
# declaring it -- removing CAMERA and explaining why in a comment made this script report CAMERA as
# still declared. The check was reading something adjacent to what it meant.
manifest_perms=$(grep -oE '<uses-permission[^>]*android:name="android\.permission\.[A-Z_]+"' "$MANIFEST" | grep -oE 'android\.permission\.[A-Z_]+' | sed 's/android\.permission\.//' | sort -u)
policy_perms=$(grep -oE '\b(ACCESS|FOREGROUND_SERVICE|POST|READ|WRITE|RECORD)_[A-Z_]+\b|\bCAMERA\b|\bINTERNET\b|\bVIBRATE\b' "$POLICY" | sort -u || true)

# ---------------------------------------------------------------------------
# 1. Every permission constant named in the policy must be declared in the manifest.
#    This is the Play-policy direction: claiming access the app does not hold.
# ---------------------------------------------------------------------------
# A constant may legitimately appear in order to be denied ("X is not declared"). Only count it
# as a claim when at least one line mentioning it is not a denial.
overclaimed=""
for p in $policy_perms; do
  if printf '%s\n' "$manifest_perms" | grep -qx "$p"; then continue; fi
  claim_lines=$(grep -n "$p" "$POLICY" | grep -viE '\b(not|never|no|without|neither)\b' || true)
  if [ -n "$claim_lines" ]; then
    overclaimed="$overclaimed $p"
  fi
done

if [ -n "$overclaimed" ]; then
  echo "FAILED (1): $POLICY names permissions the manifest does not declare:"
  for p in $overclaimed; do
    echo "  $p"
    grep -n "$p" "$POLICY" | grep -viE '\b(not|never|no|without|neither)\b' | sed 's/^/      /'
  done
  echo
  echo "  Either declare it in $MANIFEST or stop describing it in the policy."
  fail=1
else
  echo "OK (1): every permission named in the policy is declared in the manifest"
fi

# ---------------------------------------------------------------------------
# 2. Every permission the manifest declares must be accounted for in the policy,
#    either by constant or via the prose allowlist above.
# ---------------------------------------------------------------------------
undisclosed=""
for p in $manifest_perms; do
  if printf '%s\n' "$policy_perms" | grep -qx "$p"; then continue; fi
  case " $PROSE_COVERED " in *" $p "*) continue ;; esac
  undisclosed="$undisclosed $p"
done

if [ -n "$undisclosed" ]; then
  echo "FAILED (2): $MANIFEST declares permissions the policy does not account for:"
  for p in $undisclosed; do echo "  $p"; done
  echo
  echo "  Describe it in $POLICY, or add it to PROSE_COVERED in this script with the wording"
  echo "  that covers it."
  fail=1
else
  echo "OK (2): every declared permission is accounted for in the policy"
fi

# ---------------------------------------------------------------------------
# 3. Background location, in prose. The claim that broke was a sentence, not a constant,
#    so check 1 would not have caught it. If the manifest does not declare the permission,
#    every line mentioning it must be a denial.
# ---------------------------------------------------------------------------
if printf '%s\n' "$manifest_perms" | grep -qx "ACCESS_BACKGROUND_LOCATION"; then
  echo "OK (3): ACCESS_BACKGROUND_LOCATION is declared; prose check does not apply"
else
  affirmative=$(grep -in 'background location' "$POLICY" | grep -viE '\b(not|never|no|without|neither)\b' || true)
  if [ -n "$affirmative" ]; then
    echo "FAILED (3): $POLICY discusses background location as though the app has it."
    echo "$affirmative" | sed 's/^/      /'
    echo
    echo "  ACCESS_BACKGROUND_LOCATION is not declared in $MANIFEST. A recording survives"
    echo "  screen-off through FOREGROUND_SERVICE_LOCATION, not background location access."
    fail=1
  else
    echo "OK (3): no affirmative background-location claim in the policy"
  fi
fi

# ---------------------------------------------------------------------------
# 4. The package name the policy states must match the applicationId that ships.
# ---------------------------------------------------------------------------
app_id=$(grep -oE 'applicationId[[:space:]]*=[[:space:]]*"[^"]+"' "$GRADLE" | head -1 | sed 's/.*"\(.*\)"/\1/')
policy_pkg=$(grep -oE '`[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+`' "$POLICY" | tr -d '`' | grep -E '\.(app|forager)$' | head -1 || true)

if [ -z "$app_id" ]; then
  echo "FAILED (4): could not read applicationId from $GRADLE"
  fail=1
elif [ -z "$policy_pkg" ]; then
  echo "FAILED (4): could not find a package name in $POLICY"
  fail=1
elif [ "$app_id" != "$policy_pkg" ]; then
  echo "FAILED (4): package name mismatch."
  echo "      $GRADLE applicationId: $app_id"
  echo "      $POLICY says:          $policy_pkg"
  echo
  echo "  A policy naming a package the branch does not build is the same defect as one naming a"
  echo "  permission the manifest does not declare, pointed forward instead of backward."
  fail=1
else
  echo "OK (4): policy package name matches applicationId ($app_id)"
fi

echo
if [ "$fail" -ne 0 ]; then
  echo "One or more checks failed. Remember to regenerate https://zynergy-labs.com/privacy after fixing."
  exit 1
fi
echo "All checks passed."
