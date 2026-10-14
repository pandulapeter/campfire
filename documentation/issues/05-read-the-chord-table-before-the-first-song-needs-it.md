<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Read the player's chord table off the main thread before the first song needs it

**Kind:** performance  ·  **Severity:** low–medium  ·  **Platforms:** all (worst on low-end Android and the web)
**Challenged:** amended — named the holder (`PreferencesController`: it owns `userPreferences` and `viewModelScope`; `ChordLibraryState` has neither), spelled out the flow and its condition (the one `ExportController` uses), noted the lazy and wasm facts checked, and that a song already open re-collects off the main thread on a tuning change.
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/voicings/ChordVoicings.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/voicings/ChordVoicingSearch.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/voicings/ChordVoicingsTest.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/PreferencesController.kt` (starts the warm-up),
`chordpro/CLAUDE.md` or `chordpro/.../voicings` notes and `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/chords/CLAUDE.md` (one sentence each)

## Problem

Reviewed at `6c3043996`. Every tuning's shape table is parsed on first use:

```kotlin
// ChordVoicingSearch.kt
private val guitarTable by lazy { readTable(ChordVoicingTables.guitar, ChordTuning.GUITAR) }
```

`rememberSongLyricsModel` builds the first song model on the main thread during composition on purpose (so the first
frame is not empty), and that calls `songChordsOf(searchesShapes = false)` (`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/chords/SongChords.kt`), which calls
`ChordVoicings.needsSearch` / `ChordVoicings.default`. The shape *search* is already kept off the main thread
(`isShapePending`), but reading the table is not: the first lookup per tuning parses ~3,000 shape lines on the main
thread.

Measured on an M-series Mac (desktop jar): the guitar table's first lookup takes 23 ms with the JIT and 302 ms
interpreted (`-Xint`), the ukulele's 26 / 171 ms; later lookups 0.03 ms. A low-end Android phone is several times slower
per core, so the first song opened each session (Chord diagrams on) drops several frames in its opening transition,
and the single-threaded web build freezes the same way. Changing the tuning does it again for the next song opened
(a song already open re-collects its chords off the main thread, `ui/chords/CLAUDE.md`, so it pays off the main thread).

## Fix

Recommended:

1. `:chordpro` (explicitApi): add `public fun ChordVoicings.prepare(tuning: ChordTuning)` with KDoc: reads and indexes
   the table [tuning] uses, so the first lookup costs nothing; safe to call from any thread, idempotent. It forces the
   matching `lazy` in `ChordVoicingSearch` (add an `internal fun prepare(tuning) { table(tuning) }` there). The `lazy`s
   stay synchronized (the default), so a main-thread lookup during the warm-up waits for it rather than parsing twice.
2. Presentation, in `PreferencesController` (it owns `userPreferences` and is built with `viewModelScope`, so the
   warm-up starts as soon as preferences are read, with no screen involved): launch from its `init` (or a
   `startChordTableWarmUp()` the view model calls beside the other starters)
   `userPreferences.map { it?.takeIf { p -> p.areChordsEnabled && p.areChordDiagramsEnabled }?.chordTuning() }`
   `.filterNotNull().distinctUntilChanged().collect { withContext(Dispatchers.Default) { ChordVoicings.prepare(it) } }`
   — the same condition `ExportController.preparePrintSource` uses for diagrams. Keyboard needs no table
   (`chordTuning()` is null for an instrument without shapes; a keyboard tuning falls to `table`'s
   `else -> Table.EMPTY`). Tunings sharing a table (`tableTuning`, e.g. half step down) are distinct values but the
   second `prepare` finds the `lazy` done. One sentence in `ui/chords/CLAUDE.md` says where the table is read.

Checked: the `lazy`s are `SYNCHRONIZED` by default on every target, which on Kotlin/Native's current memory model is a
real lock with nothing frozen, so a main-thread lookup during the warm-up waits for it (never worse than today) and
nothing parses twice; the table's `listOf` constants (`GuitarChordTable.shapes` …) are initialized inside that same
`lazy`, `ChordVoicings.defaults` is a per-chord cache that needs nothing up front, and no other `lazy` in `:chordpro`
sits on the first lookup, so forcing the table covers the whole first-use cost.

On the web `Dispatchers.Default` is the page's one thread, so the warm-up still costs the same time, and it lands just
after preferences are read: while the library is being read from OPFS (asynchronous, so the thread is otherwise idle)
and before any song can be opened, which needs the library. That may delay the first frame of content by the parse
time; check the launch on the web build once, and if the first frame visibly waits, start the warm-up after the
library's first read rather than after the preferences' (never later than that).

Rejected: treating a not-yet-read table like `isShapePending` so diagrams fill in later. That gives the first song a
frame without its diagrams that then changes, against the first-frame rule.

## Tests

`ChordVoicingsTest`: after `ChordVoicings.prepare(ChordTuning.UKULELE)`, `ChordVoicings.default(<C major>, UKULELE)`
returns the same shape as without it (prepare changes no answer). Timing is not asserted.
