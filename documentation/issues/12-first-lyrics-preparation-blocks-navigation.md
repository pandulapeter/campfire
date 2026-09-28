# Move the first lyrics preparation out of composition

**Kind:** performance / UI speed · **Severity:** medium (P2) · **Platforms:** all
**Reviewed at:** `b8cc0bc2`
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongLyrics.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDetailsScreen.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/SongEditorScreen.kt`.

## Problem

`rememberSongLyricsModel` calls `prepare(inputs)` inside `remember` (1541–1545). Only later input changes use
`withContext(Dispatchers.Default)`. The editor preview has the same pattern (782–788). The first full parse,
notation/transposition, and render-section construction therefore happen synchronously on the composition
thread when opening a song, composing another pager page, or creating the editor preview.

This is a separate cost from issue 06's all-at-once text measurement. Truncating the resulting render model
after a full parse will not remove this initial navigation stall. The existing plan and some code comments
describe preparation as background work, which is only true after the first render.

## Evidence / reproduction

A temporary desktop test timed the actual `ChordProParser.parse` plus `prepareSongLyrics` on generated chorded
lines, without Compose layout, transposition, or resource loading:

| Input | First iteration | Following iterations |
|---|---:|---:|
| 3,000 lines / 159,015 characters | 42.8 ms | 11.3 / 6.3 ms |
| 60,000 lines / 3,180,015 characters | 77.1 ms | 43.4 / 36.7 ms |

These are local JVM test timings, not release-device frame measurements. They establish meaningful synchronous
work before layout even starts; no claim is made about ordinary short songs or exact phone latency.

## Fix

1. Prepare the first model in a keyed coroutine too. Show a lightweight delayed loading state until it arrives;
   preserve the old model during subsequent updates where appropriate.
2. Key results by all inputs and discard a result for a page/song that is no longer current. Reuse prefetched
   models only with a bounded cache and complete input keys.
3. Apply issue 06's size/render policy on this path and the editor preview. Moving work to Default alone does
   not move CPU work off the browser's event loop; for Wasm use bounded work and cooperative chunks or a worker
   if necessary. Do not advertise dispatcher switching alone as a web responsiveness fix.

## Verification

- Confirm the initial preparation callback does not run during composition on native/JVM targets.
- Change inputs during preparation; an older result must not replace the new song.
- Profile cold song opening, pager movement and Edit/Preview switching on a low-end phone with a long file.
- Test browser input responsiveness separately. Keep size-limit feedback and the editor's unsaved text intact.

## Conflicts

Implement with 06. That issue bounds measurement/memory; this one removes synchronous preparation from native UI
transitions. Both call sites need the same policy.
