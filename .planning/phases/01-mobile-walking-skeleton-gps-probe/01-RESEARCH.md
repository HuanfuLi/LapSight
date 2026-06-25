# Phase 1 Research: Mobile Walking Skeleton + GPS Probe

**Date:** 2026-06-25

## Findings

1. The current JetBrains KMP wizard mobile-shared template already uses the AGP 9-compatible structure: a separate `androidApp` module, an `iosApp` Xcode project, and a `shared` KMP module.
2. Android's KMP guidance says the new `com.android.kotlin.multiplatform.library` plugin is the officially supported Android target plugin for KMP library modules, and there is no direct replacement for applying `com.android.application` directly to a KMP module.
3. Kotlin's AGP 9 migration docs state that KMP with AGP 9 requires Android app entry point and shared code to be in separate modules.
4. Compose Multiplatform 1.11.1 supports Android and iOS and is compatible with latest Kotlin releases; the latest Compose Multiplatform is intended to align with the latest Kotlin.
5. Android Gradle Plugin 9.2 requires Gradle 9.4.1 and JDK 17+. The checked official KMP wizard template currently uses AGP 9.0.1, Kotlin 2.4.0, and Compose Multiplatform 1.11.1. Use the template first; avoid an immediate AGP bump unless the build proves stable.

## Recommended Implementation

- Import the current official KMP wizard `mobile-shared` template into this repository.
- Rename IDs/package names to `com.huanfuli.lapsight`.
- Keep the template's separate `androidApp`, `iosApp`, and `shared` structure.
- Replace the sample UI with LapSight's GPS probe dash.
- Add common models and a simulator-backed probe controller in `shared/commonMain`.
- Keep real Android/iOS location providers as interface-backed stubs in Phase 1 unless the local SDK/toolchain supports full device verification.

## Risks

- Windows cannot build/run the iOS Xcode app; iOS verification will be structural until tested on macOS/Xcode.
- Android SDK may be missing locally; Gradle task verification may stop at environment setup.
- AGP 9.x and KMP plugin behavior is actively moving; starting from the official wizard template is lower-risk than hand-writing config.
- Real GPS behavior requires a physical device; simulator data is only a UI/state proof.

## Sources

- Android KMP plugin docs: https://developer.android.com/kotlin/multiplatform/plugin
- Kotlin AGP 9 migration docs: https://kotlinlang.org/docs/multiplatform/multiplatform-project-agp-9-migration.html
- Compose Multiplatform create app docs: https://kotlinlang.org/docs/multiplatform/compose-multiplatform-create-first-app.html
- Compose Multiplatform compatibility docs: https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html
- KMP wizard template: https://github.com/Kotlin/kmp-wizard

---

*Research complete: 2026-06-25*
