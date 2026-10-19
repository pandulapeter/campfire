<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Keep a title's own "Chord" when reading a page's title, and strip a page word only where it ends a shared page's title or a part of it

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Challenged:** amended — stripping a shared title's page word only at its very end would keep it in `Riverlight Chords - The Invented Band`, `Riverlight Lyrics - The Invented Band | Site`, and `Chords: Riverlight`, which are stripped today; it is now stripped where it ends the title or a part of it (before a dash, colon, pipe, or comma), which still keeps `Three Chords and the Truth`.
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetTitles.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverterTest.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/CLAUDE.md`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/SharedTextName.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/platform/SharedTextNameTest.kt`

Lane I, after 04.

## Problem

`ChordSheetTitles.split` (added by 39901f6e7 for the first line of a copied chord page, read by `ChordSheetConverter`'s
`extractTitle` and `extractPlainTitle`) strips the word a site puts after a song's name to say what kind of page it is:

```kotlin
"^(.+?)(?: +$VERSION)? +(?:chords and lyrics|lyrics and chords|chords?|tabs?|lyrics|akkordok|akkord|dalszövege|" +
    "dalszöveg|acordes|cifra|accordi|akkorde|akordy)(?: +$VERSION)?(?: +(?:by|[-–—]) +(.+))?$",
```

The rule exists for Ultimate Guitar's `Riverlight Chords by The Invented Band` and its relatives (the 35th review's
plan, pinned by `a UG title line names the title and the artist` in `ChordSheetConverterTest`, which also pins a bare
`Riverlight chords` → `Riverlight`). The singular `chord` is in the list too, though no site names a page "Chord".
Verified at HEAD: `The Lost Chord` / blank / `Am     C` / `Hello world` converts to `{title: The Lost}`. A song titled
with "Chord" at its end loses the word from its header and so from its file name.

The same word list in `SharedTextName.kt` (a shared page's title, used where the shared text declares none) has the
singular too, and strips page words anywhere in the title, not only where a site puts one (at the end of the title or of a part
of it):

```kotlin
private const val PAGE_KIND_WORD = """(?:chords?|tabs?|lyrics|akkordok|akkord|dalszöveg|dalszövege)"""
private val PAGE_KIND = Regex("""\b$PAGE_KIND_WORD(?:\s*(?:and|&|és|,)\s*$PAGE_KIND_WORD)*\b""", RegexOption.IGNORE_CASE)
…
private fun String.withoutPageKind() = replace(PAGE_KIND, " ").replace(WHITESPACE, " ").trim { … }
```

So `The Lost Chord Chords by The Invented Band @ Ultimate-Guitar.Com` titles the song `The Lost`, and
`Three Chords and the Truth Chords by …` titles it `Three and the Truth` (checked with the same regexes).

A plural page word ending a title with no credit after it (`Three Chords`, `Bar Tab`, `Love Lyrics` → `Three`, `Bar`,
`Love`) cannot be told from UG's bare `Riverlight Chords` heading, which the rule is for; that stays a documented limit,
like `Artist - Title`.

## Fix

The narrowest change that keeps every pinned shape:

1. `ChordSheetTitles.kt`: `chords?` → `chords` in `pageKind`. Nothing else changes; `tabs?` stays (UG's page kind is
   the singular "Tab").
2. `SharedTextName.kt`: `chords?` → `chords` in `PAGE_KIND_WORD`, and strip a page kind only where it ends the title
   or a part of it, before the end or a separator, where a site puts it (`Riverlight Chords - The Invented Band`,
   `Chords: Riverlight`):

   ```kotlin
   private val ENDING_PAGE_KIND = Regex("""${PAGE_KIND.pattern}(?=\s*(?:$|[$SEPARATORS]))""", RegexOption.IGNORE_CASE)
   private fun String.withoutPageKind() = replace(ENDING_PAGE_KIND, " ").replace(WHITESPACE, " ").trim { it.isWhitespace() || it in SEPARATORS }
   ```

   (`SEPARATORS` is `-–—:|,`; inside a character class the leading `-` is literal; a lookahead is fine on Wasm, whose
   regexes are JavaScript's.) Not the end of the title alone: that would keep the page word in
   `Riverlight Chords - The Invented Band`, `Riverlight Lyrics - The Invented Band | Lyrics Site` (the pipe part is
   already cut), and `Chords: Riverlight`, all stripped today (checked with the same regexes). `PAGE_KIND_BY_ARTIST`
   keeps using `PAGE_KIND` (its lazy title group already stops at the last page kind before ` by `), so the artist is
   still read only from `<title> Chords by <artist>` and a bare " by " or a dash is never split (`presentation/CLAUDE.md`
   stays true); the singular `Riverlight Chord by …`, which no site writes, no longer names an artist. Update the KDoc
   of `sharedTextTitle` / `PAGE_KIND` where it says the words are left out, to "where they end the title or a part of
   it". Every existing `SharedTextNameTest` case stays green (`Oasis - Wonderwall Chords | Ultimate Guitar`,
   `Wonderwall Lyrics and Chords - example.com`, `Omega - Gyöngyhajú lány akkordok`, the bare `Chords` that falls back
   to the subject). A page word ending a part of the title stays a documented limit (`Three Chords - The Invented Band`
   → `Three - The Invented Band`, as today).
3. `convert/CLAUDE.md`, the `ChordSheetTitles` bullet: add "a title ending in a page word with no credit after it
   (`Three Chords`) loses that word, the same documented limit".

Drop step 2 if the user wants the converter's half only; the two lists serve different outputs and do not depend on
each other.

## Tests

- `ChordSheetConverterTest` (`./gradlew :chordpro:desktopTest`), `a title ending in Chord keeps it`:
  `convert("The Lost Chord\n\nAm     C\nHello world")` starts with `{title: The Lost Chord}\n\n`, and
  `convert("The Lost Chord Chords by The Invented Band\n\nAm     C\nHello world")` starts with
  `{title: The Lost Chord}\n{artist: The Invented Band}\n\n`. `a UG title line names the title and the artist` stays
  green unchanged.
- `SharedTextNameTest` (`./gradlew :presentation:desktopTest --tests '*SharedTextNameTest*'`):
  `sharedTextTitle("The Lost Chord Chords by The Invented Band @ Ultimate-Guitar.Com")` is
  `SharedTextTitle(title = "The Lost Chord", artist = "The Invented Band")`, and
  `sharedTextTitle("Three Chords and the Truth Chords by The Invented Band")` has the title `Three Chords and the Truth`.
  `sharedTextTitle("Riverlight Chords - The Invented Band")` has the title `Riverlight - The Invented Band` and
  `sharedTextTitle("Chords: Riverlight")` the title `Riverlight`, as today.
