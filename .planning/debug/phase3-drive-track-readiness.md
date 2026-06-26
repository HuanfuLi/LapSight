---
status: resolved
trigger: "Phase 3 ADB UAT found Drive loses saved Track timing readiness after Review navigation/cold restart"
created: 2026-06-25T22:28:52-04:00
updated: 2026-06-25T22:39:02-04:00
---

# Phase 3 debug: Drive loses saved track timing readiness

## Symptom

ADB UAT saved a simulated track with confirmed start/finish. Review listed the saved Track and TrackMarking, and Android share handoff worked for Track JSON export.

After navigating back to Drive and after force-stop/relaunch, Drive showed `Mark New Track` and `Mark a track first. Timing needs a saved start/finish line.` instead of `Start Timing`.

## Evidence

- Device: `bf67772`, package `com.huanfuli.lapsight`.
- App-private storage still contained:
  - `files/lapsight/markings/mark-1782440591781.json`
  - `files/lapsight/tracks/track-1782440616852.json`
  - `files/lapsight/index.json`
- `index.json` contained a `Track` row for `track-1782440616852`.
- `tracks/track-1782440616852.json` contained a non-null `startFinish`.
- ReviewScreen showed the saved Track because it reads `sessionStore.readIndex()`.

## Root cause

`DriveMarkingController` treats saved tracks as in-memory state:

- `private val savedTracks: MutableList<Track> = mutableListOf()`
- `snapshot()` computes timing readiness from `savedTracks.any { it.startFinish != null }`
- `saveTrack()` appends the new track to `savedTracks`

There is no hydration path from `LocalSessionStore.readIndex()`/`loadTrack()` on Drive startup or tab re-entry.

Separately, `DriveScreen` starts timing with a placeholder id:

- `sessionController.startTiming(trackId = "track-dummy-$savedTrack")`

That id does not match persisted track ids such as `track-1782440616852`, so even the immediate post-save button path cannot reliably enter the formal timing surface.

## Fix direction

Add a persisted timing-ready track selector to the Drive domain/UI boundary:

1. Extend `DriveMarkingSnapshot` with latest timing-ready track id/name/count.
2. Add a `refreshSavedTracks()` or constructor-time hydration path that reads Track rows from `LocalSessionStore.readIndex()` and loads valid Track payloads.
3. Call refresh on Drive composition/tab entry and after `saveTrack()`.
4. Replace `track-dummy-N` with the selected persisted track id.
5. Surface `StartTimingResult.Blocked` visibly if the controller refuses to start.
6. Add common tests for hydration and correct track-id selection before rerunning ADB UAT.

## Resolution

Implemented:

- `DriveMarkingController.refreshSavedTracks()` hydrates saved Track rows from `LocalSessionStore.readIndex()` + `loadTrack()`.
- `DriveMarkingSnapshot` exposes `timingReadyTrackId` and `timingReadyTrackName`.
- Timing readiness selects the newest saved Track with a confirmed `startFinish`.
- `DriveScreen` calls `SessionController.startTiming()` with the real persisted track id instead of `track-dummy-N`.
- If Start Timing is pressed on a cold Drive screen and the demo provider is stopped, the provider starts after `SessionController` accepts the track, so samples flow into the timing draft.
- A visible blocked message is retained if `SessionController` rejects the track.

Verification:

- `.\gradlew.bat :shared:check` passed.
- `.\gradlew.bat :androidApp:assembleDebug` passed.
- ADB overlay install without clearing data preserved `track-1782440616852`.
- ADB cold launch showed `Start Timing` instead of `Mark New Track`.
- ADB tapping `Start Timing` entered `Timing`, showed `DEMO — simulated GPS`, and sample count advanced.
- ADB `Stop` -> `Save Session` created a `Session · Demo` Review row.
- ADB opened the saved Session detail and confirmed duration/sample/accuracy metadata, Trace, Export JSON, and Export GPX.
- ADB tapped Session Export JSON and confirmed Android share sheet handoff.
- ADB force-stop during timing surfaced `Unfinished session found` with `Resume`, `Save`, and `Discard`.
- ADB discard cleanup removed the active draft; relaunch returned to Drive without recovery prompt.

Resolved by:

- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ui/DriveMarkingController.kt`
- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ui/DriveScreen.kt`
- `shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/ui/DriveMarkingControllerTest.kt`
