<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# :presentation — ui

The top level `ui/*.kt` files: the view model facade, the app's composition root and navigation chrome, and the rules
every screen follows.

### `ui/CampfireViewModel.kt`

`ui/CampfireViewModel.kt` — a lifecycle `ViewModel`, and a thin facade: its state lives in plain `internal` holders it
builds itself (no Koin definitions, each taking `viewModelScope`, the use cases it needs and narrow lambdas for the
other holders it calls, so that there are no cycles), and every member the screens, the shells, `app/desktop` and
`tools/screenshots` call is a one-line delegation to one of them — `MessageSink` (`ui/messages`, the snackbar queue and
`launchLibraryChange`), `SavedStateStore`, `PreferencesController`, `LibraryState`, `SongListState`, `SongPickerState`,
`SetlistsController`, `SongTextStore`, `SongMetadataEditing`, `ImportController`, `SyncController`,
`CoverArtSearchController` and `AppExitController` (`ui/state`), `DialogHost` (`ui/dialogs`), `Navigator`
(`ui/navigation`), `FontScaleController` (`ui/fontScale`), `PlayingOverrides` (`ui/playing`), `MetronomeController`
(`ui/metronome`), `ExportController` (`ui/screens/export`), `EditorSession` (`ui/screens/songEditor`) and
`FirstRunController` (`ui/firstRun`). Construction order is behaviour: the holders are declared in the order their
states used to start, and `init` starts their collectors in the order they were always launched.

What a back stack change or a dialog change sets off in other holders is registered by the view model as listeners, in
order (`Navigator.addOnBackStackChanged`, `DialogHost.addBeforeDialogChange` / `addAfterDialogChange`). The names below
are the facade's. Exposes `StateFlow`s derived from `GetScreenDataUseCase` (`asState`, which is `stateIn(viewModelScope,
Eagerly)`), the grouped song list (`songGroups`: the sections `ScreenData` arrives cut into, or one headerless group of
ranked results while a search is on, `songGroupsFor` in `SearchIndex.kt` — a query that folds to nothing, punctuation,
symbols or emoji alone, counting as no search), `setlistsWithSongs`, `visibleDialog`, and the Navigation 3 `backStack`
(`SnapshotStateList<CampfireDestination>`). `visibleDialog` is written through `setVisibleDialog`, which is where the
work parked behind a dialog — the exit behind the unsaved changes question — is dropped whenever that dialog stops being
the one on screen; a dropped exit is reported to `requestExit`'s `onCancelled`, since the macOS quit request behind it
has to be answered with a cancel rather than left waiting. The three exceptions only ever replace no dialog, so there is
nothing parked to drop: the song picker opened after a new setlist, the welcome sheet and the What's new dialog are each
a compare-and-set against null.

The import plan behind a conflict question is parked behind the import screen instead (`importReport`,
`onImportReportLeft`), and dropped as that screen leaves the back stack however it leaves. The theme and the language
come from a separate `GetUserPreferencesUseCase` rather than from `ScreenData`, so they reach the screen without waiting
for the library scan. Intent handlers are non-suspend and launch in `viewModelScope`; the ones that write to the library
go through `launchLibraryChange`, which turns a write that failed into a `Message.OperationFailed` snackbar instead of
an uncaught exception that would take the app down on Android. An import's writes run on `NonCancellable` once they
start, so the view model going away with a finished Android activity does not leave an archive half imported; planning
it and the conflicts question are still dropped with it, since nothing has been written by then. **Every state is kept
up to date from the moment the view model exists**, never only while a screen collects it. A state started by its first
collector hands it the initial value for a frame, and a screen answers the real one as a change: coming back to a tab
after a few seconds away had the song rows fading in and the "New" button expanding into the bar and pushing the search
action aside.

Several states are also acted on rather than drawn (`syncState`, which the Android shell stops the sync service from;
`userPreferences` and `setlists`, which the writes build what they save out of; `hasUnsavedEditorChanges`; `isSyncing`),
and a state that stops either goes stale or goes back to its initial value — a first sync fills the library from the
settings screen, and the song list must not be entered on the answer from before it. The price is that normalizing,
grouping and matching the setlists also run for library changes made while their screens are not showing. The search
runs over `indexedSongs` (`ui/search/SearchIndex.kt`), the library with every title, artist and tag normalized once,
rather than normalizing per keystroke, and a song whose searchable text did not change keeping what it was folded to
across library changes; it is one value holding both the filtered songs the song search ranks and the *unfiltered*
library by file name, which the setlists search and the details screen (`songsByFileName`) read, since a setlist names
its songs whatever the song filters hide. The ranking puts every match into one of eight buckets as the list is scanned
rather than sorting the matches.

Each list screen has a `SearchState` of its own here (`components/SearchState.kt`, alongside `ScrollPosition` and for
the same reason, plus one of its own: the desktop's Escape handler sits outside the composition entirely and has to be
able to close an open search). It holds the field's `TextFieldState` rather than a query string, which is what carries
the text *and the caret* across a trip to another screen — a search left open is come back to with the caret where the
typing stopped rather than in front of the word that was typed, and the field puts it at the end as it opens in any case
— without the keyboard: the field takes the focus once per opening (`takeFocusOnOpen`), so coming back to a screen whose
search is open, from a song or another tab, or after the process was restored, leaves the keyboard as the user left it,
and on the desktop and the web Ctrl / Cmd + F is what puts the caret back in it. A closed search narrows nothing,
whatever its field still holds — `activeQuery` (a `snapshotFlow` over that state, gated on `isOpen`) is what the lists
combine — because `close` deliberately leaves the text where it is, so the field still reads right while it animates
away.

`visibleSetlists` (archived filter applied, search not) is kept apart from `setlistsWithSongs` (search applied) so that
the placeholder can tell a list emptied by the archive filter from one emptied by the search, exactly as the song list
tells its empty states apart. Each entry of `setlistsWithSongs` carries the place it has in its setlist, the missing
files included, so the numbers a setlist is read by are its own order rather than a count of the rows that could be
drawn. `updateSongFileName` is the one intent that changes a song's identity: the use case under it follows the
references on disk, and what is left here are the two places the old name lives in memory — the text read from the file,
and the back stack entries opened on it, which are rewritten rather than popped so that a song renamed from the details
screen is still the song being read, and a rewritten entry names each song once, since a destination opened on a setlist
that held both names would otherwise key two of the pager's pages alike. The file moves before those references are
followed, so for the few writes in between the library no longer holds the name the details screen was opened on:
`songsBeingRenamed` carries the song under its old name across that window and the screen resolves through it, which is
why a rename neither closes the screen nor turns the setlist's pager to the next song.

The metronome resolves through it too, and holds a playing click's changes while its own song is being renamed
(`metronomeRenames`, until the click's context names the new file), so the click follows the rename on its tempo and
without a restarted bar. It is a state rather than a wait — there is no emission that says it is the last one. A
reference that could not follow does not undo any of that: the screens follow the file, and a snackbar then says what
still names the old one. Tagging a song (`setSongTags`) rewrites the file through `SetChordProTagUseCase` rather
than touching the list entry, so the tag is in the song wherever that song goes. The tag
dialog ("Manage tags") is a checklist of the library's tags with the song's own ticked and first, whose field narrows it
and creates a tag the library lacks; Done writes the whole set in one rewrite, as the languages are written, taking off
only the tags it offered and left unticked; the tag *filter* is neither a file nor a preference: `songFilter` holds the
selected tags and languages in the view model and its saved state alone, so they last as long as the app runs (a process
restored after the system killed it included) and are gone on the next launch, and `isSongFilterActive` — asked of the
chips the controls show rather than of the raw selection, which may hold a tag the library no longer has — is what
badges the songs screen's filter action while the controls are in a sheet rather than beside the list.

The languages of a song work the same way (`setSongLanguages`, `toggleLanguageFilter`), except that the whole set is
written at once, since the picker asks about all of them before it is closed. Both edits are built on `songTexts`, the
texts the view model holds — only those of the screens on the back stack (every page of a song details screen, the
editor's file and the draft's), the rest let go of once a navigation transition has ended rather than as the stack
changes, since a popped screen is still drawn while it slides away (`pruneSongTexts`) — which follow the files through
`GetSongContentInvalidationsUseCase` — a sync run or a rescan has every held text read again and applied in one update
(`rereadSongTexts`), so the details screen shows the downloaded version in place, an editor with nothing typed in it
follows its file (`FollowFileWhileUntouched`, which replaces the field's text as one step of its undo history for as
long as it equals what the file held before the change, so that saving an untouched editor never writes the old version
back over the new one), and an editor with text of its own whose file changed underneath it has unsaved changes — as has
one whose file is gone: the editor keeps the text it opened with rather than following `songTexts`, says that the file
has gone (`Message.EditedSongFileGone`), and saving writes it back under its own name, which is what the setlists point
at — a rotation and a restored process included: the editor saves whether it had opened, and one composed again over a
file that has gone opens on its retained or saved field rather than waiting for the file, while `onEditorClosed` leaves
the draft alone for as long as the editor is still on the stack, so a configuration change is never a moment with
nothing unsaved — and both are written with the text they were built on as `expectedText` (`editSongText`): a file that
changed before the view model heard of it is read again and the edit applied to that, rather than the old text being
written over the new one.

Every write to a song file, the editor's saves included, holds `songWriteMutex` from the read it is built on until
`songTexts` has the result, so two tags taken off in quick succession both stay off and two saves land in the order they
were asked for. Every entry reports `transition.isRunning` through `ReportNavigationTransition`; a back stack change
that interrupts a running transition bumps `navigationGeneration`, which goes into the entry metadata so the new scene
never equals the one the transition started from. Without this, reversing an in-flight transition (e.g. going back right
after opening a song) sends Navigation 3 down its "predictive back cancelled" path, which cannot handle an interrupted
animation and leaves the UI stuck halfway.

The three top level screens keep their scroll position here too (`ScrollPosition`, `components/ScrollPosition.kt`, read
back by `rememberRetainedLazyGridState`, and by `rememberRetainedScrollState` on the settings screen, which scrolls a
layout rather than a list): Navigation 3 throws away everything an entry remembered as the entry leaves the back stack,
and selecting a tab rebuilds that stack around the destination that was picked, so the songs screen — the one screen
that sits at the bottom of every one of those stacks — used to be the only one that came back where the user left it.

### `ui/CampfireApp.kt` and the navigation chrome

`ui/CampfireApp.kt` / `CampfireContent.kt` / `CampfireScreens.kt` / `NavigationChrome*.kt` / `NavigationTransitions.kt`
— the root composable used by every platform shell: theme, language preference, adaptive chrome (`NavigationBar` under
600dp, `NavigationRail` above — see `components/WindowSize` — and the expanded `WideNavigationRail`, labels beside the
icons, where the window is wide enough that the songs screen keeps its filter side panel next to it too
(`navigationChromeKind`, about 1290dp): it is decided by the window width alone, so the wide rail's own collapsed state
is never used, and its items start at the collapsed rail's height rather than under the room Material leaves for a
header; the filter side panel is decided for the list screens rather than by the window, see `hasRoomForSidePanel`), the
`Scaffold` + `NavDisplay`, `CampfireDialogs`, the snackbars for import and export results — a clean import's counts,
with Details where the import screen has more to say, and a saved PDF, song, setlist or library export is confirmed
unless a warning about it says so already (a share is not, the platform sheet being its own answer) — (queued in the
view model with a running number, since two identical results in a row are the same object and an effect keyed on the
message alone would never show the second, and since the Android activity is recreated on every rotation, and a queue
held by the composition would go with it, unshown; a message leaves the queue once it has been shown, `onMessageShown`),
laid out above the chrome and above the keyboard wherever it reaches higher (the same `KeyboardAwarePadding` the screens
use, so the keyboard's animation only relayouts it), and the collection of `filesToImport`.

`rememberSyncNotifications` (in `CampfireApp.kt`) only ever reports "nothing to show" after it has shown something:
reported on the first frame of every composition it would reach the Android shell as "stop the service" whenever the
app was opened onto a run already going in the background. `CampfireApp` also rescans the library when the app comes
back to the front (`ON_START`: iOS entering the foreground, the desktop window restored — not `ON_RESUME`, which iOS
also sends after Control Center or a system alert), and on the desktop when the window regains the focus too, but there
only once the last rescan is ten seconds old (`refreshIfStale`), so that switching between the app and a text editor
next to it does not re-read the library every time. Only where the folder is one somebody else can edit
(`isLibraryEditableOutsideApp`, i.e. iOS and desktop): on Android the library is app-private, and on the web a tab focus
says nothing, since OPFS cannot change behind the app's back. A rescan of a library that has been read once is no
loading state (`isLoading` is latched once a read has completed), since it publishes no partial data and the complete
library stays on screen meanwhile.

That return to the front, the read the app starts with and the retry of a list that failed to load are the only things
that ever re-read the library: no screen has a pull to refresh or a refresh button, settings included, because the
library is the app's own, the platforms where it is not re-read it on their own, and a list that is already up to date
has nothing to pull for. All `NavDisplay` transitions are defined in `NavigationTransitions.kt` and shared by every
platform (the desktop default would be none): tab switches fade through in place, next to the bar and the rail alike
(the outgoing screen fades out before the incoming one fades in, with no slide: two tabs are in no direction of each
other), pushing/popping `SongDetails` and `SongEditor` slide horizontally with a parallax (Material motion scheme
springs): both enter from the right and leave to the right regardless of the predictive back gesture’s starting edge,
while the screen underneath travels in the same direction by 12% of the slide.

That screen is also darkened like the content behind a dialog (the theme's `scrim` at 32%), fully once covered and
clearing as the card is taken off, in step with the slide: the specs record the kind of transition in a
`NavigationScrim`, and every entry's surface animates its scrim on its own enter/exit transition, so it follows the
spring and the predictive gesture's seeking alike; the card on top and either screen of a tab swap are never darkened.
The export screen darkens the app it covers the same way, from its own progress. `predictivePopTransitionSpec` follows
the predictive back gesture (Android) / edge swipe (iOS) with linear specs — a slide for a card, and a cross fade for
going back from Setlists or Settings to Songs, which is a change of tabs rather than a card leaving (a cross fade rather
than a fade through, since a gesture held halfway would otherwise show neither screen). The editor opens over the song
and uses Close in its app bar to dismiss it. The cards that cover the chrome (`ScreenSurface`: the song details, the
editor and the import screen) take no touches while they are moving, which is while Navigation 3 holds their lifecycle
below `RESUMED` in a host that is `RESUMED`.

The second tap of a double-tap on the editor's Close otherwise landed on the Back arrow of the song it uncovered. The
top level screens are left alone, since anything over their whole area would take the rail's touches.
`navigationTransition` serves both `transitionSpec` and `popTransitionSpec` and decides push vs. pop from the scene
depth itself: Navigation 3's own detection is wrong when a back stack change interrupts a running transition (it would
animate the pop with the push spec, leaving the invisible outgoing screen covering the list and swallowing clicks for
the rest of the animation). Deeper scenes get a higher z-index. The rail and bottom bar are hidden on the details and
editor screens; bars and side panels animate with expand/shrink using the same spatial spec so the content reflows
fluidly. **A window that crosses from one `NavigationChromeKind` (bar, rail, expanded rail) to another hands over rather
than switches**: the scaffold keeps the chrome it is leaving for as long as the handover runs
(`NavigationChromeTransition`, driven by the effects spring), fading it out towards its edge while the new one fades in
from its own — sliding too where the two are on different edges, a bar and a rail — and the room they take out of the
window (`NavigationChromeSize.railWidth` and `barHeight`) is worked out between the two on the same progress, so the
screens travel with them.

What the screens *decide* from is the settled width (`settledRailWidth`), so the column counts and the side panel change
once. Those decisions are made once, in `CampfireScreens`, and handed down to the top level screens as `ListLayout` (the
side panel and the list column counts) and `SettingsWidthLayout` (the category pane and the section columns) rather than
as the width itself, which changes on every frame of a window being resized: the values stay equal between two
breakpoints, so the screens recompose only in the frames one of them flips in. The song details screen alone still takes
the settled width, which its lyrics use inside their layout. The chrome is still measured for the kind of the frame the
window changes in. The chrome copies the top level screens draw while a card covers the deck are the settled kind only.
The chrome and the screens are laid out together by `NavigationChromeScaffold`, a `SubcomposeLayout` that measures the
rail (or the bar) and only then composes the screens, with its thickness already known: that size is Material's to
decide, and reporting it back as state from the laid out chrome arrives one layout pass too late, so the app's first
frame would be composed as if the window held no chrome at all — the screens covering the rail and the lists settling
their column counts for the full width, all of it laid out again a frame later.

It costs no layer that was not there already, since the window size used to come through a `BoxWithConstraints`, which
is the same thing. The chrome is placed under the screens and `placeRelative`d, so the rail sits on the start edge.
**The chrome moves with the screen under a card**: from the frame a card is dealt until the pop that takes it off has
settled (a pop starts animating a frame after the back stack changes, so the settling is waited for two frames on), the
scaffold measures its chrome without placing it and every top level screen draws a copy of its own under itself
(`TopLevelScreenSurface`'s `chrome`), selected on that screen's destination, so the bar or the rail travels with the
screen's 12% parallax, the predictive gesture included; the tabs keep the one shared chrome, which is what lets its
selection animate while they fade through in place. **No top level screen has a title bar of its own, and the rail goes
all the way to the top of the window**: the navigation chrome already says which screen is showing.

`TopLevelScreenSurface` is an opaque `Surface` inset by the rail (or the bar) and padded below the status bar — by at
least 8dp, as the rail is (`MIN_TOP_EDGE`), so a window with no top inset (the web, Linux, full screen) does not start
them at its very edge — so the screens never cover the chrome's column and block touches to the screen they cover during
a transition. Nothing is composed until the preferences have been read (`arePreferencesLoaded` — *read*, not read
successfully, so a failed read opens the app in the defaults rather than never, and it only ever turns on, since what it
gates is the whole of `CampfireContent` and everything remembered under it): they decide the palette and the language,
and the window would otherwise open on the system's guess at both and correct itself a frame later, in an accent color
and a language the user did not choose. That frame is not a fleeting one — on a cold start the second frame is several
hundred milliseconds behind the first — and the wait costs nothing but the frame that would have been wrong anyway.

What the window holds meanwhile is `LaunchScreen`, the app's mark on the theme's background: it says nothing the
preferences have not answered yet, carrying no text and drawn in a neutral rather than in the accent color. It is a
Compose screen rather than a desktop one for a reason — a window held back with `visible = false` until the app is ready
deadlocks, since Compose Desktop has no frame clock while no window is on screen, so the composition never advances to
the point of asking to be shown. **It covers the app rather than standing in for it**, and it is a `Surface` so that
nothing under it can be clicked through: the app composes, lays out and draws behind it, and the mark only fades off
(one `Animatable` driving both the screen's opacity and the mark's scale) once there is something to look at underneath
— the preferences in, the theme done moving (see `ui/theme/`) and `CampfireViewModel.hasLibraryToShow` true, which is
the first batch of the library read or a finished read that found none — and, where the shell asked for a place to open
on (`navigateOnLaunch`, the web build's address), the place found once the whole library is in.

Nothing on the back stack is animated while the launch screen is up (`hasShownApp`), since whatever is put there behind
it — that place, or Settings after a consent page — only onto a stack the user has not built on since the app started
(`openSyncSettingsAfterConsent`), since the code exchange it follows can outlast the launch screen by a minute — would
otherwise still be sliding in as the app is uncovered. Holding it through that read costs none of the time the read was
going to take anyway, and what it saves is the handful of frames the song list would otherwise be opened on its loading
indicator for — a startup screen handing over to a spinner being two startup screens in a row, which is the very thing
`onAppReady` exists to prevent. **In the desktop application the mark grows half as large again as it goes, over the
slower effects spring** (`isLaunchScreenWholeStartup`, `LAUNCH_MARK_EXIT_GROWTH`) — not in a desktop browser, where the
page's loading screen covers it, so that the screen opens into the app rather than thinning out of it: that is the one
platform where this screen is the whole of the startup, from the first frame the window paints to the last one before
the app.

The other three never watch it go — Android's splash and the web's loading page are still over it when it goes, so there
it does not fade at all (`isStartupScreenHeldUntilAppReady`) and the app is uncovered at once, and on iOS the mark
already follows the storyboard, which is the gray palette's background with nothing on it — so there it stays the plain,
quicker dissolve and the extra frames are not spent. The other three platforms open on a startup screen of their own and
never see it: Android's system splash, iOS's launch storyboard and the loading screen in the web build's `index.html`.
Each of those is taken away by the first frame the app draws, and that frame is the launch screen rather than the app —
so the two that can be held are told when to stop instead, through `CampfireApp`'s `onAppReady`, which fires once the
launch screen has been taken away (and two frames after that composition: `withFrameNanos` resumes while its own frame
is still being assembled, so the frame after it is the first one that is certainly drawn), so that they hand over to the
app itself rather than to the last frames of a mark fading off it.

The launch screen belongs to the start of the process and not to the composition: Android recreates its activity, and
with it the whole composition, on every rotation and every change of the system's theme, language or window size, and a
composition that starts after the app has been shown (`CampfireViewModel.hasShownApp`) composes no launch screen and
releases `onAppReady` at once. Otherwise every rotation would hold the new activity's first frame back behind an
invisible fade and swallow the taps made meanwhile. `CampfireWebApp` answers it by calling `window.campfireReady`, which
is what fades the page's loading screen out and finishes its progress bar; `CampfireMainActivity` answers it by letting
a pre-draw listener through, which is what postpones the first frame and with it the system's handover. iOS's storyboard
is the system's to dismiss, so there the launch screen is what covers the gap, as on the desktop. On the web the launch
screen also waits for `areDrawablesLoaded`, for at most five seconds, because Compose resources never reports a drawable
that failed to load.

### Performance mode

**Performance mode** (`UserPreferences.isPerformanceModeEnabled`, called Read only in the interface, the first row of
the settings screen's Features tab: taking controls out of the interface is what files it with the other features, and
deciding what the rest of the app is still allowed to do is what puts it on top) is the one preference that reaches
every screen rather than one of them, so `CampfireViewModel` exposes it as `isPerformanceModeEnabled` on its own and
each screen collects that instead of reading it out of the preferences.

It is read only mode for the app while it is being played from: the two "New" buttons, the per-song menu (and the long
press that opens it on touch), the actions menu of a setlist header, the drag handle and the overflow menu a setlist row
is reordered and removed by, the "Add to setlist" action, the "Choose songs" row ending every setlist, the song details
file-editing menu and the controls of the four playing values all leave, which is also every way into the editor — the
screen stays reachable by nothing. What is left is what a song is read with: the search, the sorting, the tag and
language filters, the text size and the chords switch, none of which change a file. The settings screen is the exception
and keeps every row it has, since it is where the mode is switched back off; the two library actions there are disabled
rather than hidden, the way the chord spelling is with the chords switched off. Files the operating system hands over
("open with", a share, a drop) are still imported: that is not a tap that can happen by accident.

## What a song carries in its file

### Tags are part of the song file

**Tags are part of the song file**, not a store of their own: ChordPro `{tag}` directives, read by `:chordpro` into
`Song.tags` at scan time and written back into the text the same way, so a tag travels with the file through an export,
an import or a sync run. The library's set of tags is whatever the songs carry; the Songs screen's filter offers them
counted, most used first or alphabetically (a toggle next to the group's title, which the sheet that puts them on or
takes them off — opened from the song details' About the song sheet, the editor preview's card and a song card's menu on
the Songs screen — shares; one preference per group, `UserPreferences.tagSortingMode` and `languageSortingMode`, the
languages being ordered the same way).

### The language of a song is carried the same way, and is its own category rather than one more tag

**The language of a song is carried the same way, and is its own category rather than one more tag**: a `{meta: language
en}` directive per language, read into `Song.languages` as a lowercase ISO code — 639-2's three letter codes included,
folded to their 639-1 equivalent where the standard has one (`eng` is `en`) and kept as they are where it does not
(`rom`, Romani), so one language is one code however the file spells it. It gets its own filter group on the Songs
screen — but only once the library holds more than one language, with an "Unknown" chip for the songs that declare none
— and it is shown wherever a tag is: next to them under a song in the lists, and as one read-only chip per language in
the song details' About the song sheet and the editor preview's card; the same places open the picker. The **names are
never shipped**: the app carries a list of codes and nothing else, and asks the platform what each is called in the
language the app is set to (`java.util.Locale`, `NSLocale`, `Intl.DisplayNames` behind `:presentation`'s
`languageDisplayName`), falling back to the code in capitals where it cannot say.

### The cover of a song is carried the same way too

**The cover of a song is carried the same way too**: a `{meta: cover https://…}` directive, read into `Song.coverArtUrl`
as the first one holding an `http` or `https` address. The file carries the address and nothing else, so the cover
travels through an export, an import or a sync run as a tag does, and the image is fetched where the song is read. Any
address is taken — the library and the addresses in it are the user's — and the search is only ever what recommends one.
See Cover art in the root `CLAUDE.md`.

### Links about a song are carried the same way

**Links about a song are carried the same way**: a `{meta: link https://… Optional name}` directive per link, read into
`ChordProMetadata.links`. Any page is taken, whatever site it is on, and nothing is ever fetched from one: the song
details' About the song sheet shows each as a chip named by its optional name or its host, opening the page in the
browser. The sheet is opened by tapping the song details app bar's title while the song is at its top, which a chevron
after the title says (it has no button or menu entry of its own) — and holds what the song says about itself, each group
with an edit button next to its title outside performance mode and outside an archived setlist, where a group it has
nothing for is its title and a plus alone; key, capo, tempo and time stay on the page as the song's first section (see
How a song is played below), and the sheet only reads what the file declares for them, opening the Song defaults sheet
and saying where the song is being played differently.

The editor's preview shows the same as a card that is the song's first section, flowing through its rows and columns and
scaling with the lyrics, with the same edit buttons. The editor's overflow menu offers these actions in every pane, and
the song details screen's editing menu — a pencil button of its own before the overflow menu, holding the editor and
these — offers them outside performance mode and for a song not opened from an archived setlist (which has no editing
menu and leaves its one overflow menu Edit and Export, as an archived setlist's own song menus do), with cover art
editing following the cover art setting. Performance mode leaves that row out, and the button that opens it where all of
them are empty. The Manage links sheet edits addresses and optional names together, and their order, written once on
Save; the links are shown in that order, where tags and languages are always shown alphabetically. From the editor those
buttons change the text being typed rather than the file, which only Save writes. Opening a link is the user's browser
making the request, not Campfire.

## How a song is played

### How a song is played is set in the song itself

**How a song is played is set in the song itself**: the transposition, the capo, the tempo and the time signature — the
four values that decide what is played rather than what the song is — are the first section of the song details screen's
own grid, each next to the control that sets it (the transposition stepper, which is named **Transposition** and reads
the amount next to the key it takes the chords on the page to, the capo stepper, the tempo stepper whose pill ends in a
Tap segment, since tapping a tempo in sets the very number the stepper steps, and after it the time signature, which is
only read there). They are part of the song, so they grow and shrink with its text, which is also why the text has a
floor: `UserPreferences.MIN_FONT_SCALE` is the size below which the song details screen is not worth reading — the
lyrics, and the controls with them. Starting the click stays the app bar's button, which is in reach wherever the song
has been scrolled to. Three of them are overridden where the song is read, so they belong to the setlist the band plays
it in or to this device (see `ui/playing/CLAUDE.md`); the time signature alone is written into the file as a `{time}`
directive, since it is the song rather than one band's reading of it, and it is what the click counts the bar by.

**What the file declares for all four is edited in the Song defaults sheet**, an entry of the song details editing menu
and of the About the song sheet's Song defaults group, and nowhere on the page (the editor's overflow menu has it too,
writing into the text being typed, without the line and the card below, since the editor has no steppers): it opens with
a line saying that the steppers change them for this setlist only, or, opened from the library, outside every setlist —
never naming a device, since the library's own overrides are synced (see `data/sync/implementation/CLAUDE.md`) — then a card naming what is adjusted
there, with a Reset, while there is any, then the key, capo, tempo and time signature fields, each optional, an empty
one leaving the default in force. A new song's template carries an empty `{key}` line and `{capo: 0}`, `{tempo: 120}`
and `{time: 4/4}`, the defaults written out, for them to be edited. **Read only mode reads them instead**: performance
mode and a song opened from an archived setlist get the same four as one line of accent colored text — which always
names the capo and the time signature there, "Capo 0" and the click's 4/4 where the file says nothing, since with no
control left on the page an absent value would read as an unknown one — as does the editor's preview, where the text
being typed is what says them.

With the chords switched off the key and the capo leave the line and the controls alike, and with the metronome switched
off the tempo and the time signature do (see Features below) — everywhere but in the editor's preview, which says all
four whatever the switches and in the key it is written in, since it shows what is being written. What is left in the
app bar is about the song rather than about how it is played — the click, the Choose setlists and the menu, the title
opening the About the song sheet — with the text size, which is the reader's own, at the end of that menu. **The key the
band actually hears is named in the app bar**, after the artist and the way a song card names it: the transposition
*and* the capo applied, so it is the key the song sounds in rather than the one the chords on the page spell, which is
the Transposition control's — the two read differently wherever the capo is not zero. The tempo the click would play at
follows it there, as it does on a card, and inside a setlist the song's duration after that, as its card there has it.

## Features

### Features are switched on and off as a whole

**Features are switched on and off as a whole** in Settings → Features, the second tab, so that the app is only what its
reader needs — a singer has no use for a metronome, a band playing from one list none for setlists. Every switch is a
`UserPreferences` field, never exported or synced, and a switch only hides: nothing in the library is written or deleted
by one. In order: **Read only** (`isPerformanceModeEnabled`, first because it decides what the rest of the app is still
allowed to do, and called performance mode in the code); **Chords** (`areChordsEnabled`, stored as the inverse
`isLyricsOnlyModeEnabled` the lyrics only switch was), off taking with the chords the key, the transposition and the
capo wherever a song is read, and leaving the chord spelling rows of the Songs tab disabled; **Chord diagrams**
(`areChordDiagramsEnabled`), disabled while the chords are off, off taking the Chords section off every song and leaving
the Songs tab's Instrument row disabled, the chosen shapes kept; **Metronome** (`isMetronomeEnabled`), off taking its
tab, the song details screen's click button and panel, its M key and the tempo and the time signature wherever a song is
read; **Setlists** (`areSetlistsEnabled`), off taking their tab and every way of putting a song into one, while setlist
files still travel through an import, an export and a sync run; and **Cover art**, last since it is the one that decides
whether the app reaches the network on its own.

A switch cleans the interface up rather than locking anything, so what it takes away is gone everywhere, the Song
defaults sheet and the About the song sheet's Song defaults group included (each value with its feature, the sheet's
menu entry and the group once both are off), and an override nobody is shown is neither named nor reset there. A tab
switched off leaves the navigation chrome (`CampfireViewModel.topLevelDestinations`), shrinking out of the bar or the
rail as the others close the gap (`components/NavigationItemPresence.kt`), and a back stack restored or an address
opened is cut short at the first screen that belongs to one (`NavigationState.withoutDisabledFeatures`), a song read
from a setlist included — so on the web `/metronome` with the metronome off opens the songs, and the address is written
over with theirs.
