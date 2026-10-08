<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Song details lookup — implementation plan

Written 2026-10-07 against `a0c6b7789`. "Find song details online": the song's title and artist are looked up in the
two catalogues the cover search already asks, MusicBrainz and the iTunes Search API, and the recording the user picks
offers to fill the song's header — the artist, the album, the year, the duration and the cover, and from MusicBrainz
the composer, the lyricist and the language. Every song can be looked up, a new one or one that has been in the
library for years, from the editor and from the song details screen. Nothing is asked of either catalogue until the
user opens the sheet, and nothing is written until they have ticked what they want and pressed Save (Done in the
editor).

## 1. What is being built

1. **A "Find song details online" entry** in the editor's overflow menu and in the song details screen's editing menu,
   right after Edit song details, wherever those menus are offered.
2. **A sheet** (`SongDetailsLookupSheet`) that searches by title, artist and optionally album as it opens, lists what
   both catalogues found as each one answers, and opens the recording the user taps into a **review**: one row per
   field it would change, the value it suggests over the one the song has now, each with a box to tick.
3. **Nothing written but the ticked rows**, by the paths the existing metadata sheets use: in the editor one change of
   the text being typed (one undo step, saved only by the editor's Save), elsewhere one write of the file.
4. **A second request only for the recording picked**, on MusicBrainz alone, for its songwriters and its language.
5. **An "Online song details" switch in Settings → Features**, before Cover art, which takes every entry away.

### What is already there, and what is not

- **The cover search is the model for nearly all of it**, and most of its code is shared rather than copied:
  `MusicBrainzSearch` already asks for recordings by title and artist when the album is empty, which is exactly the
  question this asks; `MusicBrainzRateLimiter` already holds the whole app to one request every 1.1 s;
  `CoverArtSearchTransport.kt` already turns transport failures into one exception and decodes off the main thread;
  `CoverArtRepositoryImpl.searchCoverArt` already fans one query out to every catalogue and reports each as it answers
  (`CoverArtSearchResults`: candidates, pending, busy, failed); `CampfireViewModel.coverArtSearch` already keeps that
  state across an Android activity being recreated and drops it in `setVisibleDialog` once the sheet is gone.
- **The writing is already there too**: `SetChordProMetadataUseCase` (`ChordProMetadataFields.set`, which rewrites the
  line a field is read from in place, in the spelling it was written in, and inserts a missing one where
  `ChordProHeaderLayout.metadataInsertionIndex` puts it), `SetChordProCoverArtUseCase` and `SetChordProLanguagesUseCase`,
  all reached through `CampfireViewModel.editSong`, which hands an editor draft's change to `editorTextEdits` and
  writes a library file through `editSongText`, re-reading a file that changed underneath it.
- **The limiter is not shared yet, and has to be before anything else lands.** `DataRemoteSourceModule
  .coverArtSearchRemoteSources` builds the one `MusicBrainzRateLimiter` inline, inside the MusicBrainz source's
  constructor call. A second MusicBrainz client built next to it would get a limiter of its own, and two of them can
  start requests 0 s apart. §2.1 builds one source per catalogue that serves both contracts.
- **What the catalogues do not have**: neither has a key, a tempo, a time signature or a capo. MusicBrainz never
  stored them (the AcousticBrainz data that once estimated key and BPM was frozen in 2022 and is not an API to build
  on); the iTunes Search API has none of them. The sheet's credit line says so, so that nobody waits for them.
- **Nothing remembers a lookup**: no preference, no cache of answers, no per-song state. A thumbnail shown in the
  results is fetched and kept the way the cover search's are (`GetCoverArtUseCase`, pruned after the next library read
  since no song names it).

### Defaults this plan assumes — veto any of them before the work starts

1. **Reached from the two menus only**: the editor's overflow menu (every pane) and the song details editing menu,
   both through one `songDetailsLookupAction` placed right after Edit song details. **Not** a button on the editor
   preview's About the song card or in the About the song sheet's header: both headers already hold Edit song
   details and, while there is no cover, Set cover art, and a third icon cuts the card's title in a narrow preview
   pane and the sheet's on a 360dp phone. **Nothing in the New song sheet**: it is eight fields already, at the edge
   of the pinned-height budget, and the editor it opens into has the entry one tap away.
2. **No proactive hint anywhere.** A chip saying "this song has no artist — look it up?" would make no request until
   tapped, but it would still be the one thing in the app that comments on what the user has written, it would move
   the preview's card as the header is typed, and "never again for this song once dismissed" needs per-song state
   that may not go into the file and would otherwise be a local list keyed by file names that never shrinks, does not
   follow a rename and differs per device. The entry is where every other header edit is.
3. **The search runs as the sheet opens**, from the text's title, artist and album, as the cover search does — the
   sheet is the user asking. After that only the Search button or the keyboard's search key asks again, never a
   keystroke. With no title there is nothing to ask: the sheet opens on its hint.
4. **Fields offered**: Title, Artist, Album, Year, Duration and Cover from either catalogue; Composer, Lyricist and
   Language from MusicBrainz, after the second request. **Never** the subtitle (a catalogue's `(Remastered 2009)` or
   `(Live)` names a version, not the subtitle a songbook means), **never** the genre as a tag (English genre names in
   a library tagged in Hungarian, and a tag is a filter chip the user did not ask for), never a link.
5. **What is ticked**: a field the song has nothing for is ticked; one it has a value for is not; one whose value is
   already exactly what is suggested is not shown at all. The title is never ticked, since a song always has one.
6. **A language is added, never swapped**: the row says "Add English", ticked only where the song declares no
   language, and Apply keeps every language the song already has.
7. **Apply writes a ticked field only if it still holds what the review showed**, so a value typed elsewhere or synced
   in while the sheet was open is never written over unseen.
8. **A switch of its own**, `UserPreferences.isSongDetailsLookupEnabled`, on by default, named "Online song details"
   and placed before Cover art, which stays last as the switch that decides whether the app reaches the network
   *on its own*. With Cover art off the lookup still works for the text fields, with no thumbnails and no Cover row.
   The alternative — one switch for both, renamed "Online catalogues" — would take covers away from somebody who only
   wanted the lookup gone, and the other way round.
9. **The year is the song's, not the album's**: MusicBrainz's recording `first-release-date`, iTunes' track
   `releaseDate`. The album is MusicBrainz's earliest release group of the recording whose primary type is Album and
   which has no secondary type (Compilation, Live, Soundtrack…), else its earliest release group of any kind; iTunes'
   collection, its ` - Single` / ` - EP` suffix taken off as the cover search does.
10. **Results from one catalogue that would write the same values are one row**: MusicBrainz lists a recording once
    per remaster, and twelve identical rows is noise. The two catalogues are not merged with each other.

## 2. Changes by module

### 2.1 `:data:source:remote:api` / `:implementation`

First a mechanical rename, since both searches now ask the same catalogues for different things:

| Now | Becomes |
| --- | --- |
| `CoverArtService` (`:data:model`) | `Catalogue`, KDoc widened |
| `CoverArtSearchException` | `CatalogueSearchException` |
| `coverArt/CoverArtSearchTransport.kt` (`coverArtSearchTransport`, `parseCoverArtSearchAnswer`) | `network/CatalogueTransport.kt` (`catalogueTransport`, `parseCatalogueAnswer`) |
| `musicBrainz/MusicBrainzCoverArtSearchRemoteSource` | `musicBrainz/MusicBrainzRemoteSource`, its `fetch` (limiter, 503/429 retries, `onBusy`) now private to it and used by every request |
| `iTunes/ITunesCoverArtSearchRemoteSource` | `iTunes/ITunesRemoteSource` |

**The new contract**, `SongDetailsSearchRemoteSource.kt` in the api, shaped like `CoverArtSearchRemoteSource`:

```kotlin
interface SongDetailsSearchRemoteSource {
    val catalogue: Catalogue
    /** The recordings [query] finds, most relevant first; empty for a query that is not searchable. */
    suspend fun searchSongDetails(query: SongDetailsQuery, onBusy: () -> Unit): List<SongDetailsCandidate>
    /** Who wrote [candidate] and in what language, or null where this catalogue cannot say. Never asks for one it cannot. */
    suspend fun loadSongCredits(candidate: SongDetailsCandidate, onBusy: () -> Unit): SongCredits?
}
class SongDetailsSearchRemoteSources(val all: List<SongDetailsSearchRemoteSource>)
```

`MusicBrainzRemoteSource` and `ITunesRemoteSource` each implement **both** contracts. `DataRemoteSourceModule` gets
`@Single internal fun musicBrainzRemoteSource(httpClient)` (the limiter built there, once, with the existing
`MUSIC_BRAINZ_REQUEST_INTERVAL`) and `@Single internal fun iTunesRemoteSource(httpClient)`, and the two list
definitions — `coverArtSearchRemoteSources` and a new `songDetailsSearchRemoteSources` — take those two instances
rather than constructing anything. One instance, one limiter, whichever sheet is asking.

**`MusicBrainzSearch`** (pure, tested):

- `recordingQuery(title, artist, album)` — the Lucene expression the cover search's recording branch already builds
  inline, pulled out so both use it: `recording:"…"`, then ` AND artist:"…"`, and for the lookup ` AND release:"…"`
  where an album is given. `quoted()` unchanged.
- `songDetailsRequest(query): Request?` — `/ws/2/recording?query=…&fmt=json&limit=25`, null without a title.
- `songDetailsCandidates(body)`: each recording with a `score` of at least 70 (MusicBrainz's own relevance, 100 for an
  exact match; below that the list is other songs), mapped to a `SongDetailsCandidate`: the title, the artist credit
  joined with its join phrases (as `toCandidate` does today), the year of `first-release-date`, the duration from
  `length` (milliseconds, null where absent), the album and its release group chosen as default 9 says, and that group's
  `coverArtUrl(id)`. Then `distinctBy` the values it would write (title, artist, album, year, duration rounded to
  the second), keeping MusicBrainz's order.
- `creditsRequests(recordingId)`: first `/ws/2/recording/{id}?inc=work-rels&fmt=json`, whose `performance` relation
  names the work and carries its `languages`; then `/ws/2/work/{workId}?inc=artist-rels&fmt=json`, whose relations
  name the people. Two requests, 1.1 s apart: about two seconds after the tap. If the spike in §6 step 1 finds that a
  recording lookup honours `work-level-rels` with the work's artist relations in it, this is one request; the plan
  does not depend on it.
- `songCredits(workBody, recordingBody)`: the composer from `composer` relations, falling back to `writer`; the
  lyricist from `lyricist` and `librettist`, falling back to `writer` unless the work's language is `zxx` (no lyrics);
  names in relation order, each once, joined with `, `. The languages are the work's, each through
  `ChordProLanguages.code` (which folds `eng` to `en` and already drops `zxx` and `und`), `mul` dropped too. A
  recording with no work relation answers `SongCredits` with nothing in it after the first request.
- `MusicBrainzModels`: `MusicBrainzRecording` gains `id`, `title`, `score`, `length`, `first-release-date`;
  `MusicBrainzReleaseGroup` gains `secondary-types`; new `MusicBrainzRecordingLookup` (relations → work with `id`,
  `languages`) and `MusicBrainzWorkLookup` (relations with `type` and `artist.name`), all defaulted, unknown keys
  ignored, as now.

**`ITunesSearch`** (pure, tested):

- `songDetailsUrl(query)`: the same `entity=song` search as `url`, the term being the artist and the **title** (the
  cover search uses the album where there is one; a song is looked up by its own name), `limit=25`.
- `songDetailsCandidates(body)`: one candidate per `trackId`, from `trackName`, `artistName`, `collectionName` (suffix
  taken off with the existing `RECORD_TYPE_SUFFIXES`), the year of `releaseDate`, `trackTimeMillis`, and
  `coverArtUrl(artworkUrl100)`. `ITunesTrack` gains `trackId`, `trackName`, `trackTimeMillis`. Then the same
  `distinctBy` as MusicBrainz's.
- `ITunesRemoteSource.loadSongCredits` answers null without a request: the API names no songwriter and no language.

### 2.2 `:data:model`

- `Catalogue` (the rename above).
- `SongDetailsQuery(title, artist, album)`, `isSearchable = title.isNotBlank()`: an artist alone names a discography.
- `SongDetailsCandidate(catalogue, id, title, artist, album: String?, year: String?, duration: Duration?,
  coverArtUrl: String?)`, with `key` as `CoverArtCandidate` has it.
- `SongCredits(composer: String?, lyricist: String?, languages: List<String>)`.
- `CatalogueSearchResults<T>(candidates, pending, busy, failed)`, the body of today's `CoverArtSearchResults`, which
  becomes `typealias CoverArtSearchResults = CatalogueSearchResults<CoverArtCandidate>` so its call sites stay.
- `UserPreferences.isSongDetailsLookupEnabled: Boolean`, after `isCoverArtEnabled`, and in `:data:source:local
  :implementation` `UserPreferencesDocument.isSongDetailsLookupEnabled = true`, `UserPreferencesMappers.kt` both
  ways, `UserPreferencesDocumentFormatTest`. Local, never exported or synced, like every switch. The test fakes that
  build a `UserPreferences` by hand (`FakeSyncCollaborators`, `GetScreenDataUseCaseImplTest`,
  `DeleteLibraryUseCaseImplTest`, `SongReferencesTest`) gain the field.

### 2.3 `:data:repository:api` / `:implementation`

- `CatalogueSearch.kt` (implementation, internal): `searchCatalogues(catalogues, search: suspend (index, onBusy) -> List<T>)
  : Flow<CatalogueSearchResults<T>>` — the `channelFlow` of `CoverArtRepositoryImpl.searchCoverArt` moved out
  unchanged (the results mutex, `onBusy` ignored once a catalogue has answered, a failure reported in `failed` and
  printed by class name only). `searchCoverArt` becomes one call of it.
- `SongDetailsRepository` (api): `fun searchSongDetails(query: SongDetailsQuery): Flow<CatalogueSearchResults<SongDetailsCandidate>>`
  and `fun loadSongCredits(candidate): Flow<SongCreditsState>` (`Loading`, `Busy`, `Loaded(SongCredits)`,
  `Unavailable` for a catalogue with nothing to say, `Failed`). `SongDetailsRepositoryImpl` (`@Single`) over
  `SongDetailsSearchRemoteSources`, picking the source by `candidate.catalogue`. Not a `BaseLocalDataRepository`:
  nothing is kept.

### 2.4 `:domain:api` / `:implementation`

- `SearchSongDetailsUseCase` and `LoadSongCreditsUseCase`, one-line `@Factory`s in a new `SongDetailsUseCaseImpls.kt`
  next to `CoverArtUseCaseImpls.kt`.
- No new writing use case: §2.5's Apply composes `SetChordProMetadataUseCase`, `SetChordProCoverArtUseCase` and
  `SetChordProLanguagesUseCase` inside one `editSong` lambda.

### 2.5 `:presentation`

**The view model.**

- `songDetailsLookup: StateFlow<SongDetailsLookupState>` (`Idle`, `Active(query, results)`) and
  `songCredits: StateFlow<SongCreditsLookup?>` (the candidate's key and its `SongCreditsState`), held exactly as
  `coverArtSearch` is.
- `showSongDetailsLookupDialog(song, isEditorDraft)`, built like `showSongCoverArtDialog` (the draft's text parsed
  where it is the editor's), showing `DialogType.SongDetailsLookup(song, isEditorDraft) : SongEdit`.
- `songDetailsQueryOf(song, isEditorDraft)`, beside `coverArtQueryOf` and reading the same `songTextOf`: the title
  as the file writes it (without the subtitle the library's `Song.title` carries), the artist, the album.
- `searchSongDetails(query)`, cancelling the one still running, as `searchCoverArt`; `loadSongCredits(candidate)`,
  cancelling the previous one, so only the recording open in the review is ever asked about.
- `setVisibleDialog`: both are cleared and cancelled whenever the dialog on screen stops being a
  `SongDetailsLookup`, and the search is started as it is put up (the cover search's two lines, repeated), so the
  first frame already says it is searching.
- `applySongDetails(fileName, isEditorDraft, changes: SongDetailsChanges)`: one `editSong` call, whose lambda applies
  `changes.toText(text, …)` (below) — one undo step in the editor, one write and one sync schedule outside it.

**`ui/dialogs/songDetailsLookup/SongDetailsReview.kt`** — the pure part, tested:

- `reviewRows(metadata: ChordProMetadata, candidate, credits, isCoverArtEnabled): List<ReviewRow>` — one row per field
  with a suggestion, `ReviewRow(field: ReviewField, current: String?, suggested: String, isTickedByDefault)`, in the
  order Title, Artist, Album, Year, Duration, Composer, Lyricist, Language, Cover. Suggestions are cleaned the way
  `SongMetadataField` cleans typing (braces and line breaks out, trimmed); the duration is
  `ChordProDuration.format`, compared with the song's through `ChordProDuration.parse`, so `4:28` and `04:28` are the
  same; a row whose suggestion equals the current value is left out; a Cover row only with the switch on.
- `SongDetailsChanges(values: Map<Field, Expected>, coverArt: Expected?, addedLanguages: List<String>)`, where
  `Expected` is the value shown as current and the one to write, and `toText(text, setMetadata, setCoverArt,
  setLanguages)`, which re-reads `text`, drops every field whose value is no longer the one shown, and applies the
  rest. A language is written as the song's languages plus the new one.

**`ui/dialogs/songDetailsLookup/SongDetailsLookupSheet.kt`** — through `CampfireBottomSheet`, titled "Find song
details online" with `songLabel(song)` as its subtitle, opened at its full height like the cover search (what it holds
grows after it opens). **Only the header holds still**; everything else is one `LazyColumn`:

1. **The query** (first item): Title, Artist and Album fields (`songs_new_song_title`, `songs_new_song_artist`,
   `song_editor_insert_album`, the album marked optional) and an outlined Search button, the keyboard's search key
   doing the same. Unfocused as it opens, since the search is already running; set once, so it scrolls away.
2. **What stands in for results**, crossfaded (`AnimatedContent`) between the hint, "Searching MusicBrainz and
   iTunes…" (reused), MusicBrainz busy (reused), nothing found, and failed with Retry (reused).
3. **One row per recording**, joining at the end as each catalogue answers (`animateItem`): a 48dp `CoverArtImage`
   where Cover art is on, the title, then the artist, the album, the year and the duration on one line, and the
   catalogue's name. A tap selects it and **expands it in place** into its review (`AnimatedVisibility` with
   `expandVertically` + `fadeIn`); tapping another collapses the first. No second screen, so Back and the close
   button mean what they mean on every other sheet.
4. **The review**, inside the expanded row: one `CheckboxListItem` per `ReviewRow`, the label over the suggested
   value, and under it "Now: …" in `onSurfaceVariant` where the song has a value. The Cover row shows the image at
   64dp; one whose image does not load is withdrawn and unticked, as the cover grid drops a tile, since an address
   that answers nothing should not be written. On MusicBrainz the Composer, Lyricist and Language rows join once the
   credits arrive (`animateItem`), after a line "Asking MusicBrainz for the songwriters…" that gives way to them, to
   "MusicBrainz names no songwriters for this recording", or to "The songwriters could not be looked up" with Retry;
   on iTunes a line says it names no songwriters. A recording that suggests nothing the song does not already say
   says so instead of rows.
5. **The credit** (last item): "Recordings from MusicBrainz and iTunes. Neither lists a song's key or tempo."

The header's one action is `BottomSheetConfirmButton`, **Done** in the editor and **Save** elsewhere (as
`SongMetadataDialog`), enabled while a recording is open and at least one of its boxes is ticked; so Ctrl / Cmd + S
presses it. It calls `applySongDetails` and closes the sheet. The selected key and the ticked fields are
`rememberSaveable`; a new search clears the selection. In a short window the header scrolls with the rest, as
`CampfireBottomSheet` already does.

**The entries.** `SongMetadataActions.kt` gets `songDetailsLookupAction(viewModel, song, isEditorDraft)`, built like
`coverArtAction`, with a new `ic_manage_search` vector drawn like the other `ic_` icons. `EditorMenu` in
`SongEditorScreen.kt` and `SongEditingActions` in `SongDetailsScreen.kt` place it after `editingActions.take(1)`,
ahead of Song defaults and the cover art, null while `isSongDetailsLookupEnabled` is off. The song details one sits
inside the existing `!isReadOnly` branch, so performance mode and an archived setlist's songs have none; the editor is
unreachable in performance mode already.

**Settings → Features**: a `SwitchListItem` before Cover art, `setSongDetailsLookupEnabled` in the view model next to
`setCoverArtEnabled`.

**`CampfireDialogs.kt`**: `CampfireDialogs` renders `DialogType.SongDetailsLookup`. Like the other editor-draft sheets it is not
taken down when the file leaves the library while it edits a draft; a library one closes with its song as the cover
search does.

**Strings**, in `values` and `values-hu` (Hungarian to be written with the rest, suggestions given):

| Key | English | Hungarian |
| --- | --- | --- |
| `song_details_lookup` | Find song details online | Dalinformációk keresése online |
| `song_details_lookup_hint` | Name the song, and its artist if you know it, to find the recordings it matches | Add meg a dal címét, és ha tudod, az előadóját, hogy megtaláljuk a hozzá illő felvételeket |
| `song_details_lookup_no_results` | No recording of this song was found. Try fewer words, or leave the album out. | … |
| `song_details_lookup_now` | Now: %1$s (read with `textResource`, since it carries the song's own text) | Most: %1$s |
| `song_details_lookup_add_language` | Add %1$s (`textResource`) | %1$s hozzáadása |
| `song_details_lookup_credits_loading` | Asking MusicBrainz for the songwriters… | … |
| `song_details_lookup_credits_none` | MusicBrainz names no songwriters for this recording | … |
| `song_details_lookup_credits_failed` | The songwriters could not be looked up | … |
| `song_details_lookup_credits_itunes` | iTunes does not name the songwriters | … |
| `song_details_lookup_nothing_new` | This recording says nothing the song does not already | … |
| `song_details_lookup_attribution` | Recordings from MusicBrainz and iTunes. Neither lists a song's key or tempo. | … |
| `settings_song_details_lookup` | Online song details | Dalinformációk online |
| `settings_song_details_lookup_description` | Offer to look a song's artist, album, year and songwriters up on MusicBrainz and iTunes, only when asked | … |

The loading, busy, failed, Retry, Search, Done, Save and field labels are the existing strings.

## 3. Tests (`desktopTest`)

- `MusicBrainzSearchTest`: the recording query with and without artist and album, a title full of Lucene operators;
  the candidates — score cut-off, the album choice (Album over Single, a Compilation passed over, the earliest kept),
  the year, `length` to a duration and its absence, the artist credit's join phrases, identical rows collapsed;
  the credits — composer and lyricist, `writer` as the fallback for both, a `zxx` work with no lyricist, `eng` read
  as `en`, `mul` dropped, a recording with no work.
- `ITunesSearchTest`: the details URL uses the title even with an album; the candidates' duration, year, suffix and
  artwork; a track without artwork kept with no cover.
- `MusicBrainzRemoteSourceTest` (the renamed source test, `MockEngine`, virtual time): a cover search and a details
  search asked together start 1.1 s apart; the credits are two requests spaced the same; 503 is waited out with
  `onBusy` for every kind of request. `ITunesRemoteSource`'s `loadSongCredits` makes no request.
- `CatalogueSearchTest` (repository): the ordering, a failed catalogue reported, `busy` dropped once answered — the
  existing `CoverArtRepositoryImplTest` search cases moved onto the shared function.
- `SongDetailsReviewTest` (`:presentation`): the default ticks; an identical value left out; a capitalisation-only
  difference offered unticked; durations compared as durations; the title never ticked; the Cover row only with the
  switch on; the language added to, never replacing; `toText` skipping a field changed since the review, writing
  the rest, and leaving every other line of the file byte for byte.
- `UserPreferencesDocumentFormatTest` with the new field.

## 4. Corner cases, and what each one does

| Case | Behaviour |
| --- | --- |
| Switch off | No entry in either menu. A sheet already open stays until closed (switches only hide). |
| Cover art off | No thumbnails, no Cover row; the text fields as usual. |
| Performance mode, archived setlist | No editing menu, so no entry. |
| A song with no title (an empty draft) | The sheet opens on its hint, no request; typing a title and Search asks. |
| Offline, or both catalogues fail | "The search could not be completed", Retry. Nothing written. |
| One catalogue fails | The other's rows stay; the failure is said under them. |
| MusicBrainz busy | Waited out by the shared limiter, "MusicBrainz is busy…" while nothing else is pending. |
| Credits requested, then another row tapped | The first request is cancelled; the limiter still spaces the next. |
| The sheet closed mid-search | `setVisibleDialog` cancels both, so nothing is asked for a sheet nobody sees. |
| A value changed elsewhere while open (sync, another sheet's write) | Apply skips that field and writes the rest. |
| Title or artist changed by Apply | No rename; the menu's Update file name appears, as after Edit song details. |
| Applied in the editor | One undo step; the file is written only by the editor's Save, its draft kept by the existing machinery. |
| Applied outside the editor | One write, which schedules a sync run like any edit. |
| The suggested cover's image 404s (a release group with no art) | The Cover row is withdrawn and unticked. |
| A catalogue value with a brace or a line break | Cleaned before it is shown, so what is shown is what is written. |
| A Hungarian song iTunes' US store does not carry | MusicBrainz answers alone. |
| A song already complete | Rows with nothing new say so; Save stays disabled. |
| Web | Both catalogues already answer the browser (the cover search proves it); the `User-Agent` is the browser's. |
| Rotation, Android activity recreated | Results in the view model, selection and ticks in saved state. |

## 5. Interplay

The sibling plan `documentation/plans/clipboard-import.md` brings songs in through the editor with little more than a
title. This lookup is how such a header is filled, from the same editor menu, with nothing in that plan needing to
know about it.

## 6. Order of work

Each step builds and tests green on its own, one commit each.

1. **A spike, thrown away**: a dozen real songs (English, Hungarian, a standard, a cover version) through the
   MusicBrainz recording search, the recording lookup with `work-rels` and with `work-level-rels`, and iTunes — to
   settle the score cut-off, the album rule and whether the credits are one request or two.
2. The renames of §2.1 and §2.2, and the shared `@Single` sources and limiter, with the tests following. No change in
   behaviour.
3. `CatalogueSearchResults`, `searchCatalogues`, and `searchCoverArt` on it.
4. The details search and the credits in both sources, with their tests.
5. `SongDetailsRepository`, the use cases.
6. The preference, the switch, `SongDetailsReview.kt` and its test.
7. The view model, the sheet, the two menu entries, the strings.
8. Docs: the root `CLAUDE.md` — **its first paragraph** ("The only things that ever reach the network are sync, and
   the cover images, the cover search and the song details lookup the user asks for", with one sentence saying the
   lookup asks MusicBrainz and iTunes only from the sheet the user opens), the architecture block's
   `:data:source:remote` line, the Features list (the new switch before Cover art), a short **Song details lookup**
   section after Cover art, and the test list; `data/model`, `data/source/remote/api`, `.../implementation`,
   `data/repository/api`, `.../implementation`, `domain/api` and `presentation` `CLAUDE.md`s; the README's feature
   list.

## 7. Checks owed by hand

- The sheet on the 360 × 640 dp phone with the keyboard up, in Hungarian: the query fields scroll away, a review of
  nine rows can be scrolled to and Saved.
- From the editor: Apply, then undo, then Save — and Apply, then leave without saving (the unsaved question).
- From the song details screen, inside a setlist and from the library: the file's other lines untouched (diff it).
- MusicBrainz's pace: a cover search right after a lookup, and rows tapped quickly one after another, never answered
  with a 503 that the busy line does not show.
- Each platform once, the web included, offline and online.
- A song whose cover the review offers is drawn on its card after Save, from the copy.

## 8. Outside the repository

- `campfire-website`: the privacy policy's sentence about the cover search widened to the lookup — what is typed into
  the sheet is sent to MusicBrainz and Apple, only when the user asks; a support answer for "Why is there no key or
  tempo?".
- Store privacy labels unchanged: nothing is collected by Campfire.
- A What's new message, by the prepare-release skill.

## 9. Not in this plan

- Key, tempo or time signature from anywhere: no free source has them.
- Any proactive suggestion, a lookup at import, or a batch lookup over the whole library (which at one request a
  second would be a quarter of an hour for a thousand songs, and a review nobody wants to go through).
- Merging a MusicBrainz and an iTunes row of the same recording, or finding an iTunes pick's songwriters on
  MusicBrainz by ISRC (the Search API gives none).
- Choosing the iTunes store's country by the app's language.
- Lyrics, links to a song's pages, genres as tags.
- An entry on the About the song card or sheet, or in the New song sheet (see default 1).
