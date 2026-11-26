# Leave only a real contents page out of a songbook, keeping lyrics-only songs that name other songs' titles

**Challenged:** amended — the entry rule is now concrete (a line starts or ends with a title, judged on `contentsWords`,
which already drops numbers, dot leaders, and page numbers), measured over at least half of the part's lines; a title is
a song's first line *with words*; every existing contents test is named with why it still passes; the linearity guard is
the file's quarter-versus-whole style, since 2,000 songs under the fixed 30 s bound would not fail the current code.

**Kind:** bug (data loss) + performance  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `libs/chordsheet/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverter.kt`,
`libs/chordsheet/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverterTest.kt`,
`libs/chordsheet/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/CLAUDE.md` (its split bullet states
the rule: "a part playing no chord whose lines name two of the other songs by title is the contents")

## Problem

`89baf08a5` (after 5.6.0) drops any part of a document of three or more that plays no chord and whose lines contain the
first lines of two other parts, as whole words anywhere in a line:

```kotlin
return songs.filterIndexed { index, song -> !isContents(song, songs.filterIndexed { other, _ -> other != index }) }
...
private fun isContents(song: List<Rendered>, others: List<List<Rendered>>): Boolean {
    if (others.size < 2 || chordCandidates(song).any { it != null }) return false
    val entries = song.map { contentsWords(it.text) }.filter { it.isNotEmpty() }
    val titles = others.mapNotNull { other -> other.firstOrNull { it.text.isNotBlank() }?.let { contentsWords(it.text) } }.filter { it.isNotEmpty() }
    return titles.count { title -> entries.any { entry -> " $entry ".contains(" $title ") } } >= 2
}
```

- **Data loss:** a lyrics-only hymnal PDF whose split (by title type, or by its contents through
  `ChordSheetIndexTitles`) yields songs headed "Amen", "Alleluia", and "Gloria": Gloria's "…Alleluia, alleluia, amen"
  names two titles, so Gloria is silently left out of the import. Short titles ("Holy, Holy, Holy", "Alleluia", "Jesus
  Loves Me") make this likely in any real hymnal; 5.6.0 imported every song.
- **Cost:** every part recomputes every other part's title (a regex split each) and compares every title against every
  line with two string concatenations: O(parts² × lines). A 900-hymn lyrics-only book is ~810k splits and ~24M
  `contains` calls.

How the pieces fit (checked): `isContents` runs on the cropped groups, after `ChordSheetSongSplit` has cut them. The split
reads a contents page itself (`ChordSheetIndexTitles`: a page whose lines are mostly found again on other pages) to find
where songs start, and opens a group at the first such page, so a recognised contents page is a group of its own rather
than glued to the cover and foreword. A group's first line is its title, with the songbook's counting number already cut
off (`withoutNumber`). `contentsWords` keeps letters only, so `1. Red River Valley ....... 2` and `Red River Valley` are
both `red river valley`: numbers, dot leaders, and page numbers need no separate handling.

## Fix

In `convert`, replace the per-part filter with one pass:

1. `titles`: for each part, the `contentsWords` of its first line whose words are not empty (not merely its first
   non-blank line, so a page or song number alone on a line does not hide the title). Compute these once, as a list
   indexed like the parts.
2. A `HashMap<String, Int>` from title words to how many parts carry that title (two songs may share one).
3. A part is the contents when it plays no chord (as now), the document has at least three parts (as now), and:
   - its **entry lines**, non-blank lines whose words (up to 24 words; a longer line is never an entry) *start or end*
     with a title of another part, are at least half of its non-blank lines and at least two; "another part" means a
     title in the map whose count is greater than one when it is the part's own title;
   - the entry lines name at least two distinct titles (the first or last matching prefix/suffix of each line; the
     existing "two titles at least").

   Look a line up by building its word prefixes and suffixes incrementally (at most 24 each) and probing the map: linear
   in the document, no title scanned per line. "Ends with" keeps an index set artist first (`Newton, John – Amazing
   Grace`); "starts with" keeps `Amazing Grace (John Newton) ..... 3` against a song headed `Amazing Grace`.

The half-of-the-lines rule is what saves the hymns: "Alleluia, alleluia, amen" does start with a title, but it is one line
of a stanza. The residual case, accepted: a lyrics-only part made of nothing but other parts' titles (a three-line
"Alleluia / Alleluia / Amen" chant) still reads as contents.

Update the KDoc of `isContents` (it names the rule) and the convert package `CLAUDE.md` bullet: "a part playing no chord,
at least half of whose lines start or end with another song's title, naming two at least, is the contents, no song".

## Tests

All in `ChordSheetConverterTest`:

- New: a lyrics-only three-song book (titles in a larger type so the split cuts it, as `a title is told by its type
  against the whole document's body…` builds pages) headed "Amen", "Alleluia", and "Gloria", Gloria's stanza holding
  "Alleluia, alleluia, amen" among four or more lines, imports three songs.
- Still passing unchanged (each relies on the rule above): `a title is told by its type against the whole document's body
  not against the page it opens` (two contents pages, `Alpha 2` / `1 Alpha` entries, under a `Contents` heading: 3 of 4
  lines are entries), `the numbers a songbook counts its songs by are left out of their titles…` (`100 songs`,
  `Contents`, then three entries: 3 of 5), the test asserting `{title: Alpha}\n\n[Am]Hello [C]world\n[G]Beta [F]\n` at
  `result[1]`, and the `:data:source:local:implementation` fixtures `songbook-numbered.cho` and `songbook-styled.cho`
  (the latter's `Amazing Grace ........ 1` dot-leader entries), which `desktopTest` covers.
- New: an index set artist first (`Newton – Amazing Grace`, …) is still left out.
- New linearity guard in the style of `a text of tens of thousands of lines takes time in proportion to its length`
  (quarter against whole, fastest of three): a lyrics-only book of N one-stanza songs with titles in a larger type.
  Pick N so the guard **fails on the current code** (check it before the fix; if the split or crop is itself superlinear
  in the number of songs at that N, say so in the commit and size the guard to what is linear) and passes after.
