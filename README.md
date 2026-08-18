# ScamAI Android

Hybrid Android client for ScamAI.

## Modes

### Offline
Runs a lightweight detector directly on the phone. It does not require a network connection.

### Online
Sends text to the ScamAI production API for the full Stage 1-6 pipeline.

## Build

The repository includes GitHub Actions. Push it to GitHub and the workflow
will build `app-debug.apk` as an artifact.

For local builds, use Android Studio or a machine with Android SDK/Gradle.

## Configure the production API

Edit:

`app/src/main/java/com/scamai/app/MainActivity.kt`

and replace:

`https://YOUR-SCAMAI-DOMAIN.example`

with your real HTTPS API endpoint.

For a real release, move the endpoint into `BuildConfig` or a secure build
configuration and add your production API authentication strategy.

## Current scope

The first APK version provides:
- message scanning
- URL text scanning
- offline risk detection
- online API switching
- result explanations

Screenshot capture/upload UI should be connected to `/v1/analyze/screenshot`
in the next Android iteration.
