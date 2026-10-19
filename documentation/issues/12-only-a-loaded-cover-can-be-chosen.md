<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Let only a cover whose thumbnail has loaded be chosen in the cover search

**Challenged:** amended — the "Manual check" section is removed (the project keeps no lists of hand checks); the fix is
unchanged. Checked: `Set<String>` in `rememberSaveable` is what `unavailableKeys` already saves on every platform, Coil
calls `onSuccess` for a memory-cache hit too, and Save still only enables on a tap.

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Lane:** C
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/lookup/CoverArtResults.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/lookup/CoverArtSearchSheet.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/lookup/CLAUDE.md`

Runs after plan 10 (same two files; 10 changes `onUnavailable` and adds `unreachableKeys`).

## Problem

A tile can be selected whether or not its image has arrived (`CoverArtResults.kt:264`):

```kotlin
.selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
```

and Save is enabled by the selection alone (`CoverArtSearchSheet.kt:110`):

```kotlin
CoverArtSheetMode.SEARCH -> selectedUrl != null && selectedUrl != dialog.song.coverArtUrl
```

The sheet does let go of a selected tile when its thumbnail fails (`onUnavailable` clears `selectedUrl`), but only once
the failure arrives. On a slow link the grid shows titled placeholders for many seconds (natively up to the 60 s silence
bound, on the web up to plan 11's bound or today's ten minutes); a user who taps a placeholder and Saves before then
writes its address into the song. For a Cover Art Archive `front-250` of a release group without a cover that is a 404:
the song shows a placeholder forever, and the sheet now says "Change cover" for a cover nobody has seen. The class KDoc
already states the rule ("a cover that cannot be seen cannot be chosen either"); only the timing breaks it.

## Fix

- `CoverArtSearchSheet.kt`: hold `var loadedUrls by rememberSaveable { mutableStateOf(emptySet<String>()) }` beside
  `unavailableKeys` (saveable for the same reason as the selection: after activity recreation the selection survives,
  and a Save that turned disabled until Coil's memory cache answered again would flicker). Not reset by `search`: an
  address that has loaded is in Coil's memory cache and draws again at once. Pass it to `CoverArtResults` with an
  `onLoaded: (CoverArtCandidate) -> Unit` that adds `candidate.coverArtUrl`.
- Save in search mode: `selectedUrl != null && selectedUrl in loadedUrls && selectedUrl != dialog.song.coverArtUrl`.
  With the tile rule below a selection is always loaded, but the check keeps Save honest if a selection ever arrives
  another way (a restored state).
- `CoverArtResults.kt`, `CoverArtTile`: take `isLoaded: Boolean` and `onLoaded: () -> Unit`;
  `.selectable(selected = isSelected, enabled = isLoaded, role = Role.RadioButton, onClick = onClick)` and
  `AsyncImage(…, onSuccess = { onLoaded() }, onError = …)`. Selection is keyed by URL (`isSelected = candidate.coverArtUrl == selectedUrl`),
  so track loaded URLs, not candidate keys; two records sharing one image load together.
- No visual change on the tile: a placeholder already looks like nothing to pick, the image fading in (Coil's crossfade)
  is what makes it pickable, and a tap on a placeholder simply does nothing. Disabled-ness is announced to accessibility
  services by `selectable(enabled = false)`. The Save button's enabled state changes only with the user's tap, as
  today, so nothing new needs animating.
- Update the class KDoc of `CoverArtSearchSheet` (the paragraph ending "or Save would write an address that answers
  nothing"): a record can be chosen only once its thumbnail has loaded, and one that fails is taken off the grid. Add a
  bullet to `presentation/.../ui/dialogs/lookup/CLAUDE.md`: "Only a tile whose thumbnail has loaded can be selected, so
  Save never writes an address nobody has seen."

## Tests

None. The logic is a set and two booleans inside Composables, and the presentation tests cover pure logic only (the UI
tests in `tools/screenshots` run with `OfflineCoverArtRepository`, where no thumbnail ever loads, so they cannot show a
loaded tile either).
