# Walking Skeleton: LapSight Phase 1

**Phase:** 01 - Mobile Walking Skeleton + GPS Probe
**Created:** 2026-06-25

## Architectural Backbone

LapSight begins as a Kotlin Multiplatform mobile app with shared Compose UI.

```text
LapSight
├─ androidApp/   Android packaging and activity entry point
├─ iosApp/       Xcode project and iOS entry point
├─ shared/       KMP shared models, state, and Compose UI
└─ gradle/       Version catalog and wrapper
```

## Stack Decisions

| Area | Choice | Reason |
|---|---|---|
| Cross-platform core | Kotlin Multiplatform | Shared lap engine and state logic must survive Android/iOS/glasses expansion. |
| UI | Compose Multiplatform | The dash UI is simple and benefits from a shared implementation. |
| Android module split | `androidApp` + `shared` | Required by AGP 9-era KMP guidance. |
| Location provider | Interface-backed provider, simulator first | Phase 1 validates UI/state while leaving real providers cleanly pluggable. |
| Storage | None in Phase 1 | Session persistence starts in Phase 3. |

## First End-to-End Slice

User launches app → sees LapSight dash → starts GPS probe → simulated samples update live → user can stop/reset → app communicates safety and accuracy limits.

## Contracts for Later Phases

- Lap engine must consume shared `LocationSample`-style data, not platform-specific location objects.
- Real providers must feed the same `GpsProbeState`.
- Session storage should serialize the same sample model.
- Future glasses bridge should consume summarized state from the phone app, not duplicate platform GPS logic.

## Verification Expectations

- Shared module builds/tests on the local machine.
- Android debug build is attempted.
- iOS project is structurally present but requires macOS/Xcode for runtime verification.

---

*Skeleton recorded: 2026-06-25*
