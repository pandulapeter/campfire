# Read the songs out of any JSON document whose objects hold a title and a chord sheet

**Kind:** feature  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** new `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/backup/JsonSongs.kt`,
new `.../backup/ForeignSongHeader.kt` (the header-writing helper extracted from `SongbookProBackup`),
`.../backup/SongbookProBackup.kt`, new `data/source/local/implementation/src/commonTest/.../backup/JsonSongsTest.kt`,
`.../source/ArchiveLocalSourceImpl.kt`, `data/source/local/implementation/src/desktopTest/.../source/ArchiveLocalSourceTest.kt`,
`data/source/local/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/api/ArchiveLocalSource.kt`,
`data/repository/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/api/ArchiveRepository.kt`,
`data/repository/implementation/.../ArchiveRepositoryImpl.kt`,
`domain/implementation/.../importing/PrepareImportUseCaseImpl.kt`, the one fake `FakeArchiveRepository` in
`domain/implementation/src/commonTest/.../RepositoryStubs.kt` (no other implementation of `ArchiveRepository` or
`ArchiveLocalSource` exists outside the real ones, `tools/screenshots` included),
`domain/implementation/src/commonTest/.../importing/PrepareImportUseCaseImplTest.kt`,
`app/android/src/main/AndroidManifest.xml` (the SEND filter), `app/ios/iosApp/ShareExtension/Info.plist` (the
activation rule and its comment), `app/ios/iosApp/ShareExtension/ShareInboxWriter.swift` (`ShareLimits`), `data/source/local/implementation/CLAUDE.md`, `data/source/local/api/CLAUDE.md`,
`domain/implementation/.../importing/CLAUDE.md`, `app/android/CLAUDE.md` and `app/ios/CLAUDE.md` where they list the
share types


**Challenged:** amended — iOS needs `ShareInboxWriter.swift`'s `ShareLimits` too (an attachment whose type is
`public.json` matches neither `fileTypes` nor plain text there, so the extension would be offered and then drop the
file), and the plist comment says JSON is left out on purpose; lyrics-only content is accepted only under a `lyrics`
key (a notes app's `{title, content}` export would otherwise import every note as a song); `sort` must become
`suspend` for the hook, and a loose document's songs take its index as their origin; the node cap bounds the walk, not
the parse; the fake to update is named.

Run after plan 11 (a JSON file under an unknown name arrives through its text sniffing). **Decision awaiting the
user** (README): the research found few chord apps that export JSON (SongbookPro, already read; OnSong's Binder
`manifest.json`, undocumented; Setlist Helper and worship tools export ChordPro or lyrics-only XML), so the payoff is
modest; the recommendation is still to build it with the strict acceptance rule below, since it costs one bounded
file and makes any future JSON export importable without a reader of its own.

## Problem

A `.json`, loose or inside an archive, is tried only as a Campfire setlist:

```kotlin
/** ".setlist.json" ends in this, and a plain ".json" is worth trying to parse as a setlist too. */
const val SETLIST_EXTENSION = "json"
```

and `SongbookProBackup` is the only reader of another app's JSON. A JSON export of any other app is reported as
skipped.

## Design

`internal object JsonSongs` with `fun read(text: String): List<ImportedFile>` (empty where nothing qualifies):

- Parse with `Json.parseToJsonElement` on input of at most `ImportLimits.MAX_TEXT_FILE_SIZE` (SongbookPro's
  `dataFile.txt` keeps its own larger limit); cap the walk at depth 32 and 200,000 nodes, stopping with what was found.
  The node cap bounds the walk only: the parse builds the whole tree of up to 8 MiB first, which is accepted since
  SongbookPro's reader already parses up to 24 MiB the same way. Nodes of JSON parsed out of a string count toward the
  same cap, and a parse failure (`SerializationException`) is caught and yields nothing.
- Walk arrays and objects. A string value that starts with `{` or `[` is parsed and walked too, at most two levels
  of JSON inside JSON (SongbookPro writes `details` and `_folders` that way).
- An object is a song when it has **both**:
  - a content candidate: a multi-line string, preferring the keys `content`, `lyrics`, `text`, `body`, `chordpro`,
    `chords`, `sheet` (case-insensitive), for which `ChordSheetStructure.hasSongStructure` (plan 11) is true, or that
    is lyrics-only with at least four lines **and** sits under a key named `lyrics` (not `content`, `text`, or
    `body`: Simplenote, Google Keep, Ghost, and most notes and blog exports write `{title, content}`, and every note
    would become a song);
  - a sibling single-line string of at most 200 characters under `title`, `name`, `song`, `songTitle`, `songName`.
- Optional siblings read as header values: `artist`/`author`/`composer`/`byline` → `{artist}`; `key`; `capo`;
  `tempo`/`bpm`; `time`/`timeSignature`; `copyright`; `tags`/`folders` (strings or arrays) → `{tag}`. Numbers that
  arrive as strings are read as SongbookPro's are.
- An accepted song object is not descended into (a `versions` array would otherwise be read twice).
- Output is `.cho` text: content that is not ChordPro is converted with
  `ChordSheetConverter.convert(ChordSheet.ofPlainText(content), normalize)` (a converter result with several songs
  keeps the first; the `.txt` import path is not used, since it would guess a title from the first line), then the
  header directives the text does not declare are prepended by the shared helper (extract `songText`'s `directive`
  logic, `headerValue`, and `linkUrl` from `SongbookProBackup` into `ForeignSongHeader` and have both use it).
  Names are unique per batch as `SongbookProBackup.uniqueName` does (move it to the helper too).
- No setlists: that needs format knowledge.

Where it is called:

- Inside archives: `ArchiveLocalSourceImpl.unpack`, after the SongbookPro check (specific before generic): every
  `.json` entry that is not a `.setlist.json`, and every sniffed-text entry whose text starts with `{` or `[`, that
  `JsonSongs.read` turns into at least one song is replaced by those songs; otherwise the entry stays as it is, so a
  setlist parse is still tried.
- Loose files: add `ArchiveLocalSource.songsIn(file: ImportedFile): List<ImportedFile>?` ("the songs a foreign
  document holds, written as Campfire files, or null where it is not one") and the same on `ArchiveRepository`;
  `PrepareImportUseCaseImpl.sort` calls it for a `.json` that is not a `.setlist.json` (and for a sniffed-text file
  starting with `{`/`[`) before the setlist path and before the unknown-extension skip. `sort` is a local non-suspend
  function today; make it `suspend` (the hook is). A loose document's songs are sorted with the document's own index
  as their origin, as an archive's entries are (`ImportPlan.SongEntry.origin`: names are unique only within it). Plans 13 and
  14 reuse this hook.
- Share intake: add `application/json` to the Android `SEND` filter, and on iOS `public.json` to the activation rule
  (it conforms to `public.text`, not `public.plain-text`, so the plain-text clause does not cover it), to
  `ShareLimits.fileTypes`, and `json` to its extension lists and `maxSize` (text limit) in `ShareInboxWriter.swift`:
  without those the extension is listed and then drops the attachment (`Attachment.init` returns nil). Reword the
  plist comment that leaves JSON out on purpose, and `app/ios/CLAUDE.md`'s share extension paragraph; `VIEW` stays unclaimed, per the `LibraryFiles` rule that
  importable-only types are never claimed system wide.

A Campfire setlist document (`songs` of `{file: …}` objects, no multi-line text) and `preferences.json` yield
nothing and fall through to today's handling.

## Tests

`JsonSongsTest` (commonTest): a root array of songs; a `{data: {items: [...]}}` wrapper; a song whose fields are a
JSON string inside JSON; numbers as strings; prose content with a title yields nothing; a notes export (`[{title, content}]` of
four-line prose) yields nothing, a `{title, lyrics}` of four lines yields a lyrics-only song; a Campfire setlist document
and a preferences document yield nothing; the depth and node caps stop cleanly; a content that is chords over lyrics
comes out as ChordPro with the title and artist in the header; two songs of one title are numbered.
`ArchiveLocalSourceTest`: a SongbookPro `dataFile.txt` still goes through `SongbookProBackup`; a `songs.json` of two
songs beside a `.cho` yields three songs; a `.setlist.json` is untouched. `PrepareImportUseCaseImplTest`: a loose
`songs.json` is planned as songs with the file's origin; a `.setlist.json` is unchanged.

## Manual check

Share a `.json` file to the app from the Files app on iOS and from a file manager on Android once, to see the app
listed: the activation rule and the manifest are not covered by tests, and a wrong predicate hides the app from
every share sheet.
