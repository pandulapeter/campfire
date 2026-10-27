<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Describe engraved sheet music to screen readers by its title, its chords in the page's names, and its words, not its ABC source

**Challenged:** amended — a tune's words are read verse by verse (several `w:` lines under one line of music are
successive verses, which the draft would have read interleaved: line 1 of each verse, then line 2 of each), with `+:`
continuations; Hungarian says `Dalszöveg` (`Szöveg` is the editor's Edit tab). The rest held: the description is one
TalkBack/VoiceOver stop, read whole (as a long lyric would be; nothing to cap), and leading with the heading only
where the tune names itself nothing is right, since the fold toggle or header pill above already reads it.

**Kind:** accessibility · **Severity:** medium · **Platforms:** all
**Files:** new `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/notation/NotationReading.kt`,
`.../ui/notation/NotationDrawing.kt`, `.../ui/notation/NotationChordNames.kt` (`withChordNames`),
`.../ui/notation/AbcTitles.kt`, `.../ui/notation/SongNotations.kt`, `.../ui/songView/SongSectionContent.kt`,
`.../ui/songView/SongNotationBlock.kt`, `presentation/src/commonMain/composeResources/values/strings.xml` and
`values-hu`, `values-ro`, `values-de`, `values-es`, `values-fr`, `values-it` (`strings.xml` each),
`.../ui/notation/CLAUDE.md`, `.../ui/songView/CLAUDE.md`,
new `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/notation/NotationReadingTest.kt`

(`...` is `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation`.) Written against the code as plans
05–08 leave it (renderings painted in the effect; if plan 08 landed, `of` also takes the run's offset).

## Problem

An engraved tune is drawn, so its node needs a content description, and it is handed the ABC source:

```kotlin
// ui/songView/SongNotationBlock.kt
// The notation is drawn, so a screen reader is handed the tune as it stands in the file, by the run's first piece.
modifier = modifier
    .then(if (slot == 0) Modifier.semantics { contentDescription = text } else Modifier)
```

with `text = run.joinToString(separator = "\n") { it.monospaceText.orEmpty() }` (`SectionRun` in
`SongSectionContent.kt`). TalkBack and VoiceOver read "X colon 1, T colon Scarborough Fair, M colon 3 slash 4, L colon 1
slash 8, K colon A m, A 2 A E 2 E, bar…": noise to anyone, and the chord symbols in it are the file's (`"Am"`), not
the transposed, re-notated names the page draws, so a reader who transposed or reads in Latin or Nashville hears other
chords than a sighted player sees. A tab block hands its lines, which is right for tablature (its columns are the
content); ABC source is not what the page shows.

## Fix

1. **The chord names the page draws travel with the drawing.** `NotationDrawing` gains `val chordNames: List<String>`
   (empty from `parseNotationDrawing`), set by `withChordNames(symbols, names, semitones)` to `names`: the page's names
   of the tune's chord symbols in the order written, already computed off the main thread in `renderNotationDrawing`.
   (Plan 03, landing after this one, names them with the tune's own `%%transpose` added; the reading follows with no
   change here. Plan 10 adds `bars` to each `NotationSystem` that `withChordNames` rebuilds.)
2. **What a tune says, read off the main thread with its rendering** — new `ui/notation/NotationReading.kt`, pure:

   ```kotlin
   /** What a reader hears of an engraved tune: the titles it shows, its chords as the page names them, and its words. */
   internal data class NotationReading(val titles: List<String>, val chords: List<String>, val words: String?)

   /**
    * The reading of [tune] engraved with [chordNames]: the first `T:` of each tune it holds but one naming the song
    * ([songTitle], left to the page, see [abcTuneWithoutSongTitle]); the chords in order, a chord repeated straight
    * after itself said once; and the words: each tune's `w:` lines verse by verse (the k-th `w:` line under a line of
    * music is verse k, so verse 1 is read through the tune before verse 2, as it is sung), a `+:` line continuing the
    * line above, then its `W:` lines; ABC's syllable marks undone (`-` joins syllables, `_`, `*` and `|` say nothing,
    * `~` is a space, `\-` a hyphen).
    */
   internal fun notationReadingOf(tune: String, songTitle: String?, chordNames: List<String>): NotationReading

   /** [this] as one sentence per part, the [heading] standing in for a tune that names itself nothing. */
   internal fun NotationReading.describedAs(heading: String, chordsTemplate: String, wordsTemplate: String): String =
       listOfNotNull(
           titles.joinToString(", ").ifEmpty { heading },
           chords.takeIf { it.isNotEmpty() }?.let { chordsTemplate.withTexts(it.joinToString(", ")) },
           words?.let { wordsTemplate.withTexts(it) },
       ).joinToString(". ")
   ```

   Move `AbcTitles.kt`'s first-`T:`-of-each-tune walk and `normalizedTitle` into a shared `internal` helper there
   (`abcOwnTitles(tune, songTitle): List<String>`, what `abcTuneWithoutSongTitle` hides being the complement), so the
   two never disagree on which tune names the song.

   The section heading is already read by its header pill (or the run's fold toggle, "Sheet music"), so the heading
   only stands in where the tune names itself nothing: "Sheet music. Chords: Am, G, Am. Words: Are you going to
   Scarborough Fair" rather than "Sheet music" twice in a row. (The brief asked for the heading first; this is that,
   without the repetition. Say so if you would rather always lead with it.)

3. **The rendering carries its reading.** `NotationRendering` gains `val reading: NotationReading`, built in
   `SongNotations`'s `render` inside the same `withContext(Dispatchers.Default)` that paints (plan 05):
   `notationReadingOf(tune, request.songTitle, drawing.chordNames)`. `NotationRun` gains `val reading: NotationReading`
   (a stand-in of plan 07 carries the stand-in's reading for its few frames, which is what is drawn).
4. **The block takes a description.** `SongNotationBlock`'s `text: String` parameter becomes `description: String`
   (KDoc: what a screen reader is told the run is), set on slot 0 as now. In `SectionRun`, replace
   `val text = remember(run) { ... }` with

   ```kotlin
   val heading = run.first().environmentLabel ?: defaultLabels.labelOf(kind, run)
   val chordsTemplate = textResource(Res.string.song_details_sheet_music_chords)
   val wordsTemplate = textResource(Res.string.song_details_sheet_music_words)
   val description = remember(notationRun.reading, heading, chordsTemplate, wordsTemplate) {
       notationRun.reading.describedAs(heading, chordsTemplate, wordsTemplate)
   }
   ```

   (templates asked for unfilled, as `SongLyrics` asks for `song_details_key_change`, and filled by `withTexts`; the
   chord names and words are the file's, so never through the formatter). `SectionRun` is the function to watch for
   the 120-line limit; if it would pass it, move the notation branch into a small `NotationRun` Composable of its own
   file.
5. **Strings**, in each `strings.xml` right after `song_details_section_abc`:

   | locale | `song_details_sheet_music_chords` | `song_details_sheet_music_words` |
   |---|---|---|
   | `values` | `Chords: %1$s` | `Words: %1$s` |
   | `values-hu` | `Akkordok: %1$s` | `Dalszöveg: %1$s` |
   | `values-ro` | `Acorduri: %1$s` | `Versuri: %1$s` |
   | `values-de` | `Akkorde: %1$s` | `Text: %1$s` |
   | `values-es` | `Acordes: %1$s` | `Letra: %1$s` |
   | `values-fr` | `Accords : %1$s` | `Paroles : %1$s` |
   | `values-it` | `Accordi: %1$s` | `Testo: %1$s` |

   French puts a no-break space (U+00A0, as the file's `song_details_key_change`, `Tonalité : %1$s`, has: bytes
   `c2 a0` before the colon) before the colon; copy that character, not a plain space. Hungarian says `Dalszöveg`, as
   `songs_lyrics_only` (`Csak dalszöveg`) does: `Szöveg` alone is the editor's Edit tab there. The other words match
   each file's `songs_lyrics_only` (`Versuri`, `Text`, `Letra`, `Paroles`, `Testo`). No counts, so no plurals.
6. **Docs.** `ui/songView/CLAUDE.md`'s "Because chords are drawn, a chorded line's content description is its ChordPro
   form …, and a tab block's its lines" gains: "an engraved tune's is its title, its chords as the page names them, and
   its words (`NotationReading`), never its ABC source". `ui/notation/CLAUDE.md`: the rendering carries its reading.

## Tests

`NotationReadingTest` (commonTest):

- `` `a tune is read by its title, its chords, and its words` ``: a tune with `T:Scarborough Fair`, `"Am"…"G"…"Am"`
  and `w:Are you go-ing to Scar-bo-rough Fair` read with chord names `[Am, G, Am]` gives titles `[Scarborough Fair]`,
  chords `[Am, G, Am]`, words `Are you going to Scarborough Fair`.
- `` `a tune named as the song leaves its title to the page` ``: same tune, `songTitle = "scarborough  fair"`, gives no
  titles, and `describedAs` leads with the heading.
- `` `a chord repeated straight after itself is said once` ``: `[G, G, C, G]` gives `[G, C, G]`.
- `` `syllable marks are not read` ``: `w:hold_ the * note~and | on\-ward` gives `hold the note and on-ward`.
- `` `verses are read one after the other` ``: two music lines, each with `w:` lines for verse 1 and verse 2, give
  verse 1's two lines and then verse 2's; a `+:` line continues the `w:` above it.
- `` `the chords are said in the names the page draws` ``: `chordNames = [La-, Sol]` (Latin) is what `chords` holds,
  whatever the source says.
- `` `a description is one sentence per part` ``: `describedAs` with simple templates, a tune with no words leaves the
  words sentence out.

The semantics wiring itself is one line; a UI test (open a song with a tune, find the node by the description built
from the resources' templates) is worth adding to plan 06's `SheetMusicReopenTest` file as
`` `a screen reader hears a tune's chords, not its source` `` if that test song exists by then.
