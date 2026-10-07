# Low-end performance review

Reviewed commit: `491c4254a` on `master`, with a clean working tree. Review date: 2026-10-06.

**Angle:** user experience on low-end devices, meaning frame rate and startup time, on every platform. The trigger
was a low-end Windows 11 2-in-1, where the web build (Kotlin/Wasm in the browser) runs significantly better than
the native Windows build (Compose Desktop on the JetBrains Runtime, from the Microsoft Store).

**How the review ran:**
- Five read-only reviewers covered:
  - the desktop shell, JVM and renderer;
  - the startup path on every platform;
  - frame costs in the shared UI;
  - CPU and parsing costs;
  - a live measurement run of the packaged release desktop build on the Mac (an Apple-silicon efficiency-core / 2-CPU
    stand-in for a weak machine).
- Four writers verified every finding against HEAD and wrote one plan per finding.
- Three fresh challengers then tried to break every fix. The plans they amended say so under their headers.

## Headlines

1. **The native desktop build spends its startup loading and verifying classes.**
   - One start loads about 10,700 classes, and only about 16% come from the JDK's class data sharing archive.
   - An archive of the app's own classes, recorded at build time, cut time to the song list from 0.92 s to 0.54 s
     on the Mac.
   - On the slow-core stand-in it went from 4.6 s to 2.8 s, and the GC pauses disappeared.
   - Plan 01 does this.
2. **The renderer is the likeliest cause of the gap during use, and today nothing can show which one a machine got.**
   - Skiko starts Direct3D 12 on Windows and silently falls back to OpenGL and then to software.
   - Software rendering scrolled at 35–39 FPS even on the Mac, against 120 FPS on Metal.
   - The browser instead runs the same Skia through WebGL, then ANGLE, then Direct3D 11, a far more heavily used path.
   - Plan 02 writes the renderer, the graphics adapter and the start time into `campfire.log`. Plan 03 ships ANGLE on Windows (D-03).
3. **A playing click with "Animate on the beat" on repaints the whole window at the display's rate for as long as it
   plays.**
   - The pendulum's swing lasts exactly one beat, so it never rests.
   - On the Mac this costs 120 FPS and about 35% of a fast core; with the setting off, 2.7%.
   - On a machine rasterising on the CPU, that would take a whole core, so scrolling a song while the click plays stutters.
   - Plan 40 would have fixed this, but the user declined it (D-40). Turning "Animate on the beat" off avoids it.
4. **Smaller main-thread costs on the way to the first frame and during use:**
   - the HTTP client is built in the first composition (11);
   - the library read waits for the window (10);
   - the song grid is searched again on every frame of the app bar collapsing (34);
   - full-viewport offscreen layers are used for edge fades (31);
   - the whole song is prettified on every keystroke (30).

## Index

| # | Plan | Severity | Lane |
|---|---|---|---|
| 01 | Ship a class data sharing archive of the app's own classes, recorded by a training run at build time | high | D |
| 02 | Write the renderer, the graphics adapter and the time to the first frame into campfire.log | high | D |
| 03 | Render through ANGLE on Windows (**D-03: ship now**) | medium | D |
| 10 | Start the first preferences and library read when Koin starts | medium | S |
| 11 | Build the Ktor HttpClient the first time a request needs it, off the main thread | medium | S |
| 12 | Read each song's size and date together with its text | low-medium | S |
| 13 | Read the next batch of songs while the current one is being parsed | low-medium | S |
| 14 | Ask Play whether an update exists once the app is on screen | low | S |
| 15 | Make the launcher icon's package manager calls on a background thread | low | S |
| 16 | Sort the whole library once per change, for both the song list and the picker (**D-16**) | low | S |
| 20 | Check the kept web build while build.json is being asked for, and wait less for it (**D-20**) | medium | W |
| 30 | Decide whether Prettify is enabled only while the editor's menu is open | medium | U |
| 31 | Paint edge fades over their known opaque background instead of masking offscreen | medium | U |
| 32 | Ask for song info only in read only mode, and from the metadata alone | low | U |
| 33 | Build the first rendering of a song page off the main thread unless it is on screen or headed for | low | U |
| 34 | Decide a song page's grid against the settled app bar height | medium | U |
| 35 | Split SongDetailsScreen below HotSpot's 8,000-byte JIT limit | low | U |
| 41 | Read the metronome icon's pulse color in the draw phase | low | M |

## Lanes

| Lane | Area | Plans in order | Files owned |
|---|---|---|---|
| D | Desktop packaging and shell | 01 → 02 → 03 | `app/desktop/**`, `.github/workflows/publish-windows.yml`, `publish-linux.yml`, `gradle/libs.versions.toml` (03 only) |
| W | Web loader | 20 | `app/web/**` (`index.html`, `tests/`) |
| S | Startup and data | 10, 11, 12 → 13, 14, 15, 16 | `:app:di`, `:data:*`, `:domain:*`, `app/android` (AppIconSwitcher, CampfireMainActivity), `AppUpdate*.kt`, `AppUpdateGate.kt` |
| M | Metronome visuals | 41 | `ui/metronome/MetronomeIcon.kt` |
| U | Song details, editor and edge fades | 30, 31, 32 → 33 → 34 → 35 | `SongDetailsScreen.kt`, `SongLyrics.kt`, `SongEditorScreen.kt`, `SongActions.kt`, `EdgeFade.kt`, `Dialogs.kt` and the other fade call sites, `SettingsScreen.kt`, `SongsScreen.kt` (one argument), `MetronomePanel.kt` (34) |

**Merge order: D, W, S, M, U.**
- D and W touch no shared code.
- S changes data and use-case contracts that U's `CampfireViewModel` edit sits next to.
- U touches the most shared UI files, so it goes last.
- Within U, 35 is strictly last, because it moves code that 32–34 add to `SongDetailsScreen`.

### Shared files

Shared files are merged word by word, keeping every sentence from both sides.
- **`CampfireViewModel.kt`:**
  - 14 changes only `isAppOnScreen`, exposing it as a StateFlow;
  - 16 changes only `indexedSongs`, `pickerSongs` and the private `IndexedSong*` classes;
  - 32 changes only `songMetadataOf` and a new `hasSongInfo(text)` beside it.
- **`presentation/CLAUDE.md`:** 14, 30, 31, 33 and 34 each edit their own paragraph.
- **Root `CLAUDE.md`:** 01 changes the publish-linux and publish-windows Build bullets, and 20 changes the Web section.

## Decisions

All answered by the user on 2026-10-07:
- **D-03, ANGLE on Windows: ship it now.** Plan 03 is executed with ANGLE preferred on Windows, falling back to
  Direct3D 12 when it fails. The measurement recipe in 03's Manual check remains the way to confirm it on the 2-in-1.
- **D-16, picker order: accepted.** The Choose songs sheet takes the Songs screen's order.
- **D-20, web loader: option (c).** The kept build is inspected in parallel with the build.json request, the wait is
  0.8 s when the kept build is whole, and a late answer naming another build makes the next launch wait the full 3 s.
- **D-40, the beat animations: unchanged.** The user declined plan 40, so the pendulum swing, the pulse and the flash
  stay as they are. A playing click with "Animate on the beat" on keeps repainting continuously, and turning that
  setting off is how a low-end machine avoids it. Plan 40 was removed (see Dropped); plan 41, which does not change
  how anything looks, stays.

## Checked and found solid

- **Idle rendering:** zero frames idle on every screen, including the Metronome tab with no click playing.
- **GC:** total pauses of 15–30 ms per start, and a live heap of about 15 MB after startup. JVM flags were not
  worth a plan (see Dropped).
- **Desktop shell:**
  - `TouchScreen.kt` costs O(1) per event.
  - `TitleBar.kt`, `DesktopLog`, `AppIcon.kt` and the dark-theme poll are cheap.
  - The single-instance claim takes 17 ms.
  - ProGuard optimises without obfuscating.
  - Windows keeps the optimised module graph, since it has no `--add-opens`.
- **Koin:** a compile-time graph with no `createdAtStart`. The audio outputs allocate nothing until a click starts.
- **Launch gating:**
  - preferences, scan, draft and first-run checks run in parallel;
  - the scan runs on Default in batches of 64, with the doubling partial publish;
  - native builds bundle no fonts;
  - the web preloads its fonts and drawables in parallel with the library read.
- **`:chordpro`:**
  - fast on typical songs (a 1.9 KB song parses in 51 µs);
  - hot regexes are precompiled;
  - `ChordVoicings` is memoised, and its search is kept off the main thread;
  - search runs per library change, with ranking by buckets;
  - the editor preview is debounced and built on Default.
- **Lists:**
  - keys and contentType are set;
  - placement animation is off during scrolls;
  - per-card top fades are offscreen only under the header;
  - Coil decodes cover thumbnails downsampled.
- **Song details:**
  - the top fade is already painted;
  - scroll is read in draw or in derived state;
  - chord diagrams are drawn in `drawBehind`.

## Dropped after verification

- **JVM flags for small machines (SerialGC, Metaspace, C1 only).** GC pauses total only 15–30 ms, and SerialGC gave
  the worst single pause (89–106 ms). C1-only's cost to steady-state frame rate was never measured, and plan 01 gets
  most of the startup gain with no trade-off.
- **A CI check for methods over 8,000 bytes.** SongDetailsScreen is the only real offender, and plan 35 splits it.
  The only other two are generated string-table initialisers that run once. A javap gate over the 26 MB jar costs
  more than it guards.
- **Precompiling the localization plugin's format regexes.** Measured at 0.48 µs per `formatString` call on the
  JVM, one per card. Even 100× slower on Wasm, that is under 2 ms for a whole list.
- **"The beat flash's spring never settles within a beat."** The flash and the pulse settle in about 170–180 ms.
  The continuous repaint comes from the pendulum swing (plan 40, declined).

- **Plan 40, letting the beat animations settle between beats.** Declined by the user (D-40): the animations stay as
  they are.

## Measurements

Release app image, JBR 21.0.10, 26 MB ProGuard-joined jar, on an Apple-silicon Mac. "Slow" means efficiency cores
only, with `-XX:ActiveProcessorCount=2 -XX:+UseSerialGC -Xmx512m`.

| Configuration | Window | Song list shown | Classes (from CDS) |
|---|---|---|---|
| Default | 0.68–0.72 s | 0.91–0.97 s | 10,670 (1,675) |
| `-Xshare:off` | 0.73 s | 0.95 s | 10,678 (0) |
| App class archive (plan 01) | **0.43 s** | **0.54 s** | 10,137 (9,964) |
| Slow | 3.2–3.4 s | 4.5–4.6 s | ~10,650 |
| Slow + app class archive | **2.24 s** | **2.77 s** | 10,121 (9,956) |
| Slow, 1,000 songs | 3.54 s | 4.93 s | 10,584 |

| Situation | CPU | Frames |
|---|---|---|
| Idle (any screen) | 0.3–0.5% | none |
| Click playing, Animate on | ~35% of a fast core | constant 120 FPS |
| Click playing, Animate off | 2.7% | none |
| Scrolling 1,000 songs, Metal | 48% | 116–120 FPS |
| Scrolling 1,000 songs, software renderer | 48% | 35–39 FPS (frames 26–31 ms) |
| Scrolling 1,000 songs, slow | 31% | ~10 FPS |

## Manual checks owed

Each plan's **Manual check** section has the details. The ones that matter most:
- **The 2-in-1 measurement for plans 02 and 03:**
  - set `JAVA_TOOL_OPTIONS` with the FPS, long-frame and hardware-info flags;
  - compare the default, OPENGL, SOFTWARE_FAST and vsync off against Chrome;
  - read the renderer line in `campfire.log`.
- **01:**
  - the Store `.msix` and the `.deb` map the archive (`-Xlog:cds`);
  - time cold and warm starts on the 2-in-1, before and after;
  - check the Linux and Windows publish runs stay green, now that they check sharing.
- **03:** the Store build on the 2-in-1 says ANGLE in `campfire.log` and scrolls at least as well as before; an
  ordinary Windows machine shows no regression; `SKIKO_RENDER_API=DIRECT3D` still forces Direct3D 12.
- **33 and 34:**
  - pedal-only reading (Up/Down alone) through a setlist of long songs, forward and back, including a fast pedal back;
  - in a short window, the app bar collapsing and returning without the sections jumping.
- **31:** screenshots of every converted fade, in light and dark and in two palettes.
- **20:** a warm launch on a slow connection, and a release published while a client sits on a link slower than 0.8 s.
- **14:** a priority 4–5 release on the internal track. The blocking screen comes right after the app shows.
