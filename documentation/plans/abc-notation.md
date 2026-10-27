<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Plan: draw ABC blocks as staff notation

**Status:** both spikes passed 2026-10-27 and the engines are chosen (see "Spike results"); the App Store exception
was asked of abc2svg's author on 2026-10-27, no answer yet; steps 2–6 not started.

Reading ABC landed on 2026-10-26 (8d9da5565..95d8540cb): an `abc` block is a run of `ChordProLine.Verbatim` lines drawn
as monospace text, and `.abc` files and pasted tunes import as ChordPro songs with a chord grid, verses, and the tune
kept in `{start_of_abc}` (`chordpro/abc`). This plan replaces the monospace text of an `abc` run with engraved staff
notation, drawn by an open-source renderer. `ui/` is
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/`, as in the root `CLAUDE.md`.

Worth doing once users ask for notation; the audience (folk melody players) is narrower than the app's.

## Decisions

- **We don't write an engraver.** The notation comes from a third-party renderer. If none passes the spike, ABC stays
  monospace text, as it is today, and this plan is dropped.
- **The file doesn't change.** The ABC text in the song file stays the source; the staff is drawn from it on every
  display, never written back, and no rendered output is stored or synced.
- **`ly` stays monospace.** Only `abc` runs are engraved; `svg` blocks may be drawn by the same SVG drawing (step 3) if
  it is cheap, never a reason to pick a renderer.
- **No network.** The renderer and its fonts are bundled; nothing is fetched at runtime.

## Where it hooks in today

- `ui/songLayout/LineRuns.kt`: `FoldableKind.VERBATIM` runs, named from the lines' `environment`.
- `ui/songView/SongSectionContent.kt`: the `FoldableKind.TAB, FoldableKind.VERBATIM` branch draws a verbatim run with
  `SongTabBlock` on the preformatted path (`SongTextMeasurements.tabRows(…, isPreformatted = true)`).
- `ui/print/PrintRows.kt`: `verbatimRows` prints the run preformatted and unselectable, gated on `options.showChords`.
- Lyrics-only mode already drops verbatim runs (`needsChords()` in `SongRenderSections.kt`), so it needs no work.

## Steps

### 1. Spike (decides whether the rest happens)

The candidate is [abc2svg](https://chiselapp.com/user/moinejf/repository/abc2svg) (Jean-François Moine, the author of
abcm2ps): ABC to SVG in plain JavaScript, needing no DOM to measure its layout, with a QuickJS runner, so it runs
headless. Answer, in a throwaway branch:

- **License:** current abc2svg sources say LGPL, older ones GPL-2. Confirm the version, and that an unmodified script
  bundled in an MPL-2.0 app is fine on every store (Play, App Store, Mac App Store, Microsoft Store).
- **JavaScript engine:** which QuickJS binding for Kotlin Multiplatform is maintained, its license, its size per ABI,
  and whether it builds on every shipped target (Android ABIs, iOS device and simulator, desktop macOS, Linux, and
  Windows). The web build needs none: it runs abc2svg in the browser.
- **Drawing the SVG:** whether abc2svg's music glyphs (its embedded music font) render correctly in Skia's SVG DOM
  through Skiko (desktop, iOS, web) and in an Android SVG library such as AndroidSVG; and how to recolour the output for
  the theme and dark mode (abc2svg's colour options, or rewriting `fill`/`stroke` before drawing).
- **Speed:** the time to render a typical tune (a 32-bar reel with chords and `w:` lyrics) on the smallest supported
  phone (360 × 640 dp), and to re-render at a new width.
- **Alternatives,** only if abc2svg fails one of the above: abcjs (MIT, but it needs a DOM to lay out, so a headless
  DOM shim or a WebView), or a native engraver. A WebView is a last resort: it breaks the shared Compose drawing and the
  PDF path.

The spike ends in a short note added to this plan: the verdict, the versions picked, the measured sizes and times, and
the open questions it settled.

### 2. Module `:abc:api` / `:abc:implementation`

Depends on nothing of the app's, like `:metronome` and `:tuner`; `:abc:api` uses `explicitApi()`.

- `:abc:api`: one interface, roughly `suspend fun render(tune: String, widthPx: Int, options: AbcRenderOptions):
  AbcRendering`, where the rendering is a list of staff systems, each an SVG string with its height, so the layout can
  treat a system as one unsplittable row. Options carry the colours, the transposition in semitones, and the chord
  notation.
- `:abc:implementation`: abc2svg bundled as a resource, run by one engine kept alive on a single-threaded dispatcher,
  behind an `expect`/`actual` of a few dozen lines: JavaScriptCore on iOS, the browser's JavaScript on wasmJs, and
  quickjs-kt on Android and desktop (`jvmSharedMain`), with Rhino the proven fallback (see "Spike results"). A tune that
  fails to render returns an error, never throws, so the caller falls back to monospace text.
- Koin as everywhere: `@Single` implementation, the module named in `app/di`.
- Credits in the same change, transitive ones included: `ui/dialogs/about/OpenSourceComponents.kt`, the licenses and
  NOTICE under `composeResources/files/licenses` of the source sets that ship them, and
  `documentation/THIRD_PARTY_SOFTWARE.md`.

### 3. Drawing SVG in Compose

- One painter in common code for the subset abc2svg emits (see "Spike results"), no `expect`/`actual` and no SVG
  library: a small XML reader, `PathParser` for `d`, the music glyphs as `Path`s extracted once from abc2svg's font by a
  tools script (keyed by code point), titles, chord symbols, and lyrics in the app's Inter through `TextMeasurer`.
  Classes map to roles (music, chord, lyric, title), each coloured from the theme. Drawn into a Compose canvas so the
  viewer and the PDF share it; an element it doesn't know makes the run fall back to monospace text.

### 4. The viewer

- `SongSectionContent`'s `VERBATIM` branch draws an `abc` run as notation (a new `SongNotationBlock`), `ly` and `svg`
  still through `SongTabBlock`; until the first rendering arrives, and if rendering fails, the run is the monospace text
  it is today, so the first frame is right and nothing is animated over.
- Width changes re-render; the systems reflow with an animation (`components/PlacementAnimation.kt`'s rule for resized
  windows), never a jump. Renderings are cached per tune, width, colours, transposition, and notation, like
  `SongTextMeasurements`.
- Transposition and capo: rendered with the semitones the grid is moved by (setlist or device override, the file's
  capo counted in), so staff and grid always agree; chord symbols in the player's display notation. The spike checks
  how abc2svg's chord symbols can be renamed (its `%%` options or rewriting the quoted symbols before rendering).
- Pedal reading: every staff system must stay reachable with Up/Down, a system one step, as a tab system is
  (`LayoutBudget`).
- A setting? Only if the spike shows a cost worth turning off; otherwise notation is simply how an `abc` block reads.

### 5. The PDF

- `PrintRows.verbatimRows` lays out an `abc` run as notation rows: one staff system per row that the layout never
  splits, rasterized by `PrintRenderer` from the same drawing, kept out of the selectable text layer as diagram labels
  are. Still gated on `options.showChords`.

### 6. Tests and docs

- `:abc:implementation` tests on the desktop target: a tune renders systems, a narrower width gives more systems, a
  broken tune returns an error. The layout tests (`LineRunsTest`, `PrintLayoutTest`) with a fake renderer only where the
  real one can't run.
- `CLAUDE.md` for the new module; the root `CLAUDE.md`'s architecture diagram; `ui/songView`'s and `ui/print`'s rules.
- A `:tools:screenshots` golden with an ABC song, if the renderer runs offscreen on the desktop JVM.

## Settled questions (2026-10-26)

- **Transposition and capo:** the staff follows them, as every name in the app is what sounds. The tune is rendered
  transposed (abc2svg's `%%transpose`) by the same semitones as the grid, the file's text untouched, and its chord
  symbols are drawn in the app's display notation (German, Latin, Nashville, Roman).
- **Chords on beats:** not now. The imported grid stays chords in order (`chordpro/abc`); the staff already shows each
  chord over its notes and `w:` syllables. Revisit only if users ask.
- **Playback:** out of scope, not planned. The module's interface shouldn't rule it out.

## Spike results (2026-10-27)

**Verdict: pass.** abc2svg runs headless in QuickJS on desktop, iOS (simulator), and Android (emulator), with output
byte-identical to node's, and its SVG draws correctly through a painter of our own. Throwaway code only; nothing kept.

- **abc2svg:** v1.23.6 (tagged 2026-09-18), built from its sources; npm (1.22.1, 2023) and the GitHub mirror are
  stale, the source is the chiselapp repository, with commits weekly. `abc2svg-1.js` is 327 KB minified (109 KB
  gzipped), the music font embedded; `chordnames-1.js` and `roman-1.js` load on demand through the host's
  `hostLoadModule`. One `<svg>` per staff system, as step 2 wants (a 32-bar reel: 5 systems at 800 px, 14 at 360 px,
  17 at 300 px).
- **License:** core and modules LGPL-3.0-or-later; the music font (`font/abc2svg.sfd`) SIL OFL, so glyph outlines
  extracted from it stay OFL. `cmdline.js`, `tosvg.js`, and `tohtml.js` are GPL-3.0+: never bundled or adapted, the
  host shim is our own over the core API (`new abc2svg.Abc(user)`, `tosvg`, `img_out`, `errmsg`, `read_file`). Ship the
  file unmodified and separate, with the LGPL, GPL, and OFL texts. Play, Microsoft Store, and desktop: low risk. App
  Store and Mac App Store: the known LGPL grey area, softened by the script being replaceable data in an open-source
  app; ask the author for an explicit store exception before shipping there.
- **Engine:** `io.github.dokar3:quickjs-kt:1.0.15` (Apache-2.0, QuickJS 2026-06-04), compiled and ran with Kotlin
  2.4.20 and AGP 9.4.1. Publishes every shipped target: Android arm64-v8a, armeabi-v7a, x86, x86_64; iosArm64,
  iosSimulatorArm64; desktop JNI for macOS arm64/x64, Linux x64/arm64, Windows x64 (no Windows arm64, which isn't
  shipped). API suspend-only, one long-lived engine.
- **Size added:** Android ~0.9 MB per ABI (~0.4 MB compressed; 16 KB page aligned); iOS +1.95 MB stripped (+0.72 MB
  gzipped); desktop jar 2.3 MB for five libraries (~0.9 MB per platform); plus the script on every platform.
- **Catch:** quickjs-kt's Apple static libraries are built `-O0` (Android `-Os`, JVM optimized), inflating iOS size and
  making it ~4× slower than it should be. Fix upstream (probably the Xcode CMake generator ignoring the build type in
  its `buildQuickJsNativeLibrary.kt`) or build our own; fallback if quickjs-kt is dropped: our own cinterop over
  QuickJS's C sources, ~1–2 days plus Apple CI.
- **Speed** (32-bar reel at 360 px, warm medians): desktop JVM 4–5 ms (first engine 147 ms, mostly loading the JNI
  library); iOS simulator 17–18 ms (cold 65 ms, with the `-O0` library); Android emulator 5 ms (cold 35 ms). The
  emulator runs near native on Apple silicon, so not a low-end proxy: estimated 20–40 ms per render and 150–300 ms cold
  on a low-end phone. Off the main thread with the text shown first, no setting is needed.
- **Transposition and notation:** `%%transpose N` moves key signature and chords (♯/♭ output). German
  (`%%chordnames Bb:B,B:H`) and Latin (`%%chordnames Do,Re,…`) rename after transposing; Roman via `%%roman 1`.
  Nashville (`%%nns`) draws a separate grid instead, so for it the app rewrites the quoted symbols itself, and then
  must transpose them itself too and drop `%%transpose` (abc2svg can't parse names it didn't write).
- **Drawing:** Skiko's `SVGDOM` (0.150.2, the same on desktop, iOS, and wasmJs) fails: it is created without a font
  manager, so no `<text>` draws, and it ignores `<style>`. abc2svg writes every music glyph as `<text>` in its `music`
  font (SMuFL code points, one `<text>` per system with x/y lists) and styles strokes by CSS class, so almost nothing
  appears. AndroidSVG (last release 2019) would be a second drawing path anyway. abc2svg's whole output is `svg`,
  `defs`, `g`, `path` (M m l h v c z), `use`, `text`, `tspan`, `translate`/`scale` transforms, and ~8 CSS rule shapes: a
  ~200-line prototype painter on plain canvas calls drew two tunes (ornaments, repeats and endings, key and metre
  changes, chords, lyrics, tempo) correctly in ~16 ms, and themes by passing `currentColor` as a parameter. Hence step
  3. `%%gchordfont … class=gch` tags chord symbols for their own colour.

### Follow-up: engines without native code (2026-10-27)

The first spike's engine, quickjs-kt, is one volunteer's native library on every platform, with an unoptimized Apple
build. The follow-up asks whether engines the platforms or Mozilla maintain can replace it.

- **iOS, JavaScriptCore: pass.** Kotlin/Native 2.4.20 ships it as `platform.JavaScriptCore` (both the Objective-C and
  the C API); the system framework is linked dynamically, so it adds only the bridge (~47 KB of Kotlin), not +1.95 MB.
  abc2svg's output is byte-identical to node's, transposition and German, Latin, and Roman names included. Simulator,
  with the JIT off as on a phone (verified: a hot loop ran 7× slower): ~23 ms cold to the first render, ~5 ms warm,
  4–5 ms re-renders, against quickjs-kt's 65 ms and 17–18 ms. The host callback (`hostLoadModule`) is a Kotlin lambda
  passed through `JSContext.setObject(_, forKeyedSubscript)`; a `JSVirtualMachine` locks itself, so one context on a
  single-threaded dispatcher. Works from iOS 7; the app targets 15.3. A bundled, never downloaded, script raises no
  store rule. Catches: abc2svg's font-class numbers (`fN`) keep growing across renders in one context, so the painter
  reads each SVG's own `<style>` and cache keys never include them; the lambda callback was checked only in a debug test
  binary (fallback: the C API's `JSObjectMakeFunctionWithCallback`, also proven); no JSValue kept in long-lived objects.
- **Android and desktop, Rhino 1.9.2: works, slower.** MPL-2.0 (its NOTICE adds BSD-3 code, credited too), Mozilla,
  near-daily commits; `org.mozilla:rhino` alone, 1.68 MB jar, Java 11 (2.0 will likely need 17 and break the embedding
  API: pin 1.9.x). Needs `VERSION_ES6` or later (abc2svg uses optional chaining); output byte-identical to node's on the
  desktop and the emulator. Android: interpreted only, minSdk 26 (the app's is 28); R8 needs rules (`-dontwarn
  java.beans.**`, `jdk.dynalink.**`, `javax.lang.model.**`, keep names and constructors of `org.mozilla.javascript.**`)
  or it crashes at start; 740 KB of dex for every ABI. Desktop: compiled mode needs `jdk.dynalink` in the jlink set,
  interpreted runs without it. A `Context` is bound to its thread; a compiled `Script` re-executes in 0.3–2.6 ms, so
  parsing is paid once. Release-build medians, 360 px: desktop 11–21 ms warm, ~200 ms cold; emulator 23–29 ms warm,
  ~0.7 s cold (debuggable builds ~3× slower), so an estimated 100–200 ms per render and 2–4 s cold on a low-end phone.
- **Choice (decided 2026-10-27):** quickjs-kt stays on Android and desktop: ~5× faster warm and ~20× faster cold than Rhino, about
  the same download (~0.4 MB compressed per ABI split against Rhino's ~0.35 MB), and on the JVM and Android it ships
  plain bytecode plus optimized JNI libraries, so the Kotlin/Native upgrade risk left with iOS. Rhino is the tested
  replacement should quickjs-kt stall.

Open, for step 2 onwards: the author's answer on the App Store exception; the App Store exception; a real low-end
Android phone and a real iPhone; abc2svg measures text with its own width tables, so lyrics and chords drawn in Inter
may differ slightly (check long lyric lines for overlaps); hiding the title and composer header (`%%writefields`,
untested); rarer constructs (multiple voices, tablature, boxes) may need a few more attributes or fall back to text.
