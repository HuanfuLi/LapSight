# Phase 6: External GNSS and Sensor Ingestion - Context

**Gathered:** 2026-07-07
**Status:** Ready for planning
**Source:** Product-owner direction after hardware cost review

<domain>
## Phase Boundary

Phase 6 resumes as a protocol-first external GNSS compatibility preview. The
user will not buy RaceBox or other external GNSS hardware for this phase.
LapSight should still do everything practical without hardware: implement clean
protocol parsers, replay-backed providers, Android connection seams, source
provenance, and deterministic tests that make real hardware success plausible.

The phase must not claim broad hardware validation. Hardware acceptance moves to
post-release user feedback or a later validation phase when a receiver is
available.
</domain>

<decisions>
## Implementation Decisions

### D-01 Protocol-first completion
- Phase 6 completion means "protocol compatibility preview": RaceBox/NMEA
  protocol parsing, replay, and Android UX are implemented and tested without
  real receiver hardware.

### D-02 Hardware validation explicitly deferred
- No RaceBox or equivalent receiver will be purchased for this phase.
- Real-device BLE behavior, receiver firmware variation, antenna placement, and
  on-track precision remain unvalidated risks until user feedback or later
  hardware access.

### D-03 Supported protocol targets
- Primary protocol targets are:
  - RaceBox live BLE protocol for RaceBox Mini, Mini S, and Micro class devices.
  - NMEA 0183 byte streams, including NMEA over BLE/TCP/replay where available.
- Qstarz/Garmin/Dual model-specific proprietary support is out of scope unless
  it speaks standard NMEA through an accessible transport.

### D-04 Existing timing pipeline remains authoritative
- External GNSS must feed the existing `LocationSampleProvider` seam.
- `LapEngine`, `SessionController`, storage, review, ghost delta, and glasses HUD
  consume normalized `LocationSample`s and must not gain hardware-specific logic.

### D-05 Honest provenance
- Samples from external receivers use `LocationSource.ExternalGnss`.
- Sessions record enough metadata to distinguish phone GPS, simulated replay,
  external NMEA, and external RaceBox-derived samples.
- UI labels must not imply hardware was physically validated.

### D-06 Replay is the acceptance backbone
- Automated verification uses public NMEA logs, generated NMEA fixtures,
  synthetic RaceBox frames, and full timing-pipeline replay tests.
- Replay must cover valid fixes, no-fix/acquiring, stale timestamps, checksum
  failure, malformed frames, high-rate bursts, reconnect gaps, and source-rate
  calculation.

### D-07 License boundary
- Do not copy GPL or unlicensed open-source RaceBox implementation code into
  LapSight.
- Open-source RaceBox clients/emulators may be used only as compatibility
  signals during research. Parser implementation must be clean-room and test
  driven.

### D-08 Product UX scope
- The Drive page should expose a simple external GNSS source state and quality.
- Detailed diagnostic raw GPS tooling stays out of the main Drive surface.
- Settings may expose protocol/source selection and connection status.

### the agent's Discretion
- Exact package names, parser class names, fixture file formats, and Android BLE
  scan/connect implementation details are left to the implementer, provided the
  existing architecture boundaries above hold.
</decisions>

<canonical_refs>
## Canonical References

### Existing Architecture
- `.planning/PROJECT.md` - phone companion owns GPS, timing state, sessions, and
  future HUD output.
- `.planning/ROADMAP.md` - Phase 6 scope and Phase 7 source-of-truth boundary.
- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/LocationSampleProvider.kt` - provider seam.
- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/GpsProbeModels.kt` - `LocationSample`, `LocationSource.ExternalGnss`, and fix states.
- `androidApp/src/main/kotlin/com/huanfuli/lapsight/AndroidPhoneLocationProvider.kt` - platform provider composition pattern.

### Prior Validation Lessons
- `.planning/phases/05.1-mvp-field-validation-and-hardening-gate/5.1-CODE-REVIEW.md` - source provenance and replay determinism guardrails.
- `.planning/phases/05.1-mvp-field-validation-and-hardening-gate/05.1-RESEARCH.md` - replay harness and provider-seam notes.

### Phase Research
- `.planning/phases/06-external-gnss-and-sensor-ingestion/06-RESEARCH.md` - protocol and replay research.
</canonical_refs>

<specifics>
## Specific Ideas

- Add a pure `external` shared module area for parsers before Android BLE wiring.
- Add `ExternalGnssProtocol` metadata values such as `RaceBox`, `Nmea0183`, and
  `Unknown`.
- Build a replay provider that can feed decoded external samples into the same
  timing path as phone GPS.
- Store receiver/protocol/update-rate metadata in session source summaries.
- Keep RaceBox support labeled experimental/hardware-unvalidated until a real
  receiver is tested.
</specifics>

<deferred>
## Deferred Ideas

- Real RaceBox Mini/Mini S/Micro hardware validation.
- Qstarz proprietary protocol support.
- Garmin/Dual system-level Bluetooth GPS integration validation on Android/iOS.
- IMU fusion or vehicle telemetry beyond optional metadata capture.
- Claims about improved lap-time precision without recorded hardware evidence.
</deferred>

---

*Phase: 06-external-gnss-and-sensor-ingestion*
*Context gathered: 2026-07-07 via product-owner protocol-first decision*
