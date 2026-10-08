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

Every song and setlist has one export entry, which opens the export screen: a PDF for printing, or the library's own
files (ChordPro, a zip for a setlist) for other Campfire users. Only what is printed goes into a PDF, and the Features
tab reaches the export screen. The detail is in `:presentation`'s `ui/screens/export/CLAUDE.md` (the screen) and
`ui/print/CLAUDE.md` (the format choice, the options and the PDF pipeline).

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
  rewrites, the web build manifest) are tested in `gradle/build-logic` with `./gradlew -p gradle :build-logic:test`.
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
- **The configuration cache is on** (`org.gradle.configuration-cache`): a build whose scripts and inputs have not
  changed (`local.properties` included, which `settings.gradle.kts` reads) skips configuring the twenty projects. A
  task action may capture only locals, providers and file collections, never a script-level `val` or function, which
  the cache cannot store — copy the value into a local outside the action, as `app/desktop/build.gradle.kts` does. No
  plugin the build applies reports a problem with it today; one that does gets
  `notCompatibleWithConfigurationCache("<why>")` on its tasks rather than the cache turned off or set to warn.
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
- Publishing, the store workflows and how CI writes `local.properties` from its secrets: see `.github/CLAUDE.md`.

## Sync

Off until the user connects a cloud folder in Settings, and built so that Dropbox is the first provider rather than
the only possible one. The invariants a change anywhere else could break: there is no server of Campfire's own; a run
belongs to the app, not to the screen that started it; content decides what changed, never a clock; an edit always
beats a deletion; a run that would delete most of the library on either side stops and asks. The whole of it — the
provider contract, the planner, the guard, scheduling, the index, conflicts and the synced preferences — is in
`data/sync/implementation/CLAUDE.md` (and the contracts in `data/source/remote/api/CLAUDE.md`).

## Metronome

A third tab and a panel of controls inside the song details screen's app bar, playing on with the screen locked.
Nothing about it reaches the network. Timing is by sample count, never by a timer; a click never outlives the screen it
was started on; a tempo override lives where a transposition does (a setlist's entry or `UserPreferences.tempos`). The
detail is in `metronome/implementation/CLAUDE.md` (timing, playback as media), and in `:presentation`'s
`ui/metronome/CLAUDE.md` (the panel, the tab, tempo changes) and `ui/playing/CLAUDE.md` (where a tempo lives).

## Cover art

A song names its cover in its own file (`{meta: cover …}`, see Conventions), and the app keeps a copy of every one it
has shown. Every request is in `:data:source:remote`, the search only ever runs from the sheet the user opens for it,
and the "Cover art" switch in Settings → Features turns all of it off. The detail is in
`data/source/remote/implementation/CLAUDE.md` (the download, the search, the switch) and
`data/repository/implementation/CLAUDE.md` (the offline copies and their deletion).

## Updates

Play's in-app updates, and only on Android; the Play release's `updatePriority` is the entire policy, chosen per release
rather than in the code, and iOS, the desktop and the web have no equivalent. The detail — the thresholds, the
blocking screen, what it waits for — is in `:presentation`'s `ui/update/CLAUDE.md`.

## Web

The web build keeps the library in the browser's Origin Private File System, one tab per origin owns it, every screen
has an address whose history is the app's back stack, and the page keeps a copy of the app so it opens without a
connection. The detail is in `app/web/CLAUDE.md` (storage, the lock, the launch and the cache) and
`presentation/src/wasmJsMain/CLAUDE.md` (addresses and history).
