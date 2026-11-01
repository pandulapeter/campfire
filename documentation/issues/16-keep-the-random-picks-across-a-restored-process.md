<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Keep the random picks, their draw time, and the time the app stopped across a process the system killed and restored

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** Android (the only platform that restores a killed process's saved state)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/songs/RandomPicksDraw.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/SavedStateStore.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songs/CLAUDE.md`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/state/songs/RandomPicksDrawTest.kt`

## Problem

The random picks' seed lives only in the view model:

```kotlin
internal class RandomPicksDraw(
    private val clock: Clock,
    private val random: Random,
) {
    private var drawnAt = clock.now()
    private var stoppedAt: Instant? = null
    private val _seed = MutableStateFlow(random.nextLong())
```

(`RandomPicksDraw.kt`), built in `CampfireViewModel` as `RandomPicksDraw(clock = Clock.System, random = Random.Default)`.
On Android the user opens the system file picker or share sheet from the songs list, the system kills the process
while it is in the background, and on return the app is restored: back stack, song filter, search, and scroll come back
from `SavedStateStore` ("a process killed in the background and restored is still the same run of the app as far as the
user can tell", `CampfireViewModel`'s `_songFilter` KDoc), but a new view model draws a new seed, so the five cards at
the top of the list are different after under a minute away. That is exactly the return the 51st review's decision D2
protects ("catch-up on return only after 5 min away on the wall clock"): `onAppStarted` ignores a start with no stop
before it, and a restored process has none, so it neither keeps the old picks nor applies the five-minute rule.

## Fix

Persist the draw in the saved state, as the filter and the searches are:

- `RandomPicksDraw` takes what a restored process saved, `restored: SavedDraw?` (a `@Serializable` data class of
  `seed: Long`, `drawnAtMillis: Long`, `stoppedAtMillis: Long?`, wall-clock epoch milliseconds), and a
  `persist: (SavedDraw) -> Unit`. It starts from `restored` when there is one (else draws as now), and calls `persist`
  whenever `drawnAt`, the seed, or `stoppedAt` changes: in `init`, `reshuffle`, `onAppStopped`, and `onAppStarted`
  (which clears `stoppedAt`). No defaulted constructor parameters are needed (it is not a Koin definition, but keep the
  test and the view model passing both explicitly).
- `CampfireViewModel`: `RandomPicksDraw(clock = Clock.System, random = Random.Default, restored =
  savedStateStore.restore<RandomPicksDraw.SavedDraw>(RANDOM_PICKS_KEY), persist = { savedStateStore.persist(RANDOM_PICKS_KEY, it) })`,
  with `RANDOM_PICKS_KEY = "randomPicks"` added to `SavedStateStore`'s companion; mention the picks in the
  `SavedStateStore` KDoc's list.
- Persisting `stoppedAt` is what makes D2 hold across the restore: `ON_STOP` writes it, and on Android 9+ (minSdk 28)
  `onSaveInstanceState` runs after `onStop`, so it is in the saved bundle; the restored process's first `ON_START`
  then sees it and draws again only after five minutes away (and an hour since the draw), as an unkilled process would.
- Update the class KDoc ("kept nowhere … a launch is a new draw" becomes: kept only in the saved state of a process the
  system may restore, never synced or written to disk, so a launch is still a new draw) and the Random picks section of
  `ui/screens/songs/CLAUDE.md` ("a device-local seed, never synced or kept" gains "beyond a restored process").

The seed stays out of the preferences: a new launch is meant to be a new draw.

## Tests

`RandomPicksDrawTest` (its draw built with `restored = null` and a `persist` that records into a list):

- A draw built from what another one persisted has the same seed, and `catchUp` after 59 more minutes (counted from the
  first draw's `drawnAt`) keeps it, after 60 draws again.
- A draw that persisted `onAppStopped`, rebuilt from that, then `onAppStarted(isSongsOnTop = true)` 10 seconds later
  keeps the seed; rebuilt and started six minutes later, an hour after the draw, draws a new one.
- `reshuffle` persists the new seed.
