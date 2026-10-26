# Warm the song view's non-UI path once, in the background, after a desktop launch

**Kind:** performance · **Severity:** low · **Platforms:** desktop (JVM)
**Files:** `presentation/src/desktopMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireDesktopApp.kt`, new
`presentation/src/desktopMain/kotlin/com/pandulapeter/campfire/presentation/ui/SongViewWarmUp.kt`,
`presentation/src/desktopMain/CLAUDE.md`; option B also `app/desktop/CLAUDE.md`

**Challenged:** amended — the recommendation is now option A alone (the serializer and the model on
`Dispatchers.Default`), started from `CampfireDesktopApp` with no public entry point, no start delay, no probe, and no
`TrainingRun.kt` change. Option B (an offscreen `ImageComposeScene` on a background thread) stays a choice but is not
recommended: its premise that `:tools:screenshots` already renders scenes off the EDT is false (`DeviceScreen.kt` and
`SkikoComposeUiTest` run on `Dispatchers.Main`, the Swing thread), so nothing in the repo has run a second composition
on another thread beside a live window; each scene gets its own `FontFamily.Resolver` (`RootNodeOwner`'s
`createFontFamilyResolver()`), so it warms no font or paragraph cache of the window's; and a few renders stay far below
HotSpot's compile thresholds, so the ~117 ms of interpreted code mostly stays. Its realistic gain is the class loading
and linkage (≤ ~150 ms, part of it in `SongDetailsScreen` and the chrome, which it cannot reach), for a production
`ImageComposeScene`, lifecycle owners, theme, and a thread of their own.

**Decision D2** (awaiting the user): A recommended; B; or neither (drop the plan).

## Problem

The user: "The very first time during the session when the song details screen is opened feels laggy" — confirmed on
the desktop. An offscreen probe in `:tools:screenshots` (demo library, 360x640dp) measured the first open in a JVM at
~360 ms of main-thread work over 4 frames (one of 222 ms), every later open at ~40–50 ms, and a fresh Koin graph and
view model in a warm JVM at the later-open cost: it is the JVM warming up, once per process, not app state. The ~286 ms
excess, attributed (overlapping):

| Cause | ms | Where |
|---|---|---|
| Class loading | ~107 | first `BasicTextField` of the session ~13 (`components/controls/StepperField.kt` via `SongPlayingControlsRow`); the rest over `SongDetailsScreen`, `SongPages`, `SongLyrics`, `InstrumentPillRow`, `SongSectionsLayout` |
| Interpreted, not yet JIT-compiled code | ~117 | `SongLyrics` → `SongSectionsLayout` / `SongSectionItem` +65; `SongMetadataSection` / `SongPlayingControlsRow` / `Stepper` +33; `SongGridSearch.searchGrid` / `flowIntoPages` +16; `rememberSongLyricsModel` / `prepareSongLyrics` +13; `SongDetailsAppBar` +15; `NavigationChromeScaffold` transition +49 |
| Lambda / invokedynamic linkage | ~31 | spread |
| Skia's first paragraph | ~24 | |
| Static initializers (string tables) | ~8–10 | |
| `Navigator.persistBackStack` building the `CampfireDestination` serializer | ~29 | on the click |

AppCDS cannot help the Mac (no archive there, see `recordClassDataArchive`'s KDoc), the Compose plugin 1.12.2 has no
AppCDS DSL, and JBR 21 has no AOT cache for compiled code. Most of the excess is the window's own composables loading
and running for the first time, which only the window itself can do.

## Fix

### Option A (recommended): the non-UI half, ~40 ms

Of the excess, two pieces need no composition and are safe on any thread: the `CampfireDestination` serializer that
`Navigator.persistBackStack` builds on the click (~29 ms, before frame 0 — input latency), and the parser and model
classes `prepareSongLyrics` loads (~13 ms; the app already builds models on `Dispatchers.Default` for the pages beside the
one on screen, so this path is proven off the main thread).

1. `SongViewWarmUp.kt` (desktopMain, `internal`): one function, e.g.
   `internal suspend fun warmUpSongView(songRenderer: SongRenderer, labels: DefaultSectionLabels)`, which on
   `Dispatchers.Default`:
   - encodes `listOf(CampfireDestination.Songs, CampfireDestination.SongDetails(songFileNames = listOf("warm-up.cho"),
     setlistFileName = null, initialIndex = 0))` with `Json.encodeToString<List<CampfireDestination>>(...)`, as
     `persistBackStack` does (the generated serializer is a cached, thread-safe singleton);
   - reads `Res.readBytes("files/demo/traditional_american-house_of_the_rising_sun.cho")` and runs what
     `SongDetailsPage`'s `prepare` lambda runs on it (`songRenderer.transposedSong(...)`, then `prepareSongLyrics(...)` with
     `searchesShapes = true` and the default tuning), discarding the result;
   - catches any `Throwable` but `CancellationException`, prints one line (it reaches `campfire.log`), and returns: a
     warm-up must never take the app down.
2. `CampfireDesktopApp.kt`: one `LaunchedEffect` that waits until the library has been read (the first frame after the
   view model's songs state holds data; reuse whatever `CampfireDesktopApp` or the view model already exposes rather
   than adding a flag), then calls it once, with `viewModel.songRenderer` and `rememberDefaultSectionLabels()` read in the
   composition. Not in common code (Android has its Baseline Profile; iOS and the web are compiled ahead of time), and not
   in `:app:desktop`: `CampfireDesktopApp` is the desktop shell, and `:tools:screenshots` hosts `CampfireApp`
   (`CampfireAppHost.kt`), so its UI tests and goldens never run it. No start delay and no "already opened" check: it is
   ~40 ms of one `Default` thread, after the launch's own reads.
3. The Windows and Linux CDS training run ends 120 frames after the demo library is written (`TrainingRun.kt`), by which
   time this has run, so `campfire.jsa` holds its classes with no change there.
4. `presentation/src/desktopMain/CLAUDE.md`: a short `## Warm-up` section — what it touches (serializer, demo song model),
   that it shares no state with the window, why only the desktop, and that composing the song view in the background was
   weighed and rejected (one line, the reason above).

### Option B (not recommended): A, plus one offscreen composition of `SongLyrics`

Compose `SongLyrics` over A's model in an `ImageComposeScene` on a daemon thread of its own (never the EDT), inside
`CampfireTheme`, with `LocalLifecycleOwner` provided (`RememberSongPlayingControls` collects with lifecycle), playing
controls present, rendered a few times at one- and two-column widths, then `close()`d; start it ~3 s after the library
is on screen and skip it if a song was already opened. Ship it only if a throwaway probe in `tools/screenshots/src/test`
(untracked, deleted afterwards; never with another Gradle build running) shows ≥ 100 ms saved on the first open over
three fresh-JVM runs each way and no exception; otherwise ship A. Its open questions are the challenge's above: no
precedent for a background composition beside a live window, a separate font cache, and a gain that the JIT share
largely escapes. `app/desktop/CLAUDE.md` would get one line under Startup order on when it starts.

## Tests

No lasting unit test (there is no logic to pin beyond "it runs"). Run `./gradlew :presentation:desktopTest
:tools:screenshots:test :app:desktop:compileKotlin`, then `./gradlew :app:desktop:createReleaseDistributable` and
`runRelease` once (ProGuard must keep what the warm-up reaches; it is the app's own code), checking `campfire.log` has no
warm-up failure line.
