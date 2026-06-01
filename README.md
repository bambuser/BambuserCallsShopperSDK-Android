# BambuserCallsShopper · Android

Native Android wrapper for Bambuser's one-to-one video-consultation web embed, written in Kotlin and Compose. Mirrors the API surface of the iOS package ([`BambuserCallsShopperSDK`](https://github.com/bambuser/BambuserCallsShopperSDK)) one-to-one so the integration story is the same.

- A `BambuserCallController` that owns embed lifecycle and Compose-observable state.
- A `BambuserCallOverlay` composable that renders the embed full-screen or as a draggable PiP mini-player.
- A two-method `BambuserCallDelegate` (event stream + error channel) so the host reacts to navigation, cart, checkout, and lifecycle events with a single `when`.
- An outbound `notify(callbackKey, info)` helper for resolving JS-side callbacks from native.
- A runnable demo app at [`app/`](./app) — open in Android Studio, fill in your org id + embed URL in `MainActivity.kt`, build & run.

## Modules

- **`bambusercalls-shopper/`** — the library. Will be published to Maven as `com.bambuser:bambusercalls-shopper:1.0.0`.
- **`app/`** — demo app consuming the library via a local `project(":bambusercalls-shopper")` dependency. Switch to the published Maven coordinate once it's live.

## Run the demo

1. Open the project root (`BambuserCallShopperSDKAndroid/`) in **Android Studio Hedgehog or newer** (Gradle 8.4+, AGP 8.3+).
2. Fill in `DEMO_ORG_ID` and `DEMO_EMBED_URL` near the top of `app/src/main/kotlin/com/bambuser/demo/MainActivity.kt`. The app shows a setup alert at launch and on each "Talk to expert" tap until both are set.
3. Build & run on an Android 7.0+ (`API 24+`) device or emulator with camera/mic. On first launch the app requests `CAMERA` and `RECORD_AUDIO` permissions — those need to be granted before the embed can start a call.

## Publishing to Maven

The library module is wired with `maven-publish`. Local install for development:

```bash
./gradlew :bambusercalls-shopper:publishToMavenLocal
```

To publish to a remote repository (Maven Central, GitHub Packages, or an internal Nexus), add a `repositories { … }` block to the `publishing` section in `bambusercalls-shopper/build.gradle.kts` with the credentials your CI uses.

Coordinates:

| Field | Value |
|---|---|
| `groupId` | `com.bambuser` |
| `artifactId` | `bambusercalls-shopper` |
| `version` | `1.0.0` |

Consumer Gradle snippet once published:

```kotlin
dependencies {
    implementation("com.bambuser:bambusercalls-shopper:1.0.0")
}
```

## Heads up

This is an unreleased Bambuser SDK shared for evaluation only — please don't redistribute. Check with your Bambuser contact before using it in production; the public API and embed contract may still change.
