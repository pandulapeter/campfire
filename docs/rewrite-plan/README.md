# Campfire 4.0 rewrite plan: lightweight ChordPro viewer and editor

This folder is the execution plan for turning Campfire from an online song-library client into an offline
[ChordPro](https://www.chordpro.org/chordpro/chordpro-directives/) viewer and editor. Each numbered document is one
self-contained step meant to be handed to an agent on its own. Execute them **in order**; every step ends with the whole
project building for all four platforms, so a half-finished plan is still a working app.

## What changes, in one paragraph

Today the song list comes from Google Sheets ("databases") and song texts from GitHub, cached in Room / `localStorage`.
After the rewrite there are **no databases and no network access at all**. The app owns a folder of plain `.cho`
(ChordPro) files on every platform. Users create songs in a built-in editor or import `.cho` files and `.zip` archives;
they can export single songs or the whole library as a zip. Setlists become small JSON files next to the songs so they
round-trip through zips too. The viewer keeps everything users like today (columns layout, sticky section headers,
transposition, text size, lyrics-only mode, setlist pager, light/dark theme, English/Hungarian UI) but renders real
ChordPro, including tabs and grids.

## Decisions already made (do not re-open these)

| Topic | Decision |
| --- | --- |
| Storage | App-managed library folder on every platform. Web uses the Origin Private File System (OPFS). No user-picked folders, no cloud. |
| Editor | Raw ChordPro text editor with syntax highlighting and a live rendered preview (side by side on wide windows, toggled on narrow ones). Autosave. |
| Setlists | Kept. One `*.setlist.json` file per setlist, stored in the library, exported with the songs. |
| Old data | No migration. The first launch of 4.0 starts with an empty library; Room and the `localStorage` documents are deleted, not read. |
| ChordPro coverage | Core directives + environments + `{chorus}` recall + comments + tabs + grids. Chord diagrams (`{define}`), fonts, colours, images and page directives are parsed and ignored. |
| Dependencies | Pure Kotlin for zip (STORED writer, STORED + DEFLATE reader with an in-house inflater). No new third-party libraries. |
| Architecture | The current `api` / `implementation` module split, Koin, use cases, `DataState` and mappers stay. Remote modules are deleted; one new pure module `:chordpro` is added. |
| Platforms | All four stay: Android, iOS, desktop (JVM), web (wasmJs). |
| Tests | New pure logic (`:chordpro`, zip) gets `commonTest` unit tests run on the desktop target. The UI stays untested, as today. |

## Decisions made while writing this plan (change only if you find a hard blocker, and record why in the step doc)

- **Song identity is the file name** (e.g. `Oasis - Wonderwall.cho`), unique inside the library. Title and artist are
  read from `{title}` / `{artist}` (fallback `{subtitle}`), falling back to the file name.
- **Library layout** inside the app-private data directory: `library/songs/*.cho`, `library/setlists/*.setlist.json`,
  and `preferences.json` outside the library (it is not exported).
- **Setlist file format** (`<slug>.setlist.json`):
  ```json
  { "title": "Friday gig", "priority": 3, "songs": [ { "file": "Oasis - Wonderwall.cho", "transposition": 2 } ] }
  ```
  Transposition inside a setlist lives in the setlist file (so it round-trips); transposition of a song opened from the
  library lives in `preferences.json` keyed by file name.
- **Import** accepts `.cho`, `.chordpro`, `.chopro`, `.crd`, `.pro`, `.txt` and `.zip` (recursively, any depth, only the
  file names are kept). Everything is stored as `.cho`. A file containing `{new_song}` is split into several files. Name
  collisions get a ` (2)`, ` (3)`… suffix; nothing is ever overwritten by an import.
- **Export** writes songs as they are on disk (the user's transposition is *not* baked in), setlists as their JSON.
- **Editor saves automatically**: 1 s after typing stops and whenever the editor leaves the screen. No "discard changes"
  dialog. Undo/redo comes from `TextFieldState.undoState`.
- **Refresh** (pull to refresh / the refresh action) now means "rescan the library folder". This matters on iOS, where the
  Files app can change the folder (step 10) and on desktop where users may drop files in.
- **Legacy heading compatibility**: a `{c: Verse 1}` / `{comment: Chorus}` line *outside* any environment whose first
  word is `Intro`, `Verse`, `Pre-Chorus`, `Chorus`, `Bridge`, `Solo` or `Outro` starts an implicit section with that
  label. This keeps the songs Campfire 3 published (which use exactly this dialect) rendering correctly when imported.

## Target architecture

```
app:android / app:desktop / app:ios / app:web   entry points, Koin startup, platform chrome, file pickers, "open with"
  presentation                                 CampfireViewModel, Navigation 3 back stack, screens (Songs, Setlists,
                                               Settings, SongDetails, SongEditor), string resources; platform shells
                                               provide LocalFilePicker
  domain:api / :implementation                 use cases (single-method interfaces)
    data:repository:api / :implementation      SongRepository, SongContentRepository, SetlistRepository,
                                               UserPreferencesRepository (all local-only, DataState flows)
      data:source:local:api -> :implementation SongLocalSource, SetlistLocalSource, UserPreferencesLocalSource on top
                                               of an expect/actual FileStorage (files on Android/desktop/iOS, OPFS on web)
                                               + the pure-Kotlin zip package
        data:model                             Song, Setlist, UserPreferences, DataState, ImportResult
        chordpro                               NEW, dependency-free: ChordProSong model, parser, serializer, transposer
```

Deleted: `:data:source:remote:api`, `:data:source:remote:implementation`, everything named `Database*`,
`RawSongDetails*`, `Transposition*` (folded into setlists/preferences), Room, the custom `roomMain`/`retrosheetMain`
hierarchy templates, Ktor, Ktorfit, Retrosheet, the CSV library, KSP.

## Steps

| # | Document | Result when done |
| --- | --- | --- |
| 01 | [01-chordpro-module.md](01-chordpro-module.md) | New `:chordpro` module with model, parser, serializer, transposer and unit tests. Nothing uses it yet. |
| 02 | [02-file-storage.md](02-file-storage.md) | `FileStorage` expect/actual for all four platforms inside `:data:source:local:implementation`. Nothing uses it yet. |
| 03 | [03-zip.md](03-zip.md) | Pure Kotlin `ZipReader` / `ZipWriter` / `Inflater` / `Crc32` with tests. Nothing uses it yet. |
| 04 | [04-remove-remote-layer.md](04-remove-remote-layer.md) | Remote modules, databases and networking deleted. App builds and runs with an empty library. |
| 05 | [05-file-based-data-layer.md](05-file-based-data-layer.md) | Room / localStorage replaced by the file library. Songs on disk show up in the list and open. |
| 06 | [06-viewer.md](06-viewer.md) | Song details render the `:chordpro` model: environments, chorus recall, comments, tabs, grids, metadata header, key-aware transposition. |
| 07 | [07-library-and-setlists-ui.md](07-library-and-setlists-ui.md) | New song / delete song, empty states, setlists on files, Settings "Library" section. |
| 08 | [08-import-export.md](08-import-export.md) | Import `.cho` / `.zip` and export song / library through platform file pickers. |
| 09 | [09-editor.md](09-editor.md) | Syntax-highlighted editor with live preview and autosave. |
| 10 | [10-platform-integration.md](10-platform-integration.md) | "Open with" file associations, share sheet, drag and drop, iOS Files app access. |
| 11 | [11-cleanup-and-release.md](11-cleanup-and-release.md) | Dead dependencies and strings removed, docs and version updated. |
| 12 | [12-verification.md](12-verification.md) | Manual test matrix and CLI recipes for all platforms. Run after every step and at the end. |

Steps 01, 02 and 03 are independent of each other and can be executed in parallel by different agents (they touch
different modules). Everything from 04 onwards is sequential.

## Rules for the executing agent

1. **Read `CLAUDE.md` first** and follow its conventions: `internal` `<Interface>Impl` classes, Koin wiring in each
   module's `Module.kt`, mappers between layers, strings in both `values/strings.xml` and `values-hu/strings.xml` read
   through `com.pandulapeter.campfire.presentation.localization.stringResource`, no `java.*` in shared code, Material 3
   only.
2. **Build all four platforms before declaring a step done**:
   ```
   ./gradlew :app:android:assembleDebug :app:desktop:build :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution
   ```
   plus `./gradlew :chordpro:desktopTest :data:source:local:implementation:desktopTest` once those tests exist. Then do
   the manual checks listed under "Verify" in the step, using [12-verification.md](12-verification.md).
3. **Do not skip ahead.** If a step needs something from a later step, stub it minimally and leave a `// TODO(step NN)`
   comment; the later step lists these stubs.
4. **Delete, do not comment out.** Removed code goes away; git has the history.
5. **Keep the app usable at the end of every step.** An empty list with an empty state is fine; a crash on launch is not.
6. **One commit per step**, message `Rewrite step NN: <title>` plus the attribution lines the session provides.
7. When a step's instructions and the real code disagree (a function was renamed, a file moved), trust the code, do the
   equivalent thing, and note the difference at the bottom of the step document under "Execution notes".
8. Do not update `CLAUDE.md` until step 11, except for the "No tests exist" sentence once step 01 adds tests.

## Glossary

- **ChordPro**: text format for lyrics with inline chords, e.g. `[Am]Hello [C]world`, and `{directives}`.
- **Environment**: a ChordPro block delimited by `{start_of_x}` / `{end_of_x}` (verse, chorus, bridge, tab, grid, custom).
- **Library**: the app-owned folder of `.cho` and `.setlist.json` files.
- **OPFS**: Origin Private File System, the browser's per-origin sandboxed file system, used by the web build.
