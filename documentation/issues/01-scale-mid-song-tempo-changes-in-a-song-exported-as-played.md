<!--
  This file is part of Campfire.
  Copyright (c) Pandula Péter 2017-2026.
  https://github.com/pandulapeter/campfire

  This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
  If a copy of the MPL was not distributed with this file, You can obtain one at
  https://mozilla.org/MPL/2.0/.
-->
# Scale every later `{tempo}` of a song exported as played by the tempo override, as the page, the click, and the PDF do

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Challenged:** amended — the rewrite runs before `set`, with `songFileBpm` read by `ChordProParser.parseMetadata` (the song's own tempo, which may be a body line where the header has none) rather than "the header's"; nothing is scaled where that tempo is missing or unreadable or equals the played one; a line whose number does not change keeps its spelling; the `:chordpro` addition is tempo-only, with no `Field` parameter; tests for these cases.
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/edit/ChordProMetadataFields.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/edit/ChordProMetadataFieldsTest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/playing/SongAsPlayed.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/playing/SongAsPlayedTest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/export/CLAUDE.md`

## Problem

Reviewed at `a59cc76c3`. `SongAsPlayed.applyTo` writes the override into the header's `{tempo}` only:

```kotlin
fun applyTo(text: String): String = ChordProMetadataFields.set(
    text = if (semitones == 0) text else ChordProTransposer.transposeText(text, semitones, preferFlats),
    values = buildMap {
        capo?.let { put(ChordProMetadataFields.Field.CAPO, it.takeIf { fret -> fret != 0 }?.toString()) }
        tempo?.let { put(ChordProMetadataFields.Field.TEMPO, it.toString()) }
    },
)
```

`ChordProMetadataFields.set` keeps every `{tempo}` in the body as it stands (its KDoc: lines "in the body are changes
mid-song and are always kept"). The page, the click, and the PDF scale each later tempo by the override's ratio
(`ChordProSong.withTempo` / `sectionBpm` in `ui/playing/SongTempo.kt`: "a file going from 120 to 60, played at 110, goes
to 55"). A file `{tempo: 120}` … `{tempo: 60}` overridden to 110 plays and prints its second part at 55 here, but the
exported `.cho` says 110 then 60. Reimported into Campfire, `songFileBpm` (110) equals the played tempo, so the part
plays at 60, as in any other ChordPro app. The switch's own wording promises the file as the PDF prints it.

## Fix

1. In `:chordpro` (explicit API), add to `ChordProMetadataFields` one public function, tempo only (the one caller; a
   `Field` parameter would be surface for key and time nobody uses and a no-op for every other field):
   `public fun mapTempoChanges(text: String, transform: (String) -> String?): String`. It visits each `{tempo}` line
   from `ChordProHeaderLayout.bodyStartIndex` on (the split `set` uses, which is where the parser's `isInBody` starts),
   recognised as `set` recognises them (`kind()`, so `{meta: tempo 90}` counts and a selector suffix does not), passes
   it the line's value (`value()`), and writes a non-null result back in the line's own spelling (`rewritten`, so a
   `meta` stays a `meta`, indentation kept); null keeps the line. Header lines are never visited. A text with nothing
   changed is returned as is (`kept == lines`, as in `set`). KDoc in the voice of `set`'s.
2. In `SongAsPlayed.applyTo`, when `tempo != null`, on the transposed text and **before** `set`:
   - `songFileBpm = ChordProTempo.parse(ChordProParser.parseMetadata(text).tempo)?.let(MetronomePattern::coerceBpm)`,
     which is exactly `withTempo`'s: the song's own tempo as the parser takes it, which is the first readable body line
     where the header names no tempo at all (`ChangeableValue`), and nothing for an empty `{tempo: }` header line or an
     unreadable one (`{tempo: fast}`), whose body changes the page plays as written.
   - If `songFileBpm` is null or equals `tempo`, rewrite nothing (the page plays the changes as written).
   - Otherwise each body tempo becomes
     `sectionBpm(ChordProTempo.parse(value)?.let(MetronomePattern::coerceBpm), songFileBpm, tempo)`, except that a value
     that does not parse is kept (`sectionBpm` would write the played tempo over it) and so is one whose result equals
     its own parsed number, which, the ratio never being 1 here, happens only where the metronome's 30–300 holds it (a
     `{tempo: 30 bpm}` slowed down stays as written rather than respelled `30`).
   - Then `set` as today. Running the scaling first matters where the header names no tempo: the song's own body line
     scales to exactly `tempo` (its ratio is 1), and `set`, which takes that line as the effective one, then finds it
     already saying `tempo`; run after `set`, it would scale the override a second time (110 → 101).
   Body changes the parser drops (one equal to the tempo in force, a group that returns to it) scale with the tempo in
   force, so they stay equal to it and the file plays as the page does. A metronome switched off leaves `tempo` null
   (`1fb15e270`), so nothing is scaled, as nothing is written.
3. One sentence in `ui/screens/export/CLAUDE.md`'s as-played paragraph: `{tempo}` the one played, its later changes
   scaled as the page plays them.

Do not move the rewrite onto a parsed `ChordProSong` and serialize it back: the file must keep every other byte.

## Tests

- `ChordProMetadataFieldsTest`: `mapTempoChanges` on `{title: S}\n{tempo: 120}\n[C]a\n{tempo: 60}\n[G]b\n` with
  `{ "55" }` changes only the body line; `{meta: tempo 60}` in the body becomes `{meta: tempo 55}`; a transform
  returning null, and a header-only song, return the text unchanged (same instance).
- `SongAsPlayedTest` (on its own texts, not `capoChart`):
  - `{title: Song}\n{tempo: 120}\n[G]One\n{tempo: 60}\n[C]two\n` at 110 is written `{tempo: 110}` … `{tempo: 55}`;
  - with no tempo override (only a transposition) the body tempo stays 60;
  - a body `{tempo: fast}` is kept as written;
  - no header tempo, `{title: Song}\n[G]One\n{tempo: 120}\n[C]two\n{tempo: 60}\n[D]three\n` at 110: the first body
    line becomes `{tempo: 110}` (not 101), the second `{tempo: 55}`;
  - an unreadable header `{tempo: fast}` with a body `{tempo: 60}` at 110: the header reads 110, the body stays 60;
  - header `{tempo: 120}`, body `{tempo: 30 bpm}`, at 110: the scaled 27.5 is held at the metronome's 30, its own
    number, so the line is kept as written.
