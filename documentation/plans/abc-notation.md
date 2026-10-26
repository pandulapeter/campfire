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

**Status:** not started; the first step is a spike. Reading ABC landed on 2026-10-26 (8d9da5565..95d8540cb): an `abc`
block is a run of `ChordProLine.Verbatim` lines drawn as monospace text, and `.abc` files and pasted tunes import as
ChordPro songs with a chord grid, verses, and the tune kept in `{start_of_abc}` (`chordpro/abc`). This plan replaces the
monospace text of an `abc` run with engraved staff notation, drawn by an open-source renderer. `ui/` is
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

- `:abc:api`: one interface, roughly `suspend fun render(tune: String, widthPx: Int, options: AbcRenderOptions): AbcRendering`,
  where the rendering is a list of staff systems, each an SVG string with its height, so the layout can treat a system
  as one unsplittable row. Options carry the colours, the transposition in semitones, and the chord notation.
- `:abc:implementation`: abc2svg bundled as a resource; on Android, iOS, and desktop it runs in QuickJS (one engine
  kept alive, renders queued off the main thread); on wasmJs the same interface calls the browser's JavaScript through
  an `actual`. A tune that fails to render returns an error, never throws, so the caller falls back to monospace text.
- Koin as everywhere: `@Single` implementation, the module named in `app/di`.
- Credits in the same change, transitive ones included: `ui/dialogs/about/OpenSourceComponents.kt`, the licenses and
  NOTICE under `composeResources/files/licenses` of the source sets that ship them, and
  `documentation/THIRD_PARTY_SOFTWARE.md`.

### 3. Drawing SVG in Compose

- An `expect`/`actual` SVG painter: Skia's SVG DOM through Skiko on desktop, iOS, and the web; the Android library the
  spike picked. Drawn into a Compose canvas so the viewer and the PDF share it.

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
