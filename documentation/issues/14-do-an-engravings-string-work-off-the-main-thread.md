<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Do the string work around each engraving off the main thread

**Challenged:** amended — the characters blanked are `\u0000`–`\u0002` (`RECORD_SEPARATOR` is `\u0001`, and the test is
`it <= RECORD_SEPARATOR + 1`), not `\u0000`–`\u001F`: a test written to the wider range would expect newlines and tabs
blanked and fail on a correct fix. Placed after plans 20 and 21 in lane N.

**Kind:** performance  ·  **Severity:** low  ·  **Platforms:** Android, iOS, desktop (the web has one thread)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/notation/SongNotations.kt`,
`libs/abc/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/abc/implementation/AbcRendererImpl.kt`,
`…/ui/notation/CLAUDE.md` (the painting line, if it needs widening)

Lane N, after plans 05, 20, and 21 (none of them touches what this one does; the order only keeps the lane linear).

## Problem

The song page's rendering effect runs on Main and calls `render` → `renderNotationPartOrScore` →
`renderNotationDrawing` → `renderer.render(…)` without changing dispatcher; only the engine call itself
(`QuickJsAbcScriptEngine` / `IosAbcScriptEngine`, `withContext(dispatcher)`) and the parse (`withContext(Dispatchers.Default)`)
leave Main. So on Main run: `abcTuneWithPart` and `abcTuneWithoutSongTitle` (line splitting and regexes), and in
`AbcRendererImpl.render` the char-by-char copy

```kotlin
val readable = tune.map { if (it <= RECORD_SEPARATOR + 1) ' ' else it }.joinToString("")
```

(a boxed `Char` and a `String` per character), `voiceCountOf`'s and `zeroDurationsIn`'s regexes, `TAB_VOICE`, and after
the engine returns `substringBefore`, `split`, `count`, `heightOf`, and `abcMessagesOf` over hundreds of KB of SVG. The
PDF's `printNotationsOf` (called from `produceState`, on Main) does the same.

## Fix

1. In `SongNotations.kt`, run `renderNotationPartOrScore`'s body under `withContext(Dispatchers.Default)` (it is shared
   by the screen and the PDF), keeping the existing `withContext(Dispatchers.Default)` around the parse (now a no-op
   switch) or folding it in. On the web `Dispatchers.Default` is the same single thread, as today.
2. In `AbcRendererImpl.render`, build `readable` without boxing (e.g. `buildString(tune.length) { tune.forEach { … } }`
   or a `CharArray`). The API already says calls may come from any thread, so nothing else changes there.

Must not break: cancellation between tunes (`ensureActive()` in the loop; `withContext` is cancellable); a cancellation
racing the end of the work is still not remembered as a failure (`if (isActive) cache?.markFailed(key)` runs back on
Main); the cache stays main-thread only (written after the `withContext` returns); the engine's single thread and its
interrupt.

## Tests

- `AbcRendererImplTest` passes unchanged (the blanking of separator characters keeps positions: add an assertion if
  none covers a tune holding `\u0000`, `\u0001`, and `\u0002`, each blanked to a space in place, and a newline and a tab
  kept).
- `SheetMusicReopenTest` and `PrintNotationsTest` pass unchanged.
