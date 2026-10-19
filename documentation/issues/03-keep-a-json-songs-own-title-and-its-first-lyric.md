<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Keep a JSON song's own title, and its first lyric, instead of taking that lyric for the title

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Challenged:** amended — the guess that agrees with the JSON title gives up its `{title}` line so the object's own spelling is written (`RIVERLIGHT` over `Riverlight`), content that declares its own title is left alone explicitly, the comparison goes through `ForeignSongHeader.headerValue`, and the double conversion and the differently-named first line are stated as accepted.
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverter.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverterTest.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/CLAUDE.md`,
`data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/backup/JsonSongs.kt`,
`data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/backup/JsonSongsTest.kt`

Lane I, first of 03 → 04 → 05 → 06. Plan 04 also touches `ChordSheetConverter.kt` (a new function); the two do not
overlap.

## Problem

Since 39901f6e7 the plain-text title rule (`extractPlainTitle` in `ChordSheetConverter.kt`) takes a first line that
sits straight over a chord row as the song's title; `ChordSheetConverterTest` pins it on purpose
(`a title, its credit, and a blank line before the chords`):

```kotlin
assertTrue(convert("Riverlight\nAm     C\nHello world").startsWith("{title: Riverlight}\n\n[Am]Hello [C]world"))
```

`JsonSongs.song` runs the whole `convert` over a song's `content`, and only then writes the JSON object's own title,
which `ForeignSongHeader.songText` leaves out wherever the converted text declares one:

```kotlin
val converted = ChordSheetConverter.convert(ChordSheet.ofPlainText(content), String::normalizedToNfc).firstOrNull() ?: return null
val text = ForeignSongHeader.songText(converted) {
    directive("title", title, "t")
```

A JSON song already has its title in a field, so whatever the converter guesses from the content can only be worse.
Verified at HEAD: `{"title":"Riverlight","content":"Hum along softly now\nG        D\nWalking down the road\nC        G\nSinging all the way"}`
imports as

```
{title: Hum along softly now}

[G]Walking [D]down the road
[C]Singing [G]all the way
```

The JSON title is gone and the song's first lyric is eaten into the title. Any other-app export whose content opens
with a chordless lyric line over the first chord row (a pickup line) hits this; the file is even named `Riverlight.cho`
in the import review, and renamed to the wrong title once imported.

## Fix

Give `convert` a way not to guess a title, and have `JsonSongs` use it unless the guess agrees with the JSON title.

1. `ChordSheetConverter.convert(sheet, normalize = { it }, guessesTitle: Boolean = true)`: where `guessesTitle` is
   false, `convertSong` skips `extractTitle` (pass the flag down; `extractHeader`, the body, and everything else stay
   as they are). KDoc on the parameter: a caller that knows the song's title from elsewhere (another app's field) has the
   first lines read as the song's lines, the title and the credit under it included. The default keeps every other
   caller (`PrepareImportUseCaseImpl`, `DocumentGoldenTest`) unchanged. The module is in explicit API mode, so the
   parameter is `public` by being on a public function; no Koin rule applies (`:chordpro` has no Koin).
2. `JsonSongs.song`: convert as today. Where the converted text's title (`ChordProParser.parseMetadata(guessed).title`)
   is null, or the content declares its own (`ChordProParser.parseMetadata(content).title`, content that is ChordPro
   already, which `convert` hands back untouched), keep it as it is: the content's own `{title}` still wins, as the
   `ForeignSongHeader` KDoc says. Where the guessed title equals the JSON title (both through
   `ForeignSongHeader.headerValue`, NFC via `normalizedToNfc`, ignoring case), the guess only removed the title line the
   content repeats (and may have read its `by …` credit), so keep the guess but drop its `{title: …}` line, which is
   always the first line `convert` writes, so that `ForeignSongHeader` writes the object's own title in its own
   spelling (a chord page's first line is often in capitals: `RIVERLIGHT` over a JSON `Riverlight`). Otherwise convert
   again with `guessesTitle = false`:

   ```kotlin
   val sheet = ChordSheet.ofPlainText(content)
   val guessed = ChordSheetConverter.convert(sheet, String::normalizedToNfc).firstOrNull() ?: return null
   val guessedTitle = ChordProParser.parseMetadata(guessed).title
   val ownTitle = ForeignSongHeader.headerValue(title.normalizedToNfc())
   val converted = when {
       guessedTitle == null || ChordProParser.parseMetadata(content).title != null -> guessed
       guessedTitle.equals(ownTitle, ignoreCase = true) -> guessed.substringAfter('\n')
       else -> ChordSheetConverter.convert(sheet, String::normalizedToNfc, guessesTitle = false).firstOrNull() ?: return null
   }
   ```

   Only a guess that disagrees is converted twice, so a large export costs at most twice today's conversion, within
   the document's bounds (8 MB, 200,000 nodes). A first line that names the song differently from the object (the
   `Title - Artist` order, which `ChordSheetTitles` reads artist first; `Riverlight (Live)` under a JSON `Riverlight`)
   stays the first line of the body under the object's title: nothing is lost, which is the point. Add one sentence to
   the `JsonSongs` KDoc: the content's first lines are never taken for a title the object names differently, and the
   object's spelling of its title is the one written.

   Rejected alternative: `convertFragment` + `ChordSheetLyrics.escaped`, as OpenSong and OnSong do. It returns null for
   content without a chord row (a lyrics-only sheet with `[Verse]` headings, or content that is ChordPro already, both
   of which `JsonSongs.content` accepts), which would then be escaped whole and lose its sections and chords; and it
   keeps `Key: G` lines as text rather than in the header.
3. `convert/CLAUDE.md`: add one bullet after the `convertFragment` one: `convert` can be told not to guess a title
   (`guessesTitle`), for a caller that has the song's title from another app's field.

**Decision for the user (the paste half).** The same rule makes a plain paste whose first lyric sits straight over the
first chord row (`Hum along softly now` / `G   D` / `Walking down the road`) open in the editor titled
`Hum along softly now`, the lyric missing from the body. That shape is indistinguishable from the one 39901f6e7 meant
to read (`Riverlight` / `Am   C` / `Hello world`, listed in that plan as a probed miss and pinned by the test above),
and the editor shows the title field, so the line is not lost, only misplaced. Recommended default: **keep the paste
rule as it is** and fix only JSON here. The alternative is to drop the no-blank-line shape (a title is then taken only
before a blank line, a credit, metadata, or a heading), which changes the pinned assertion to expect no title.

## Tests

- `ChordSheetConverterTest` (`./gradlew :chordpro:desktopTest`): `a caller that knows the title has the first lines
  read as lines`: `ChordSheetConverter.convert(ChordSheet.ofPlainText("Hum along softly now\nG        D\nWalking down the road"), guessesTitle = false).single()`
  has no `{title` and starts with `Hum along softly now\n[G]Walking [D]down the road`; the same text with the default
  still starts with `{title: Hum along softly now}` (the paste rule, unless the Decision above changes it).
- `JsonSongsTest` (`./gradlew :data:source:local:implementation:desktopTest`, which also runs `DocumentGoldenTest`):
  - `a lyric over the first chord row stays a lyric under the object's own title`: the JSON above → parsed title
    `Riverlight`, and the text contains `Hum along softly now\n[G]Walking [D]down the road`.
  - `content repeating the object's title is not written twice`: `{"title":"Riverlight","content":"Riverlight\n\nG        D\nWalking down the road\nC        G\nSinging all the way"}`
    → exactly one line mentions `Riverlight` (the `{title: Riverlight}` directive).
  - `the object's spelling of its title wins over the content's`: `{"title":"Riverlight","content":"RIVERLIGHT\nby The Invented Band\n\nG        D\nWalking down the road"}`
    → parsed title `Riverlight`, artist `The Invented Band`, and no line `RIVERLIGHT` in the text.
  - `content naming the song differently keeps that line`: `{"title":"Riverlight","content":"Riverlight - The Invented Band\n\nG        D\nWalking down the road"}`
    → parsed title `Riverlight`, and the text contains `Riverlight - The Invented Band` as a line of the body.
- Keep every existing `ChordSheetConverterTest` title test and `DocumentGoldenTest` green unchanged.
