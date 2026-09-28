<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Cover art thumbnails: implementation plan

A song can carry a cover image URL in its own file. The image is shown faded into the end of the song cards and in
the song details header, is cached for offline use, and can be written by hand in the editor or picked from the
[Cover Art Archive](https://musicbrainz.org/doc/Cover_Art_Archive/API) through a search sheet reached from the song
details screen's overflow menu.

## Decisions taken

| Question | Decision |
|---|---|
| Which URLs are loaded | Any `http(s)` URL. The library is the user's, and so are the URLs in it. The bundled demo songs are ours to control. |
| What the tag stores | The full image URL. MusicBrainz is only used to recommend one. |
| Image size | `front-250` for both the cards and the details header. |
| Search inputs | Artist and album; when the album is empty, the song title finds the albums the song appeared on. |
| Setlists screen and Performance mode | Both show the cover. |
| Shape of the search flow | A modal bottom sheet. |

## Facts about the services (checked live on 2026-09-28)

- **MusicBrainz search** (`https://musicbrainz.org/ws/2/{release,release-group,recording}?query=…&fmt=json`):
  - One request per second per source IP on average. Going over it gets every request rejected with a
    `503 Service Unavailable` until the rate drops.
  - Every request must carry a `User-Agent` that lets MusicBrainz contact the maintainers, in the form
    `Application name/<version> ( contact-url )`. Anonymous or generic user agents are throttled hardest.
- **Cover Art Archive:**
  - Has no rate limit.
  - `https://coverartarchive.org/release-group/{mbid}/front-250` (also `-500` and `-1200`) answers with a 307 to
    archive.org, which answers with a 302 to a storage node.
  - `release/{mbid}` works the same way.
- **CORS:** MusicBrainz, coverartarchive.org and both archive.org hops send `access-control-allow-origin: *`, so the
  web build can call all of them directly.

## 1. The tag (`:chordpro`, `:data:model`)

- **Format:** `{meta: cover <url>}`, following the `{meta: language en}` precedent. ChordPro defines no directive for
  it, and a `{meta}` line survives any other ChordPro tool.
- **Reading:**
  - `ChordProSyntax.COVER_NAME` plus a `cover(directive)` reader.
  - `ChordProMetadata.coverArt: String?` holds the first one; any `http(s)` value is accepted.
  - `summarize` carries it, so the library scan fills `Song.coverArtUrl` without a second read.
- **Writing:**
  - A new `ChordProCoverArt.set(text, url?)` object, next to `ChordProTags` and `ChordProLanguages`.
  - It replaces the existing line, removes it when given null, or inserts it through `metadataInsertionIndex`.
  - The tag's place in `metadataOrder` is after `album`.
- **Demo songs:**
  - Add `front-250` Cover Art Archive URLs to the bundled demo songs where a fitting release group exists (they are
    public domain standards, so one may not).
  - Check what Settings' "add the demo songs" offer does for a library that holds the previous versions of the
    files: they are compared by content.
- **Tests:** `:chordpro` unit tests for parsing, set, replace, remove and round trip.

## 2. Fetching and the offline cache

The root `CLAUDE.md`'s network statement becomes "sync, and the cover images and the cover search the user asks
for". Every network call still happens in `:data:source:remote`, so Coil gets **no** `coil-network-*` artifact.

- **`:data:source:remote`: `CoverArtSource`**
  - Ktor GET of the image bytes, following redirects.
  - Since any URL is allowed: a size cap (around 5 MB) and an `image/*` content type check. A failure caches nothing.
  - The requests carry the `User-Agent` of section 5 whenever the host is a MetaBrainz one (`coverartarchive.org`,
    and `musicbrainz.org` for the search). The one Ktor client may simply send it on every request; it names the app
    and nothing about the user.
- **`:data:source:local`: `CoverArtLocalSource`**
  - Stores the bytes as `covers/<sha256 of url>`, using a new `FileStorage` directory kind (OPFS on the web).
  - The folder is outside `library/`, so it is never exported or synced: the URL travels in the song file.
  - It is left out of device backup, since it can be downloaded again. Android's allow-list already leaves it out;
    iOS needs `keepOutOfDeviceBackup`.
- **`:data:repository`: `CoverArtRepository.get(url)`**
  - Reads the disk copy first, otherwise downloads the image, writes it to disk and returns it.
  - Simultaneous requests for one URL share one download.
  - A failure is remembered for the session, so a dead URL is not requested on every scroll.
  - After each library scan, cached files that no song refers to are deleted.
- **`:presentation`: Coil 3 (`coil-compose`)**
  - A custom `Fetcher` / `Keyer` for a `CoverArt(url)` model calls `GetCoverArtUseCase`.
  - One `SingletonImageLoader` is set up in `CampfireApp`.
  - Coil's memory cache serves scrolling; our file store is the offline cache on all four platforms. (Coil's own
    disk cache is LRU and does not exist on wasm.)
- **Platform limits.** These are documented, not worked around; the song just shows no image.
  - The web build can only show images from hosts that send CORS headers.
  - Plain `http://` is refused on Android and iOS by the platforms' defaults, and on the web as mixed content.
- **Settings:** a "Show cover art" switch (`UserPreferences`), on by default. When it is off, nothing is fetched or
  drawn.

## 3. Display (`front-250` everywhere)

- **Song cards** (`SongListItem` in `ListItems.kt`, used by both the Songs and Setlists screens):
  - A square image, as tall as the card, sits behind the content at the card's end, under the action buttons.
  - It fades toward the start through a `BlendMode.DstIn` horizontal gradient mask on an offscreen `graphicsLayer`,
    at reduced alpha. Tune it on every palette in both themes.
  - Coil's crossfade runs only for disk or network loads, so an image already in memory appears at once.
  - `MissingSongListItem` never shows an image.
- **Details header** (`SongMetadataHeader` in `SongLyrics.kt`):
  - The same treatment rather than a full-width banner, since 250 px stretched across a wide screen would be blurry.
  - A square of about 120–140 dp at the end of the header area under the toolbar, fading toward the start and the
    bottom, with the tag and language chips drawn over its faded part.
  - It is part of the existing `headerHeight` measurement, so the section grid still knows the room it has.
  - Shown in Performance mode and in the editor's preview too.

## 4. Editor

- A "Cover art" entry in the `EditorToolbar` metadata insertions: `EditorInsertion.meta(label, "cover")`.
- The highlighter already handles `{meta}`, so nothing changes there.

## 5. The search sheet

- **Entry:** "Find cover art…" in the details screen's `SongActionsButton` menu, or "Change cover art…" when the song
  has one. That menu is already hidden in Performance mode (`SongDetailsScreen.kt`, the
  `currentSong?.takeIf { !isPerformanceModeEnabled }` block).
- **Sheet:**
  - A new `DialogType.CoverArtSearch(song)`, drawn with the existing `CampfireBottomSheet` in `Dialogs.kt`. That gives
    it the edge-to-edge insets, keyboard handling, dismissal and web Back behaviour the other sheets have, and no web
    address of its own.
  - The song's title is the subtitle.
  - A wider `sheetMaxWidth` makes room for the results grid on the desktop and on tablets.
- **Contents:**
  - Artist, Album and Title fields, prefilled from `{artist}`, `{album}` and `{title}`. A search runs on submit, not
    per keystroke.
  - Below them, a grid of results showing title, artist, year and type.
  - Selecting a result and pressing **Save** writes the tag through `SetSongCoverArtUseCase`, the path tag and
    language edits take. **Remove** is offered when the song already has a cover.
  - States: idle, loading, no results, failed (with retry), and "the service is busy, retrying".
- **State:**
  - Held in `CampfireViewModel` as a `StateFlow`, so it survives an Android configuration change.
  - Cleared, and its search cancelled, when the sheet is dismissed (`dismissSheet`).
- **Queries** (a pure, tested builder with Lucene escaping):
  - With an album: `/ws/2/release?query=release:"…" AND artist:"…"&fmt=json`. Keep the releases whose
    `cover-art-archive.front` is true, and group them by release group.
  - With only a title: `/ws/2/recording?query=recording:"…" AND artist:"…"&inc=releases&fmt=json`, then take the
    distinct release groups.
  - The URL written is always `https://coverartarchive.org/release-group/{mbid}/front-250`. A tile whose image
    answers 404 is dropped from the grid.
- **Identifying the app to MusicBrainz:**
  - Every request carries `User-Agent: Campfire/<campfire.versionName> ( https://github.com/pandulapeter/campfire )`.
    It is set once, as a default request header of the Ktor client in `createHttpClient`. The version comes through
    the same generated-constant route the Dropbox app key takes, so it is never typed by hand.
  - On the web, `User-Agent` is a header browsers forbid scripts to set, and fetch drops it silently, so the browser's
    own user agent is what reaches MusicBrainz. Find out whether MusicBrainz documents a way for browser clients to
    identify themselves, and use it if it does. Either way, the web build must not break because the header is
    missing.
  - A unit test checks the header's exact shape.
- **Rate limiting** (MusicBrainz only, inside `MusicBrainzSource`):
  - One limiter for the whole app: a `Mutex` plus the time of the last request, spacing calls at least 1.1 s apart.
  - A 503 is retried after its `Retry-After`, or with a doubling back-off with a ceiling when there is none, the way
    the Dropbox provider does it.
  - A new search cancels the one still running.
  - The Cover Art Archive thumbnails go through the cover pipeline of section 2, which has no limiter.
  - Response models default every field and ignore unknown keys.

## 6. Everything around it

- **Dependencies:** Coil in `gradle/libs.versions.toml`, at a version built against Kotlin 2.4.20 and Compose 1.12.
- **Desktop build:** Coil's keep rules in `app/desktop`'s ProGuard configuration, checked with the Xvfb start check
  of `publish-linux.yml`.
- **Documentation:**
  - The root `CLAUDE.md`: the network statement in the introduction, the library layout block (`covers/`), a
    conventions bullet on the cover tag, and the backup paragraph.
  - The module `CLAUDE.md` files of every module touched.
  - `documentation/testing/release-check.md`: a cover art check.
- **Outside the repository:** the privacy policy and the App Store privacy answers. Search terms now go to MetaBrainz,
  and song files can make the app contact whatever host their cover URL names.
- **Strings:** every new one in both `values` and `values-hu`.
- **Tests:** the query builder, response parsing, the `User-Agent` value, the limiter under virtual time, and the
  cache pruning.
- **Baseline profile:** regenerate the Android profile afterwards, since the song rows draw more.

## Order of work

1. The tag in `:chordpro` and the model.
2. Cover fetching and caching, and Coil.
3. The cards and the details header.
4. The editor entry and the Settings switch.
5. The MusicBrainz source, its `User-Agent` and the limiter.
6. The search sheet.
7. The demo song URLs, the documentation and the privacy texts.

Steps 1–4 already make the feature work for tags written by hand, so they can land as a first series before the
search sheet.
