<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Build a tune's text, and the part of it engraved, once per tune rather than on every effect restart and recomposition

**Challenged:** amended — the memo stays in the request (plan 03's step 6 makes the old request's keys from the old
request's own texts) but out of its `equals` and `hashCode`: the request is hashed on every composition
(`placedWidthFor(base)`, every frame of a pinch), and a map of every tune's text in its hash would double that work for
nothing, since the texts follow from `tunes` and `parts`, which it already compares.

**Kind:** performance  ·  **Severity:** low-medium  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/notation/SongNotations.kt`,
`…/ui/notation/NotationCache.kt` (`keyOf` only), `…/ui/songView/SongSectionContent.kt`, `…/ui/songView/SongLyrics.kt`
(the `runSlotLines` lambda, only if its call changes), tests in
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/notation/`

Lane N, first.

## Problem

- `NotationRequest.textOf(tune)` runs `abcTuneWithPart` (line filtering and four regexes over the whole score) for a
  tune with a part shown, every time it is called: from `keyOf` for each tune in
  `val isCached = cache != null && request.tunes.all { cache.isKept(request.keyOf(it)) }` on **every restart of the
  rendering effect** (every frame of a pinch or a resize, since the request holds the width and unit size), again in the
  loop's `keyOf`, and again in `render` (`renderNotationPartOrScore` builds `partText` itself). The new string is hashed
  anew for each key.
- `SongNotations.of`, `partPickerOf`, and `placeText` call `run.abcTune()` (`joinToString` over the run's lines) on every
  call; `SongSectionContent` calls them on every recomposition of a section holding a tune (the heading's
  `notations.partPickerOf(it.run, it.tuneOffset)`, `SectionRun`'s `partPickerOf`, `of`, and `placeText`), and
  `partPickerOf` calls `of` again.

## Fix

1. In `rememberSongNotations`, compute once per `(tunes, parts)` (`remember(tunes, parts)`; `parts` is rebuilt every
   composition but compared by equality) a map from each `AbcTune` to its engraved text (the part's, or the tune's
   own), and put it in the request; `textOf` reads it. This memo is where plan 03 adds each tune's "has a tab voice" and
   "names the song" facts, so make it a small per-tune value (e.g. `TuneFacts(engravedText)`) in a holder class of its
   own (e.g. `NotationTuneFacts(byTune: Map<AbcTune, TuneFacts>)`) whose `equals` is "is also one" and `hashCode` a
   constant, KDoc'd as derived from `tunes` and `parts`, which the request already compares — so the data class's
   equality and hash stay what they are today, and `copy(width = …)` carries the memo along. Keep it in the request
   rather than passing it beside: plan 03 compares a tune's key under the new request with its key under
   `rendered.request`, and a part picked in between changes the text, so each request must carry its own. It is
   immutable once built, so the effect may read it from any thread (plan 14 moves the engraving to `Dispatchers.Default`).
   `NotationCache.renderedFor(request)` and `keyOf` read it through the request as today.
2. `render` passes the engraved text to `renderNotationPartOrScore`, which takes it instead of building it (keep the
   function usable by `printNotationsOf`, e.g. a parameter defaulting to `part?.let { abcTuneWithPart(tune, it) } ?: tune`).
3. Change `SongNotations.of`, `partPickerOf`, and `placeText` to take the tune's text (`String?`, null for a run that is
   no tune) instead of the run; `partPickerOf` reuses the lookup instead of calling `of`. In `SectionRun`,
   `val tune = remember(run) { run.abcTune() }` once, passed to all three. The heading's call in `SongSectionContent`
   (a function of ~160 lines, past the 120 limit, which may not grow) changes in place, line for line. `SongLyrics`'s
   `runSlotLines` already runs inside a `remember`; leave it unless the signature change touches it.

Must not break: the engraved text byte for byte (the cache keys and the screen-reader reading depend on it); a part
that fails still falls back to the whole score; the PDF's `printNotationsOf`.

## Tests

- A commonTest: a request built with a part returns from `textOf` the same text `abcTuneWithPart` gives (and the
  tune's own without a part), and `keyOf` is equal to today's for both.
- The notation and song details tests pass unchanged (`AbcPartsTest`, `SheetMusicReopenTest`).
