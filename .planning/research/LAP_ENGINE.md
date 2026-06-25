# Research: Lap Engine Direction

**Date:** 2026-06-25

## Recommendation

Build a clean-room lap engine in shared Kotlin. Treat existing open-source lap timers as design references and test inspiration, not as code to copy.

## Core Algorithm

1. Normalize each GPS sample into a shared `LocationSample` model:
   - timestamp
   - latitude/longitude
   - optional altitude
   - horizontal accuracy
   - optional speed
   - optional heading/course
   - sample source

2. Convert recent samples into local meter coordinates near the session origin.

3. Define start/finish as a line segment:
   - either two marked GPS points
   - or current GPS point plus heading, generating a perpendicular line of configurable width

4. For every pair of consecutive samples:
   - reject samples with poor accuracy or impossible jumps
   - reject very low speed when movement is required
   - test whether movement segment intersects the start/finish segment/corridor
   - apply direction gate if configured
   - enforce cooldown and minimum lap time
   - interpolate crossing timestamp from the movement segment

5. Produce lap events:
   - lap number
   - start timestamp
   - end timestamp
   - duration
   - distance estimate
   - crossing point
   - quality flags

## Ghost / Delta Algorithm

Use progress-distance matching first:

1. Build a reference lap polyline with cumulative distance at each sample.
2. During current lap, estimate cumulative distance since the last crossing.
3. Find the reference lap timestamp at equivalent cumulative distance by interpolation.
4. Delta = current elapsed lap time - reference elapsed lap time.

This is more stable for v1 than nearest-coordinate matching, especially with phone GPS noise and slightly different driving/riding lines.

## Required Tests

- GPS-to-local-coordinate conversion sanity.
- Segment intersection positive/negative cases.
- Crossing timestamp interpolation.
- Direction gating.
- Cooldown and minimum lap time.
- Accuracy and speed filtering.
- Synthetic oval/loop replay.
- Noisy GPS replay with jitter near the line.
- Missed sample / low-frequency GPS replay.
- Reference-lap delta interpolation.

## Open-Source References

### DovesLapTimer

Strong algorithm reference. It accepts GPS fixes, supports start/finish and sector lines, interpolates crossings, tracks current/last/best lap, supports direction/course detection, and includes replay regression testing. It is GPL-3.0, so do not copy code unless the project explicitly adopts a compatible license.

### DovesDataViewer

Strong reference for post-session analytics: automatic track/course detection, direction detection, waypoint mode, start/finish lap detection, sectors, reference lap overlay, pace delta, multiple data formats, and offline PWA behavior. GPL-3.0.

### Blackbox

Useful MIT-licensed reference for embedded lap timing, including line detection and delta-to-best concepts. Still review exact license and source boundaries before direct reuse.

### PUTM_VP_LAPTIMER

Apache-2.0 ROS2 package with GPS lap time and delta-to-reference concepts. Useful for comparison, less directly portable to mobile.

## Sources

- DovesLapTimer: https://github.com/TheAngryRaven/DovesLapTimer
- DovesDataViewer: https://github.com/TheAngryRaven/DovesDataViewer
- Blackbox lap timer docs: https://github.com/jctoledo/blackbox/blob/main/docs/LAP_TIMER.md
- PUTM_VP_LAPTIMER: https://github.com/PUT-Motorsport/PUTM_VP_LAPTIMER

---
*Last updated: 2026-06-25*
