<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Plan 01: read ABC blocks, import ABC tunes

**Status:** first draft, 2026-10-26. Nothing implemented.

**Goal:**

- a `{start_of_abc}` block reads as what it is (verbatim notation), not as lyrics;
- an `.abc` file, or ABC text pasted, shared, or dropped, imports as one song per tune, with a chord grid and lyrics
  the player can use straight away.

No dependency, no network request, no file format change. The follow-up, staff notation drawn by
abc2svg, is sketched in the [README](README.md); this plan writes no engraver and needs none.

## What exists today

- `abc`, `ly`, `svg`, and `textblock` are delegated environments (`ChordProEnvironments.delegateEnvironments`). The
  parser, highlighter, transposer, splitter, and Prettify already leave their lines alone.
- But the model forgets that. `ChordProSectionBuilder.addContent` turns each verbatim line into
  `ChordProLine.Lyrics(text, chords = emptyList())`, in a section of type `SectionType.Custom("abc")`. So:
  - the viewer draws `K:G` and `|:GABc dedB|` in the lyrics font (`SongSectionContent.kt`), headed "Abc"
    (`DefaultSectionLabels.labelOf`);
  - the PDF wraps them as prose (`PrintRows.partRows` → `lyricsRows`);
  - lyrics-only mode keeps them, since `needsChords()` (`SongRenderSections.kt`) is only `Tab || Grid`.
- An `.abc` file has an unknown extension: it's read as text by content (`ImportLimits.isTextOfUnknownName`), and
  imported only if `ChordSheetStructure.hasSongStructure` passes, then as escaped prose.
  `ChordSheetConverter.isChordPro` can also misread ABC's inline `[CEG]` chords as ChordPro chords.
- Pasted text becomes a `.txt` `ImportedFile` (`sharedTextsToImportedFiles`) and is classified only in
  `PrepareImportUseCaseImpl.planSongs`'s plain-text branch; it never reaches
  `ArchiveLocalSourceImpl.foreignSongs`.

## Steps

### A1. A verbatim line kind in the model

- Add `ChordProLine.Verbatim(text: String, environment: String)` and have `ChordProSectionBuilder.addContent` produce it
  for `LineMode.VERBATIM` lines of `abc`, `ly`, and `svg`. `textblock` is prose by definition and stays `Lyrics`.
- Every exhaustive `when` over `ChordProLine` gains the case. Verbatim lines carry no chords, no markup, and no
  definitions: `ChordProChords`, `SongParts` (scales), `ChordProChorusRecalls`, `ChordProChordRewriter`,
  `ChordProSong`, `LineRuns`, `PrintRows`, `SongSectionContent`, `LayoutBudget`, `SongRenderSections`.
- The test-only `ChordProSerializer` writes them back verbatim; the round-trip oracle must keep passing.
- Tests: change `ChordProParserTest`'s "the lines of an abc block are kept verbatim with no chords" to assert
  `Verbatim`; add `ly` and `svg` cases and a `textblock` case that stays `Lyrics`.

### A2. Drawn as a monospace block, hidden in lyrics-only mode

- **Runs:** a new `FoldableKind.VERBATIM` in `songLayout/`, grouped by `groupIntoRuns` like tab runs.
- **Viewer:** a verbatim run reuses `SongTabBlock` and `TabRows` in `monospaceLyricsStyle`, but **always takes the
  preformatted path** (`ChordProTabWrapper.wrapPreformatted`). Otherwise a tied ABC line full of `-` could pass
  `ChordProTokens.isStaffLine` and be drawn with staff strokes.
- **Heading:** the section is labelled from strings, not by capitalising the environment: "ABC notation" / "ABC-kotta"
  for `abc`, "LilyPond" for `ly`, "SVG" for `svg` (an explicit `label=` still wins). New keys in `values` and
  `values-hu`.
- **Lyrics-only:** `needsChords()` covers `Verbatim`, so a song with chords off drops these lines, and a section made
  only of them disappears with its heading, as tab-only sections do.
- **PDF:** `PrintRows` prints a verbatim run as `tabRows` does (preformatted, `tabStyle`), gated on `options.showChords`
  like grids. It isn't selectable text: read back, ABC's `"G"` and `[CEG]` would become chords.
- Docs: `chordpro/CLAUDE.md` (delegated environments are now their own line kind), `ui/songView/model/CLAUDE.md`
  (lyrics-only drops verbatim lines too), `ui/songLayout` and `ui/print` `CLAUDE.md` where they list run kinds.
- Tests: `LineRunsTest`, `RenderSectionsTest` (lyrics-only drops the section), `PrintLayoutTest`, and a golden in
  `:tools:screenshots` if the song details goldens show a delegated block.

### A3. ABC reading in `:chordpro` (new package `abc/`)

A sibling of `convert/`, not inside it: `convert/` knows no file format, and this is one. Pure, total (never throws),
`explicitApi()`, with its own `CLAUDE.md`.

- **`AbcText.isAbc(text)`:** the sniff. True when the first lines that aren't blank or `%` comments are information
  fields, at least one of them `X:` or `T:`, and a `K:` field comes before the first line of music. A ChordPro
  directive before that point means it isn't ABC.
- **`AbcTunes.split(text)`:** one tune per `X:`, each ending at a blank line, as the standard says. The file header
  (fields before the first `X:`) is prepended to every tune, so each stays a valid tune on its own. Text before the
  first `X:` that isn't a header is dropped, as a free-text line in ABC is.
- **`AbcBars`:** a tokenizer over the tune body. It reads only what plan 01 needs: bar lines (`|`, `||`, `|]`, `[|`,
  `|:`, `:|`, `::`, `:|:`), voltas (`[1`, `|1`, `:|2`), quoted chord symbols, inline fields (`[K:D]`, `[M:3/4]`), line
  continuations (`\`), and comments; every other character is music it skips. It stays this small: notation is
  abc2svg's job (README), never a reason to grow it.
- **`AbcToChordPro.convert(tune)`** writes one song's ChordPro text:
  1. **Header:**
     - first `T:` → `{title}`, a second `T:` → `{subtitle}`;
     - `C:` → `{artist}`, since the songs list and the file name are artist and title;
     - `R:` (reel, jig, hornpipe) → a tag;
     - `K:` → `{key}`, from the tonic and mode. Check what `{key}` accepts before writing this: if a mode
       (`Edor`, `Amix`) can't be written, fold minor-like modes (dorian, phrygian, aeolian, locrian) to minor and the
       rest to major. `K:none` and `K:HP` give no key.
     - `M:` → `{time}`, with `C` written as 4/4 and `C|` as 2/2. A free meter (`M:none`) gives no time.
     - `Q:` → `{tempo}` as the number after `=` (`Q:1/4=120` → 120). `ChordProTempo.parse` takes the first number it
       sees, which would be the 1 of the beat. A bare `Q:120` is taken as written.
  2. **Chords:** a `{start_of_grid}` run, when the tune has any chord symbols.
     - One grid line per line of music, one grid bar per ABC bar, ABC's bar lines and voltas written in grid syntax
       (`|]` → `|.`, `::` → `:|:`, `[1` → `|1`).
     - A bar's chords become its cells, in order. A bar with no chord is `%` (the previous one continues).
     - A quoted string that isn't a chord is skipped: annotations start with `^`, `_`, `<`, `>`, or `@`, and anything
       the chord parser rejects is text. A parenthesised alternative (`"(Em)"`) is skipped too.
     - Cells are chords in order, not beats: placing them on beats needs note durations, which this
       tokenizer doesn't read; the follow-up may take them from abc2svg's parsed tune.
     - Write a bare `{start_of_grid}` (`{start_of_grid: …}` would turn the value into a heading).
  3. **Lyrics:** `W:` lines (words printed after the tune) → a plain verse section, a blank `W:` starting the next
     verse. `w:` lines stay in the ABC block: they're aligned to notes, and abc2svg draws them under
     the staff in the follow-up.
  4. **The tune itself**, whole and unchanged (the prepended header included), inside `{start_of_abc}` …
     `{end_of_abc}`. A line that would close the block early can't occur in valid ABC; if one does (a line that
     `ChordProDirectives.matchDelegatedDirective` accepts), it's prefixed with `%`, which makes it an ABC comment.
- Tests in `chordpro/src/commonTest/.../abc/`, on tunes written for the tests (traditional tunes, our own
  transcriptions): the sniff on ABC, on ChordPro with an ABC block, and on prose with a `K:` in it; multi-tune files
  and the file header; each header mapping; the grid (repeats, voltas, chordless bars, annotations, inline key
  changes); `W:` verses; a tune with no chords gives no grid; the output parses (`ChordProParser`) with its grid
  tokens as expected.

### A4. Import: files, archives, paste, and sharing

- **Extension:** add `.abc` to `LibraryFiles.FOREIGN_SONG_EXTENSIONS` (read with the text limit, importable, never a
  library file) and to `PrepareImportUseCaseImpl`'s own copies.
- **Files and archives:** `ArchiveLocalSourceImpl.foreignSongs` sends `.abc` (by extension), and sniffed text that
  `AbcText.isAbc` accepts, to a new `backup/AbcFile.read(text, fallbackTitle)`. It returns one `ImportedFile` per tune,
  numbered within the batch by `BatchNames` as `JsonSongs`' songs are, and uses the file name for a tune with no `T:`.
- **Paste, share, and drop of text:** `PrepareImportUseCaseImpl.planSongs`'s plain-text branch asks `AbcText.isAbc`
  **before** `ChordSheetConverter`, and converts each tune with `AbcToChordPro`. One tune opens the editor, as a pasted
  chord sheet does; several go to the import review. `isConverted` is true.
- **Message:** a tune with no chord symbols would get `songs_paste_no_chords` ("lyrics only"), which is wrong for a
  melody. Add one message for an ABC tune without chords (the notation is kept, there are no chords to show), in
  `values` and `values-hu`, chosen in `ui/messages/Messages.kt`.
- **Platforms**, so "Open with", sharing, and the pickers offer `.abc`:
  - Android: a VIEW `pathPattern` for `.abc` and the `text/vnd.abc` MIME type in `AndroidManifest.xml` (not the
    default handler: Campfire isn't an ABC editor).
  - iOS: an imported type declaration (`org.abcnotation.abc`, conforming to `public.plain-text`, extension `abc`, MIME
    `text/vnd.abc`) in `Info.plist`, a document type with the `Alternate` rank, and `abc` in
    `ShareInboxWriter.swift`'s `songExtensions`.
  - Desktop and web pickers are unfiltered, and folder walks use `isImportableFileName`, so they need nothing. No
    desktop file association, for the same reason as Android.
- Tests: `AbcFileTest` beside `OnSongFileTest`; `ArchiveLocalSourceTest` (an `.abc` inside a zip);
  `PrepareImportUseCaseImplTest` (an `.abc` file, pasted ABC, pasted ChordPro containing an ABC block stays ChordPro);
  `PastedSongControllerTest` (one tune opens the editor).

### A5. Docs

- Root `CLAUDE.md`: the import format lists (the intro and "Other apps' songs and backups") name ABC.
- `data/source/local/implementation/CLAUDE.md` "Other apps' libraries" and `domain/implementation/.../importing/CLAUDE.md`:
  the `.abc` route and the sniff's place before the chord-sheet converter.
- The new `chordpro/.../abc/CLAUDE.md`, and `chordpro/CLAUDE.md`'s list of packages.
- At release time, not as part of this plan: the store listings, the website's format list, and the release notes.

## Order and size

- A1 and A2 go together and ship on their own: they fix the raw text on screen for every file that already has an ABC
  block. About two days.
- A3 is independent of A1 and A2. About three days, most of it the grid and its tests.
- A4 needs A3. About two days, the platform declarations included.
- One commit per step, or per platform in A4.

## Verification

- `./gradlew desktopTest` carries the logic: parser, runs, render sections, print layout, the `abc/` package, and the
  import tests.
- `:tools:screenshots:test` for the song details goldens, if A2 changes one.
- Once A4 lands, open an `.abc` file with Campfire on Android and share one into the iOS extension. The declarations
  are the only part no test covers.
