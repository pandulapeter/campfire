# Read the title and the artist from the first lines a chord page is copied with

**Kind:** bug  ·  **Severity:** high  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverter.kt`,
new `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetTitles.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverterTest.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/CLAUDE.md`
**Challenged:** amended — dropped the `X by Y` split without a page kind (its own rule split "Stand By Me" and
"Stand by Me") and the `X (Y)` artist split ("Hallelujah (Live)"); the page kind is stripped before the dash split;
the chord-summary check stays in the converter, which owns `chord()`.

Run after plans 01 and 02 (the `SECTION` kinds they add are what step 3 counts).

## Problem

`extractTitle` takes a plain-text title only in one shape:

```kotlin
val first = kinds.indexOfFirst { it != Kind.BLANK }
if (first >= 0 && kinds[first] == Kind.LYRIC && !isChordLine(lines[first].text) && (isTitle(lines[first], lines) ||
        kinds.getOrNull(first + 1) == Kind.BLANK && kinds.indexOf(Kind.CHORD) in (first + 2)..(first + 4))) {
    …
    val split = title.split(Regex(" +[-–—] +"), limit = 2)
    if (split.size == 2) {
        header.add(0, "{artist: ${headerValue(split[0])}}")
        header.add(0, "{title: ${headerValue(split[1])}}")
    } else header.add(0, "{title: ${headerValue(title)}}")
    …
    val credit = lines.getOrNull(artist)?.let { credit.matchEntire(it.text.trim()) }
    if (split.size == 1 && kinds.getOrNull(artist) == Kind.LYRIC && (credit != null || …)) {
```

For plain text `isTitle` is always false (no sizes, no bold), so the title must be a lyric line followed by a blank
line with a chord row two to four lines down. Probed misses, in order of how often the shape is pasted:

- The most common first line of all, Ultimate Guitar's `Riverlight Chords by The Invented Band` →
  `{title: Riverlight Chords by The Invented Band}`; `Riverlight chords` → `{title: Riverlight chords}`.
- A whole UG page: `Riverlight Chords by …` / `Capo: 2nd fret` / … The line after the title is `METADATA`, not
  `BLANK`, so no title is taken and the line stays a lyric.
- `Title` / `by The Invented Band` / blank / chords: the credit regex exists but is only consulted after a title has
  been found, and the title is not found because line 2 is not blank.
- `Title` / `The Invented Band` / blank / chords (Cifra Club's shape): same.
- `Title` / chords with no blank line between.
- `Title` / blank / `[Verse 1]` / lyrics with no chords (a lyrics site): no `CHORD` in the window, so no title, and
  the editor opens with the placeholder title while the real one sits in the lyrics.
- `Chords used: G D Em C` / blank / `[Verse]` / chords → `{title: Chords used: G D Em C}`.

## Fix

Put the title vocabulary into a new `internal object ChordSheetTitles` (the converter is past its line budget) and
let `extractTitle` use it:

1. `ChordSheetTitles.split(line: String): Pair<title, artist?>`:
   - strip a trailing page kind: ` (chords?|tabs?|lyrics|chords and lyrics|lyrics and chords|akkordok|akkord|
     dalszöveg|dalszövege|acordes|cifra|accordi|akkorde|akordy)` with an optional ` by <artist>` / ` – <artist>`
     after it, case-insensitive, and an optional `(ver 2)` / `(version 2)` / `v2` before it; `Riverlight Chords by
     The Invented Band` → `Riverlight` / `The Invented Band`; `Riverlight chords` → `Riverlight` / null;
   - no `by` split without a page kind before it: "Stand by Me", "Saved by the Bell", and "Killed by Death" are
     titles, and a credit on its own line is read by step 2;
   - no artist from a parenthesized tail: `Hallelujah (Live)`, `Riverlight (Acoustic)`, `(feat. X)` are part of the
     title as often as not, so the line stays whole;
   - the page kind is stripped first, then `A - B` / `A – B` is split as today (artist first; `The Invented Band -
     Riverlight Chords` → `Riverlight` / `The Invented Band`), noted as a documented limit (both orders are common;
     see `CLAUDE.md`).
   It is a pure function of the line's text; it needs nothing private of the converter.
   The same word list exists in `:presentation`'s `SharedTextName.kt` for file names; leave that one alone, the two
   serve different outputs.
2. Widen the shape `extractTitle` accepts. With `first` the first non-blank line:
   - it must be `Kind.LYRIC`, not a chord row, not starting with `[` or `{`, and not a `label:` line where half or
     more of the tokens after the colon are chords (`Chords used: G D Em C`; this check stays in `extractTitle`, which
     has the converter's private `chord()`), nor one of the words `Chords`, `Tab`, `Lyrics` alone;
   - skip over a credit line (below) and any `METADATA` lines right after it (they are the song's header anyway;
     `the row campfires PDF heads a song with…` then reads `Shapes` / `Someone` as title and artist, and its
     `contains` assertions stay green), then accept when the next
     line is `BLANK` **or** a `CHORD`/`SECTION` line, and the first `CHORD` or `SECTION` of the sheet is within the
     five lines after `first`;
   - a credit line at `first + 1` (`credit.matchEntire`, or a shorter `LYRIC` line that is neither a chord row nor a
     heading nor metadata, followed by `BLANK` or a `METADATA` line, which is how a copy of Campfire's own PDF reads:
     title, artist, the header row) is the artist, as the size rule does for documents today; only where
     `ChordSheetTitles.split` gave no artist (today's `split.size == 1` guard).
3. Keep the existing document rules (`isTitle`, `isTitleContinuation`, the bold/size credit) untouched; the new
   rules apply where `isTitle` is false.

## Tests

- `a UG title line names the title and the artist`: `Riverlight Chords by The Invented Band` → `{title: Riverlight}`
  `{artist: The Invented Band}`; `Riverlight chords` → `{title: Riverlight}`; `Riverlight Chords (ver 2) by X` too;
  `Stand by Me` and `Hallelujah (Live)` above chords stay whole titles with no artist.
- `a title above metadata lines is still the title`: `Riverlight Chords by X` / `Capo: 2nd fret` / `Key: G` / blank
  / `[Verse 1]` / chords → title, artist, `{capo: 2}`, `{key: G}`.
- `a title, its credit, and a blank line before the chords`: both `by The Invented Band` and a bare artist line.
- `a lyrics-only paste with headings gets its title`: `Riverlight` / blank / `[Verse 1]` / four lyric lines →
  `{title: Riverlight}` and a verse section.
- `a chord summary line is not a title`: `Chords used: G D Em C` / blank / `[Verse]` / chords → no `{title}` holding
  chords; `Chords` alone is not a title either.
- `a lyric line that looks like a title is left alone without structure`: `Riverlight` / blank / four prose lines →
  escaped prose, no title (the settled rule: no structure means prose).

Keep `derives plain title and expands tabs`, `styled page titles split songbooks…`, and `a title wrapped over several
lines…` green. Add one line to `convert/CLAUDE.md`: the title shapes come from `ChordSheetTitles`; `Artist - Title` is
read artist first (a documented limit, since both orders are common on the web).
