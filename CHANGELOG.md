# Changelog

All notable changes to this project are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.3.0] - 2026-09-02

### Added
- Optional variation-level `.url()` on the product factory spec. Product-level `.url()` remains the required source of truth for the agent's "View page" link and co-browse events; variation-level is a nice-to-have when a specific colour/size has its own landing page.
- Demo: SwiftUI + UIKit + Compose demos now sync the shopper's local cart to the widget on `Connected` via `notifyCustomerEvent("ADDED_TO_CART", …)`, one item per line, carrying the full `sku / name / price / currency / quantity` payload the embed requires.

### Changed
- `EmbedEventRouter`: `call-starting` and `call-started` now map to `CallState.Connected` (previously `Connecting`), matching the moment the agent-side cart tool becomes ready to receive customer events.
- Demo: `BambuserCallBridge.notifyCustomerEvent` payload now carries `name`, `price`, and `currency` — the embed's `notifyCustomerEvent` public API (added in [MR 4697](https://gitlab.bambuser.com/livecommerce/one-to-one/liveshopping-one-to-one/-/merge_requests/4697)) rejects events missing `name`.
- Demo: `DemoProduct.url` added and threaded through both `provideProductData` and `provideSearchData` factory specs.
- Docs: unhide Android section from `sidebarsOneToOne.js`; refresh Repsy coordinates and consumer-side `libs.versions.toml` example; document the mandatory product-level `.url()` and optional variation-level `.url()`.

### Fixed
- Release pipeline: `Scripts/prepare-release.sh` now swaps the demo's project dep to `libs.calls.shopper.sdk.android`, bumps `callsShopperSdkAndroid` in `gradle/libs.versions.toml`, and injects the public Repsy repo into `settings.gradle.kts` on the staging copy — GitHub consumers no longer need to add the repo themselves.
- Release pipeline: `Scripts/validate-release.sh` fails the build when `MainActivity.DEMO_ORG_ID` is non-empty, preventing accidental leakage of internal org ids into the public GitHub mirror.

## [0.2.1] - 2026-08-28

### Fixed
- Release pipeline plumbing so the demo-app dependency swap runs cleanly on the staging copy only. Public GitHub mirror pulls the SDK from Repsy at the pinned version; internal develop keeps building against the local `:bambusercalls-shopper` module.
- Public repo cleanup: SDK source (`bambusercalls-shopper/`) is now excluded from the GitHub push and served exclusively via the Repsy Maven artifact.

## [0.2.0] - 2026-08-28

### Added
- SDK-side permission ownership: `AndroidManifest.xml` declares `INTERNET`, `CAMERA`, `RECORD_AUDIO`, and `MODIFY_AUDIO_SETTINGS` so manifest merger folds them into the host app.
- Runtime permission prompt inside `BambuserCallOverlay`: `CAMERA` + `RECORD_AUDIO` are requested via Compose's `rememberLauncherForActivityResult` the first time the overlay becomes visible. WebView is withheld until granted; denial surfaces as `BambuserCallError.PermissionsDenied` via `BambuserCallDelegate.onError`.
- Edge-to-edge safety: demo overlay applies `Modifier.safeDrawingPadding()` so the widget's top-bar controls render below the status bar on Android 15+ devices.

### Changed
- `BambuserCallController` teardown lifecycle: `AndroidView.onRelease` now performs the full WebView shutdown sequence (`stopLoading` → `loadUrl("about:blank")` → `clearHistory` → detach from parent → null delegates + JS interface → `destroy()`), and `controller.detach()` clears the internal ref so post-tear-down `evaluate()` calls no-op safely. Prevents the WebView + Chromium renderer process from outliving the overlay.

### Fixed
- Repository hygiene: `.gitignore` and `.git/index` now consistently exclude `build/`, `.gradle/`, `.idea/`, and other Gradle artefacts. Purged 900+ tracked build artefacts from earlier commits.

## [0.1.0] - 2026-08-28

Initial public release. Native Android wrapper for Bambuser's one-to-one video-consultation web embed, written in Kotlin and Compose.

### Added
- **`BambuserCallController`** — session lifecycle, JS invocation, and Compose-observable state (`isVisible`, `isPiP`, `pipPresentation`, `callState`).
- **`BambuserCallOverlay`** — Compose surface that renders the widget full-screen or as a draggable Picture-in-Picture mini-player, with configurable frame size, drag gesture, and full-screen ↔ PiP animation.
- **`BambuserCallHandlers`** — data-source closures for `provideProductData`, `provideSearchData`, `shouldAddToCart`, `shouldUpdateCart`. Only the handlers you set are subscribed on the JS side.
- **`BambuserCallDelegate`** — fire-and-forget event stream: `NavigateTo`, `Checkout`, `ChatRequested`, `CallStateChanged`, `PresentationChanged`, `QueueOpened/Closed`, `AgentsOnlineChanged`, `WaitingTimeChanged`, `TrackingEvent`, `Other`, plus an `onError` channel.
- **`BambuserCallConfiguration`** — full config surface: `orgId`, `environment` (US / EU / stageUS / custom), `queue`, `triggers`, `locale`, `initialCustomerData`, `initialTrackingTags`, dropdown/bookings knobs, `merchantBaseUrl`, `disableCoBrowsing`, `themeId`, `autoExpandOnConnect`, `floatingPipSize`, `minimizedPipSize`, PiP margin/reserve, transition duration.
- **Outbound host → widget API**: `show`, `close`, `expand`, `enterPiP`, `notifyProductNavigation`, `notifyCustomerEvent`, `updateData`, `setTrackingTags`, `updateElement`, `floatAbove`, `destroy`, and a generic `notify(callbackKey, info)` escape hatch.
- **`BambuserFactorySpec` DSL** — Kotlin equivalent of the JS factory-chain builder used by `provideProductData` and `provideSearchData` responses.
- **Compose Android WebView bridge** — single `WebView` mounted via `AndroidView`, with a JS `postMessage` bridge (`window.__bambuserAndroidBridge`), auto-granted camera/mic permissions on the WebView side, WebChromeClient popup handling, and Chromium debugging enabled in debug builds.
- **Iframe force-fill shim** — document-start `MutationObserver` that force-sizes the widget's iframe to the WebView dimensions in pixels, working around Chromium/Android WebView's `100vh` layout timing bug that would otherwise render the widget at 0×0 on connect.
- **Maven publishing** — `com.bambuser:calls-shopper-sdk-android` on Bambuser Repsy Maven, plus internal-only GitLab `-dev.N` builds.
- **Demo app** (`app/`) — Compose showcase with a mock product catalog, product-list / PDP / cart flow, `BambuserCallBridge` reference wiring, and permission preboarding helper.

### Requirements
- **Min SDK**: 26 (Android 8.0)
- **Compile / Target SDK**: 36
- **Kotlin**: 2.0.21+
- **AGP**: 8.9.2+
- **Gradle**: 8.11.1+
- **Java**: 17
- Compose (host screen must be composable)
