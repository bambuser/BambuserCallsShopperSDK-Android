# BambuserCallsShopper · Android

Native Android wrapper for Bambuser's one-to-one video-consultation web embed, written in Kotlin and Compose. Mirrors the API surface of the iOS package ([`BambuserCallsShopperSDK`](https://github.com/bambuser/BambuserCallsShopperSDK)) one-to-one so the integration story is the same.

- A `BambuserCallController` that owns embed lifecycle and Compose-observable state.
- A `BambuserCallOverlay` composable that renders the embed full-screen or as a draggable PiP mini-player.
- A two-method `BambuserCallDelegate` (event stream + error channel) so the host reacts to navigation, cart, checkout, and lifecycle events with a single `when`.
- An outbound `notify(callbackKey, info)` helper for resolving JS-side callbacks from native.
- A runnable demo app at [`app/`](./app) — open in Android Studio, fill in your org id in `MainActivity.kt`, build & run.

## Modules

- **`bambusercalls-shopper/`** — the library. Published to Maven as `com.bambuser:calls-shopper-sdk-android:X.Y.Z`.
- **`app/`** — demo app consuming the library via a remote Maven dependency `implementation(libs.calls.shopper.sdk.android)`.
- Add the Bambuser Maven repository to dependency resolution, in `settings.gradle.kts`:

```kotlin
maven {
    url = uri("https://repo.repsy.io/mvn/bambuser/shopper-sdk-android")
}
```

## Run the demo

1. Open the project root in **Android Studio Hedgehog or newer** (Gradle 8.4+, AGP 8.3+).
2. Fill in `DEMO_ORG_ID` near the top of `app/src/main/kotlin/com/bambuser/demo/MainActivity.kt`. The app shows a setup alert at launch until it's set.
3. Build & run on an Android 8.0+ (`API 26+`) device or emulator with camera/mic. On first launch the SDK requests `CAMERA` and `RECORD_AUDIO` — grant both before the embed can start a call.

Consumer Gradle snippet:

```kotlin
dependencies {
    implementation("com.bambuser:calls-shopper-sdk-android:0.3.0")
}
```

Version alias in `gradle/libs.versions.toml`:

```toml
[versions]
callsShopperSdkAndroid = "X.Y.Z"

[libraries]
calls-shopper-sdk-android = { module = "com.bambuser:calls-shopper-sdk-android", version.ref = "callsShopperSdkAndroid" }
```

## Heads up

This is an unreleased Bambuser SDK shared for evaluation only — please don't redistribute. Check with your Bambuser contact before using it in production; the public API and embed contract may still change.

## Documentation

Full integration guide, per-feature reference, and troubleshooting live on Bambuser's docs site:

**[bambuser.com/docs/video-consultation/mobile-sdk/android/installation](https://bambuser.com/docs/video-consultation/mobileSDK/android/mobile-sdk/android/installation/)**

Sections you'll find there — installation, architecture, integration, provide product data, provide search data, cart integration, customer events, picture-in-picture, and co-browse.
