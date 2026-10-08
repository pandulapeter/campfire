<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Campfire

Kotlin Multiplatform app (Android + iOS + JVM desktop + wasmJs web) for viewing and editing song lyrics and chords,
with a metronome.
Compose UI is shared between all platforms. The app owns a library folder of plain
[ChordPro](https://www.chordpro.org) files on every platform, which the user fills by writing songs in the built-in
editor or by importing ChordPro, plain text, PDF and Word documents, zip archives, and other apps' library backups. **The only things that ever reach the network are sync, and the cover
images and the cover search the user asks for.** Sync is off until the user connects a cloud folder of their own in
Settings, and still involves no server of Campfire's own — see the Sync section below. A cover is fetched from the
address a song's own file names, once, and kept on the device; the search asks MusicBrainz and the iTunes Search API,
and only from the sheet the user opens for it; the "Cover art" switch in Settings → Features turns both off — see Cover art below. (The web build also asks its own deployment, at every launch, which build is current, which is the page being
loaded rather than a request about the user — see Web below. The Android build also asks Play whether a newer version of itself exists, but that
question is answered over IPC by the Play Store app; Campfire's own process makes no request — see Updates below.
On Android and iOS the system's own device backup also carries the library and the settings — to the user's Google or
iCloud backup, or straight to their next phone — but that is the operating system copying the app's files on the
user's backup settings; Campfire's process makes no request for it, and the sync credentials, the sync index, the editor
draft and the cover copies are not part of it on either (`app/android/src/main/res/xml`; on iOS a device-bound Keychain
item and files marked as excluded from backup).)

## Architecture

Strict `api` / `implementation` module split at every layer. Only `:app:*` modules see implementations; everything else
depends on `api` modules and gets wiring via Koin.

```
app:android / app:desktop / app:ios / app:web   entry points, platform chrome, "open with" and share intents (app:ios
                                             also holds the Xcode project, app:web the index.html)
  app:baselineprofile                        the Baseline Profile generator for app:android, run by hand on an emulator
  app:di                                     the Koin application: the one place every module is named, and the
                                             function the four entry points start Koin with
  presentation                               CampfireViewModel, Navigation 3 back stack, Material 3 theme + every screen
                                             (Songs, Setlists, Metronome, Settings, SongDetails, SongEditor), string resources; the
                                             platform shells (system bars, file pickers, drag and drop, URL opening,
                                             desktop key handling) are its platform source sets
  domain:api / :implementation               use cases (single-method interfaces)
    data:repository:api / :implementation
    data:sync:implementation                 sync: the engine, the planner, the index, the synced preferences and the
                                             SyncRepository implementation, over the repositories' API and both sources
      data:source:local:api  -> :implementation   files on Android/desktop/iOS, OPFS on web (see Web below)
        data:formats                         the pure-Kotlin zip reader/writer and PDF/Word text extractors; depends on
                                             :data:model and :chordpro, used by :data:source:local:implementation
      data:source:remote:api -> :implementation   the sync contracts and the Dropbox provider, the cover download
                                                  and the MusicBrainz and iTunes searches; the only module in the project that
                                                  makes a network call (see Sync and Cover art below)
        data:model                           domain models, shared by everything
  metronome:api / :implementation            the click: the Metronome contract, its patterns and tap tempo, and the
                                             engine with one audio output per platform (see Metronome below). Depends
                                             on nothing of the app's; used by :presentation, :app:android and :app:ios
  tools:screenshots                          the store screenshots, rendered offscreen from the desktop build drawn as
                                             each platform (see tools/screenshots); nothing depends on it
  chordpro                                   dependency-free ChordPro model, parser, serializer, transposer, tab
                                             wrapper, tag editor, highlighter, positioned chord-sheet converter, and
                                             what a chord name means and how it is played. Depends on nothing; used by
                                             :data:source:local:implementation (metadata for the song list),
                                             :domain:api and :presentation
```

Data flow: `FileStorage` (one flat directory per kind of file) -> `LocalSource` (files in, models out) -> `Repository`
(emits `DataState<T>`, reads once and caches) -> use cases (`GetScreenDataUseCase` combines the song and setlist
repositories into one `ScreenData` flow) -> `CampfireViewModel` (a lifecycle `ViewModel` exposing `StateFlow`s and the
Navigation 3 back stack) -> screens (`collectAsStateWithLifecycle`).

The library layout, inside the app-private data directory of each platform:

```
library/songs/*.cho                  one song per file; the file name is the song's identity
library/setlists/*.setlist.json      one setlist per file, exported together with the songs
preferences/preferences.json         everything in UserPreferences; outside library/, so it is never exported
preferences/preferences.json.bad     the last preferences document that did not decode, kept before it is saved over
preferences/sync-credentials.json    the connected account's tokens, and an unfinished authorization, on desktop and
                                     the web; the Keystore (an encrypted sync-credentials.bin) and the Keychain on
                                     Android and iOS
preferences/sync-index.json          what the last successful sync run saw, `preferences.json` included
preferences/sync-credentials-forget-pending   a previous installation's credentials a first launch could not forget yet
preferences/editor-draft.json        the editor's unsaved text as the app last left the front, so that the system
                                     ending it in the background does not end the text too; gone once it is saved or
                                     discarded
covers/<sha256 of the address>      the copies of the cover images the songs name; outside library/, so never exported
                                     or synced, and deleted once no song names them
instance.lock / instance.endpoint    desktop only: what keeps a second process off the library (see app/desktop)
campfire.log / campfire.log.1        desktop only: everything the process printed, bounded (see app/desktop)
```

On Android and iOS `library/` and `preferences/preferences.json` are in the system backup and the transfer to a new
device; the sync credentials, `sync-index.json`, `editor-draft.json` and `covers/` are not, so a restored installation starts
disconnected, its first sync run compares by content and its covers are downloaded again. Android does it with an allow-list of paths in
`:app:android`, iOS with a Keychain item bound to the device and `FileStorage.keepOutOfDeviceBackup` on the index, the
draft and every cover. A reinstall starts disconnected too: a
launch that finds no preferences document forgets any credentials it finds, since the iOS Keychain outlives an
uninstall and nothing else does.

## Printing

Song and setlist action menus have one export entry each (**Export song**, **Export setlist**), which opens the export
screen titled the same (`presentation/ui/screens/export/ExportScreen.kt`), full screen over the app with Save as its floating
action button (on a phone the options end above it and the preview is their first item, scrolling away with them; from
520dp of width it stands beside them) and, on Android and iOS, Share in the app bar: there is no separate share or
file export entry. Its first option is the format, with a line saying what each is for — a **PDF**, for printing, or
the library's own files, for sharing with other Campfire users: **ChordPro** for a song, the `.cho` file as the library
holds it, and **Zip** for a setlist, a setlist manifest (its `*.setlist.json`) next to its songs as ChordPro files. A setlist's songs are ticked off for either format
alike, and a zip of only some of them carries a manifest naming only those (`ExportSetlistUseCase`'s `songFileNames`,
everything else in the document kept), so that the archive never names a song the user left out — a ticked song
whose file is missing from the library is still named, and an import shows it as a missing song, as the setlist itself
did; with all of them ticked the
manifest is the stored file unchanged. The library's own files take no other option and are previewed as the text or
the files they write. The rest of this section is the PDF. `PrintSettings` (the format among
them) are local user preferences, mapped through
`PrintSettingsDocument`, saved once the options have settled and whatever way the screen closes, independent of the
viewer's text size and folded sections. A setlist can export its running order or the selected song sheets, retaining
the original slot numbers and the transposition of each entry; a lone song reached through a setlist uses that entry's
key too. Under each song's heading a row says how it is played — the key it is printed in, the transposition that
took it there and the capo, then the tempo in BPM and the time signature — each half an option of its own (**Key,
transposition and capo**, **Tempo and time signature**) beside the chords, the chord diagrams, the comments and the
artist. **The Features tab reaches the export screen**: an option whose feature is switched off (the chords, which take
the chord diagrams and the key with them, or the metronome, which takes the tempo) is not offered and not printed,
whatever was chosen before (`PrintSettings.withinFeatures`), the choice itself kept for the switch to be turned back
on. Missing or unreadable songs retain a visibly marked place. The source is a snapshot read when the screen
opens. `presentation/ui/print/PrintLayout.kt` lays out PDF points using the same font measurements as the preview —
lyrics in the app's text font, tablature and grids in its monospace one — keeping lyric/chord pairs and guitar systems
together, and flowing long songs across columns and pages. `PrintRenderer` draws both the preview and the page images
(216 dpi, sixteen grays, which print no differently from 256) embedded by the common `PrintPdfWriter`, which titles
the file after the song or setlist, compressed with Flate by `PrintDeflater`, a small pure-Kotlin zlib encoder. These
PDFs retain those page images and add invisible selectable text positioned from the same Compose shaping, with
small glyphless Type 3 fonts and explicit ToUnicode maps. Only the printed content is included, in its printed key
and with the chosen options; no original ChordPro or excluded metadata is embedded. New exports can be searched,
copied and reimported without OCR, a right-to-left run in its logical order through `ActualText`; older image-only exports remain unreadable to the importer. Saving uses the existing `FilePicker` on every
platform, and Android and iOS offer Share in the app bar; the save button counts the pages as they are drawn, and is
Cancel until the picker is up. The file is named from the song's header or the setlist's title, the way
`ExportFileNames.kt` names a song (see `presentation/CLAUDE.md`). New controls and text written into PDFs are
localized in both languages.

## Conventions

- Library modules apply the convention plugins from `gradle/build-logic` (`campfire-library`, or
  `campfire-compose-library` when they contain Compose). These configure the Android, `desktop` (JVM), `iosArm64`,
  `iosSimulatorArm64` and `wasmJs` (browser) targets and derive the Android namespace from the Gradle path. Sources live
  in `src/commonMain/kotlin`; platform code goes in `androidMain` / `desktopMain` / `iosMain` / `wasmJsMain` via
  `expect`/`actual`, and JVM code both Android and the desktop need in `jvmSharedMain`, a group the convention plugin
  adds to the default hierarchy template.
- Shared code must stay JVM-free: no `java.*`, `KoinJavaComponent`, or JVM-only libraries. Use `kotlin.uuid.Uuid`,
  `androidx.compose.ui.text.intl.Locale`, `KoinPlatform.getKoin()`, and `import kotlinx.coroutines.IO` for
  `Dispatchers.IO`.
- UI strings live in `presentation/src/commonMain/composeResources/values[-hu]/strings.xml`. Read them with
  `com.pandulapeter.campfire.presentation.localization.stringResource(Res.string.x)` (generated by the
  `com.hyperether.localization` plugin, switchable at runtime via `currentLanguage`), never with the
  `org.jetbrains.compose.resources` variant, which ignores the in-app language. Add every new string to both files;
  formatted strings must always be called with their arguments. A sentence that takes text somebody else wrote — a
  title, a tag, a header value, a file or account name — is read with `textResource(Res.string.x, text)`
  (`:presentation`'s `components/TextResource.kt`) instead: the plugin's formatter scans its own output a second
  time, and the `% s` in `100% sure` is a format specifier to it (`pluralTextResource` for a `<plurals>` that
  carries such text). A counted sentence whose singular reads differently
  is a `<plurals>` with `one` and `other` items, read with `pluralStringResource`, rather than a second key.
- **Formatting is checked, not only described**: the root `.editorconfig` holds the IDE settings (150 columns, trailing
  commas, no wildcard imports) and the ktlint rules the code follows, which `campfire-style` (Spotless) applies to every
  module — `./gradlew spotlessApply` fixes them, `spotlessCheck` runs in CI with the tests — and
  `.github/scripts/check_license_headers.py` requires the MPL-2.0 header in every source file, script, workflow, XML
  resource and `CLAUDE.md`. The rest of the style (comments, KDoc, the trailing comma exceptions ktlint cannot express)
  is the `code-style` skill's.
- The UI is Material 3 Expressive (`org.jetbrains.compose.material3:material3`, versioned separately from Compose
  Multiplatform in `jetbrains-compose-material3`); don't add `androidx.compose.material` (M2) back.
- **Nearly every change the user can see is animated**: something that appears, disappears, moves, resizes, changes
  color or swaps for something else gets there with a transition (a fade, a size or bounds animation, a crossfade, a
  morph) rather than in one frame. Only a change the user caused is narrated this way, though — data arriving, or a
  state that starts on a wrong initial value, is fixed at its source so the first frame is already right, not
  animated over.
- **Content scrolled under a bar fades out into it rather than the bar lifting**: no tonal elevation, shadow or
  divider appears when something is scrolled under an app bar, tabs or a pinned header — the content fades into
  nothing over a short gradient instead (`fadingTopEdge` / `fadingVerticalEdges` in `:presentation`'s
  `components/EdgeFade.kt`, and `ListTopFade` for the list screens' cards), and the bars stay flat in the background
  color. A new scrolling container gets the same treatment, not Material's scrolled-under elevation.
- **Every modal with text inputs is a bottom sheet**, and the date picker is one too, holding only a calendar (no
  typed entry, whose strings Material draws in the system's language). Forms use `TextFieldBottomSheet` over `CampfireBottomSheet`; Save, Create, Done, Delete,
  sorting and Add link actions sit in the header, whose close button cancels the draft. Ctrl / Cmd + S presses the
  header's Save, Create or Done (never a Delete), as it saves the editor and the export screen and answers the
  editor's unsaved changes question with Save. Keep each form's existing first-field focus behavior. Both **Choose songs** and **Choose setlists** open with search unfocused;
  tapping their search field brings up the keyboard. New song offers subtitle, artist, album, composer, lyricist,
  year and duration alongside the required title; each optional label uses the same parenthesized marker as setlist
  description and link name. The date picker's Material container uses the shared sheet color, matching its header
  and the other sheets in both themes.
- **A sheet or a dialog holds as little still as it can, and the rest scrolls**: on a small phone with the keyboard
  up (360 × 640 dp leaves about 330 dp above it) every pinned row — a header, tabs, a field, a row of chips, a bar of
  buttons under the content — is taken from the one scrolling part, and a few of them leave it no room at all. What is
  only set once (filters, query fields that a search button runs, a credit line) goes into the scrolling content as
  its first or last item; what finishes the sheet (Save, Remove) goes into its header (`CampfireBottomSheet`'s
  `actions`); only the header and what is typed into throughout (a search field) or switches the whole content (tabs)
  stay pinned. Count the pinned height against that screen before adding anything that does not scroll.
- **A short window gives the keyboard everything it can** (`SHORT_WINDOW_HEIGHT`, 480dp: a phone on its side, or the
  smallest one with the keyboard up). There a sheet's header and pinned controls scroll away with its content, above
  the keyboard; forms with text inputs use the same sheet layout, with their actions in the header; the editor's
  Shortcuts collapse once when the keyboard appears, with their chevron kept available to reopen them while typing (in
  the title row where a landscape keyboard leaves no room for the control row);
  its title row is 48dp and its lines closer together; both assignment sheets open without the keyboard; and the
  song details app bar hides as the song is scrolled down and comes back as it is scrolled up. On Android, typing in a short window also takes the status bar away until the keyboard goes
  (`CompactKeyboardEffect`, swiped back as a transient bar) — the only way a landscape keyboard leaves room for a
  field, a title row and three lines. A phone held upright gives song cards two title lines and narrower padding, and
  a setlist's description starts at two lines in a short window, opening on a tap. Song lists keep their 360dp
  minimum column (a setlist's 416dp, since its cards say more on a line), so a phone on its side stays one column:
  two would each be narrower than the portrait one.
- `:app:android` and `:app:baselineprofile` are plain Android modules, `:app:desktop` a plain JVM one, `:app:ios`
  Kotlin/Native-only and `:app:web` Kotlin/Wasm-only; every other module (`:presentation` and `:chordpro` included) is
  a multiplatform library.
- **Koin is wired with Koin Annotations through the Koin compiler plugin** (`io.insert-koin.compiler.plugin`, applied
  through `campfire-koin` by every module that declares a definition). A class declares itself: `@Single` on repositories, local sources,
  the platform storage and the authenticators, `@Factory` on use cases, `@KoinViewModel` on `CampfireViewModel`.
  Each module's top-level `Module.kt` holds one `@Module @ComponentScan object XxxModule`, empty where the classes
  annotate themselves and holding a `@Single` function where a definition is built rather than constructed (the
  HTTP client, the list of sync providers). Platform definitions are ordinary annotated classes in the platform
  source sets (`AndroidFileStorage`, `IosSyncAuthenticator`, …), found by the same scan, so there is no
  `expect`/`actual` factory between a platform and its Koin definition. `:app:di` names the seven module objects in
  the one `@KoinApplication`, and `startCampfireDependencyGraph()` is what the four entry points start Koin with;
  the plugin checks the whole graph there at compile time, so a definition asking for something nobody declares
  fails the build. A dependency only a platform shell provides — the Android `Context` — is marked `@Provided`,
  which tells that check not to look for it. **Never inject a `List<T>`**: the plugin resolves a list parameter as
  `getAll<T>()`, every definition bound to `T`, and not as a definition whose type is the list, so it compiles, passes
  that check and arrives empty. A list that is itself a definition is wrapped in a type of its own (`SyncProviders`).
  `:chordpro` has none of this: it is a set of stateless objects, reached
  through use cases.
- Implementation classes are `internal` and named `<Interface>Impl`. Use cases are `operator fun invoke`.
- `:chordpro` and `:metronome:api` build in explicit API mode (`explicitApi()`), so a new public declaration is written
  `public`, with its type, on purpose; what nothing outside the module calls is `internal`.
- Repositories extend `BaseLocalDataRepository`, which holds the cached `DataState` and the read-once logic.
- Layer boundaries are crossed via mappers (`mapper/` packages), never by leaking document/entity types.
- **A setlist shows every song it names**, whatever the Songs screen is filtered to: the filters narrow a view of the
  library, while a setlist is the list somebody wrote down. What the Setlists screen's own controls ask is the order
  the setlists come in and whether the archived ones are among them. Archiving is how a setlist that has been played
  is put away without the songs in it being lost; it is a field of the `*.setlist.json` file rather than a
  preference, so it travels through an export, an import or a sync run the way a tag does. The **description** — an
  optional sentence about what a setlist is for, shown under its header and read by the screen's search — lives in
  the file for the same reason, and so does the **date**: the day the setlist is for, an ISO date that starts as the
  day it was created here and is moved with a calendar sheet opened from the sheet that names the setlist. **Every
  setlist has one**: an import dates a setlist that carries none the same way (the bundled demo one included), unless
  it replaces a library setlist, whose day it keeps, and a file in the library that names none — written before there
  were dates, by hand, or by an older version on another device — is given the day it is first read on and saved with
  it right after that read, through the repository's locks but announcing nothing, so the next sync run carries it.
  The setlist details sheet's **Countdown** checkbox, off by default and in the file too, puts a subtitle under the setlist's
  sticky header that says how far that day is ("In 5 days", "Today", "Yesterday") — the only place the date shows
  outside the sheet, so the one way it can be seen in performance mode. The same subtitle carries how long the setlist's songs take ("42:30 running time", the label after the number so that a narrow header cuts off the label rather than the time), after the countdown or in its place,
  added up from their `{duration}`s and marked with a `+` where some songs have none that can be read. Sorting by date puts the latest day on top, the setlists
  of one day by their title.
- **Both list screens are searched from a button rather than from a field that is always there**: the app bar has
  no title — the list's pinned section header stands in its place — and the one search icon is the one close button (the mark morphs
  between the two as the button travels from the actions to the start of the bar, with the field after it, see
  `:presentation`). On the desktop and the web Ctrl / Cmd + F opens it, in place of the browser's find bar there.
  The songs are searched by title, artist and tags, ignoring case, accents, spaces and punctuation alike (`ymca` finds
  `Y.M.C.A.`), a song found by a tag alone coming after those found by their title or artist; a setlist answers by its own title or description, or by holding a
  song that does — and a setlist that answers is shown **whole**, since a setlist is the list somebody wrote down and
  three of its twelve songs is not that list.
- **Tags are part of the song file**, not a store of their own: ChordPro `{tag}` directives, read by `:chordpro`
  into `Song.tags` at scan time and written back into the text the same way, so a tag travels with the file through
  an export, an import or a sync run. The library's set of tags is whatever the songs carry; the Songs screen's
  filter offers them counted, most used first or alphabetically (a toggle next to the group's title, which the sheet
  that puts them on or takes them off — opened from the song details' About the song sheet, the editor preview's card and a song card's menu on the Songs screen — shares; one preference per
  group, `UserPreferences.tagSortingMode` and `languageSortingMode`, the languages being ordered the same way).
- **The language of a song is carried the same way, and is its own category rather than one more tag**: a
  `{meta: language en}` directive per language, read into `Song.languages` as a lowercase ISO code — 639-2's three
  letter codes included, folded to their 639-1 equivalent where the standard has one (`eng` is `en`) and kept as
  they are where it does not (`rom`, Romani), so one language is one code however the file spells it. It gets its own
  filter group on the Songs screen — but only once the library holds more than one language, with an "Unknown" chip
  for the songs that declare none — and it is shown wherever a tag is: next to them under a song in the lists, and as
  one read-only chip per language in the song details' About the song sheet and the editor preview's card; the same places open the picker. The **names
  are never shipped**: the app carries a list of codes and nothing else, and asks the platform what each is called in the language the app is set
  to (`java.util.Locale`, `NSLocale`, `Intl.DisplayNames` behind `:presentation`'s `languageDisplayName`), falling
  back to the code in capitals where it cannot say.
- **The cover of a song is carried the same way too**: a `{meta: cover https://…}` directive, read into
  `Song.coverArtUrl` as the first one holding an `http` or `https` address. The file carries the address and nothing
  else, so the cover travels through an export, an import or a sync run as a tag does, and the image is fetched where
  the song is read. Any address is taken — the library and the addresses in it are the user's — and the search is only
  ever what recommends one. See Cover art below.
- **Links about a song are carried the same way**: a `{meta: link https://… Optional name}` directive per link, read into
  `ChordProMetadata.links`. Any page is taken, whatever site it is on, and nothing is ever fetched from one: the
  song details' About the song sheet shows each as a chip named by its optional name or its host, opening the page in
  the browser. The sheet is opened by tapping the song details app bar's title while the song is at its top, which a chevron after
  the title says (it has no button or menu entry of its own) — and holds what the song says about itself, each group with an edit button next to its title outside
  performance mode and outside an archived setlist, where a group it has nothing for is its title and a plus alone; key, capo, tempo and time stay on the page as the song's first section (see How a song is played below), and the sheet only reads what the file declares for them, opening the Song defaults sheet and saying where the song is being played differently. The editor's preview shows
  the same as a card that is the song's first section, flowing through its rows and columns and scaling with the
  lyrics, with the same edit buttons. The editor's overflow menu offers these actions in every pane, and the song
  details screen's editing menu — a pencil button of its own before the overflow menu, holding the editor and these —
  offers them outside performance mode and for a song not opened from an archived setlist (which has no editing menu
  and leaves its one overflow menu Edit and Export, as an archived setlist's own song menus do), with cover art editing
  following the cover art setting.
  Performance mode leaves that row out, and the button that opens it where all of them are empty. The Manage links sheet edits addresses and optional names together,
  and their order, written once on Save; the links are shown in that order, where tags and languages are always
  shown alphabetically.
  From the editor those buttons change the text being typed rather than the file, which only Save writes.
  Opening a link is the user's browser making the request, not Campfire.
- **How a song is played is set in the song itself**: the transposition, the capo, the tempo and the time signature —
  the four values that decide what is played rather than what the song is — are the first section of the song details
  screen's own grid, each next to the control that sets it (the transposition stepper, which is named **Transposition**
  and reads the amount next to the key it takes the chords on the page to, the capo stepper, the tempo stepper whose
  pill ends in a Tap segment, since tapping a tempo in sets the very number the stepper steps, and after it the time
  signature, which is only read there). They are part of the song, so they
  grow and shrink with its text, which is also why the text has a floor: `UserPreferences.MIN_FONT_SCALE` is the size
  below which the song details screen is not worth reading — the lyrics, and the controls with them. Starting the click
  stays the app bar's button, which is in reach wherever the song has been scrolled to. Three of them are
  overridden where the song is read, so they belong to the setlist the band plays it in or to this device (see the
  Metronome section); the time signature alone is written into the file as a `{time}` directive, since it is the song
  rather than one band's reading of it, and it is what the click counts the bar by. **What the file declares for all
  four is edited in the Song defaults sheet**, an entry of the song details editing menu and of the About the song sheet's Song defaults group, and nowhere on the page (the editor's overflow menu has it too, writing into the text being typed, without the line and the card below, since the editor has no steppers): it opens with a line saying that the steppers
  change them for this setlist only, or, opened from the library, outside every setlist — never naming a device,
  since the library's own overrides are synced (see Sync) — then a card naming what is adjusted there, with a Reset, while there is any, then the key, capo, tempo and time signature fields, each optional, an
  empty one leaving the default in force. A new song's template carries an empty `{key}` line and `{capo: 0}`,
  `{tempo: 120}` and `{time: 4/4}`, the defaults written out, for them to be edited. **Read only mode reads them
  instead**: performance mode and a song opened from an archived setlist get the same four as one line of accent
  colored text — which always names the capo and the time signature there, "Capo 0" and the click's 4/4 where the file
  says nothing, since with no control left on the page an absent value would read as an unknown one — as does the
  editor's preview, where the text being typed is what says them. With the chords switched off the
  key and the capo leave the line and the controls alike, and with the metronome switched off the tempo and the time
  signature do (see Features below) — everywhere but in the editor's preview, which says all four whatever the
  switches and in the key it is written in, since it shows what is being written. What is left in the app bar is about the song rather than about how it is
  played — the click, the Choose setlists and the menu, the title opening the About the song sheet — with the text size, which is
  the reader's own, at the end of that menu. **The key the band actually hears is named in the app bar**, after the
  artist and the way a song card names it: the transposition *and* the capo applied, so it is the key the song sounds
  in rather than the one the chords on the page spell, which is the Transposition control's — the two read differently
  wherever the capo is not zero. The tempo the click would play at follows it there, as it does on a card, and inside a setlist the song's duration
  after that, as its card there has it.
- **Features are switched on and off as a whole** in Settings → Features, the second tab, so that the app is only
  what its reader needs — a singer has no use for a metronome, a band playing from one list none for setlists. Every
  switch is a `UserPreferences` field, never exported or synced, and a switch only hides: nothing in the library is
  written or deleted by one. In order: **Read only** (`isPerformanceModeEnabled`, first because it decides what the rest
  of the app is still allowed to do, and called performance mode in the code); **Chords** (`areChordsEnabled`, stored
  as the inverse `isLyricsOnlyModeEnabled` the lyrics only switch was), off taking with the chords the key, the
  transposition and the capo wherever a song is read, and leaving the chord spelling rows of the Songs tab disabled;
  **Chord diagrams** (`areChordDiagramsEnabled`), disabled while the chords are off, off taking the Chords section off
  every song and leaving the Songs tab's Instrument row disabled, the chosen shapes kept; **Metronome** (`isMetronomeEnabled`), off taking its tab, the song details screen's click button and panel, its M key
  and the tempo and the time signature wherever a song is read; **Setlists** (`areSetlistsEnabled`), off taking their tab and every way of putting a song into one, while
  setlist files still travel through an import, an export and a sync run; and **Cover art**, last since it is the one
  that decides whether the app reaches the network on its own. A switch cleans the interface up rather than locking
  anything, so what it takes away is gone everywhere, the Song defaults sheet and the About the song sheet's Song
  defaults group included (each value with its feature, the sheet's menu entry and the group once both are off), and
  an override nobody is shown is neither named nor reset there. A tab switched off leaves the navigation chrome
  (`CampfireViewModel.topLevelDestinations`), shrinking out of the bar or the rail as the others close the gap
  (`components/NavigationItemPresence.kt`), and a back stack restored or an address opened is cut short at the first
  screen that belongs to one (`NavigationState.withoutDisabledFeatures`), a song read from a setlist included — so on
  the web `/metronome` with the metronome off opens the songs, and the address is written over with theirs.
- **How every chord of a song is fingered is shown at its top**, on the guitar, the ukulele or the keyboard (the
  Songs tab's Instrument): a Chords section after the controls of how it is played, one diagram per chord in the order
  they are first played, folded by one preference for every song and cut between its rows of diagrams wherever a page
  ends inside it, like any other section. Nothing is shipped for it and nothing is fetched: `:chordpro` reads what
  notes a chord name stands for (`ChordProChords`) and finds its shapes (`ChordVoicings`) — a hand-typed table of the
  shapes everybody knows first, a search for every other one after it. Which shape a chord is drawn with is, in order, the song's own `{define}` (or `{chord}`) for that
  instrument, which ChordPro has for "in this song the G is played this way" and which travels with the file; the
  player's own choice from the Chord shapes sheet, **one per chord and instrument for the whole library**, since which F
  somebody plays is a habit of their hands rather than a reading of one song, stored as the shape rather than its
  number and keyed by the chord's notes so every spelling shares it (`UserPreferences.chordVoicings`, synced, see Sync);
  and the app's first shape. A definition for another instrument is not used on the page, nor translated: a guitar
  shape's fingers make another chord on a ukulele, and a keyboard plays the chord's own notes. A definition follows its
  chord through every transposition — the reader's, the file's `{transpose}` and the editor's transpose action, which
  rewrites the line in place — along the neck, in whichever octave a hand can hold it, and one a transposition left
  needing a fifth finger gives way to the player's shape. On the keyboard a capoed song draws the chords that sound.
  The editor writes definitions (its Chord shape button), marks one it cannot read and draws every one in its preview.
  The PDF prints them under each song's heading where the export screen's Chord diagrams box is ticked (as every
  option is for a new user; offered only while the feature is on and a song has a chord, and only with the chords printed), drawn as the song
  details screen draws them, their names left out of the file's selectable text.
- **The app is shipped with two songs and one setlist**, in
  `presentation/src/commonMain/composeResources/files/demo`: public domain campfire standards, bundled as the plain
  ChordPro and setlist files they are and reaching the library through the ordinary import, so they collide, are
  numbered and are disregarded when the same file is already there like anything else. The songs are few on purpose
  and chosen so that between them they use the directives the song details screen draws, and every one carries a
  `Demo` tag, so that they can be filtered out of a library that has grown past them. They are planted once, on a
  run that finds no preferences document *and* an empty library — which is what a fresh installation looks like from
  the inside, and is why a library somebody has been using is never touched. That first run writes the preferences
  whether it planted anything or not, so an installation that started with an import of its own and was emptied
  later is not taken for a fresh one. Settings offers to add them for as long as the library is missing any of them, so
  a deleted one comes back by being asked for rather than on its own. Both ways of planting them remember what they
  wrote under the demo's own names (`RememberDemoLibraryFilesUseCase`, a local-only preference), so that a sync run
  meeting another version's untouched demo in the cloud folder takes it instead of keeping a copy of each. That first run is also the only one that opens
  with the **welcome sheet** over the library: a line about the app, the theme and the color, and the way to Settings,
  naming Dropbox sync where the build has it — short on purpose, since the demo songs behind it say the rest. Each file is named exactly as the library would
  name the song inside it, which is what lets one list both read the resources and answer whether they are already
  there.
- **What's new** introduces each version once, after the app is on screen and startup import questions have finished.
  `UserPreferences.seenWhatsNewVersions` remembers every introduced version and the first installed version, which
  is skipped in favor of the welcome. Empty `whats_new_message` resources suppress the dialog and still record the
  version; the prepare-release skill replaces or clears that message in both languages for every release.
- **The app icon follows the theme color** wherever the platform lets an app change it: the launcher entry on Android
  (one `activity-alias` per color, see `app/android`), an alternate icon on iOS, the favicon on the web, and the
  window, taskbar and Dock icons of a running desktop app — unless the user turned that off
  (`UserPreferences.isAppIconThemed`, a switch under the colors named after the platform's icon), which keeps the
  app's own. **The app's own colors are the promotional material's purple and orange** (#5A49CA and #F57C00): its icon,
  and so every packaged one (the installed app, the Start menu, a store's page), is their diagonal gradient, and the
  "Campfire" palette is dusk around a fire: Material tones of the purple, tinting the neutrals too (a lavender paper by
  day, a violet night sky in the dark theme), with the orange as the tertiary and the second accent — the chords,
  the key, the capo and the lists' sticky headers (`LocalSecondAccentColor`, which is the primary color in every other
  palette); the one place the two meet in the interface is its color disc in Settings, which is the icon's gradient.
  The gray is the last option, a color like the rest; it was the app's own before, under the id the new palette took, so
  nobody was left on it. Android's own launcher icon and its Play icon are drawn as they are; every other icon, the
  packaged ones included, is the hand-drawn orange one in `app/icons` recolored by `app/generate_theme_icons.py` — the
  app's own onto the gradient read off the Android icon's background — and the files it writes are committed.
- **The app says nothing about the other builds but where to find them.** Settings → About is one section on every
  platform, and the row that names no platform — "Every version of Campfire" — leads to the download section of the
  app's website, https://campfire-songbook.com/#download (the `campfire-website` repository, which the web build is
  deployed into as well), which is a page that can be kept up to date without a release and the one place a store has
  nothing to say about. `Distribution` (in `:presentation`'s `ui/platform/Platform.kt`) is now just the four app stores and
  their listing URLs, a null `listingUrl` marking one the app is not on yet; publishing is filling it in.
  Every platform has exactly one official way to get the app, so no build is told where it is handed out:
  `platformStore` is the store of the platform the app is **running** on — a Mac build made by hand is a Mac build
  like the one the Mac App Store hands out — and it decides both the one "Rate Campfire" row, absent on Linux, on the
  web and wherever that listing does not exist yet, and whether the app may ask for money at all
  (`canAskForDonations`: never on an Apple platform, guideline 3.1.1). The row says *rate* and never *install*: a store page
  carries an install button, and a second copy of the app would come with a library of its own. **campfire-songbook.com is the
  project's website**, and what the app and the README point people at first: the downloads, the support page (which
  answers the common questions and gives both an email address and the GitHub issues as the way to report a problem —
  Settings' "Help and support" row), and the privacy policy. **GitHub is the source code and the issue tracker**, and
  its row comes after those; the About section links nothing else but the author's own site and the donation page.
- The file name is a song's (and a setlist's) identity. Nothing is ever overwritten implicitly: a new or imported file
  that collides gets a `_2`, `_3`… suffix (`FileNames.kt`). An **import decides before it writes**: every incoming
  file is held against the name it wants (`PrepareImportUseCase` -> `ImportPlan`), a song the library already holds
  under that name or a numbered sibling of it (`x_2.cho`) — or under the very name it arrived with, which is what an
  export of a file named by an older rule carries — is disregarded rather than copied (for a song, comparing both
  sides after `ChordProPrettifier`, notation normalization and with every directive in one spelling — `{t:X}` is
  `{title: X}`), and two different files of one
  batch that want the same name are never a question: the second is numbered like any other collision — the
  library's own file among them: a song or setlist the batch brings back unchanged is never offered up for
  replacement, so a different one wanting its name is numbered next to it. The names
  taken by something *different* are put to the user as one question about the whole batch — keep both, replace,
  skip, or cancel the import. Replacing is the
  only thing in the app that ever overwrites a library file, and it takes an answer to that question and a
  confirmation after it. An import that takes a moment shows its phase and a processed-entry count in a dialog, and
  can be cancelled until it starts writing; one that went the one happy way — everything written or already there, nothing left out — ends in a snackbar (with
  **Details** for a batch of more than one file). **Anything else is a screen of its own** (`CampfireDestination.ImportReport`),
  pushed on the back stack rather than told in dialogs following one another: the question, then the import it decides
  on being written, then every file of what it came to, grouped by what became of it and searchable, a song opened from
  it coming back to it. A write failure stops the batch and reports its partial success, the failed source and every
  unprocessed entry there; an import that could not be read at all is one "Import failed" snackbar.
- **Documents are converted locally**, never rendered, uploaded or kept: `.pdf` and `.docx` pass through
  `DocumentRepository` to pure-Kotlin extractors, then `ChordSheetConverter` to ordinary ChordPro. Plain `.txt`
  and text shared on Android use the same converter; the ChordPro extension family never does. Recognized ChordPro
  text keeps its content, with chords written in the standard notation (see below) and raw formatting standardized
  by `ChordProPrettifier`, also available from the editor overflow menu. All imported songs use that formatter after
  conversion and splitting. Import comparisons format both sides so older library files still match. Documents with no readable text (scans, encrypted PDFs and legacy `.doc`) are
  reported separately from unsupported files. The input limit is 16 MiB per document, 8 MiB for the text produced,
  within the selection's existing 24 MiB budget. Positioned chords, English/Hungarian sections and styled headers
  are best attempts, and clearly titled page starts can split a songbook. Converted songs are named by the header
  the converter wrote, with the ordinary filename fallback when it wrote none; duplicate and conflict rules are
  unchanged. The result counts only converted songs actually written and offers **Open** for a single converted
  song, without navigating automatically. No new document Open with association is registered; Android adds only
  PDF and Word share MIME types.
- **Other apps' libraries are archives under names of their own.** A file whose extension the import does not know
  is read anyway (`ImportBudget`, within the selection's budget) and kept only where its bytes start like a zip
  archive's, which is then unpacked like a `.zip` (or, where it holds nothing an import reads — an OpenDocument, an
  e-book — reported as the one unsupported file it was) — anything else is reported as unsupported and gives its share
  of the budget back. SongbookPro's `.sbpbackup` and `.sbp` are also named (`LibraryFiles.LIBRARY_BACKUP_EXTENSIONS`), so that
  the pickers offer them and an archive is looked inside when it holds one. An archive that turns out to be a
  SongbookPro library (`dataFile.txt`, one JSON document) is translated into the files an export of Campfire's own
  would carry — a ChordPro song per song, its title, artist, key, capo, tempo, time, duration, copyright, link and
  folders (as tags) written into the header where the text does not declare them, and a setlist per set, with the
  capo a set plays a song with where it differs — and goes through the ordinary import from there
  (`:data:source:local:implementation`'s `backup/`). Its transpositions are not carried over. None of these is
  registered as an Open with type: they are another app's files.
- **Every file is in the standard chord notation** (`C D E F G A B`, `#` and `b`), whatever notation its reader
  prefers (Settings → Songs, one choice of five): the German one (`H` for B, `B` for B flat) and the Latin one (`Do Re
  Mi Fa Sol La Si`) are ways of showing chords and of typing them, converted on the way to the screen and in and out of
  the editor's field (`:chordpro`'s `ChordProNotation`), so a library reads the same in every app and on every device.
  An import writes every song's chords that way, which only changes a chart that used an `H`, a Latin name or the `♯`
  and `♭` signs; a file that arrives otherwise (a sync run, the library folder edited by hand) is read the same way —
  an `H` anywhere marks it German, and a Latin name is read as the chord it names wherever it stands, since none is
  also a standard one — and brought into the standard notation the next time the editor saves it. The one chart
  nothing can tell apart is a German one in a flat key, which never needs an `H`: it is read as standard. The editor's
  field is in the reader's notation, so what they type is never ambiguous. **Nashville numbers and Roman numerals**
  (`1 4 5 6-`, `I IV V vi`, an extension that starts with a digit set off in parentheses in numbers: `5(7)`, never
  `57`) are only ever shown: the page, the editor's preview and the PDF count the chords from the
  song's key (a minor song from its own tonic), every key the app names stays in letters, a song with no key stays in
  letters, the chord diagrams are named by the step and by their letters, and the editor's field is in letters, since
  nothing is ever typed or stored in numbers.
- **Every name the app writes is normalized** — lowercase words joined with underscores, Latin letters without their
  accents and letters of every other script kept as they are (`катюша.cho`), capped at 120 UTF-8 bytes per half
  (`LibraryFiles.normalizedName`), a song's `artist` and `title` folded one at a time so the dash between them
  survives as structure: `tukorfurogep-arviz.cho`, `summer_set_2026.setlist.json`, colliding as `_2`. Three of the
  folding rules are there so that the same song written down by two people arrives at one name: an apostrophe is
  dropped rather than folded to a separator (`dont_cry`), `&` and `+` are spelled out (`rock_and_roll`), and a credit
  is filed under `ft` however it was abbreviated. Before any of that the name is brought to Unicode NFC
  (`normalizedToNfc`, an expect/actual in `:data:model`), since macOS and iOS hand out names decomposed and every other
  platform composed, and a non-Latin letter keeps its marks — so the two forms would be two songs; sync's name matching
  and the import's family lookup compose too, and every place that asks whether two names are one file asks
  `LibraryFiles.identityKey` / `isSameLibraryName` (NFC, then a per-character case fold, the same on every platform). The rule is idempotent, which it has to be, since a name that left the
  app is normalized again on its way back in. Nothing is migrated, and a name that differs from the normalized one
  only by case or by Unicode form is taken as that name — it is the same file to APFS, NTFS and the sync service, and
  a move nothing else can see is one other devices never follow — so a capitalised or decomposed file keeps its
  spelling until **Update file name** (or, for a setlist, a new title) moves it for a reason that is part of the name.
- **A song is named by its own header, wherever it came from**: `{artist}`, `{title}` and `{subtitle}`, the subtitle
  joining the title half (`green_day-good_riddance_time_of_your_life.cho`) because it is part of the title everywhere
  else in the app. That holds for a song written in the editor, one that arrives through an import
  (`SongLocalSource.importFileName`) and one handed out by an export (`ExportFileNames.kt`) alike — the name a file
  arrives under counts for nothing except where the song inside it declares no `{title}`, in which case it stands in
  as the title, since that is what would title the song in the library anyway. So the invariant worth stating plainly
  is that **a file name is reproducible from its header alone**, and `Song.canUpdateFileName` is what notices where
  that has stopped being true. Inside an exported archive the entries keep their library names, since a setlist points
  at its songs by file name.
- **A file is only ever renamed by the app when the user asks for it, or when nothing is lost by it.** A setlist's
  file follows its title, because that title is written inside the document and the file name records nothing
  (`EditSetlistUseCase`, which is also where the description is written, since the two are the whole of what the
  user gets to say about a setlist). A song's does not: its name is what titles it wherever the file declares no `{title}`, it
  is what a setlist points at, and on the platforms where the library is a folder the user may have chosen it — an
  import is not one of those cases, since nothing has pointed at the incoming name yet. Where
  a song's name and its metadata have drifted apart (by more than case or Unicode form), `Song.canUpdateFileName` puts an **Update file name** entry in
  its menu, and taking it moves the file and everything that named it — every setlist entry, the saved transposition,
  the open screens (`RenameSongFileUseCase`), a setlist that already named the file under its new name keeping the one
  entry it had. Files that were named before any of this keep their names until one of those two things happens to
  them.
- A rename reaches **sync** as a deletion and a new file, since `SyncPlanner` is keyed by name and knows no moves. The
  "an edit beats a deletion" rule then applies: a device that edited the file under its old name since the last run
  puts that file back, leaving both.
- Only pure logic is tested: `commonTest` unit tests in `:data:model` (the library name identity rule and the tag
  normalization), `:chordpro` (including chord-sheet conversion, and chord names, shapes and definitions, every shape of the tables checked against the chord it is filed under), `:domain:implementation` (`ImportPlanner` and conversion import plumbing),
  `:data:formats` (zip, bounded PDF/Word readers), `:data:source:local:implementation` (the JVM file storage, with independent-producer document goldens in `desktopTest`), `:data:source:remote:*` (hashing, encoders,
  the OAuth authorization URL, the cover search's queries, its `User-Agent` and its pace, the cover download),
  `:data:repository:implementation` (the caches and the cover cache), `:data:sync:implementation` (`SyncPlanner`, which
  decides what happens to every file in a sync run, the engine and the synced preferences), `:metronome:*` (the sequencer, the synthesizer, the mixer, the engine's state machine, tap tempo and time signatures) and
  `:presentation` (the pure helpers behind its screens: the search index and ranking, the song picker's filter chips, the fast scroller's section
  index, the setlist slots, stepper labels, section grid and the cutting of sections into columns, row snapping and section measurements of the details screen, the editor's token cache, where a song's tempo comes from and what a click plays for, which chords a song plays and which
  shape each is drawn with, the diagrams' geometry and what the editor's Chord shape button writes), run on
  the desktop target with
  `./gradlew :data:model:desktopTest :data:formats:desktopTest :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest :data:sync:implementation:desktopTest :metronome:api:desktopTest :metronome:implementation:desktopTest :presentation:desktopTest`.
  The build logic's packaging helpers (the `.msix` version and publisher id, the launcher configuration and `.deb`
  rewrites) are tested in `gradle/build-logic` with `./gradlew -p gradle :build-logic:test`.
  The web build's JavaScript — its storage worker, its service worker's routing and the page's decisions about the
  build it keeps — has Node tests of its own (see `app/web`), and the parser of a
  release's description, which is Python, a `unittest` next to it in `.github/scripts`.
  `.github/workflows/tests.yml` runs all three — every module's `desktopTest`, the Node tests and the Python one, with
  `spotlessCheck` and the license header check beside them — and compiles what `desktopTest` does not: the Android
  app (every library's `androidMain`), the desktop and web apps, the Baseline Profile generator and the screenshot tool
  on Linux, and the iOS framework (every `iosMain`) on a macOS runner — on every pull request, every
  night on the default branch, and from `publish-all.yml` before it starts a single store build, so a failing test
  or a platform that does not compile stops every store's release rather than one of them.
  The UI itself is untested by code: `:app:baselineprofile` drives it, but only to record a profile, asserts nothing
  and is never run by CI; a release is checked by hand, on a Mac, before it is published.

## Build

- Dependency versions in `gradle/libs.versions.toml` (including `android-compileSdk` / `android-minSdk`). The
  version and the build number are `campfire.versionName` and `campfire.buildNumber`, one of each for every platform:
  the build number is Android's version code, the Mac build's `CFBundleVersion` and the iOS one's, the last written
  into the built
  `Info.plist` by a build phase of the Xcode project, which sets no version of its own, reading `gradle.properties`
  and then `local.properties` the way Gradle does.
- `gradle.properties` turns on the local build cache and parallel project execution (`org.gradle.caching`,
  `org.gradle.parallel`). Parallel lets a Kotlin/Native release link run beside other compilations in one daemon,
  which is why `publish-ios.yml` raises the daemon's heap, and the shared Kotlin daemon ran out of memory with one
  compilation per core, which is why `org.gradle.workers.max` is 4; lower it rather than turning parallel off. CI caches Gradle with `gradle/actions/setup-gradle`, the
  cache written by runs on the default branch and read by every other.
- **Everything configurable is a `campfire.*` Gradle property**, declared with a default in `gradle.properties` and
  read with `project.property("campfire.x")`: the app version and the build number, the
  Android release signing values, the Dropbox app key, the Mac App Store
  signing, the Microsoft Store package identity, and whether the web distribution is precompressed. `property`
  rather than `findProperty`, so a typo fails the build instead of writing the string "null" into an APK. Inside a `tasks.registering { }` block it has to be `project.property(...)`, or the
  lookup goes to the task.
- **`local.properties` overrides any of them, and is never committed.** `settings.gradle.kts` loads it and writes each
  entry onto every project before it is configured, so no build file knows the mechanism exists — they all just read
  a property. That is the whole secret story: nothing private is in the repository, and a fresh clone still builds
  every variant, because the checked-in defaults point at the debug keystore committed next to them and at an empty
  sync key. A release built that way is installable but not publishable, and Settings says sync is not configured.
  To sign for real, or to build with sync, add the keys to `local.properties`:

  ```properties
  campfire.android.keyAlias=...
  campfire.android.keyPassword=...
  campfire.android.keystoreFile=release.keystore   # relative to app/android, or an absolute path
  campfire.android.keystorePassword=...
  campfire.dropbox.appKey=...
  ```

  CI has no `local.properties`, so every workflow writes one from its own secret store with
  `.github/scripts/write_local_properties.py` (tested), each value reaching the
  script through `env:` rather than being interpolated into it: a `-P` puts the value in the runner's process list,
  and a secret substituted into a `run:` block is re-read by the shell, so a password holding a `$`, a backtick or a
  quote would sign with something other than what is stored. Backslashes are doubled on the way in, since
  `java.util.Properties` reads one as an escape. The file is written and read as UTF-8, with LF line endings on the Windows runner too, so a value outside
  ASCII survives as well. Before any of it, `.github/scripts/require_secrets.py` stops a workflow whose secrets are
  empty.
- The `campfire-library` convention plugin sets each module's `archivesName` from its Gradle path, because a klib
  carries the name of the artifact it is built into and half the modules here are called `api` or `implementation`.
- `./gradlew :app:android:assembleDebug` — Android APK; from Android Studio, the shared "Android" run configuration,
  never "Default Activity" (see `app/android`)
- `./gradlew :app:android:generateBaselineProfile` — records the Android app's Baseline Profile and startup profile on
  the connected emulator into `app/android/src/main/generated/baselineProfiles`, which is committed; a release build,
  CI's included, only packages those files and needs no device (see `app/baselineprofile`). Regenerate it when the
  startup path or the main screens change noticeably; the prepare-release skill also records it before every release,
  even one with empty notes. A stale profile is only less useful, never wrong.
- **`.run/` holds the four shared run configurations** — Android, Desktop, Web and iOS — and the IDE writes them itself,
  which is why they carry no license header: one would be gone on the next save. Desktop and Web are the Gradle tasks
  below. iOS is the Kotlin Multiplatform plugin's own kind, which an IDE without it (any on Windows or Linux) lists as
  one it cannot run and otherwise leaves alone; it finds the Xcode project through `.idea/xcode.xml`, the one file of
  `.idea` that is checked in, and makes a shared Xcode scheme of the configuration on every Mac, which is ignored (see
  `app/ios`).
- `./gradlew :app:desktop:run` — desktop app; `:app:desktop:packageDistributionForCurrentOS` for installers
- `./gradlew :app:ios:linkDebugFrameworkIosSimulatorArm64` — compile/link check of the iOS framework; run the app from
  Xcode (`app/ios/iosApp/iosApp.xcodeproj`) or with
  `xcodebuild -project app/ios/iosApp/iosApp.xcodeproj -target iosApp -sdk iphonesimulator -arch arm64 SYMROOT=<dir> OBJROOT=<dir> build`,
  then `xcrun simctl install/launch`.
- `./gradlew :tools:screenshots:run` — the store screenshots for every platform, into the gitignored
  `tools/screenshots/renders`, from a library copied into the gitignored `tools/screenshots/library`; the
  `store-screenshots` skill runs it, frames the images in Screenshot Bro and exports the store images to the Desktop
  and the README's banners to `documentation/screenshots`
- `./gradlew :app:web:wasmJsBrowserDevelopmentRun` — web app on a dev server; `:app:web:wasmJsBrowserDistribution` writes
  the deployable site to `app/web/build/dist/wasmJs/productionExecutable`.
- **Publishing a GitHub release is the release.** `publish-all.yml` answers it (a pre-release is left alone) by checking
  that the tag is the `campfire.versionName` of the commit it is on — a tag on a commit that still carries the last
  version would submit that version again under a new name — and that `campfire.buildNumber` is higher than the
  last published release's (the highest of the three per-store counters, for a release from before there was one),
  since a store would refuse a used one only after the other builds had gone out, running every test, and then calling the six workflows below side by side. Each of them is the local build command plus the secrets a checkout does not have, and each can still be
  dispatched by hand, to publish without a release or to repeat one half of a release that went wrong. What they build is the tag's, but the
  scripts in `.github/scripts` and the actions in `.github/actions` come from the commit the workflow itself runs from — the tag's on a release, and the
  branch's when the workflow is dispatched by hand with `release_tag` set, which is how a fix to one reaches a release
  already tagged without a new tag. The JDK and Gradle are set up by one action of the repository's own
  (`.github/actions/setup-jdk`), in `tests.yml` and every publish workflow alike; the step that fetches it from the
  workflow's commit is the one written out in each. Every build
  passes `campfire.dropbox.appKey` from the `DROPBOX_APP_KEY` secret, because a published app built without it would
  quietly have no sync provider at all — so each workflow, and `publish-all.yml` before it calls any of them, refuses to
  start when that secret is empty. The check is in the workflows rather than in Gradle: an empty key is the
  checked-in default and has to keep building a fresh clone. **A release carries only what no official channel
  offers**: nothing that a store or the website already hands out is attached to it, which leaves the Linux `.deb`.
  Nothing for the Mac or for Windows is attached: the Mac App Store build is the Mac build, and it is Apple silicon
  only, and the Microsoft Store build is the Windows build. `packageReleaseMsi` and `packageDmg` still build, and
  nothing publishes either.
  - `publish-web.yml` builds the distribution and copies it over `app/` in the `campfire-website` repository
    (https://campfire-songbook.com/app/), which it reaches with the deploy key in `CAMPFIRE_WEBSITE_DEPLOY_KEY`. The
    copy is an `rsync --delete`, so the folder holds nothing but the distribution — the privacy policy and the rest of
    the site live elsewhere there.
  - `publish-linux.yml` builds `packageReleaseDeb` on amd64 and arm64 — jpackage only packages for the machine it runs
    on — and attaches both to the release, which is the whole of how the Linux build is handed out (the website's download
    section and the README's "Get Campfire" section link to the latest release's page, and a `.deb` is not something anybody signs on its own).
    It builds on the oldest supported Ubuntu rather than the newest, since a `.deb` asks for the system libraries it
    was built against and the runner therefore decides the lowest distribution it installs on. The two legs do not
    cancel each other. ProGuard breaks an app in ways only starting it shows (see `app/desktop`), so each leg also
    builds the app image (`createReleaseDistributable`; the plugin packages the jars directly and leaves no image
    behind on its own) and starts it under Xvfb with an empty data directory, and attaches nothing unless the demo
    library appears, the process is still there after that, its log names no exception and at least 80% of the
    classes it loaded came from the class data sharing archives — the start check
    `.github/scripts/start_release_build.sh` makes for the Windows and macOS legs as well, each workflow preparing
    only its own environment around it. The packaging itself runs under Xvfb too, since it
    starts the image once to record the archive of the app's own classes that the package ships (see `app/desktop`).
  - `publish-windows.yml` builds `packageReleaseMsix` on a Windows runner (whose image has the SDK's makeappx),
    checks the identity and the version in the package's manifest against `gradle.properties`, starts the app image it
    was made of the way the Linux legs do (and reads the `campfire.log` the app writes into its data directory for an
    exception, and requires the class data sharing archive the packaging's training start recorded to be used), keeps
    the `.msix` as an artifact of the run and submits it with `.github/scripts/microsoft_store_submission.py`. That finds the app by its package identity name,
    so no Store ID is kept anywhere, creates a submission — a copy of the last published one — swaps its package for
    the new one, writes the release's `whats-new` notes as its "What's new in this version", sets it to be published
    as soon as it passes certification, uploads and commits it, and waits for Partner Center to accept the commit. A
    green run means submitted, not certified. A **draft** the script made whose commit failed is used instead of the copy:
    only its package and its "What's new" are replaced, everything else in it (the publish mode included) is kept as it
    is, and it is committed. **A draft started in Partner Center stops the run**, untouched: the API refuses to change a
    submission it did not create, so one prepared there is finished there with the run's `.msix` artifact, or deleted
    so that the run can make its own. **A release with new screenshots is dispatched by hand with `submit` off**
    instead: the run writes the notes into a draft of its own making and leaves its package alone, since Partner Center
    only takes in a package the API uploaded when the API commits the submission — one sent from Partner Center goes
    out with whatever package it shows, which for a copy is the last release's. So the package is replaced there with
    the run's `.msix` artifact, the screenshots are added and it is submitted there; the run ends green with a warning
    that says so.
    A submission past its commit with this version is left alone, so a repeated run succeeds; one past its commit with
    anything else stops the run, since a product has only one in progress at a time. It signs in as a Microsoft Entra application with the
    Manager role in Partner Center (`MICROSOFT_STORE_TENANT_ID` and `_CLIENT_ID`) and **with no secret**: the
    application has a federated credential that trusts the OIDC token GitHub hands the job, for the subject
    `repo:pandulapeter/campfire:environment:microsoft-store` — which is why the job runs in the `microsoft-store`
    environment and why `publish-all.yml` grants it `id-token: write` — so, like everything Apple's workflows use, nothing
    it signs in with expires (a client secret would, after two years at most). The package is unsigned, since the
    Store signs what it certifies with a certificate of its own, so nothing a later run does can invalidate a build
    still in certification; nothing is attached to the release.
  - `publish-macos.yml` builds `packageReleasePkg` on an Apple silicon runner — asking for the `.pkg` is what signs
    and sandboxes it — signed with a Mac App
    Distribution and a Mac Installer Distribution certificate and the two Mac App Store provisioning profiles (the
    app's and the bundled Java runtime's) that the run finds for the signing key (see below), all of them written into
    `local.properties` as a developer's machine keeps them. A build signed for
    the store does not start outside TestFlight, so the start check of the other desktop legs is made on a copy
    signed ad hoc with the same entitlements less the two that name the App ID — the demo library has to appear in
    the fresh sandbox container. It uploads the `.pkg` with `altool` and the same App Store Connect API key as iOS,
    and submits it for review the way iOS does (below); its `build_number` input uploads a release again under a
    number App Store Connect has not seen.
  - `publish-ios.yml` archives the app signed with the Apple Distribution certificate of the signing key (see below),
    lets xcodebuild make the App Store profile for it with the App Store Connect API key, and uploads the exported
    `.ipa` to App Store Connect, where it lands in TestFlight. Nothing is attached to the release.
  - **Both Apple workflows submit what they upload for review** (dispatched by hand with `submit` off, they stop short
    of the submission and leave the version prepared, for new screenshots to be added and submitted in App Store
    Connect): `.github/scripts/app_store_submission.py` waits for
    App Store Connect to process the build, takes the platform's version for `campfire.versionName` — the existing one, the
    editable one renamed, or a new one set to be released as soon as it is approved — attaches the build, writes
    the release's `whats-new` notes as its "What's New" (all but a platform's first version) and submits it. A
    **draft** that is already there — a version prepared in App Store Connect with the release's new screenshots,
    added to a review submission or not, or one that was rejected — is used as it is: only its build and its "What's
    New" are replaced, everything else in it (the release option included) is kept, and it is submitted in the review
    submission it is already in. A green
    run means submitted, not approved; App Review answers by email, and a rejection is answered in App Store Connect.
    A version that is already in review with this build is left alone, so a repeated run succeeds. Where another
    version of the platform is still waiting for Apple — in review, or approved and not on the store yet — the build
    is not submitted at all, since a platform takes one version at a time: it stays in TestFlight and the run ends
    green with a warning, before waiting for processing; it is submitted by hand once the other has been decided, or
    replaced by the next release's. The Xcode project
    starts Gradle itself and passes it no properties, so the sync key is written into `local.properties` there —
    which the version build phase reads too, after `gradle.properties` and with the last value winning, which is how
    the hand-dispatched form's `build_number` uploads a release again under a number App Store Connect has not seen
    without a commit. The archived `Info.plist` is checked against the expected version before anything is uploaded.
  - **Nothing Apple signs with expires.** A distribution certificate lasts a year; the private
    key it is made for and the App Store Connect API key (`APP_STORE_CONNECT_KEY_ID`, `_ISSUER_ID` and `_PRIVATE_KEY`,
    the last one the `.p8` file's text rather than base64, an Admin key) do not. So the secrets hold the two keys alone
    — the signing key in `APPLE_SIGNING_KEY`, as unencrypted PEM text — and `.github/scripts/app_store_signing.py`
    asks the API for the certificate of each type made for that key, matching it by its public key, and imports it into
    a keychain of the run's own. Where there is none, or the newest has less than two months left, it creates one for
    the same key; Mac App Store profiles are found or made for it the same way. **Kubriko signs with the same key and
    the same secrets**, since Apple allows the team only two Apple Distribution certificates: the two pipelines share
    one, and the other place is the renewal's (a certificate made by hand in Xcode takes one too, which is what a
    creation refused with 409 means). **The signing key's certificates are never revoked**: a build whose certificate is revoked before
    App Review approves it is refused as an invalid binary (ITMS-90238), even after it was processed, attached and
    submitted; the old one is left to expire, by which time everything it signed has long been decided, and the
    renewal comes early enough that no build waiting for review is ever signed with one about to expire. The
    `app-store-signing-ios` / `-macos` artifacts hold what runs made for keys of their own: each run revokes what one
    names once App Store Connect says the build it signed is not attached to a version still waiting for Apple, being
    prepared or rejected (one that names no build is kept while any version of the platform is waiting), and deletes
    the artifact; an artifact that expires leaves its certificates to expire on their own. It must never be used for a
    Developer ID certificate, whose revocation breaks every copy of an app already downloaded.
  - `publish-android.yml` writes the keystore out of `ANDROID_KEYSTORE_BASE64`, builds `assembleRelease` signed with
    the other three `ANDROID_*` secrets and uploads it and its mapping file to the production track with
    `PLAY_SERVICE_ACCOUNT_JSON` (as a draft there when dispatched by hand with `submit` off, rolled out from the Play
    Console); nothing is attached to the release. It is an **APK** and not an app bundle because the Play listing predates the bundle
    requirement and was never migrated; a `bundleRelease` would be rejected on upload. The "what's new" text comes
    from the workflow's `release_notes` input, which `publish-all.yml` fills from comments in the release's description
    that the rendered page hides (`<!-- whats-new en-US … -->`, written for every store and passed to the Apple and Windows workflows as well, and `<!-- play-store update-priority: 0 -->`, and one
    `<!-- <store> submit: true -->` for each of `play-store`, `app-store`, `mac-app-store` and `microsoft-store`, whose
    `false` passes that store's workflow `submit` off so the release is left there as a draft; the
    format is in that file's header). `.github/scripts/release_description.py` reads them, and nothing it cannot read
    is taken as absent, since every default is the stronger action: a store name it does not know, a `submit` other
    than `true` or `false`, a priority outside 0–5, or notes longer than App Store Connect's 4 000 characters or
    Partner Center's 1 500 (whatever those stores' `submit` says) stop the release before a build starts, and notes
    over Play's 500 are a warning, since Play is given the lines that fit. The text is carried through as it is,
    backslashes included; only the hand-dispatched
    form's `\n` is expanded, since a single-line text box has no other way to ask for a line break. It falls back to the visible description with its markdown taken out, and to "Bug fixes and improvements." where the description has no visible text either — or,
    dispatched by hand with nothing given, to the commit log since the previous tag. Every store listing is in
    English only, however many languages the app itself speaks. Its `update_priority` input is
    what decides whether the new version says anything about itself inside the old one — see Updates below.

## Sync

Off until the user connects a cloud folder in Settings, and built so that Dropbox is the first provider rather than
the only possible one. The per-module `CLAUDE.md` files carry the detail; the short version:

- `SyncProvider` sees one flat remote folder addressed by `(kind, name)`, the same shape the library has. Revisions
  are **opaque strings** the engine never parses, and a service's content hash stays in the provider — which is what
  keeps Drive's file ids and MD5s out of the engine when it arrives. What is not a song or a setlist by its extension
  is invisible to the engine on both sides, so whatever else the user keeps in the folder is left alone. A remote file
  whose name the device cannot hold (a `\` anywhere, or `? : * " < > |` on Windows) is left out too, and named once in the run's summary
  rather than failed on every run.
- `SyncPlanner` is a pure function of (local hashes, remote listing, the index of what the last run saw) and is the
  part that is tested. Content decides what changed, never a clock: the platforms disagree about modification times
  and the web has none. An edit always beats a deletion.
- A plan that would delete, **on this device or in the cloud folder**, more than half of the files the index knows
  (and at least five of them), or every one of them, is not carried out: the run stops before anything moves and
  Settings asks, naming the side. On this device that is the shape of a remote folder that was emptied, renamed or
  replaced; in the cloud folder, of a library folder that was moved or deleted under the app — which lists as empty,
  so an empty library with an index that is not always asks, however small. Carried out faithfully either would leave
  every device with only what had been edited since the last run. **Delete them here too** / **Delete them from the
  cloud too** runs again with the deletions allowed; **Keep them and upload** / **Keep them and download** runs again
  with those files' index entries dropped, so they are new on the side that still has them and are copied back. An
  answer waives the guard of its own direction only, this device being asked about first. The answer belongs to that
  one run, and an ordinary run asks again for as long as the folder stays that way. The one run that starts with the
  cloud folder's answer already given is the one Settings' library deletion starts, since typing `DELETE` in a sheet
  that says the folder goes too is that answer.
- A fresh installation never inherits a connection: a launch that finds no preferences document forgets whatever
  credentials a previous installation left in a store that outlived it (the iOS Keychain), locally and without a
  request, before anything restores them (`ForgetSyncConnectionUseCase`), so no run starts on an account nobody
  connected here. One that cannot forget them notes that it still owes it, in a file of its own (removed with the
  app, unlike the Keychain), and every start up tries again and restores nothing until it has; connecting on this
  installation crosses the note off.
- A run belongs to the **app**, not to the screen that started it: `SyncRepository` is a singleton with its own
  scope, so a run carries on while the user moves around or leaves. Android keeps the process alive with a
  foreground service and iOS with a background task, both driven by `SyncNotifier`, which each app shell provides
  the way it provides `FilePicker`. The strings are resolved in the UI so the notification follows the language
  chosen *in the app*, not the system's.
- `SyncEngine` runs the plan a few files at a time rather than one after another (which made a first sync one round
  trip per file), except the remote deletions, which go to the provider in one call — on Dropbox one batch job, about
  seven files a second rather than one — so that the folder spends as little time as possible half deleted, the
  state in which another device's guard can let part of a large deletion through unasked. It retries when the service asks
  it to slow down — being rate limited is the expected answer to a first sync of a whole library, not a reason to
  give up on it. A file that fails on its own is named in the
  run's summary rather than ending it, and such a run does not count as the last successful one.
- **A run starts on its own at launch and after every change the app makes to a song or a setlist** — a save, a tag, a
  new, imported, renamed or deleted file. The launch's starts at once; a change's ten seconds after the latest such
  request, so a burst of edits or an import is one run (`SyncRepository.scheduleSynchronization`). A request made during
  a run is carried out after it. Every run that starts at once — the launch's, Sync now, and the first run after
  connecting — takes the place of one that is waiting; Stop drops the waiting one too. The
  app leaving the front starts a waiting run at once, since a phone only keeps alive a run it was told about while the
  app was still in front, and a desktop quit hides the window and lets the run finish (for up to fifteen seconds, then
  stops it) before the process ends. The files a run writes go around the repositories that announce changes, so a run never
  schedules the next one.
- The index carries an "a run was going" marker, written before anything moves and cleared when it finishes, so a
  run the app never came back from — killed, swiped away, suspended by iOS — is reported as interrupted next time
  rather than silently forgotten, and that run is left for the user to start rather than started on launch. That is
  only for a run the user or a launch started: an automatic one cut short is not reported, and the launch run that
  follows carries its changes.
- A file changed on both sides is never merged: the local one keeps the name and the incoming one lands next to it
  as ` (2)` — or the first number free both on this device and in the cloud folder, so that it never takes the name
  of a file still on its way down — a name of the other device's making, numbered the way any document is, rather
  than with the underscore a name the app derived itself collides with (`_2`) — except a setlist whose two versions
  differ only in the day they name, which every device gives an undated setlist on its own, or where this device's
  only change is the day its read gave an undated file, or a demo file this device planted that still holds exactly
  what was planted, met in the folder for the first time: the cloud folder's version is taken.
- **The library's per-song overrides travel too**: the transposition, tempo and capo of a song opened from the library
  (`UserPreferences.transpositions`, `tempos`, `capos`; a setlist's own are in its file already) are one
  `preferences.json` at the top of the cloud folder, beside `songs/` and `setlists/`, where the engine never looks:
  `{"version": 1, "songs": {"<file name>": {"transposition": 2, "tempo": 92, "capo": 1}}}`, an entry only for a song
  something is set for. Every run that completes ends by settling it (`SyncedPreferencesSync`) — a three-way merge,
  value by value, of this device's, the folder's and the last synced one, which the index keeps — so two devices that
  changed different songs or fields both keep their change, a change beats a removal, and two changes of one value
  keep this device's. It is merged as a JSON tree and this version only writes the fields it knows, so settings that
  have nothing to do with the songs can join `songs` at the top level later without an older version dropping them;
  a document that is missing or cannot be read is taken as unchanged and replaced with this device's values, never
  read as one that removed everything, and one whose `version` is newer than this one's is left alone.
  A song no longer in the library after the run takes its entry with it, here and in the folder, unless the run
  failed to move it or it reached the folder after the run listed it. **The player's chord shapes travel the same way**
  (`UserPreferences.chordVoicings`): a `chords` member beside `songs`, by instrument and then by the chord's notes
  (`{"guitar": {"F:0.4.7": "x x 3 2 1 1"}}`), merged value by value, so two devices that chose for different chords both
  keep their choice and two choices for one chord keep this device's; an instrument this version does not know passes
  through, and no entry is ever dropped with a song, since none belongs to one. A change to those three maps or to the
  chord shapes schedules a run like a change to a file does; the run's own write does not.
- Authorization is OAuth 2.0 with PKCE and no client secret, which is what lets this work with no backend. The four
  platforms get back from the consent page in four different ways, all behind `SyncAuthenticator`.

## Metronome

A third tab and a panel of controls inside the song details screen's app bar, playing on with the screen locked.
Nothing about it reaches the network. The module `CLAUDE.md` files carry the detail (`metronome/*`, `presentation`); the short version:

- **Timing is by sample count, never by a timer**: `:metronome:implementation`'s `MetronomeSequencer` places every click
  at its frame in the output's stream with one integer division from the frame its timing started at, so nothing
  drifts; the sounds are synthesized in Kotlin (no assets, identical everywhere). The flash and the haptics follow the
  `beats` the engine emits when each click is *heard*, from the output's reported playback position; an output that
  cannot open runs the same clock silently and says so.
- **Where a tempo lives mirrors the transposition**: a song opened from a setlist keeps an override in that setlist's
  entry (`Setlist.Entry.tempo`, a `tempo` member of the `*.setlist.json` song, left out where null, so it travels
  through an export, an import and a sync run), one opened from the library in `UserPreferences.tempos`, never exported
  but synced (see Sync); neither reads the other, and the song file's `{tempo}` (`Song.tempo`, read at scan time with `{time}` and
  `{capo}`) is only changed in the editor and the Song defaults sheet. The capo is kept the same way (`Setlist.Entry.capo`, `UserPreferences.capos`,
  0 to 12 frets, a stored 0 being a capo this setlist takes off rather than no override at all), since one set is
  played capoed and the next in another key without. The first `{tempo}` and `{time}` are the song's own (the capo is the song's as a whole, so its first readable `{capo}` counts, as for any other field a song says once, a later one being marked as a contradiction in the editor); the tempo counts the
  clicks of the bar (6/8 at 120 is six clicks a bar at 120 a minute), within 30–300.
- **A later `{tempo}` or `{time}` is a change from where it stands** (`ChordProBlock.Timing`), and the page is what
  says where the band is: on the song details screen a change starts a page of its own (one written before the song's
  first line stands on the first page, played from there), headed by one read only line
  naming the tempo and the time signature from there on as the click plays them, and a playing click follows the page
  being read — the one a step or a fling is headed for, never one a finger is still dragging past — from beat one, the
  panel's beat row and the app bar's tempo with it. A change inside a section cuts it there, the rest heading the new
  page with its fold toggle alone; a recalled chorus is played in whatever is in force where it is recalled. The
  stepper, a setlist's entry and the library's override still hold one number, the song's opening tempo, and a later
  tempo keeps its ratio to the file's opening one (120 → 60, played at 110 → 55), so nothing new is stored. A song that
  fits one screen is still cut into pages by a change, since the page is the signal; a songbook of more than 200
  sections that changes its tempo or time is one column whatever the width, the click following the change scrolled
  past. The editor offers Tempo and Time
  signature again and again — into the header first, at the start of the caret's line after that — and the preview
  shows each change in place and is never paged; the PDF prints it as a line kept with what follows it. **With the
  Metronome feature off none of it exists**: no line, no forced page, the song laid out as if it had none. A `{key}`
  further down is still only read past.
- **The click belongs to the screen it is played from, and there are two of them**: the Metronome tab, whose whole
  screen is the instrument, and the song details screen, where it is a panel in the app bar. Nowhere else has a
  metronome, and a click never outlives the screen it was started on - going back to the songs, selecting a tab,
  opening the editor or the export screen over the song, deleting it, a song opened over the tab or over another
  song (an "Open with", an import's Open), all stop it - so there is never a click playing with nothing on screen to
  stop it with. On a song details screen it follows the page the pager is heading for, so paging to the next song moves the
  click to its tempo from beat one. Every way onto the tab clears the back stack.
- **Playback is media**: on Android a `mediaPlayback` foreground service with a media session and notification
  (`app/android`), on iOS the `audio` background mode, Now Playing and the remote commands (`app/ios`), on the web a
  worker-timed Web Audio scheduler and a best-effort media session (`app/web`); the desktop needs nothing. Each audio
  output owns the platform's focus or session: a call refuses or stops the click, as do headphones pulled and another
  app taking the audio, and a click that stopped on its own says why. **A click outlives the app being sent to the
  background and not the app being left**: the screen locked or another app in front is a phone on a music stand and
  keeps it, while the app being closed — swiped away or backed out of on Android, quit on the desktop — stops it, since
  nothing is left to look at the notification it keeps up. A click that cannot sound — the volume at zero or every
  beat muted — is the exception: it is stopped a few seconds after the app goes out of sight, and says so, since out
  of sight it has nothing left to show and the phones' background audio is not for silence
  (`MetronomePattern.canSound`, `CampfireViewModel.onAppStopped`) — except on Android with Vibrate on and a beat not
  muted, where the click is still felt in a pocket and plays on (iOS has no background haptics).
- **Both are played from the same panel** (`MetronomePanel`): the least of a metronome that is still one — the bar as
  it is heard, with its accents tapped on it, since the accents are the bar's rather than one screen's, and play and
  stop at the end of the row. On the **song details screen** it is inside the app bar, under the title row, because a
  song is what that screen is for and the tempo is already in the song's own first section a line below it; the bar's
  own button shows and hides it, opening it starts nothing, stopping the click leaves it up for the next one, and
  closing it stops a click that is playing. **Whether it is up is a preference** (`MetronomeSettings.isSongPanelShown`)
  rather than something each screen is asked for again, so a player who reads to a click finds the instrument on the
  next song and on the next launch. On the **Metronome tab** the same panel is pinned at the top and never hidden, drawn larger
  there (a 56dp row and button), so a
  page longer than the screen never has to be scrolled to stop a click, and under it the rest of the instrument scrolls
  as the rows of a settings page, in two sections a wide window sets side by side: what is played — the tempo on the
  song details screen's own stepper with its Tap segment, its Italian marking and a slider across the range, the time
  signature (chips, and two steppers with a slash between them, under a line saying that the bar above is tapped
  to accent or mute a beat) and the subdivision as a segmented row of the clicks per beat — and, on a card since it holds for a song's click too, how it reaches the player:
  the sound as chips, the volume, and the animate and vibrate switches.
- Performance mode keeps the play button, and the panel has no tempo stepper to hide; the song's own line of text says
  the tempo there, as it says the transposition. The tab stays fully usable. Settings (sound, subdivision, accents per signature, volume, flash, vibrate) are
  `UserPreferences.metronomeSettings`; there is no mute of its own, since a volume of zero leaves the click running
  with nothing sounding — on screen, for the flash and the haptics.

## Cover art

A song names its cover in its own file (`{meta: cover …}`, see Conventions); the app shows it as a thumbnail at the start of
the song cards, on the Songs and the Setlists screen alike, at the end of the Choose songs sheet's rows, in the song details app bar before the title, at the start of the About the song sheet's and the editor preview's details (where a tap opens the cover search), and in the editor's app bar, where it follows the text as it is typed, and keeps a copy of every one it has shown. The module `CLAUDE.md` files carry the detail;
the short version:

- **Every request is in `:data:source:remote`**, through the one Ktor client sync uses: `CoverArtRemoteSource`
  downloads an image, following redirects (the Cover Art Archive answers with two), and takes nothing that is not an
  `image/*` or is larger than 5 MB. Coil draws what `GetCoverArtUseCase` hands it and has no network artifact of its
  own.
- **The copy is the offline cache on all four platforms**: `covers/<sha256 of the address>`, outside `library/`, kept
  out of every device backup and deleted after a library read that leaves no song naming it (`CoverArtRepository`).
  Settings → Library shows how much they take up, under the library's own size, once there is any, and tapping that
  row deletes them (`CoverArtRepository.clearCoverArtCache`) after a confirmation; the library's own row deletes every
  song and setlist, after a sheet that wants `DELETE` typed, stops any run that is going and then starts a sync run
  with the deletions allowed
  (`DeleteLibraryUseCase`), the typed word being the answer the run's guard would otherwise stop to ask for — so the
  cloud folder and every device synced with it are emptied too, which the sheet says while an account is connected.
  Requests for one address share one download, only a few are made at a time, one nobody is waiting for any more by
  its turn is not made at all, and an address that failed is not asked again for the rest of the
  session (an answer that is not a cover) or for a minute (no answer at all).
- **The search is MusicBrainz and the iTunes Search API side by side**, from a sheet the song details and the editor's
  overflow menus open (`Set cover art` / `Change cover art`; the sheet's Remove cover asks for confirmation), each catalogue's records
  joining the grid as it answers and one that fails leaving the other's
  there. On MusicBrainz, the release groups of an album, or those a song's recordings came out on where the album is
  empty, each with the Cover Art Archive's `front-250` of its release group; on iTunes, the albums the songs matching
  the artist and the album (or title) are on, each with Apple's artwork at 250 px. That address is what is written
  into the song. The sheet's other tab takes an address typed in, previewed before it is saved, for a cover neither
  has. iTunes needs no key and sends CORS headers, so the web build uses it as it is. MusicBrainz allows one request a
  second from the whole app, so one limiter spaces them 1.1 s apart and a 503 is waited out, the sheet saying so. Every
  request names the app in its `User-Agent` (`Campfire/<campfire.versionName> ( https://github.com/pandulapeter/campfire )`),
  which MusicBrainz asks of every client — except in the browser, where a script cannot set one, and MusicBrainz
  documents no other way for a page to name itself; the web build's requests carry the browser's agent and the
  page's `Origin`.
- **The "Cover art" switch in Settings → Features** (`UserPreferences.isCoverArtEnabled`, on by default) turns all of it off: no cover
  is fetched or drawn and the search is not offered.
- What a platform will not load is simply not shown: the web build only reaches hosts that send CORS headers, and
  plain `http://` is refused by Android's and iOS' defaults and by the browser as mixed content.

## Updates

Play's in-app updates, and only on Android: `:presentation`'s `ui/update/AppUpdate.kt` is the contract and
`ui/update/AppUpdateGate.kt` the UI, with the Play Core implementation in `androidMain` and a no-op actual on the other
three. The gate wraps the whole app inside `CampfireApp`, so it speaks the theme and the language chosen in the app.

- The **Play release's `updatePriority` is the entire policy** and it is chosen per release rather than in the code:
  0–1 is left to Play's own schedule, 2–3 offers a dismissible flexible update that downloads in the background,
  4–5 covers the app with a screen that cannot be dismissed until the update is there. The thresholds live in
  `AppUpdate.android.kt`. The priority also says which kind of flow an update already in progress is, since Play's
  answer does not — which is what lets an Activity recreated mid-download pick the download up instead of offering
  it again. `publish-android.yml` asks for the number as its `update_priority` input — which a release
  sets with a `<!-- play-store update-priority: N -->` comment in its description — defaulting to 0 — the number belongs to the release being published, not to the code being published.
- Back on the blocking screen closes the app. The app it covers is still composed behind it, so the gesture has to
  be taken rather than allowed through, and leaving is the only thing it can honestly mean there.
- The blocking screen is drawn **over** the app rather than in place of it, so a required update that turns out not
  to install leaves the library exactly where the user was. Nothing that is a window of its own — a dialog, a sheet,
  a menu — is shown while it is up.
- Neither the blocking screen (nor the immediate flow started with it) nor the flexible update's Restart is put over
  an editor with unsaved text: the gate waits until the text has been saved or let go of (`hasUnsavedEditorChanges`).
  Restart waits for a sync run too; a required update does not — a run it cuts off is reported as interrupted the
  ordinary way.
- Nothing of this exists outside a Play-installed build: a debug APK, a sideloaded release or a device with no Play
  answers every check with an error, which is why the flow can only be exercised from an internal testing track.
- **iOS has no equivalent.** Apple ships no API that tells an app the store has a newer build; the only way to ask
  is to poll their public lookup endpoint for the published version, which would make it the second thing in the app
  that reaches the network. iOS updates apps on its own, so the iOS actual stays `NotAvailable`. Desktop and the web
  answer to no store at all, and the web build is downloaded again every time it is opened.

## Web

The web build differs from the other three in where the files are. `FileStorage` has a `wasmJsMain` actual backed by
the **Origin Private File System**, so the library is a real directory tree in the browser's own storage, private to
the origin and invisible in the user's downloads. It is also the only build that has to be downloaded before it can
start, which is what the rest of `app/web` is about — see its `CLAUDE.md`.

- The library is the only copy of the user's own work, and the browser's storage for an origin is evictable until it
  is asked not to be, so `requestLibraryPersistence()` (in `:presentation`) asks for persistence as the app starts.
  Whether it is granted is the browser's business — engagement, a bookmark, an install — so the answer is reported in
  Settings rather than insisted on: a refusal says so there, next to the export that is the way to keep a copy
  elsewhere. Clearing the site's data still removes the library, as it does for anything a page stores.
- One tab per origin owns the library through a Web Lock taken before the app is downloaded. A second tab gets a
  localized page that asks it to close or continue in the first, which keeps OPFS from changing behind the running
  app's cached repositories.
- **Every screen has an address, and the browser's history is the app's back stack**: `/` is the songs, then
  `search`, `setlists`, `setlists/search`, `metronome`, `settings/{general,features,songs,library,about}`, `song/{song}`, `song/{song}/edit`,
  `setlist/{setlist}/{song}` and `import`, one history entry per step a back gesture would take — a dialog, a sheet or a
  menu open over a screen is one too, and so is the setlist reorder mode, at the screen's address (`:presentation`'s
  `ui/navigation/BrowserHistory.kt`). The app decides and the history follows — pushed, replaced or gone back through
  to match — and the browser's Back is sent into the navigation event dispatcher like Escape, so it closes a dialog
  or asks about unsaved text before it leaves a screen. An address that is opened is resolved once the library has
  been read, behind the launch screen; one naming nothing the library holds opens the songs. GitHub Pages serves a
  deep address as its site-wide 404 page, which hands it to `index.html` in the query string (`404.html` in the
  `campfire-website` repository does this for addresses under `app/`), and `index.html` writes a `<base>`
  for the folder it lives in, which every relative URL of the page and the app depends on.
- **The page keeps a copy of the app in the browser**, so that the address opens without a connection after one
  visit with it, on the songs or on a bookmarked screen. Every launch asks the deployment's `build.json` which build
  is current (past every cache, for three seconds, or 0.8 s where the kept build is whole, the cache being checked
  meanwhile; an answer that comes later than that and names another build makes the next launch wait the full three
  seconds, so a slow network gets a release one launch late): the page's own build tops up whatever the cache lacks
  and starts, another build is downloaded, checked file by file against its SHA-256, stored with its page last and
  loaded once, and no answer, or an update that fails anywhere, starts the build that is kept. A `service-worker.js` at an address
  that never changes only answers the folder from that one cache and decides nothing; it is the way out of a kept page
  that turned out broken, so it is never deleted. Nothing else changes for the user: no manifest, no install prompt,
  no update dialog, and a build published while the app is open arrives on the next launch. The loading screen's
  determinate progress bar measures the download of whatever the kept build is missing (or, where nothing can be kept,
  the binaries as the app fetches them, against the total the build wrote into the page). It is a page and not an
  installable app on purpose, because every platform that should have an installable Campfire has a native build. A
  browser without Wasm GC is told so before the download starts. Settings' web-only Storage row reports whether the
  app was saved together with whether the browser promised to keep the library, since the two are kept or evicted
  together (`isAppAvailableOffline`, next to `requestLibraryPersistence`). See `app/web` for the launch and the cache.
- `finishWebDistribution` (in `app/web/build.gradle.kts`) finalizes `wasmJsBrowserDistribution`: it writes the build's
  id and the size and digest of every file into `index.html` and `build.json`, and precompresses the files when
  `campfire.web.precompress` is on — which it is not, since GitHub Pages ignores the copies (see `app/web`).
- OPFS, the file input and the download link are reached through `js(...)` blocks rather than through typed wrappers:
  one crossing of the Kotlin/Wasm boundary per operation is far cheaper than one per element, and several of these APIs
  have no binding. A Kotlin lambda cannot be passed into a `js(...)` block, so callbacks (file drops) come back as
  promises instead.
- `settings.gradle.kts` uses `RepositoriesMode.PREFER_SETTINGS` rather than `FAIL_ON_PROJECT_REPOS` because the
  Kotlin/Wasm tooling adds the Node.js, Yarn and Binaryen download repositories to the root project; those are declared
  in settings instead.
