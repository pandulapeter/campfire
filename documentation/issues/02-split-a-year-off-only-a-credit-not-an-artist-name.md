# Take a year off the end of a credit line only, never off a value labelled Artist

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverter.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverterTest.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/CLAUDE.md`

## Problem

Converting a pasted or imported chord sheet (`ChordSheetConverter`), `metadata(text)` folds three labels onto one name:

```kotlin
"előadó", "by", "words and music by" -> "artist"
...
else -> label   // so "artist" stays "artist"
```

and `extractHeader` then splits a year off any `artist` value:

```kotlin
name == "artist" && creditYear.matches(value) -> creditYear.matchEntire(value)!!.destructured.let { (artist, year) ->
    header += "{artist: ${headerValue(artist)}}"
    header += "{year: $year}"
}
```

with `creditYear = Regex("^(.+?),?\\s+((?:1[5-9]|20)[0-9]{2})$")`, written for `Words & music by David Bowie 1972`. A plain
`Artist:` or `Előadó:` label names a performer, and some performers' names end in a four-digit number from 1500 to
2099: `Artist: The 1975` becomes `{artist: The}` and `{year: 1975}`, misnaming the song in the user's library (its
file name is `the-…cho`).

## Fix

Keep the credit apart from the artist until the header is written:

1. In `metadata(text)`, map `"by"` and `"words and music by"` to a private constant `CREDIT = "credit"` (beside
   `TRANSPOSITION` and `NO_CAPO`), and only `"előadó"` to `"artist"`. `"credit"` is not a label the
   `metadata` regex reads, so it cannot collide with one. `metadata()`'s other callers (`classify`, `isTitle`,
   `labelledRow`) only test whether a value is there or is `copyright`, so they are unaffected.
2. In `extractHeader`:

   ```kotlin
   name == CREDIT || name == "artist" -> {
       val split = (if (name == CREDIT) creditYear else commaYear).matchEntire(value)
       if (split == null) header += "{artist: ${headerValue(value)}}" else split.destructured.let { (artist, year) -> … }
   }
   ```

   where `commaYear = Regex("^(.+?),\\s+((?:1[5-9]|20)[0-9]{2})$")`: an `Artist:` value gives a year only when a comma
   sets it apart (`Artist: David Bowie, 1972`), which no band name is written as. The year then still lands in the
   header once, as the existing dedup of a credit printed twice expects.

   Option B, simpler: never split an `Artist`/`Előadó` value at all (drop `commaYear`). Recommended: A, since a
   comma-separated year after an artist is a common way sites print it, and the comma makes it unambiguous.
3. Update the KDoc of `creditYear` if needed (it already says "a credit"), and `convert/CLAUDE.md`'s line "a credit
   ending in a year gives both" to add: "an Artist label's value is a name, kept whole unless a comma sets a year off
   (`The 1975`)".

The existing regexes use `\\s` (against the module's `Char.isWhitespace` rule); keep the new one in the same form as
`creditYear` so the two read alike, or, if touching both, write them without `\\s`. Not required by this fix.

## Tests

In `ChordSheetConverterTest`, beside "a header labelled with semicolons names the song, its album, and the year its
credit ends in, each once" (which must keep passing unchanged):

- `convert("Artist: The 1975\nTitle: Somebody Else\n\nAm     C\nHello world")` starts with `{artist: The 1975}` and
  holds no `{year:`.
- `Előadó: Valaki 1972` keeps the whole name as well.
- `Artist: David Bowie, 1972` gives `{artist: David Bowie}` and `{year: 1972}` (only with option A).
- `By: Someone 1972` still gives `{artist: Someone}` and `{year: 1972}`.

Run `./gradlew :chordpro:desktopTest --tests '*ChordSheetConverterTest*'`.
