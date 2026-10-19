<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Keep the click action and the button role of the Scales and Chords tabs' cells for screen readers

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all (Android TalkBack, iOS VoiceOver, desktop and web readers)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/scales/ScaleLibraryCell.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/chords/ChordLibraryCell.kt`,
`tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/screenshots/...` (a UI test beside the Scales tab's,
using the `openScales()` opener plan 09 adds to `AppTestNavigation.kt`), `presentation/.../ui/screens/scales/CLAUDE.md`
(one clause), `presentation/.../ui/screens/chords/CLAUDE.md` (one clause).

**Challenged:** found by the challenge of plans 09–15, written after it; not itself challenged.

## Problem

Both cells hand a clearing semantics modifier to a clickable `Surface`:

```kotlin
// ScaleLibraryCell.kt
) = Surface(
    modifier = modifier.clearAndSetSemantics { contentDescription = description },
    onClick = onClick,
```

```kotlin
// ChordLibraryCell.kt
    Surface(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        onClick = onClick,
```

Material's clickable `Surface` chains its own `clickable` after the modifier it is given, so the click action and
`Role.Button` sit inside the chain that `clearAndSetSemantics` clears (a node's semantics are built from the tail and
reset at a clearing node). The cell is read as a plain node with a description: a screen reader never says it is a
button, and its activation gesture (TalkBack's double tap sends the accessibility click action) does nothing, so a
reader cannot open a scale or a chord from either tab. A test matcher `hasClickAction()` finds no cell either, which is
why plans 09 and 10 match the cells by their descriptions.

## Fix

Set the description without clearing on the `Surface`, and clear the semantics of the cell's content instead, so that
the merged node keeps the click action and the role and reads the description alone:

```kotlin
) = Surface(
    modifier = modifier.semantics { contentDescription = description },
    onClick = onClick,
    …
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clearAndSetSemantics { },
```

The same change in `ChordLibraryCell`. `Surface(onClick)` merges its descendants, so the description on the outer node
is what is read, and the cleared content adds nothing. Check the other `clearAndSetSemantics` uses in
`presentation/.../ui` (`InstrumentChips.kt`, `TunerDisplay.kt`, `TunerStrings.kt`, `PushedSongSectionHeader.kt`,
`CampfireScreens.kt`) and apply the same split wherever one sits on a clickable node; leave the ones on nodes that are
not clickable.

Add one clause to `screens/scales/CLAUDE.md` and `screens/chords/CLAUDE.md`: a cell's description is set on its
surface and its content's semantics cleared, never the other way round, or the click action goes with them.

## Tests

A UI test in `tools/screenshots`, beside the Scales tab's tests, named `a scale cell is a button with its description`:
`runAppTest`, `openScales()`, then `onNode(hasClickAction() and hasContentDescription("C Major: ", substring = true))`
(the description's prefix as plan 10's test uses it) `.assertExists()`, and the same for a chord cell on the Chords tab
(`openChords()`, the cell's `chordCellDescription`). The test fails at HEAD (no node has both) and passes with the fix.
