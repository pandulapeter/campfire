<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->

# Match the song picker's search off the main thread

**Challenged:** dropped — the gain does not pay for the asynchrony. On wasmJs `Dispatchers.Default` is the page's one
thread (plan 10's premise), so "the web most" gets nothing; elsewhere it saves 1–3 ms of a 16 ms frame at 2,000 songs
while the lazy list's own recomposition of the new rows stays on the main thread. In exchange the list would lag the
query by a frame or more, so `refreshKey`, `revealRowsKey`, `ChecklistOrder` and the no-results text (all keyed on the
live `query`) would have to be re-keyed on the query the matches were computed for. The plan's premises are also off:
the query is a `rememberSaveable` `String`, not a `TextFieldState`, and is sheet-local while `SongPickerState` lives
for the session ("search starts empty each time"); and the first value is not the unfiltered list, since the tag and
language chips survive reopening. Revisit only if a measurement shows the match itself over ~8 ms per keystroke.

**Kind:** performance  ·  **Severity:** low  ·  **Platforms:** all (large libraries; the web most)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/pickers/SongPicker.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/search/SongPickerIndex.kt`, the picker's state holder (`SongPickerState`),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/pickers/CLAUDE.md`

## Problem

`SongPicker.kt`: `val matches = remember(pickableSongs, query, activeTags, activeLanguages) { songPickerMatches(...) }` runs on
the main thread in composition on every keystroke: a `filter` copy of the whole library and up to three `contains` per song,
~1–3 ms per keystroke at 2,000 songs on a low-end phone, more on the web, with the keyboard up. The Songs and Setlists screens
do this work on `Dispatchers.Default` in their state holders.

## Fix

Move the matching into the picker's state holder as a flow (`combine` of songs, query, filters → `songPickerMatches` →
`flowOn(Dispatchers.Default)`), collected with an initial value equal to the synchronous result, so the first frame is right
(root rule: never animated over). Read the query from the `TextFieldState` through `snapshotFlow`.

## Tests

`songPickerMatches` keeps its tests. Add a state holder test with `runTest`: the matches follow the query and the first value
is the unfiltered list.
