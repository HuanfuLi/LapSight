# Requirements: LapSight

**Defined:** 2026-06-25
**Core Value:** The user can mount a phone, record a session, and see trustworthy live lap timing plus delta-to-best with minimal interaction while moving.

## v1 Requirements

### Platform

- [ ] **PLAT-01**: User can install and launch a native mobile app on Android.
- [ ] **PLAT-02**: User can install and launch a native mobile app on iOS.
- [ ] **PLAT-03**: User can use the live dash in portrait orientation.
- [ ] **PLAT-04**: User can use the live dash in landscape orientation.
- [ ] **PLAT-05**: User can keep the live dash visible during an active timing session.

### GPS Acquisition

- [ ] **GPS-01**: User can grant location permission and see current GPS fix status.
- [ ] **GPS-02**: User can see live speed, location accuracy, and update rate during a session.
- [ ] **GPS-03**: App records timestamped GPS samples with latitude, longitude, accuracy, speed, heading/course when available, and altitude when available.
- [x] **GPS-04**: App marks samples with quality metadata so noisy data can be filtered or diagnosed.
- [x] **GPS-05**: User can replay a recorded GPS session through the lap engine for debugging.

### Lap Engine

- [x] **LAP-01**: User can define a start/finish line using two GPS points or current position plus heading.
- [x] **LAP-02**: App detects crossing of the start/finish line from consecutive GPS samples.
- [x] **LAP-03**: App estimates crossing time by interpolation between GPS samples.
- [x] **LAP-04**: App prevents false laps using direction gating, minimum lap time, speed threshold, cooldown, and GPS accuracy filters.
- [x] **LAP-05**: User can see current lap time, last lap time, best lap time, lap count, and speed live.
- [x] **LAP-06**: Lap engine can run from synthetic and recorded replay fixtures without UI or platform services.
- [x] **LAP-07**: User can configure sector lines, see sector split timing in the live dash, and replay sector-line crossings through the lap engine.

### Ghost and Delta

- [x] **GHOST-01**: User can save a completed lap as the reference/best lap.
- [x] **GHOST-02**: App calculates current-lap delta against the reference lap by progress distance along the lap.
- [x] **GHOST-03**: User can see ahead/behind delta live without reading a dense chart.
- [x] **GHOST-04**: App stores enough reference-lap data to replay ghost comparison in later sessions.

### Session Review and Export

- [x] **SESS-01**: User can start, stop, save, and discard a timing session.
- [x] **SESS-02**: User can review a saved session with lap list, best lap, total duration, and GPS quality summary.
- [x] **SESS-03**: User can view a simple track trace for a saved session.
- [x] **SESS-04**: User can export a session as JSON.
- [x] **SESS-05**: User can export a session as GPX or another common GPS interchange format.

### Safety and UX

- [ ] **SAFE-01**: Live dash uses large typography and high contrast suitable for quick glances.
- [ ] **SAFE-02**: Live dash avoids small controls and complex navigation during movement.
- [ ] **SAFE-03**: App displays a closed-course/private-track safety note during onboarding or first session setup.
- [x] **SAFE-04**: App exposes GPS accuracy limitations clearly instead of implying pro-grade timing precision.

### Architecture and Compliance

- [ ] **ARCH-01**: Shared lap engine contains no platform UI dependencies.
- [x] **ARCH-02**: Shared lap engine has automated tests for geometry, line crossing, interpolation, filters, and replay scenarios.
- [ ] **ARCH-03**: App documents all third-party code and licenses before direct reuse.
- [ ] **ARCH-04**: GPL-licensed projects may be studied as references but not copied unless the project explicitly adopts a compatible license.

## v2 Requirements

### External Sensors

- **EXT-01**: User can connect an external GNSS receiver over BLE, Bluetooth serial, Wi-Fi, or TCP/NMEA.
- **EXT-02**: App can prefer external GNSS over phone GPS when connected.
- **EXT-03**: App can ingest basic IMU or vehicle telemetry when available.

### Meta Glasses Bridge

- **MR-01**: Phone app exposes live timing state to a Meta Display Glasses web app.
- **MR-02**: Glasses HUD can show current lap, last lap, best lap, speed, and delta.
- **MR-03**: Glasses HUD remains passive and does not require interaction while driving/riding.

### Advanced Review

- **REV-01**: User can compare two laps on a map.
- **REV-02**: User can see speed heatmap, braking/acceleration zones, and sector analysis.
- **REV-03**: User can import DOVEP/DOVEX-compatible files if compatibility is useful.

## Out of Scope

| Feature | Reason |
|---------|--------|
| Public-road competition features | Safety and legal risk; closed-course positioning only. |
| Cloud sync and accounts | Not needed to validate core lap timing. |
| Social leaderboards | Increases moderation and abuse scope without proving core value. |
| Native watch app | Useful later; phone companion is the first product. |
| Glasses app in v1 | The phone app must exist first as the data owner. |
| Copying GPL code | Avoid license contamination unless the project intentionally adopts GPL. |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| PLAT-01 | Phase 1 | Pending |
| PLAT-02 | Phase 1 | Pending |
| PLAT-03 | Phase 1 | Pending |
| PLAT-04 | Phase 1 | Pending |
| PLAT-05 | Phase 1 | Pending |
| GPS-01 | Phase 1 | Pending |
| GPS-02 | Phase 1 | Pending |
| GPS-03 | Phase 1 | Pending |
| GPS-04 | Phase 1 | Complete |
| GPS-05 | Phase 2 | Complete |
| LAP-01 | Phase 2 | Complete |
| LAP-02 | Phase 2 | Complete |
| LAP-03 | Phase 2 | Complete |
| LAP-04 | Phase 2 | Complete |
| LAP-05 | Phase 2 | Complete |
| LAP-06 | Phase 2 | Complete |
| LAP-07 | Phase 2 | Complete |
| SESS-01 | Phase 3 | Complete |
| SESS-02 | Phase 3 | Complete |
| SESS-03 | Phase 3 | Complete |
| SESS-04 | Phase 3 | Complete |
| SESS-05 | Phase 3 | Complete |
| GHOST-01 | Phase 4 | Complete |
| GHOST-02 | Phase 4 | Complete |
| GHOST-03 | Phase 4 | Complete |
| GHOST-04 | Phase 4 | Complete |
| SAFE-01 | Phase 1 | Pending |
| SAFE-02 | Phase 1 | Pending |
| SAFE-03 | Phase 1 | Pending |
| SAFE-04 | Phase 2 | Complete |
| ARCH-01 | Phase 1 | Pending |
| ARCH-02 | Phase 2 | Complete |
| ARCH-03 | Phase 1 | Pending |
| ARCH-04 | Phase 1 | Pending |

**Coverage:**
- v1 requirements: 34 total
- Mapped to phases: 34
- Unmapped: 0

## Definition of Done

- Android and iOS builds both run locally.
- Lap engine tests pass from deterministic fixtures.
- A real or simulated session can produce current, last, and best lap timing.
- A saved reference lap can produce live delta-to-best.
- Session data can be exported and replayed.
- Safety and accuracy limitations are visible in the product.

---
*Requirements defined: 2026-06-25*
*Last updated: 2026-06-25 after initial definition*
