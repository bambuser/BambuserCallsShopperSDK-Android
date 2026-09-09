# Changelog

All notable changes to this project are documented here. The format
follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the
project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

<!--
This is the **public**, business-facing changelog that ships to the
GitHub mirror. Keep entries short, outcome-focused, and free of
Bambuser-internal terminology. The full technical changelog lives in
the internal CHANGELOG.md and stays on GitLab.
-->

## [Unreleased]

## [1.1.0] - 2026-09-09

### Added

- **Shopper analytics.** New `BambuserShopperSDK` entry point with a single `suspend fun track(event, data)` method for reporting shopper events (purchase, add-to-cart, refund, and more). Events attribute to the shopper's most recent Video Consultation call — including purchases made hours or days after the call ends.
- Purchase-tracking demo flow in the sample app (Add to Cart + Complete purchase).

### Changed

- Enter Picture-in-Picture (`enterPiP`) now switches the widget itself into its mini video player. Previously the widget kept rendering its cart tool inside the shrunken frame.
- Demo shrinks to a floating mini-player on the widget's checkout event so the shopper can complete purchase in the app underneath.

### Fixed

- Mini-player could be dragged off the edge of the screen and disappear. It now stays inside the visible area, including after rotation.
- Mini-player drag moved opposite to the finger in right-to-left languages (Arabic, Hebrew). Fixed.
- Widget-initiated Picture-in-Picture transitions (e.g. agent-driven minimize) were being reverted by the SDK. Fixed.


## [1.0.0] - 2026-09-02

First stable release.

### Added

- Full integration path for embedding Bambuser Video Consultation calls in an Android app.
- Reference demo app with product catalog and cart flows.
- Comprehensive API documentation.

### Changed

- Public API surface stabilised for 1.x.
- Improved performance and reliability of the video stream.
- Clearer, more actionable error messages surfaced to host apps.

### Fixed

- Session persistence across app restarts.
- Call-connection stability.
