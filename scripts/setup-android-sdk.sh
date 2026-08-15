#!/usr/bin/env bash
# Installs the Android command-line tools and core SDK packages needed to
# build this project (platform-tools, platform 37.1, build-tools 37.0.0).
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

yes | sdkmanager --sdk_root="$ANDROID_SDK_ROOT" --licenses > /dev/null
sdkmanager --sdk_root="$ANDROID_SDK_ROOT" \
  "platform-tools" \
  "platforms;android-37.1" \
  "build-tools;37.0.0"

echo "Android SDK installed at $ANDROID_SDK_ROOT"
echo "Add this to your shell profile:"
echo "  export ANDROID_HOME=$ANDROID_SDK_ROOT"
echo "  export ANDROID_SDK_ROOT=$ANDROID_SDK_ROOT"
echo "  export PATH=\$PATH:\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools"
