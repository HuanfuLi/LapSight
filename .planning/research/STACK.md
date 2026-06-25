# Research: Stack Direction

**Date:** 2026-06-25

## Recommendation

Use Kotlin Multiplatform with Compose Multiplatform for the phone companion app, with platform-native location providers hidden behind shared interfaces.

## Rationale

- Kotlin Multiplatform lets the project share business logic, data models, networking, and related code across Android and iOS while retaining native platform access.
- Google documents Kotlin Multiplatform as stable, production-ready, and officially supported for sharing Android/iOS business logic.
- Compose Multiplatform is production-ready for mobile and desktop, with Android and iOS supported for shared UI.
- LapSight's complexity is in domain logic, GPS/session processing, and low-friction dash UI. KMP keeps that logic shared while avoiding a full WebView-style abstraction.

## Proposed Project Structure

```text
LapSight/
├─ shared/
│  ├─ domain/
│  │  ├─ location/
│  │  ├─ lap/
│  │  ├─ ghost/
│  │  └─ session/
│  ├─ data/
│  └─ presentation/
├─ composeApp/
│  ├─ commonMain/
│  ├─ androidMain/
│  └─ iosMain/
├─ androidApp/ or android platform target
├─ iosApp/
└─ tools/
   └─ replay-fixtures/
```

Exact folder names can follow the current Kotlin Multiplatform wizard once implementation starts.

## Platform Services

### Android

Use Google Play services Fused Location Provider as the Android location implementation. It is the primary Android API for obtaining device location with high-level accuracy/power configuration.

### iOS

Use Core Location through `CLLocationManager` or modern Core Location APIs. The iOS implementation must expose location, timestamp, horizontal accuracy, speed, course/heading when available, and altitude when available.

## Alternatives Considered

### Flutter

Good cross-platform UI option, but less attractive here because the project needs a clean shared lap engine and strong native integration for location/sensors. It remains viable if Compose Multiplatform becomes a real-device blocker.

### React Native / Expo

Viable for app UI, but not clearly better than Flutter or KMP for realtime GPS, future BLE/GNSS, and shared domain logic.

### Capacitor / Tauri Mobile

Good for fast web-based prototypes and future glasses/web sharing, but realtime location, screen-on behavior, BLE, and background constraints may push too much work into native plugins.

### Native SwiftUI + Jetpack Compose + Rust Core

Architecturally clean and high-control. Consider if KMP/CMP creates iOS UI or build friction. Rust core via UniFFI is a later option if the lap engine benefits from language-level portability to mobile and web/WASM.

## Sources

- Kotlin Multiplatform overview: https://kotlinlang.org/multiplatform/
- Android Developers Kotlin Multiplatform guidance: https://developer.android.com/kotlin/multiplatform
- Compose Multiplatform overview: https://kotlinlang.org/compose-multiplatform/
- Mozilla UniFFI user guide: https://mozilla.github.io/uniffi-rs/
- Android FusedLocationProviderClient: https://developers.google.com/android/reference/com/google/android/gms/location/FusedLocationProviderClient
- Apple Core Location: https://developer.apple.com/documentation/corelocation

---
*Last updated: 2026-06-25*
