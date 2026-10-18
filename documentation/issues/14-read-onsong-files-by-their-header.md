# Read OnSong's `.onsong` files, with the title and artist lines their header starts with

**Kind:** feature  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** new `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/backup/OnSongFile.kt`,
new `.../commonTest/.../backup/OnSongFileTest.kt`, `.../source/ArchiveLocalSourceImpl.kt`,
`.../desktopTest/.../source/ArchiveLocalSourceTest.kt`,
`data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/library/LibraryFiles.kt` (an `.onsong`
entry in a new `FOREIGN_SONG_EXTENSIONS` list folded into `IMPORTABLE_EXTENSIONS`, read with the text limit by
`maxSizeOf`), `data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/library/CLAUDE.md`,
`domain/implementation/.../importing/PrepareImportUseCaseImpl.kt` (through plan 12's `songsIn` hook),
`domain/implementation/src/commonTest/.../importing/PrepareImportUseCaseImplTest.kt`,
`data/source/local/implementation/CLAUDE.md`, `app/ios/iosApp/ShareExtension/Info.plist` and
`ShareInboxWriter.swift` (comments only, see below)

**Challenged:** amended — `.onsong` in `IMPORTABLE_EXTENSIONS` is not enough for a loose file: `sort` skips any extension
outside its own sets, so the hook must run before that skip; the header rule is made exact (a chord-named title such
as `Am` is kept by the file name, a third bare line ends the header); a body mixing `{c:}` directives with chord rows
makes `convertFragment` return null (it is "ChordPro already"), so the body is converted between directive lines, and a
chordless body is escaped rather than kept as is; the iOS share extension cannot be offered `.onsong` (no system type)
and now says so; and the plan says what to do when plans 12 or 13 were declined.

Run after plan 12 (the `songsIn` hook). **Decision awaiting the user** (README): OnSong is the largest iOS performer
app and the format musicians most often ask to bring over (SongbookPro, FreeSong, and OpenSongApp all import it);
its per-song `.onsong` export is plain text and cheap to read. Its whole-library `.backup` is a zip whose loose
`.onsong`/`.chopro`/`.pdf` files this plan and plan 10 cover, but **songs typed in OnSong live only in its
`OnSong.sqlite3`**, which needs a SQLite reader of 500–800 lines; that is not part of this plan and is listed under
"not planned" in the README. **If plan 12 was declined**, this plan first adds the `ForeignSongHeader` helper and the
`songsIn` hook as plan 12 describes them (its Design's "Output" and "Loose files" bullets, without `JsonSongs`), unless
plan 13 already did; **if plan 13 was declined**, it adds plan 13's `ChordSheetLyrics.escaped` (and its test).

## Format (onsongapp.com/docs/features/formats/onsong, /metadata, /chords; verify against a sample)

- The header is everything before the first blank line. Either `Label: value` lines (`Title:`, `Artist:`, `Key:`,
  `Capo:`, `Tempo:`, `Time:`, `Duration:`, `Copyright:`, `CCLI:`, `Flow:`, `Keywords:`/`Topic:`), or bare lines: the
  first is the title, the second the artist (`Riverlight` / `The Invented Band`), with labelled lines after them.
- The body: sections as `Verse 1:` / `Chorus:` / `Bridge:` lines (a label ending in a colon alone on its line), chords
  inline in brackets `[G]` or on a row above the lyrics, `|` bar lines, `*` for a bold line, `{comment}` and `{c:}`
  ChordPro directives accepted as is.

## Fix

`internal object OnSongFile` with `fun read(text: String, fallbackTitle: String): String` (`fallbackTitle` is the file
name without its extension, which is how OnSong names its exports):

1. Split the header at the first blank line (CRLF folded). Labelled lines (`Label: value`, the labels above, case
   insensitive) are header values wherever they stand in it. Bare lines: the first is the title, the second the
   artist; a third bare line ends the header there (the file has no blank line after its header, and the rest is
   body). A first line that is a chord row (`ChordSheetConverter.isChordLine`) ends the header at once (no header,
   `fallbackTitle` titles it), unless it is one word equal to `fallbackTitle` ignoring case: a song called `Am` or
   `La` is exported as `Am.onsong` and keeps its title.
2. Write the ChordPro header through plan 12's `ForeignSongHeader` helper (`{title}`, `{artist}`, `{key}`, `{capo}`,
   `{tempo}`, `{time}`, `{duration}`, `{copyright}`, `{tag}` per keyword), only where the body does not declare the
   same directive.
3. The body is cut at its lines that are ChordPro directives (`{comment: …}`, `{c: …}`; written as they are), and each
   run between them is set on its own: with `ChordSheetConverter.convertFragment` where it has a chord row and no
   bracket chords (`convertFragment` returns null for any text holding a directive, so the whole body cannot go in
   one call); kept as it is where it has inline `[G]` chords (ChordPro already); and `ChordSheetLyrics.escaped` (plan
   13) where `convertFragment` returns null otherwise, so a lyrics-only body's `[`, `{`, or leading `#` never becomes
   markup. `Verse 1:` labels become sections through the fragment converter's heading rule (plan 01's vocabulary
   included) in the runs it converts.

Where it is called and what changes with the extension:

- `LibraryFiles`: `.onsong` in a new `FOREIGN_SONG_EXTENSIONS` folded into `IMPORTABLE_EXTENSIONS`. Every use of the
  list follows: `ImportLimits.maxSizeOf` and so `ImportBudget.read` read it with the text limit; the desktop folder
  walk and the web drop read it by name; the web picker offers it while plan 09's filter stands. `isArchiveFileName`
  and `SONG_EXTENSIONS` (the library scan, sync, Open with) do not change: never registered as an Open with type
  (importable-only, per the `LibraryFiles` rule).
- `ArchiveLocalSourceImpl.unpack`: every `.onsong` entry is replaced by `OnSongFile.read`'s text under the entry's base
  name with `.cho`, so the domain's bytes-only archive gate (`isImportable`) counts it as a song: an OnSong `.backup`
  recognised by its bytes is not reported as one skipped file.
- `PrepareImportUseCaseImpl.sort`: a loose `.onsong` goes through `songsIn` **before** the unknown-extension skip
  (`extension !in SONG_EXTENSIONS && …` would otherwise report it skipped).
- iOS share extension: not extended. An `.onsong` file has no system type on a device without OnSong, so the
  activation rule cannot name it without an imported type declaration; the in-app picker (`UTTypeData`) takes it.
  `ShareInboxWriter.swift`'s comment that its lists mirror `IMPORTABLE_EXTENSIONS` and the plist's "Keep in step"
  comment name `.onsong` as left out, as they name JSON (or not, if plan 12 added JSON). Android's picker and share
  (`application/octet-stream`) take it as they are.

## Tests

`OnSongFileTest`: a bare-line header plus chords over lyrics → ChordPro with title and artist and chords set; a
labelled header; a header the body already declares (`{title}` inside) is not written twice; a file with no header
(first line a chord row) keeps the fallback title; a song titled `Am` in `Am.onsong` keeps its title; a header without
a blank line after it ends at its third bare line; a body with a `{c: …}` line between chord rows has its chords set
on both sides; a lyrics-only body is escaped; CRLF. `ArchiveLocalSourceTest`: a zip of two `.onsong` files
yields two songs named by their titles, as `.cho` entries (what keeps a bytes-only OnSong `.backup` past the gate). `PrepareImportUseCaseImplTest`: a loose `.onsong` is planned as a converted
song, not skipped.
