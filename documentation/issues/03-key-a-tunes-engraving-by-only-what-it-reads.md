<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Key a tune's engraving by only what it reads, so a capo, instrument, or tuning change leaves plain staves alone

**Challenged:** amended — the capo case narrowed: a capo that raises the pitch, or that renames the chords (names by
shapes), still changes `semitones` or `nameCapo` and rightly re-engraves every staff; only an instrument or a tuning
change, and a capo that moves neither (Capo keeps the key, chords named as they sound), changes the tab fretting alone.
Step 6 must compare keys made from each request's own per-tune texts (plan 12 keeps them in the request), never the
new composition's memo for the old request.

**Kind:** performance, stray animation  ·  **Severity:** medium-high  ·  **Platforms:** all
**Files:** `libs/abc/api/src/commonMain/kotlin/com/pandulapeter/campfire/abc/api/AbcTablature.kt`,
`libs/abc/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/abc/implementation/AbcRendererImpl.kt`,
`libs/abc/api/CLAUDE.md`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/notation/NotationChordNames.kt`,
`…/ui/notation/AbcTitles.kt`, `…/ui/notation/NotationCache.kt` (`keyOf` only), `…/ui/notation/SongNotations.kt`,
`…/ui/notation/CLAUDE.md`, tests in `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/notation/`
(`NotationChordNamesTest.kt`, `AbcTitlesTest.kt`), `presentation/src/desktopTest/…/ui/notation/NotationCacheTest.kt`,
`libs/abc/implementation/src/commonTest/…/AbcTablatureRenderingTest.kt`

Lane N, after plan 12 (it stores the per-tune facts in plan 12's memo).

## Problem

Every tune's `NotationChordSpelling` carries the reader's tab fretting
(`SongDetailsPage.kt`: `tablature = inputs.tabs?.abcTablature(capo = inputs.tabCapo)`), and it is part of the cache
key (`NotationCache.kt`: `chords = chords.movedBy(tune.offset)`) and of the cross-fade decision (`SongNotations.kt`:
`previous.chords != request.chords || …` → `isRerendering` → every tune in `rerenderedTunes`). But the engine reads it
only for a tune with a tab voice:

```kotlin
val header = headerOf(width, transposition, hasStringTablature = TAB_VOICE.containsMatchIn(tune), tablature = tablature)
```

So an instrument or a tuning change, and a capo change that moves neither the pitch nor the chord names (`semitones`
is the file's transpose plus the reader's transposition, `nameCapo` the shapes' capo where chords are named by
their shapes: a capo changing either rightly re-engraves every staff), re-engraves every plain-staff tune (an engine call, a parse, a paint each:
14–17 ms for a medium tune in the app) and cross-fades it into an identical drawing — a user-visible flicker for
nothing. Same root, found by measurement: the key holds `songTitle` (`NotationRequest.keyOf`: `songTitle = songTitle`),
which `abcTuneWithoutSongTitle` and `notationReadingOf` only use where a tune's first `T:` is the song's title, so the
same tune in another song misses the cache.

The PDF needs no change here: its spelling and title are fixed for an export's source (plan 08 keeps its engravings).

## Fix

1. **One predicate for "this tune has a tab voice"**, in `:libs:abc:api` so the engine and the page share it: a public
   function next to `AbcTablature` (e.g. `public companion object { public fun isReadBy(tune: String): Boolean }`,
   KDoc'd: a voice in the tab clef, `V:… tab` or `clef=tab`, inline or not), holding today's `TAB_VOICE` regex.
   `AbcRendererImpl` uses it in place of its private `TAB_VOICE`. (Public API addition: see the README's decision.)
2. **`abcNamesSongTitle(tune, songTitle): Boolean`** in `AbcTitles.kt`: whether any tune's first `T:` is the song's
   title, by the same `isSongTitle` the hiding uses.
3. **Per tune**, the spelling and title its engraving reads: `NotationChordSpelling.forTune(hasTabVoice)` (tablature
   kept only for a tune with a tab voice) and the title kept only where the tune names it. Decide both on the **whole
   score's** text (`tune.text`), not the part: a part that fails is engraved whole (`renderNotationPartOrScore`).
   Compute the two booleans once per tune text, in the per-tune memo plan 12 adds to `rememberSongNotations`.
4. `NotationRequest.keyOf` uses them: `chords = <the tune's spelling>.movedBy(tune.offset)`,
   `songTitle = songTitle.takeIf { <tune names it> }`.
5. **The request itself** is normalized the same way for the song as a whole in `rememberSongNotations`: tablature
   null where no tune of the song has a tab voice, title null where no tune names it — so in a song of plain staves a
   tab setting change is not even a new request (no effect restart, no new `SongNotations`, no re-measure).
6. In the effect's loop, a tune whose key under the new request equals its key under `rendered.request` (each made
   from its own request's engraved text, which plan 12 keeps in the request: a part picked changes the text, so the
   old request's key must not be made with the new composition's texts) reuses
   `rendered.renderings[tune]` (the same instance, so `rememberNotationCrossfade` sees equal systems) and is left out
   of `rerenderedTunes`; only tunes whose key changed cross-fade. This also covers a cache that evicted the entry.
7. `notation/CLAUDE.md`: in "Engraved again when tunes, transposition, spelling, width, or text size change", add that
   a tune is keyed by only what its engraving reads (the tab fretting only with a tab voice, the song's title only where
   its `T:` names it). `libs/abc/api/CLAUDE.md`: name the predicate if it lists the API.

Must not break: a tune with a tab voice still re-engraves and cross-fades on a capo, instrument, or tuning change
(`ui/tabs` rule: sheet music's tab staves follow the player); a tune naming the song still hides its title and composer
and its screen-reader reading leaves the title out; "notation on its first frame" from the cache; the
`isOnlyResized` / `isOnlyPlaced` timing (300 ms wait only for a width or size change).

## Tests

- `NotationCacheTest` (or a commonTest of `keyOf`): a plain-staff tune's key is equal under two different
  `tablature`s; a tune with `V:2 clef=tab` gets different keys; a tune titled otherwise gets equal keys under two song
  titles, one titled as the song differs.
- `AbcTitlesTest`: `abcNamesSongTitle` true for a matching first `T:` (case and spacing aside), false otherwise.
- `:libs:abc:implementation` tablature tests pass unchanged; add one asserting the shared predicate accepts both forms
  and rejects a plain `V:1`.
- Confirm with the sweep's measurement method (untracked `:tools:screenshots` test, deleted afterwards): an instrument
  change on song C makes 0 engine calls for its plain-staff tunes. Lane report only.
