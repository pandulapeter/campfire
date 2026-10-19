<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Show a cover search whose thumbnails could not be reached as failed, with Retry, and let a new search ask them again

**Challenged:** sound

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Lane:** C
**Files:** a new `data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/catalogue/CoverArtResult.kt`,
`data/repository/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/api/CoverArtRepository.kt`,
`data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/CoverArtRepositoryImpl.kt`,
`data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/CoverArtRepositoryImplTest.kt`,
`data/repository/implementation/CLAUDE.md`,
`domain/api/src/commonMain/kotlin/com/pandulapeter/campfire/domain/api/catalogue/GetCoverArtUseCase.kt`,
`domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/catalogue/GetCoverArtUseCaseImpl.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/coverArt/ProvideCoverArtImageLoader.kt`,
a new `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/coverArt/CoverArtUnavailableException.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/lookup/CoverArtSearchSheet.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/lookup/CoverArtResults.kt`,
a new `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/lookup/CoverArtResultsContent.kt`,
a new `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/lookup/CoverArtResultsContentTest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/lookup/CLAUDE.md`,
`tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/screenshots/TestFakes.kt`

## Problem

The repository remembers an unreachable cover for a minute and answers it from that memory
(`data/repository/implementation/.../CoverArtRepositoryImpl.kt:101-104` and `:153`):

```kotlin
if (failures.containsKey(url)) {
    val retryAt = failures[url]
    if (retryAt == null || retryAt.hasNotPassedNow()) return null
    failures.remove(url)
}
…
CoverArtDownload.Unreachable -> null.also { mutex.withLock { failures[url] = environment.timeSource.markNow() + UNREACHABLE_RETRY_DELAY } }
```

`getCoverArt` answers `null` for both "nothing there" and "no network", so the sheet cannot tell them apart. In the
cover search a tile whose thumbnail fails is dropped (`CoverArtResults.kt:282`, `onError = { onUnavailable() }`), and
with every tile dropped and the catalogues answered the grid shows (`CoverArtResults.kt:93-99`):

```kotlin
!results.isComplete -> if (results.busy.containsAll(results.pending)) ResultsContent.BUSY else ResultsContent.LOADING
results.failed.isNotEmpty() -> ResultsContent.FAILED
else -> ResultsContent.NO_RESULTS
```

Scenario on a poor link: MusicBrainz and iTunes answer (small JSON), every thumbnail times out → every tile dropped →
"No record with a cover was found. Try the album's name, or fewer words." The message blames the query, and has no
Retry. The network comes back, the user taps Search: `CoverArtSearchSheet.kt:93-98` resets `unavailableKeys` and its
comment says a momentary failure "gets another chance", but within the minute every thumbnail is answered `null` at
once from the repository's memory, so the same message returns. The Web address tab says "No image could be loaded from
this address" for an address that only timed out, for the same minute.

## Fix

### Carry Missing vs Unreachable up to the sheet

- New `data:model` type `catalogue/CoverArtResult.kt`: `sealed interface CoverArtResult { class Image(val bytes: ByteArray); data object Missing; data object Unreachable }`
  (KDoc: what the app could find at a cover's address; `Missing` will not change, `Unreachable` may a minute later).
  Not the remote API's `CoverArtDownload`, which must not leak above the repository. (`CoverArtImage` is taken: it is a
  Composable in `ui/components/coverArt`.)
- `CoverArtRepository.getCoverArt(url): CoverArtResult` (KDoc: the copy or a download as `Image`; `Missing` for nothing
  there or not an image; `Unreachable` for no answer, remembered for a minute). In `CoverArtRepositoryImpl`, a remembered
  failure answers `Missing` when its mark is null and `Unreachable` otherwise; `fetch` maps `CoverArtDownload` one to one;
  an abandoned download (no waiters) answers `Unreachable` and still records nothing. `Download.deferred` becomes
  `Deferred<CoverArtResult>`.
- `GetCoverArtUseCase` returns `CoverArtResult` (KDoc updated); `GetCoverArtUseCaseImpl` unchanged otherwise.
- `ProvideCoverArtImageLoader.kt`: `CoverArtFetcher.fetch` maps `Image` to the `SourceFetchResult` and throws
  `CoverArtUnavailableException(url, isUnreachable = result is CoverArtResult.Unreachable)` otherwise. The exception moves
  to its own `internal` file, `CoverArtUnavailableException.kt`, since the sheet now reads it.
- `tools/screenshots/.../TestFakes.kt`: `OfflineCoverArtRepository.getCoverArt` returns `CoverArtResult.Missing`.

### A new search asks unreachable thumbnails again

In `CoverArtRepositoryImpl.searchCoverArt`, forget every time-limited failure when the search starts, e.g.
`searchCatalogues(...).onStart { mutex.withLock { failures.values.removeAll { it != null } } }`. The search runs when
the sheet opens and on every Search or Retry, all explicit acts, so this is "an explicit search asks again" without a new
use case. Missing addresses (`null` marks) stay remembered for the session: that is how a release group without a cover
keeps dropping out at once. Clearing all time-limited entries rather than only the candidates' also gives the song
list's and the Web address tab's unreachable covers a fresh try, which is what a user who just retried wants. Add to the
`searchCoverArt` KDoc in `CoverArtRepository`: starting a search forgets the addresses that could not be reached, so its
thumbnails, and any other cover shown afterwards, are asked again.

Alternative: a new `RetryUnreachableCoverArtUseCase` called by `CoverArtSearchController.searchCoverArt`. More wiring
(a domain interface, an impl, a view-model parameter) for the same effect; not recommended.

### Say "failed" when the thumbnails could not be reached

- `CoverArtSearchSheet.kt`: keep `unavailableKeys` and add `unreachableKeys` (`rememberSaveable`, reset by `search`
  like the other). `onUnavailable` gets `(candidate, isUnreachable)`; both add the key to `unavailableKeys`, and an
  unreachable one also to `unreachableKeys`. Rewrite the comment above the reset to say a new search asks every
  thumbnail again (the repository forgets unreachable ones when a search starts) while a record really without a cover is
  answered at once from its memory and drops out again. Also update the class KDoc's last paragraph the same way.
- `CoverArtResults.kt`: `onError = { state -> onUnavailable((state.result.throwable as? CoverArtUnavailableException)?.isUnreachable == true) }`
  in `CoverArtTile` (any other throwable, a decode failure, is not unreachable). Move `ResultsContent` and the `when`
  above into `CoverArtResultsContent.kt` as `internal enum class ResultsContent` plus a pure
  `internal fun resultsContentOf(results: CoverArtSearchResults?, shownCandidates: List<CoverArtCandidate>, unreachableKeys: Set<String>): ResultsContent`,
  with the new branch: complete, nothing shown, and `results.failed.isNotEmpty() || results.candidates.any { it.key in unreachableKeys }`
  → `FAILED`.
- **Any** unreachable dropped tile makes it `FAILED`, not only "every one": with ten records of which seven have no
  cover and three timed out, "no record with a cover was found" may well be false, and Retry costs one search. Choosing
  "every" instead would keep NO_RESULTS for the mixed case; not recommended.
- The FAILED state already exists with its Retry button (`onRetry = search`) and `cover_art_search_failed`, "The search
  could not be completed." / "A keresés nem sikerült.", which fits; no new string.

"Save is the only write" is untouched: nothing here writes the song.

Update `presentation/.../ui/dialogs/lookup/CLAUDE.md`, the bullet "A tile whose thumbnail fails is dropped …; a new
search restores them, since the failure may have been momentary.": "A tile whose thumbnail fails is dropped (that is
how a cover-less release group is found out); if any dropped tile was unreachable and none is left, the search shows as
failed with Retry. A new search restores them all, and the repository asks unreachable ones again." Update
`data/repository/implementation/CLAUDE.md`, "The cover copies": "an unreachable address not for a minute" gains "or
until the next cover search starts".

## Tests

- `CoverArtRepositoryImplTest`: update the existing `assertNull(repository.getCoverArt(URL))` calls to the new results
  (`assertEquals(CoverArtResult.Unreachable, …)`, `CoverArtResult.Missing`, `assertIs<CoverArtResult.Image>`).
- `CoverArtRepositoryImplTest`: `a cover search asks unreachable addresses again`: remote answers `Unreachable`;
  `getCoverArt(URL)` → `Unreachable` (1 request); collect `repository.searchCoverArt(query).toList()` (no sources, so it
  completes); `getCoverArt(URL)` again → a second request (the count is the behaviour: it was asked again).
- `CoverArtRepositoryImplTest`: `a cover search does not ask a missing address again`: remote answers `Missing`; after a
  search, `getCoverArt(URL)` answers `Missing` with still one request.
- `CoverArtResultsContentTest` (presentation `commonTest`, beside `SongDetailsReviewTest`): every candidate dropped as
  unreachable → `FAILED`; every candidate dropped as missing → `NO_RESULTS`; mixed → `FAILED`; a catalogue failed →
  `FAILED`; incomplete with nothing shown → `LOADING` / `BUSY` as before; something shown → `GRID`.
