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

**Status:** first draft, 2026-10-26, checked against the code the same day. Nothing implemented. Written from a read of
the code and of the [ABC 2.1 standard](https://abcnotation.com/wiki/abc:standard:v2.1). Paths below are repository
paths; `ui/` is `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/`, as in the root
`CLAUDE.md`.

**Goal:**

- a `{start_of_abc}` block reads as what it is (verbatim notation), not as lyrics;
- an `.abc` file, or ABC text pasted, shared, or dropped, imports as one song per tune, with a chord grid and lyrics
  the player can use straight away.

No dependency, no network request, no file format change. Staff notation is a follow-up (the last section); this plan
writes no engraver and needs none.

## Decisions

- **The library stays ChordPro.** An `.abc` file is an import format, like OnSong; a tune is kept whole inside
  `{start_of_abc}` in its `.cho`, which is what official ChordPro reads too. Sync, sharing, and file names don't change.
  Keeping `.abc` files in the library as their own type would give every importer, sync rule, export, and the
  file-name-is-identity rule a second case.
- **The ABC block is the source, the grid a one-off translation.** The import derives a `{start_of_grid}` section and a
  lyrics section once; editing the ABC later doesn't regenerate them.
- **We don't write an engraver.** This plan reads only what a chord grid needs (bars and chord symbols). Staff notation
  comes from an open-source renderer (the follow-up); if none passes its spike, ABC stays verbatim text.
- **ABC blocks are not hidden:** the file names them, and a user who wrote one should see it is there.

## What exists today

- `abc`, `ly`, `svg`, and `textblock` are delegated environments (`ChordProEnvironments.delegateEnvironments`). The
  scanner, parser, highlighter, transposer, splitter, summary cache, and Prettify already leave their lines alone.
- But the model forgets that. `ChordProSectionBuilder.addContent` turns each `LineMode.VERBATIM` line into
  `ChordProLine.Lyrics(text, chords = emptyList())`, in a section of type `SectionType.Custom("abc")`. So:
  - the viewer draws `K:G` and `|:GABc dedB|` in the lyrics font (`SongSectionContent.kt`), headed "Abc", since
    `DefaultSectionLabels.labelOf(SectionType.Custom)` (`ui/songLayout/DefaultSectionLabels.kt`) capitalises an
    environment it has no string for;
  - the PDF wraps them as prose (`PrintRows.partRows` → `lyricsRows`);
  - lyrics-only mode keeps them, since `needsChords()` (`SongRenderSections.kt`) is only `Tab || Grid`.
- An `.abc` file has an unknown extension, so it is read by content (`ImportBudget.readUnknown`, flagged
  `isPlainTextByContent`). `ArchiveRepository.songsIn` finds nothing in it (`ArchiveLocalSourceImpl.foreignSongs` knows
  JSON, XML, and `.onsong`), and `PrepareImportUseCaseImpl` keeps a sniffed text only when
  `ChordSheetStructure.hasSongStructure` passes, which a tune (no directive, no `[G]Hello`, chords in quotes) rarely
  does: it is reported skipped. One that passes is imported as escaped prose, and `ChordSheetConverter.isChordPro` can
  misread ABC's bracketed note groups (`[CEG]`) as ChordPro chords where one spells a chord name.
- Pasted or shared text becomes a `.txt` `ImportedFile` (`sharedTextsToImportedFiles`) and is classified only in
  `PrepareImportUseCaseImpl.planSongs`'s plain-text branch, through `ChordSheetConverter`; it never reaches
  `foreignSongs`.

## Steps

### A1. A verbatim line kind in the model

- Add `ChordProLine.Verbatim(text: String, environment: String)` and have `ChordProSectionBuilder.addContent` produce it
  for `LineMode.VERBATIM` lines of `abc`, `ly`, and `svg`. `textblock` is prose by definition and stays `Lyrics`.
- Every exhaustive `when` over `ChordProLine` gains the case. Verbatim lines carry no chords, no markup, and no
  definitions. The ten files with one today: `ChordProChords`, `SongParts` (scales), `ChordProChorusRecalls`,
  `ChordProChordRewriter`, `ChordProSong`, `LineRuns`, `PrintRows`, `SongSectionContent`, `LayoutBudget`,
  `SongRenderSections`.
- The test-only `ChordProSerializer` writes them back verbatim; the round-trip oracle must keep passing.
- Tests: change `ChordProParserTest`'s "the lines of an abc block are kept verbatim with no chords" to assert
  `Verbatim`; add `ly` and `svg` cases and a `textblock` case that stays `Lyrics`.

### A2. Drawn as a monospace block, hidden in lyrics-only mode

- **Runs:** a new `FoldableKind.VERBATIM`, returned by `foldableKind()` (`ui/songLayout/LineRuns.kt`) and grouped by
  `groupIntoRuns` like tab runs. A delegated environment always opens a section of its own (`SectionType.Custom`,
  unlike tab and grid, which switch the line kind inside the running section), so the run is the whole section and
  folds as the section, with no toggle of its own.
- **Viewer:** `SongSectionContent`'s run `when (kind)` draws a verbatim run with `SongTabBlock` and
  `textMeasurements.tabRows` in `monospaceLyricsStyle`, but **always on the preformatted path**
  (`ChordProTabWrapper.wrapPreformatted`): `SongTabBlock` picks it today only when `ChordProTabWrapper.isTablature` says
  no line is a staff, and a tied ABC line full of `-` could pass `ChordProTokens.isStaffLine` and be drawn with staff
  strokes.
- **Heading:** `DefaultSectionLabels.labelOf(SectionType.Custom)` gets cases for `abc`, `ly`, and `svg`, read from
  strings beside `song_details_section_tab` in `values` and `values-hu`: "ABC notation" / "ABC-kotta", "LilyPond",
  "SVG". An explicit label (`{start_of_abc: Melody}`) still wins. `labelOf(FoldableKind)` gains `VERBATIM` for the
  `when`, named from the first line's `environment`.
- **Lyrics-only:** `needsChords()` covers `Verbatim`, so a song with chords off drops these lines, and a section made
  only of them disappears with its heading, as tab-only sections do.
- **PDF:** `PrintRows.partRows` prints a verbatim run as `tabRows` does (preformatted, `tabStyle`), gated on
  `options.showChords` like grids (tabs are gated on `options.showTabs`). It isn't selectable text: read back, ABC's
  `"G"` and `[CEG]` would become chords.
- Docs: `../../chordpro/CLAUDE.md` (delegated environments are now their own line kind, in the model section too),
  `ui/songView/model/CLAUDE.md` (lyrics-only drops verbatim lines; the run toggle rule names the third kind), and
  `ui/print/CLAUDE.md` where it lists what a row never splits.
- Tests: `LineRunsTest`, `RenderSectionsTest` (lyrics-only drops the section), `PrintLayoutTest`. No song of the
  `:tools:screenshots` goldens has a delegated block, so none changes.

### A3. ABC reading in `:chordpro` (new package `abc/`)

A sibling of `convert/`, not inside it: `convert/` knows no file format, and this is one. Pure, total (never throws),
`explicitApi()`, with a `CLAUDE.md` of its own.

- **`AbcText.isAbc(text)`:** the sniff. True when the first lines that aren't blank or `%` comments are information
  fields, at least one of them `X:` or `T:`, and a `K:` field comes before the first line of music. A ChordPro
  directive before that point means it isn't ABC.
- **`AbcTunes.split(text)`:** one tune per `X:`, each ending at a blank line, as the standard says. The file header
  (fields before the first `X:`) is prepended to every tune, so each stays a valid tune on its own. Text before the
  first `X:` that isn't a header is dropped, as a free-text line in ABC is.
- **`AbcBars`:** a tokenizer over the tune body. It reads only what this plan needs: bar lines (`|`, `||`, `|]`, `[|`,
  `|:`, `:|`, `::`, `:|:`), voltas (`[1`, `|1`, `:|2`), quoted chord symbols, inline fields (`[K:D]`, `[M:3/4]`), line
  continuations (`\`), and comments; every other character is music it skips. It stays this small: notation is the
  renderer's job (the follow-up), never a reason to grow it.
- **`AbcToChordPro.convert(tune)`** writes one song's ChordPro text:
  1. **Header:**
     - first `T:` → `{title}`, a second `T:` → `{subtitle}`;
     - `C:` → `{artist}`, since the songs list and the file name are artist and title;
     - `R:` (reel, jig, hornpipe) → `{tag: …}`;
     - `K:` → `{key}`. `{key}` is read by `ChordProTransposer.keyOf`: a chord name, or a note followed by one of
       `ChordProChordRewriter.keyWords` (major, minor, and their translations); any other word is read as a chord
       suffix, so `Edor` would come out E major. Write `Em` for the minor-like modes (dorian, phrygian, aeolian,
       locrian, and `m` / `min` / `minor`) and the bare tonic for the rest (major, ionian, lydian, mixolydian). Clef
       and other modifiers (`K:G clef=bass`) and explicit accidentals are dropped. `K:none` and `K:HP` give no key.
     - `M:` → `{time}`, copied as written: `ChordProTime.parse` already reads `C`, `C|`, and a fraction. A sum
       numerator (`(3+2)/8`) is written as its total (`5/8`). A free meter (`M:none`) gives no time.
     - `Q:` → `{tempo}` as the number after `=` (`Q:1/4=120` → 120). `ChordProTempo.parse` takes the first number it
       sees, which would be the 1 of the beat. A bare `Q:120` is taken as written.
  2. **Chords:** a `{start_of_grid}` run, when the tune has any chord symbols.
     - One grid line per line of music, one grid bar per ABC bar, ABC's bar lines and voltas written in grid syntax.
       `ChordProTokens.isBar` accepts `|`, `||`, `|.`, `|:`, `:|`, `:|:`, and voltas `|1`, `:|2`, `:|2>`, so:
       `|]` → `|.`, `[|` → `||`, `::` → `:|:`, `[1` → `|1`, `[2` → `|2`; the rest as written.
     - A bar's chords become its cells, in order. A bar with no chord is `%` (the previous one continues).
     - A quoted string that isn't a chord is skipped: annotations start with `^`, `_`, `<`, `>`, or `@`, and anything
       the chord parser rejects is text. A parenthesised alternative (`"(Em)"`) is skipped too.
     - Inline fields are read only to be skipped: a `[K:D]` mid-tune changes no chord name, since the symbols are
       written as spelled, so `{key}` stays the opening key.
     - Cells are chords in order, not beats: placing them on beats needs note durations, which this
       tokenizer doesn't read; the follow-up may take them from the renderer's parsed tune.
     - Write a bare `{start_of_grid}` (`{start_of_grid: …}` would turn the value into a heading).
  3. **Lyrics:** `W:` lines (words printed after the tune) → a plain verse section, a blank `W:` starting the next
     verse. `w:` lines stay in the ABC block: they're aligned to notes, and the follow-up draws them under the staff.
  4. **The tune itself**, whole and unchanged (the prepended header included), inside `{start_of_abc}` …
     `{end_of_abc}`. A line that would close the block early can't occur in valid ABC; if one does (a line that
     `ChordProDirectives.matchDelegatedDirective` accepts), it's prefixed with `%`, which makes it an ABC comment.
- Tests in `chordpro/src/commonTest/.../abc/`, on tunes written for the tests (traditional tunes, our own
  transcriptions): the sniff on ABC, on ChordPro with an ABC block, and on prose with a `K:` in it; multi-tune files
  and the file header; each header mapping, modes included; the grid (repeats, voltas, chordless bars, annotations,
  inline key changes); `W:` verses; a tune with no chords gives no grid; the output parses (`ChordProParser`) with its
  grid tokens as expected.

### A4. Import: files, archives, paste, and sharing

- **Extension:** add `.abc` to `LibraryFiles.FOREIGN_SONG_EXTENSIONS` (read with the text limit, importable through
  `IMPORTABLE_EXTENSIONS`, never a library file) and name it in that constant's KDoc. `PrepareImportUseCaseImpl` needs
  no change for the extension: a loose file goes through `ArchiveRepository.songsIn` before its own extension check, so
  an `.abc` arrives as `.cho` songs, as an `.onsong` does. `LibraryFilePredicatesTest` lists the importable names.
- **Files and archives:** `ArchiveLocalSourceImpl.foreignSongs` sends `.abc` (by extension, text-like bytes only, as
  `.onsong`) and sniffed text that `AbcText.isAbc` accepts to a new `backup/AbcFile.read(text, fallbackTitle)`. It
  returns one `ImportedFile` per tune, named and numbered within the batch through `ForeignSongHeader` and
  `BatchNames` as `JsonSongs`' songs are, and uses the file name for a tune with no `T:`.
- **Paste, share, and drop of text:** `PrepareImportUseCaseImpl.planSongs`'s plain-text branch asks `AbcText.isAbc`
  **before** `ChordSheetConverter`, and returns one ChordPro text per tune (`AbcTunes.split` then `AbcToChordPro`). That
  fits the branch as it is: `converted` is already a list, `isConverted` is already "differs from the original", and
  `ChordProSplitter` keeps each part. One tune opens the editor, as a pasted chord sheet does
  (`PastedSongController.newSongFromText`); several go to the import review.
- **Message:** a tune with no chord symbols would get `songs_paste_no_chords` ("lyrics only"), which is wrong for a
  melody. `Message.NewSongFromText` carries `hasChords` and `isConverted`; add a flag for notation and one message for
  an ABC tune without chords (the notation is kept, there are no chords to show), in `values` and `values-hu`, chosen in
  `ui/messages/Messages.kt`.
- **Platforms**, so "Open with", sharing, and the pickers offer `.abc`:
  - Android: the VIEW filter in `AndroidManifest.xml` lists four `pathPattern`s per extension (one per depth of dots,
    see its comment); add the four for `.abc`, and `text/vnd.abc` beside `text/plain` in the MIME filter. Not the
    default handler: Campfire isn't an ABC editor.
  - iOS: an imported type declaration in the app's `Info.plist` (`org.abcnotation.abc`, conforming to
    `public.plain-text`, extension `abc`, MIME `text/vnd.abc`) and a document type with the `Alternate` rank. The share
    extension's `NSExtensionActivationRule` already accepts anything conforming to `public.plain-text`, so the
    declaration is what makes an `.abc` reach it; `ShareInboxWriter.swift`'s `songExtensions` gets `abc` (its comment
    on `.onsong` explains why that one is absent).
  - Desktop and web pickers are unfiltered, and folder walks use `isImportableFileName`, so they need nothing. No
    desktop file association, for the same reason as Android.
- Tests: `AbcFileTest` beside `OnSongFileTest`; `ArchiveLocalSourceTest` (an `.abc` inside a zip, and a loose one);
  `PrepareImportUseCaseImplTest` (pasted ABC, two pasted tunes, pasted ChordPro containing an ABC block stays ChordPro);
  `PastedSongControllerTest` (one tune opens the editor).

### A5. Docs

- Root `../../CLAUDE.md`: the import format lists (the intro and "Other apps' songs and backups") name ABC.
- `../../data/source/local/implementation/CLAUDE.md` "Other apps' libraries" (the `.abc` route and the sniff) and
  `domain/implementation/.../importing/CLAUDE.md` (`songsIn` now knows `.abc`; the sniff's place before the
  chord-sheet converter in the plain-text branch).
- The new `chordpro/.../abc/CLAUDE.md`, and `../../chordpro/CLAUDE.md`'s rules where they name the module's packages.
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
- Once A4 lands, open an `.abc` file with Campfire on Android and share one into the iOS extension. The declarations
  are the only part no test covers.

## Follow-up: staff notation (not planned)

Worth planning once this plan has landed and users ask for notation; the audience (folk melody players) is narrower
than the app's. Notes for whoever plans it:

- **Renderer:** [abc2svg](https://chiselapp.com/user/moinejf/repository/abc2svg) (Jean-François Moine, the author of
  abcm2ps) turns ABC into SVG in plain JavaScript, needs no DOM to measure its layout, and ships a QuickJS runner, so
  it runs headless. Android, iOS, and desktop would run it in QuickJS through a Kotlin Multiplatform binding, in a
  module of its own (`:abc:api` / `:abc:implementation`, depending on nothing of the app's, like `:metronome` and
  `:tuner`); the web build runs it in the browser behind the same `expect`/`actual` interface, since no binding has a
  wasmJs target.
- **Drawing:** the SVG drawn into a Compose canvas (Skia's SVG DOM through Skiko on desktop, iOS, and the web; a
  library such as AndroidSVG on Android). Drawn in Compose, the PDF needs no work of its own: `PrintRenderer`
  rasterizes the same drawing, a staff system one row the layout never splits, like a tab system; notation stays out
  of the PDF's selectable text layer, as diagram labels do.
- **The hook:** `ChordProLine.Verbatim` from A1. An `abc` run draws as notation instead of monospace text, `ly` stays
  monospace, and `svg` blocks could be drawn by the same SVG renderer.
- **Spike first:** the license (current abc2svg sources say LGPL, older ones GPL-2; confirm the version and that an
  unmodified script bundled in an MPL-2.0 app is fine on every store); which QuickJS binding is maintained, its
  license, size per ABI, and whether it builds on every target shipped (Windows included); whether abc2svg's music
  glyphs render in Skia's SVG DOM and AndroidSVG, and how to recolour the output for the theme and dark mode; the time
  to render a typical tune on the smallest supported phone and to re-render at a new width (a resized window must
  reflow systems with an animation, and every line must stay reachable with the pedal); and the credits for every
  shipped piece, transitive ones included (`OpenSourceComponents.kt`, the licenses folder, `THIRD_PARTY_SOFTWARE.md`).
- **Open questions:** transposition and capo (this plan leaves the ABC text as written while the grid moves; abc2svg
  can transpose, so the staff could show what sounds, with its chord symbols in the app's display notation or hidden in
  favour of the grid), and chords over lyrics (the renderer's parsed tune, with note times and `w:` syllables, might
  place chords over the `w:` lyrics syllable by syllable, and put grid cells on beats, which this plan leaves to order
  only).
