# Drop the selection of a search tile that turns out to have no cover

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/CoverArtSearchSheet.kt

## Problem
In the cover search sheet a tile is selectable from the moment it appears, while its thumbnail is still loading
(`CoverArtTile`, CoverArtSearchSheet.kt:523-584, `selectable(...)` on the whole tile). A tile whose thumbnail fails is
taken off the grid (`onUnavailable = { unavailableKeys += it.key }`, line 188) — which is how a MusicBrainz release
group without a cover is found out — but `selectedUrl` is left as it was:

```kotlin
onSelected = { selectedUrl = it.coverArtUrl.takeUnless { url -> url == selectedUrl } },
onUnavailable = { unavailableKeys += it.key },
```

and Save stays enabled on `selectedUrl != null` (line 206). Scenario: search, tap a tile while its thumbnail is still
loading (common on a slow connection, and a Cover Art Archive 404 takes a redirect or two to arrive), the tile then
disappears; the sheet shows no selection but Save is enabled, and pressing it writes
`{meta: cover https://coverartarchive.org/release-group/…/front-250}` — an address that answers 404 — into the song.
From then on every list row and the details bar show the album-icon placeholder for that song as if it had a cover,
and the failure memory never asks again that session.

Related: `unavailableKeys` is `rememberSaveable` for the life of the sheet and is never cleared by a new search, so a
tile that failed for a transient reason (`Unreachable`: a timeout, a 503) stays hidden from every later search in the
same sheet, Retry included.

## Fix
In `CoverArtSearchSheet`:
1. `onUnavailable = { candidate -> unavailableKeys += candidate.key; if (candidate.coverArtUrl == selectedUrl) selectedUrl = null }`.
2. In the `search` lambda (which already clears `selectedUrl`), also reset `unavailableKeys = emptySet()`: a key that
   is really missing is answered at once from the repository's failure memory and drops out again immediately, while a
   transient one gets its second chance.
Optionally make a tile selectable only once its image has loaded (`AsyncImage(onSuccess = …)` setting a per-tile
`isLoaded`), which removes the window entirely; if done, say so in the sheet's KDoc.

## Verification
Manual (desktop or Android with a throttled network): search for an artist/album with many MusicBrainz release
groups, tap a tile before its thumbnail arrives, wait for it to vanish (pick one of the ones that turn out empty):
Save must be disabled afterwards. Press Search again after toggling the network off and on: tiles that failed only for
the outage come back.

## Conflicts
Same file as plan 15 (different composables: `CoverArtSearchSheet` / `CoverArtResults` here, `CoverArtAddressPreview`
there).
