# Read OpenSong's XML songs, loose and inside OpenSongApp's `.osb` backups

**Kind:** feature  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** new `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/backup/OpenSongSong.kt`,
new `.../commonTest/.../backup/OpenSongSongTest.kt`, `.../source/ArchiveLocalSourceImpl.kt`,
`.../desktopTest/.../source/ArchiveLocalSourceTest.kt`,
`data/formats/src/commonMain/kotlin/com/pandulapeter/campfire/data/formats/document/XmlPullParser.kt` (make
`parseXml` and `XmlElement` public, they are `internal` today), `data/formats/CLAUDE.md`,
`data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/library/LibraryFiles.kt` (`.osb` in
`LIBRARY_BACKUP_EXTENSIONS`), `domain/implementation/.../importing/PrepareImportUseCaseImpl.kt` (through the
`songsIn` hook of plan 12), `data/source/local/implementation/CLAUDE.md`, new
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetLyrics.kt` (+ its test),
`data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/library/CLAUDE.md`

**Challenged:** amended — `convertFragment` returns null for a block without a chord row, and `:chordpro` has no public
escape, so a chordless section needs a new public `ChordSheetLyrics.escaped` (else its `[`, `{`, and `#` lines become
markup); `parseXml` throws and must be caught; widening it
contradicts `data/formats/CLAUDE.md`'s "used only by" sentence, which is reworded; unmarked lyric lines are lyrics;
`.osb` in `LIBRARY_BACKUP_EXTENSIONS` is pickable everywhere but not shareable to the iOS extension (no UTI), which is
now said rather than left to the OS registration rule; and if plan 12 was declined this plan adds the hook and the
header helper itself.

Run after plans 11 (extensionless entries are read when text-like) and 12 (the `songsIn` hook). **Decision awaiting
the user** (README): OpenSong is the second most worthwhile format by the research (documented XML, widely used by
church musicians on Android through OpenSongApp, whose `.osb` backup is a zip of the song folders), and the cheapest
of the readers. Drop this plan if the decision is no. **If plan 12 was declined**, this plan first does plan 12's two
shared pieces without `JsonSongs`: the `ForeignSongHeader` helper extracted from `SongbookProBackup` (its Design's
"Output" bullet) and the `songsIn` hook on `ArchiveLocalSource` / `ArchiveRepository` / the fake (its "Loose files"
bullet, `sort` made `suspend`, the document's index as origin).

## Format (from opensong.org/development/file-formats and OpenSongApp's sources; verify against a sample before
pinning the tests)

One XML file per song, usually **without an extension**, root `<song>` with children `title`, `author`,
`copyright`, `ccli`, `key`, `capo`, `tempo`, `timesig`, `theme`, `aka`, `presentation`, `lyrics`. Inside `<lyrics>`:

- `[V1]`, `[V2]`, `[C]`, `[B]`, `[P]` (pre-chorus), `[T]` (tag), `[I]`/`[O]` (intro/outro) on a line of their own
  open a section;
- a line starting with `.` is a chord line (`.G      D`), positioned over the lyric line under it;
- a line starting with a space is a lyric line (` The river runs`); a line starting with a digit (`1The river`,
  `2Another verse`) is the n-th verse's lyric sharing one chord line;
- a line starting with `;` is a comment; `||` and `|` are column and line breaks; `---` ends a slide.

OpenSongApp's `OpenSongBackup_<date>.osb` is a zip whose entries are `<folder>/<file>` (the `MAIN` folder at the
root) plus the app's own SQLite database, which holds only extra metadata for non-OpenSong files and is skipped
(`.db` is a binary extension in plan 11).

## Fix

`internal object OpenSongSong` with `fun read(text: String, fallbackTitle: String): String?`: null unless the text
parses with `parseXml` (which throws `IllegalArgumentException` / `IllegalStateException` on malformed XML, a DTD, or
past its 600,000 events: catch them and return null) and its root's `localName` is `song` holding a `lyrics` child.
The result is ChordPro text:

1. header: `{title}` (`fallbackTitle`, the file name without extension, where the element is empty), `{artist}` from
   `author`, `{key}`, `{capo}` (digits), `{tempo}` (digits, within `MetronomeSettings.TEMPO_RANGE`), `{time}` where
   `ChordProTime.parse` accepts it, `{copyright}`, `{tag}` per `theme` (`;`-separated) — written through plan 12's
   `ForeignSongHeader` helper;
2. body: each section tag becomes `{start_of_verse: Verse 1}` / `{start_of_chorus: Chorus}` / `{start_of_bridge:
   Bridge}` / `{comment: …}` for the rest, written by the reader (not left to the converter's heading vocabulary) and
   closed before the next. A section's lines are rendered as a plain chords-over-lyrics block: a `.` line is a chord row
   with the dot dropped, a line starting with a space a lyric line with the space dropped (both shift by one column, so
   the chords stay over their syllables), **an unmarked line a lyric line too** (hand-written files omit the space), a
   numbered lyric line (`1…`, `2…`) splits the section into verses that each repeat the chord line above it, `||` a line
   break and `|` a space, `---` dropped; `;` comments become `{comment: …}` lines between blocks. Each block is set with
   `ChordSheetConverter.convertFragment`; where it returns null (no chord row in the block), the block is
   `ChordSheetLyrics.escaped(block)`: a new public `object ChordSheetLyrics { fun escaped(text: String): String }` in
   `:chordpro`'s `convert/` (lane A's new file, `explicitApi`; it applies the internal `ChordProLiteralText.escape` to
   each line, the escaping the converter gives prose), so a lyric's `[`, `{`, or leading `#` never becomes markup.
   Plan 14 uses it too.

Where it is called: `ArchiveLocalSourceImpl.unpack` (after SongbookPro and JSON, for every sniffed-text or `.xml`
entry whose trimmed text starts with `<`) and `PrepareImportUseCaseImpl` through `songsIn` for a loose `.xml` or
sniffed-text file. A `<set>` file of OpenSongApp's `Sets/` folder returns null and is left to plan 11's rule (no song
structure, so skipped). OpenLyrics XML (OpenLP's export, root `song` in the `openlyrics.info` namespace with
`<verse name="v1"><lines>`) is **not** this plan; `read` returns null for it, since it has no `lyrics` child.

`:data:formats`: make `parseXml` and `XmlElement` public (they are `internal`), and reword `data/formats/CLAUDE.md`'s
"Used only by `DocumentLocalSourceImpl` and `ArchiveLocalSourceImpl`" to name the local source's `backup/` readers too;
"public is only what those use" then still holds.

`.osb`: add it to `LIBRARY_BACKUP_EXTENSIONS`, so a nested one is unpacked by name and the web's picker offers it
while plan 09's filter stands (the bytes rule already unpacks it elsewhere). The pickers of Android (`*/*`), iOS
(`UTTypeData`), and the desktop take it as they are. **The iOS share extension is not extended**: an `.osb` has no
system type there, so its activation rule cannot name it without an imported type declaration such as SongbookPro's
`com.pandulapeter.campfire.songbookpro-backup`; OpenSongApp is Android only, so the in-app picker is the way in on
iOS. Say so in `data/model/library/CLAUDE.md`'s rule that the extension lists agree with the OS registrations (`.osb`
is the stated exception), and in `data/source/local/implementation/CLAUDE.md`'s "Other apps' libraries".

## Tests

`ChordSheetLyricsTest` (`:chordpro` commonTest): `[`, `{…}`, and a leading `#` are escaped as the converter escapes
prose. `OpenSongSongTest`: a two-verse song with a chorus, chord lines over lyrics → the expected ChordPro (header, sections,
chords set); a numbered lyric pair sharing one chord line → two verses with the same chords; `;` comments and `||`
breaks; an OpenLyrics document → null; a non-song XML and malformed XML → null; a
lyrics-only song comes out escaped; unmarked lyric lines are read as lyrics; a song with empty `title` named by its file.
`ArchiveLocalSourceTest`: an `.osb`-shaped zip (`MAIN/Riverlight` extensionless XML, `Sets/...` ignored, a `.db`
skipped) yields the song. `PrepareImportUseCaseImplTest`: a loose extensionless OpenSong file is planned as a song.

## Manual check

Import one real `.osb` exported by OpenSongApp before the release that ships this: the section markers and the
numbered-verse convention come from documentation, not from a file in hand, and a wrong reading would import every
song of a church's library garbled.
