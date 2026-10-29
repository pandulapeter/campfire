<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Keep the editor's song direction beside its text instead of parsing the song on the main thread whenever the field is rebuilt

**Kind:** performance · **Severity:** low · **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/EditorTextDirection.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/ChordProTextField.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/LoadedSongEditor.kt`
**Challenged:** amended — the holder is named: `LoadedSongEditor`, where `editorField.textFieldState`, the three
`rememberScrollState()`s, and the shared `text` derived state are created; the call moves there, next to `text`, and
plan 04's arrow keys read the same state.

Runs after plan 04 in the same lane (both touch `ChordProTextField.kt`).

## Problem

```kotlin
val parsed = remember(text) { mutableStateOf(text.value to directionOf(text.value)) }
…
private fun directionOf(text: String) = ChordProParser.parse(text).textDirection
```

`rememberEditorTextDirection` is called by `ChordProTextField`, which the Edit / Preview / Split `AnimatedContent`
composes afresh on every switch. Each time, the whole song is parsed synchronously on the main thread to know its
direction, on top of the summary cache's own parse. A songbook-sized document (the editor's `LONG_DOCUMENT_LENGTH` is
50,000 characters) stutters on each pane switch.

## Fix

Keep the direction state where the field's `TextFieldState` and scroll states already live ("Scroll states live next to
the `TextFieldState`, not in the panes", `songEditor/CLAUDE.md`): in `LoadedSongEditor`, right after
`val text = remember(textFieldState) { derivedStateOf { textFieldState.text.toString() } }`, call
`val textDirection = rememberEditorTextDirection(text)` (it is keyed on `text`, which is keyed on the field, so a new
song's field gets a fresh one) and pass the `State<WritingDirection>` into `ChordProTextField` as a new
`textDirection: State<WritingDirection>` parameter (KDoc it like `text`), replacing the call inside the field. The first
value is still worked out synchronously once, when the editor opens, so the field never opens in the wrong direction;
later ones come from the existing debounced background flow. If plan 04's option B has landed, its
`editorArrowKeys` reads this same state (`{ textDirection.value }`) rather than parsing again. Update the KDoc of
`rememberEditorTextDirection` ("worked out right here" stays true; say it is called once per opened song). The field then never parses on composition. Keep the debounce and
`Dispatchers.Default` work as it is. Do not add a direction to `ChordProSummary` (a public `:chordpro` model change for
one screen).

## Tests

None can measure the main thread here; the existing editor UI tests (`tools/screenshots`) must still pass, Hebrew
editor golden renders included.
