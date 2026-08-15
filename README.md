# Forager

## Environment setup

### Android SDK

Run `scripts/setup-android-sdk.sh` to install the Android command-line
tools and the SDK packages this project builds against:

- `platform-tools`
- `platforms;android-34`
- `build-tools;34.0.0`

The script installs to `$ANDROID_SDK_ROOT` (defaults to `/opt/android-sdk`)
and accepts all SDK licenses non-interactively. After installing, export:

```sh
export ANDROID_HOME=/opt/android-sdk
export ANDROID_SDK_ROOT=/opt/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools
```

### iNaturalist API access

This app looks up species data via the [iNaturalist REST API](https://api.inaturalist.org/v1/docs/).
Run `scripts/verify-inaturalist-access.sh` to confirm the environment can
reach `www.inaturalist.org` and `api.inaturalist.org`.
