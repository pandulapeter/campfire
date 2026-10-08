# Move the web build's pure address mapping (`BrowserRoutes`) to `commonMain`, decoupled from the view model, and test it

**Kind:** testability  ·  **Severity:** medium  ·  **Effort:** S  ·  **Risk:** low  ·  **Platforms:** web (code moves to common, behaviour only matters on the web)
**Challenged:** amended — the moved file must drop `@file:OptIn(ExperimentalWasmJsInterop::class)` and its import
(`kotlin.js.ExperimentalWasmJsInterop` does not exist in common code); `DialogType` and `SettingsTab` are imported from
wherever the package-move pass / plan 09 put them; one decode vector added for encoded surrogates.
**Files:** `presentation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/presentation/ui/navigation/BrowserRoutes.kt`
(`BrowserRoutes.paths`, `entryCount`, `resolve`, `validate`, `songPathSegment`, `setlistPathSegment`, `songFileName`,
`candidates`, `SettingsTab.pathSegment`, the route constants, `encodePathSegment`, `decodePathSegment`);
`presentation/src/wasmJsMain/.../ui/navigation/BrowserHistory.kt` (every `BrowserRoutes.paths(viewModel)` call, the
`snapshotFlow`/`combine` that observes it, `navigateToBrowserAddress`); new
`presentation/src/commonMain/.../ui/navigation/BrowserRoutes.kt`, `ui/navigation/PercentEncoding.kt`; new
`presentation/src/commonTest/.../navigation/BrowserRoutesTest.kt`, `PercentEncodingTest.kt`; `presentation/CLAUDE.md`
(the `wasmJsMain/ui/navigation/` paragraph: say where the pure part lives and that it is tested); `app/web/CLAUDE.md`
if it names the file's location
**Depends on:** none

## Problem

`BrowserRoutes` decides every address the web build writes and every address it accepts — the deep links people
bookmark and share — and three of its four functions (`entryCount`, `resolve`, `validate`) are already pure functions of
`NavigationState`, `List<Song>`, `List<Setlist>` and two booleans. None of it is tested, because it lives in
`wasmJsMain`, which has no test source set in this repo, and because:

- `paths` takes the whole view model and reads seven things off it plus a UI global:
  ```kotlin
  fun paths(viewModel: CampfireViewModel) = buildList {
      viewModel.backStack.forEach { destination -> when (destination) {
          CampfireDestination.Songs -> { add(ROOT); if (viewModel.songsSearch.isOpen.value) add(SEARCH) }
          …
          is CampfireDestination.SongDetails -> { val song = viewModel.currentSongFileName(destination)?.let(::songPathSegment).orEmpty() … }
      } }
      val dialog = viewModel.visibleDialog.value
      if (dialog != null && dialog != CampfireViewModel.DialogType.UnsavedChanges || isAnyOverflowMenuOpen) lastOrNull()?.let(::add)
  }
  ```
- the percent codec is two `js(...)` blocks (`encodeURIComponent(value).replace(/~/g, '%7E')` and a
  `try { decodeURIComponent } catch { null }`).

The rules it encodes are subtle (a settings tab other than General is an extra entry; the reorder mode repeats
`setlists` only while the search is closed; an editor address opens only the song in performance mode; a setlist
address pages through the songs that exist; `~` is escaped because the 404 page carries `&` as `~and~`), and a
regression in any of them is only found by hand in a browser.

## Fix

1. **Pure percent codec in common code** (`ui/navigation/PercentEncoding.kt`), matching the JavaScript exactly:
   - `encodePathSegment(value)`: UTF-8 encode (`value.encodeToByteArray()`), keep the bytes of
     `A–Z a–z 0–9 - _ . ! * ' ( )` as characters (`encodeURIComponent`'s unreserved set **minus `~`**, which this app
     escapes too), and write every other byte as `%XX` with **uppercase** hex, as `encodeURIComponent` does. (A lone
     surrogate, on which `encodeURIComponent` throws, cannot occur in a library file name — names are NFC text — and
     `encodeToByteArray` replaces it; note that in the KDoc.)
   - `decodePathSegment(value): String?`: walk the string, turning each `%XX` into a byte (null on a `%` not followed
     by two hex digits) and every other character into its UTF-8 bytes, then
     `decodeToString(throwOnInvalidSequence = true)` and null on `CharacterCodingException` — which is exactly when
     `decodeURIComponent` throws `URIError`. `+` stays `+` (it is not a space to `decodeURIComponent`).
   Considered and rejected: keep `expect`/`actual` with the JS actuals and a JVM actual for tests (`java.net.URLEncoder`
   encodes space as `+` and has a different unreserved set, so the test would not test the shipped code).
2. **`RouteInputs`**, a plain data class in common code holding exactly what `paths` reads:
   `backStack: List<CampfireDestination>`, `isSongsSearchOpen`, `isSetlistsSearchOpen`, `isSetlistReordering`,
   `settingsTab: SettingsTab`, `currentSongFileNames: Map<String, String?>` (by `SongDetails.id`, or a
   `(CampfireDestination.SongDetails) -> String?` function — a function keeps it cheap but makes the data class's
   equality meaningless, so prefer the map), and `hasOverlay: Boolean`
   (`dialog != null && dialog != DialogType.UnsavedChanges || isAnyOverflowMenuOpen`). `paths(inputs: RouteInputs)`
   is today's body reading those fields. Move `BrowserRoutes` (object, all four functions, the private helpers and the
   constants) to `commonMain/.../ui/navigation/BrowserRoutes.kt` unchanged apart from that — and minus the file's
   `@file:OptIn(ExperimentalWasmJsInterop::class)` and `import kotlin.js.ExperimentalWasmJsInterop`, which only the
   `js(...)` codec needed and which do not resolve in `commonMain` (the desktop, Android and iOS compilations would
   fail). Import `SettingsTab` and `DialogType` from their current packages (not `screens.settings` /
   `CampfireViewModel.DialogType`, which the package-move pass and plan 09 change).
3. **In `wasmJsMain`**, add `internal fun CampfireViewModel.routeInputs() = RouteInputs(…)` next to `BrowserHistory`,
   reading the same states the old `paths(viewModel)` read (so the `snapshotFlow { BrowserRoutes.paths(viewModel.routeInputs()) }`
   still observes the same snapshot state — `backStack`, `settingsTab`, `reorderingSetlistFileName`,
   `songDetailsCurrentSongs`, the overflow menu count — and the existing `combine` with `songsSearch.isOpen`,
   `setlistsSearch.isOpen` and `visibleDialog` still covers the flows). Replace every `BrowserRoutes.paths(viewModel)`
   with `BrowserRoutes.paths(viewModel.routeInputs())`. Delete the two `js(...)` functions.
   Steps 1–3 are one commit (a move plus a parameter change).

Keep the class comment's note that a new first segment must also be added to `ROUTES` in `app/web`'s
`service-worker.js` and `webpack.config.d/routes.js`.

## Tests

`PercentEncodingTest` with vectors taken from a browser's `encodeURIComponent` (write them into the test as literals):
`"tukorfurogep-arviz"` unchanged; `"a b&c~d"` → `"a%20b%26c%7Ed"`; `"катюша"` → `"%D0%BA%D0%B0%D1%82%D1%8E%D1%88%D0%B0"`;
`"!*'()"` unchanged; `"a/b"` → `"a%2Fb"`; round trip of each; decode of `"%"`, `"%G1"`, `"%C3"`, `"%C3a"` is null;
decode of `"a+b"` is `"a+b"`; decode of lowercase `"%c3%a9"` is `"é"`; decode of an encoded surrogate `"%ED%A0%80"` and
of an overlong `"%C0%AF"` is null (both are `URIError` to `decodeURIComponent`).

`BrowserRoutesTest`:
- `paths`: `[Songs]` → `[""]`; songs search open → `["", "search"]`; `[Songs, Setlists]` reordering with search closed →
  `["", "setlists", "setlists"]`, with search open → `["", "setlists", "setlists/search"]`; Settings on Library →
  `["", "settings/general", "settings/library"]`; a song from a setlist → `setlist/<setlist without .setlist.json>/<song without .cho>`;
  a song file with a `.chopro` extension keeps it; `hasOverlay` repeats the last path; an `UnsavedChanges`-only overlay does not.
- `entryCount` equals `paths(...).size` for the same state with no overlay and no reorder mode, for each of the above.
- `resolve`: every address of `paths` resolves back to the state it came from (round trip) for a small library;
  `song/x/edit` with performance mode on resolves to the song alone; an unknown song is null; `setlist/s/x` where `x`
  is missing from the library is null; `settings` alone keeps `current.settingsTab`; `settings/nope` is null;
  four segments is null; a segment with malformed escapes is null.
- `validate`: a stack is cut at the first song no longer in the library; a setlist song page is re-paged to what the
  setlist holds now; the editor is cut in performance mode; `ImportReport` is cut without a report.

## Manual check

On the web build (`./gradlew :app:web:wasmJsBrowserDevelopmentRun`): open `/song/<a song with an accented or Cyrillic
name>`, `/setlist/<setlist>/<song>`, `/settings/library`, `/setlists/search`; use Back and Forward through a dialog, a
search and the setlist reorder mode; rename a song while its page is open and confirm the address is replaced. Every
address should read exactly as it did before (compare with the deployed build).
