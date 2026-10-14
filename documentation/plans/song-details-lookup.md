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

Written 2026-10-07 against `a0c6b7789`, rewritten 2026-10-12 against `c648f8ba5` for the About the song sheet, the
`SongEditTarget`, and the view model's controllers. "Find song details online": the song's title and artist are looked
up in the two catalogues the cover search already asks, MusicBrainz and the iTunes Search API, and the recording the
user picks offers to fill the Edit song details form — the artist, the album, the year and the duration from either,
and from MusicBrainz the composer and the lyricist. Nothing is asked of either catalogue until the user opens the sheet,
and nothing is written by it at all: it fills the form's draft, and the form's own Save (Done in the editor) is still
the only thing that writes.

## 1. What is being built

1. **A "Find song details online" icon button in the Edit song details sheet's header**, beside Save, the way Add link
   sits beside Save in Manage links. That sheet is reached the same way for a library song and for the editor's draft
   (the About the song sheet's details tile, the editor preview's card), so one entry serves both.
2. **A sheet** (`SongDetailsLookupSheet`) that searches by title, artist and optionally album as it opens, lists what
   both catalogues found as each one answers, and expands the recording the user taps into a **review**: one row per
   field it would change, the value it suggests over the one the form holds now, each with a box to tick.
3. **Fill in**, the header's action, puts the Edit song details form back with the ticked values in its draft; closing
   the lookup puts it back unchanged. Either way the form is where the user looks the result over and saves it.
4. **One request more for the recording picked**, on MusicBrainz alone, for its songwriters.
5. **An "Online song details" switch in Settings → Features**, before Cover art, which takes the button away.

### What is already there

- **The cover search is the model**, and its code is shared rather than copied: `MusicBrainzRateLimiter` holds the app
  to one request every 1.1 s, `CoverArtSearchTransport.kt` turns transport failures into one exception and decodes off
  the main thread, `CoverArtRepositoryImpl.searchCoverArt` fans one query out to every catalogue and reports each as it
  answers, `CoverArtSearchController` keeps that state across an Android activity being recreated, and
  `CoverArtQueryFields` is the artist, album and title fields with a Search button that this sheet asks for too.
- **The writing is already there**: `SongMetadataEditing.setSongMetadata` writes the fields the form changed, and only
  those, through `editSong` — one undo step in the editor, one write and one sync schedule outside it.
- **The form's draft lives in its composition**, so a sheet put up over it would lose it. `DialogType.SongMetadata`
  gains the draft to open with, and the lookup carries it there and back.
- **The limiter is not shared yet, and has to be first.** `DataRemoteSourceModule.coverArtSearchRemoteSources` builds
  it inside the MusicBrainz source's constructor call; a second client built next to it would get a limiter of its own.
- **What the catalogues do not have**: a key, a tempo, a time signature or a capo. The sheet's credit line says so.
- **Nothing remembers a lookup**: no preference, no cache of answers, no per-song state.

### What the spike found (2026-10-12)

- **MusicBrainz ranks badly for a hit.** "Wonderwall" by Oasis gives 247 recordings at score 100, live bootlegs and
  compilations first; the studio recording is not among the first hundred. For a Hungarian song (TNT, "Kicsi
  gesztenye") it is the better catalogue by far. iTunes is the reverse: the studio recording first for a hit. Hence
  both, the recordings of MusicBrainz reordered so a studio album's come first, and a score cut-off of 70 (the
  Hungarian song's radio edit and album version are at 78).
- **The credits are one request**: `/ws/2/recording/{id}?inc=artist-rels+work-rels+work-level-rels&fmt=json` carries
  the work with its languages and its composer and lyricist relations.

### Decisions

1. **Reached only from the Edit song details sheet.** The About the song sheet's header has no buttons by rule, and the
   form is where these fields are typed. Nothing in the New song sheet (at the edge of its height budget already).
2. **No proactive hint anywhere**: the button is where every other header edit is.
3. **The search runs as the sheet opens**, from the form's title, artist and album as they stand in its draft. After
   that only the Search button or the keyboard's search key asks again. With no title the sheet opens on its hint.
4. **Fields offered**: Title, Artist, Album, Year and Duration from either catalogue; Composer and Lyricist from
   MusicBrainz. **Never** the subtitle (a catalogue's `(Remastered 2009)` names a version), the genre, a link, the
   cover (the cover search is one tap away on the same About the song sheet and asks the same catalogues), or a
   language (the form has no language field; Manage languages is beside it).
5. **What is ticked**: a field the form holds nothing for is ticked; one it has a value for is not; one whose value is
   already what is suggested is not shown. The title is never ticked.
6. **A switch of its own**, `UserPreferences.isSongDetailsLookupEnabled`, on by default, local like every switch,
   placed before Cover art, which stays last. With Cover art off the lookup still works; it shows no covers either way.
7. **The year is the song's**: MusicBrainz's recording `first-release-date`, iTunes' track `releaseDate`. The album is
   MusicBrainz's earliest release whose group is an Album with no secondary type, else its earliest release of any
   kind; iTunes' collection, its ` - Single` / ` - EP` suffix taken off.
8. **Results from one catalogue that would fill the same values are one row.** The two catalogues are not merged.

## 2. Changes by module

### 2.1 `:data:source:remote:api` / `:implementation`

The renames, since both searches now ask the same catalogues for different things: `CoverArtService` → `Catalogue`
(`:data:model`), `CoverArtSearchException` → `CatalogueSearchException`, `coverArt/CoverArtSearchTransport.kt` →
`network/CatalogueTransport.kt` (`catalogueTransport`, `parseCatalogueAnswer`), `MusicBrainzCoverArtSearchRemoteSource`
→ `MusicBrainzRemoteSource`, `ITunesCoverArtSearchRemoteSource` → `ITunesRemoteSource`, and `CoverArtCandidate.service`
→ `catalogue`.

`SongDetailsSearchRemoteSource` in the api, shaped like `CoverArtSearchRemoteSource`: `catalogue`,
`searchSongDetails(query, onBusy): List<SongDetailsCandidate>` and `loadSongCredits(candidate, onBusy): SongCredits?`
(null where the catalogue names no songwriters at all, without a request), with `SongDetailsSearchRemoteSources`.
Both sources implement both contracts; `DataRemoteSourceModule` builds each once (`musicBrainzRemoteSource`, with the
limiter, and `iTunesRemoteSource`) and both lists take those instances.

`MusicBrainzSearch`: `recordingQuery(title, artist, album)` pulled out of `request`, `songDetailsRequest(query)`
(`limit=50`), `songDetailsCandidates(body)` (score ≥ 70, studio album releases first, the album and year by decision 7,
the duration from `length`, deduplicated), `creditsUrl(recordingId)` and `songCredits(body)` (composer from `composer`
falling back to `writer`; lyricist from `lyricist` and `librettist` falling back to `writer` unless the work's language
is `zxx`; names in relation order, once each, joined with `, `). `ITunesSearch`: `songDetailsUrl(query)` (the artist
and the title, `limit=25`) and `songDetailsCandidates(body)`.

### 2.2 `:data:model`

`Catalogue`; `SongDetailsQuery(title, artist, album)` with `isSearchable` on the title; `SongDetailsCandidate(catalogue,
id, title, artist, album, year, duration)` with `key`; `SongCredits(composer, lyricist)`; `SongCreditsState` (`Loading`,
`Busy`, `Loaded`, `Unavailable`, `Failed`); `CatalogueSearchResults<T>`, with `CoverArtSearchResults` a typealias of it;
`UserPreferences.isSongDetailsLookupEnabled`, with its document field, mappers, format test and the hand-built fakes.

### 2.3 `:data:repository:api` / `:implementation`

`CatalogueSearch.kt`: the `channelFlow` of `searchCoverArt` moved out as `searchCatalogues`, which `searchCoverArt` and
the new `SongDetailsRepositoryImpl.searchSongDetails` both call. `SongDetailsRepository.loadSongCredits(candidate):
Flow<SongCreditsState>`. Nothing is kept.

### 2.4 `:domain:api` / `:implementation`

`SearchSongDetailsUseCase` and `LoadSongCreditsUseCase`, in `SongDetailsUseCaseImpls.kt`. No writing use case.

### 2.5 `:presentation`

- **`state/online/SongDetailsLookupController`**, beside `CoverArtSearchController`: `songDetailsLookup` (`Idle`,
  `Active(query, results)`), `songCredits` (the candidate's key and its state), `searchSongDetails(query)` and
  `loadSongCredits(candidate)`, each cancelling the previous one, and `clear()`. The view model's dialog listener starts
  the search as the sheet is put up and clears both whenever the dialog on screen stops being the lookup.
- **`DialogType.SongMetadata` gains `draft`**, the form's values to open with (null: the file's). The form keys its
  saved state by it. **`DialogType.SongDetailsLookup(song, target, values, draft) : SongEdit`** carries the form's
  offered values and its draft. `CampfireViewModel.showSongDetailsLookup(dialog, draft)` and
  `returnToSongMetadata(dialog, draft)`, the latter only while the lookup is still the dialog on screen.
- **`DialogHost`**: the lookup is one of the sheets that go back to the About the song sheet, and the form and the
  lookup hand it over to each other as the cover search and its Remove question do.
- **`dialogs/songDetailsLookup/SongDetailsReview.kt`**, pure and tested: `songDetailsReviewRows(draft, candidate,
  credits)` and `Map<Field, String>.withSongDetails(rows, ticked)`, in the form's draft spelling (the duration as its
  digits).
- **`SongDetailsLookupSheet`**: `CampfireBottomSheet`, opened at full height, the header's one action **Fill in**
  (Ctrl / Cmd + S presses it), the content one `LazyColumn` — the query fields (`CatalogueQueryFields`, the cover
  search's renamed), the state line, one row per recording expanding in place into its review, and the credit line.
- **Settings → Features**: the switch before Cover art.

## 3. Tests

`MusicBrainzSearchTest` and `ITunesSearchTest` (the queries, candidates, ordering, deduplication, credits);
`MusicBrainzRemoteSourceTest` (a cover search and a details search 1.1 s apart, one credits request, 503 waited out);
`CoverArtRepositoryImplTest`'s search cases still green over `searchCatalogues`; `SongDetailsRepositoryImplTest` for the
credits states; `SongDetailsReviewTest`; `UserPreferencesDocumentFormatTest`; `DialogHostTest` for the hand-over.

## 4. Corner cases

| Case | Behaviour |
| --- | --- |
| Switch off | No button. A sheet already open stays until closed. |
| Performance mode, archived setlist | The About the song sheet's tiles do nothing, so the form is not reached. |
| No title in the form | The sheet opens on its hint, no request. |
| Offline, or both catalogues fail | "The search could not be completed", Retry. Nothing changes. |
| MusicBrainz busy | Waited out by the shared limiter, the busy line while nothing else is pending. |
| Another row tapped while its credits load | The first request is cancelled. |
| The sheet closed mid-search | Both cancelled by the dialog listener. |
| Fill in | The form comes back with the ticked values typed in; Save writes only what differs from the file. |
| The song deleted or renamed by sync meanwhile | The lookup closes with its song, as every library song sheet does. |
| Rotation, activity recreated | Results in the controller, selection and ticks in saved state. |

## 5. Not in this plan

Key, tempo or time signature; a cover or a language from the lookup; any proactive suggestion or batch lookup;
merging rows of the two catalogues; the iTunes store's country by the app's language.
