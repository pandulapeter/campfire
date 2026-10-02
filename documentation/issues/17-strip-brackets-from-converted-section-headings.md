# Write converted section headings without the brackets, parentheses and colon that marked them
**Challenged:** sound — traced against the existing tests (only lines 57-58 of `ChordSheetConverterTest` pin the decorated labels; the `DocumentGoldenTest` goldens use plain `Verse 1`). Like plan 18, it changes what a re-import of an already-imported UG-style file produces (library copy keeps the old bracketed label, so the import asks about a conflict); accepted, since no migration is the repo's rule.

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all (every PDF, Word and text import)
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordSheetConverter.kt`, `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordSheetConverterTest.kt`, `chordpro/CLAUDE.md`

## Problem
`ChordSheetConverter.section()` detects a heading after stripping its decoration
(`text.trim().removeSurrounding("[", "]").removeSurrounding("(", ")").removeSuffix(":")`), but `convertSong`'s
`Kind.SECTION` branch writes the raw line: `{start_of_: ${headerValue(line.text.trim())}}` and
`{comment: ${headerValue(line.text.trim())}}`. The Ultimate-Guitar layout (`[Verse 1]`, `[Chorus]`) is the most common
one. Verified at 8c267e01a:

- `"[Verse 1]\nC   G\nHello world\n\n[Intro]\nC G\n\n[Chorus]\nAm F\nLa la"` converts to
  `{start_of_verse: [Verse 1]}` … `{comment: [Intro]}` … `{start_of_chorus: [Chorus]}`.
- `"Verse 1:"` gives `{start_of_verse: Verse 1:}` and `"(Chorus)"` gives `{start_of_chorus: (Chorus)}`.

The viewer then shows the label "[Verse 1]" with the brackets (and the folds and the setlist outline carry them).

## Fix
In the `Kind.SECTION` branch compute the label once with the same stripping `section()` uses (extract a private
`headingLabel(text)` = `text.trim().removeSurrounding("[", "]").removeSurrounding("(", ")").removeSuffix(":").trim()`, and
let `section()` call it) and pass that to `headerValue` for both the `start_of_` and the `{comment}` outputs. Keep the
rest of the line (the number, `Verse 2`) untouched. Do not strip anything from non-heading lines.

## Tests
In `ChordSheetConverterTest`: the Ultimate-Guitar sample above yields `{start_of_verse: Verse 1}`,
`{comment: Intro}`, `{start_of_chorus: Chorus}`; `Verse 1:` and `(Chorus)` likewise; a plain `Verse 1` is unchanged.
Update any existing expectation that pinned the bracketed label. Document the stripping in the converter bullet of
`chordpro/CLAUDE.md`.

## Manual check
Import a pasted Ultimate-Guitar style `.txt` with `[Verse 1]` headings; the song's section titles read "Verse 1".
