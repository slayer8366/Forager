#!/usr/bin/env bash
# Installs the Android command-line tools and core SDK packages needed to
# build this project (platform-tools, platform 34, build-tools 34.0.0).
set -euo pipefail

ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-/opt/android-sdk}"
CMDLINE_TOOLS_VERSION="11076708"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip"

mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools"

if [ ! -x "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" ]; then
  tmp_zip="$(mktemp -d)/commandlinetools.zip"
  curl -sS -o "$tmp_zip" "$CMDLINE_TOOLS_URL"
  tmp_extract="$(mktemp -d)"
  unzip -q "$tmp_zip" -d "$tmp_extract"
  mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools/latest"
  mv "$tmp_extract"/cmdline-tools/* "$ANDROID_SDK_ROOT/cmdline-tools/latest/"
  rm -rf "$tmp_extract" "$tmp_zip"
fi

export ANDROID_SDK_ROOT
export ANDROID_HOME="$ANDROID_SDK_ROOT"
export PATH="$PATH:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools"

# Accept the SDK licenses non-interactively.
#
# The answers are fed in as a here-string, deliberately NOT as `yes | sdkmanager`.
# `yes` is an infinite writer: sdkmanager reads the handful of answers it needs
# and exits, `yes` keeps writing into the now-closed pipe, takes SIGPIPE and dies
# with 141. `set -o pipefail` then reports the pipeline as 141 -- the rightmost
# non-zero status -- and `set -e` aborts the script, even though sdkmanager
# itself succeeded. It is timing-dependent, and *more* likely on a re-run where
# the licenses are already accepted and sdkmanager exits almost immediately.
#
# A here-string has no writer process at all (bash hands sdkmanager a pre-filled
# pipe, or a temp file when the content exceeds the pipe buffer), so there is
# nothing left alive to receive SIGPIPE. And because this is not a pipeline, the
# exit status is sdkmanager's own -- a genuine failure here still aborts loudly.
#
# 100 is just "more answers than there are licenses" (there are currently 7);
# it is a bound on the license count, not a buffer-size trick, so it is safe to
# raise. Do not replace this with `yes` or any other unbounded writer.
sdkmanager --sdk_root="$ANDROID_SDK_ROOT" --licenses > /dev/null <<< "$(printf 'y\n%.0s' {1..100})"
sdkmanager --sdk_root="$ANDROID_SDK_ROOT" \
  "platform-tools" \
  "platforms;android-34" \
  "build-tools;34.0.0"

echo "Android SDK installed at $ANDROID_SDK_ROOT"
echo "Add this to your shell profile:"
echo "  export ANDROID_HOME=$ANDROID_SDK_ROOT"
echo "  export ANDROID_SDK_ROOT=$ANDROID_SDK_ROOT"
echo "  export PATH=\$PATH:\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools"
