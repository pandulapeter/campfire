<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# 22 — Let a setlist change recompose only the song rows whose star changed

| | |
|---|---|
| Lane | C |
| Impact | low (a one-off 2–4 ms per tap, estimated) |
| Confidence | high |
| Platforms | all |
| Files | presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songs/SongsScreen.kt |
| Depends on / conflicts with | Conflicts with 15, 19 and 20 (same item block of `SongsScreen.kt`); land last. |
| Commit message | `Recompose only the song rows whose setlist star changed.` |

## Problem
In `SongsScreen.kt:421-442`, the `actions` lambda of every song row captures the whole set:

```kotlin
SetlistAssignmentsButton(viewModel = viewModel, song = song, isInSetlist = song.fileName in songFileNamesInSetlists)
```

- `songFileNamesInSetlists` (`CampfireViewModel.kt:459-461`) is a new `Set` on every setlist write: a star tapped, a song added or removed, a reorder, a sync.
- A `Set` is compared by identity under strong skipping, so every visible row gets a new `actions` lambda.
- Every `SongListItem` then recomposes in full, not just the row whose star changed.

## Fix
Work the Boolean out in the item scope and capture only that:

```kotlin
val isInSetlist = song.fileName in songFileNamesInSetlists
...
actions = if (isPerformanceModeEnabled) null else {
    { Row(...) { SetlistAssignmentsButton(viewModel = viewModel, song = song, isInSetlist = isInSetlist); SongActionsButton(...) } }
},
```

The lambda is then memoized on a Boolean that only changes for the affected row. The KDoc of `SetlistAssignmentsButton` already says the value is "passed in rather than collected"; that stays true.

What must not change: the star's turn animation, which is keyed on `isInSetlist` inside the button.

## Verification
- `./gradlew :presentation:desktopTest`, and the Android debug build.
- Manual check: tap a row's star and add the song to a setlist. Only that row's star turns, and the others are untouched.
- Measurement: in Layout Inspector's recomposition counts, tapping a star should add a recomposition to one `SongListItem`, not to every visible one.
