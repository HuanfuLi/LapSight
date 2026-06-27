---
status: testing
phase: 04-ghost-lap-live-delta
source: [04-01-SUMMARY.md, 04-02-SUMMARY.md, 04-03-SUMMARY.md, 04-04-SUMMARY.md]
started: 2026-06-26T01:10:00Z
updated: 2026-06-26T01:10:00Z
---

## Current Test
<!-- OVERWRITE each test - shows where we are -->

number: 1
name: Pre-timing Demo Feed
expected: |
  Tap 'Start Demo Feed' on Drive screen. Wait several seconds. Verify the GPS feed continues and updates position before any timing session starts.
awaiting: user response

## Tests

### 1. Pre-timing Demo Feed
expected: Tap 'Start Demo Feed' on Drive screen. Wait several seconds. Verify the GPS feed continues and updates position before any timing session starts.
result: pass

### 2. Value-Only Live Delta
expected: Tap 'Start Timing'. (If no saved track exists, complete 1 lap to set it). Observe live delta: initially it may show `--`, then later slower/faster laps show only signed seconds (e.g., `+0.421s` or `-0.218s`). No directional words like "ahead/behind/faster/slower" appear beside it.
result: pass

### 3. Independent Metrics
expected: Confirm that Last, Best, Laps, Speed, and Accuracy metrics continue to render normally and are not erased when the live delta temporarily shows `--`.
result: pass

### 4. Same-Session New Best
expected: Keep timing running long enough to record a new best lap. Verify the reference update is silent, and the following lap compares against this new best without needing to start a new session.
result: pass

### 5. UI Hierarchy & Demo Labeling
expected: Confirm current lap remains the largest readout, live delta is the second core readout, and the `DEMO — simulated GPS` badge remains clearly visible during the session.
result: pass

### 6. Simulated Storage Isolation
expected: Tap Stop, then Save Session. Open Review tab. Verify the saved simulated artifacts remain visibly labeled as Demo/Simulated, and do not overwrite your real reference lap.
result: pass

## Summary

total: 6
passed: 6
issues: 0
pending: 0
skipped: 0

## Gaps

