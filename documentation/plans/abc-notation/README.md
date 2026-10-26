<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# ABC notation: implementation plans

First draft, 2026-10-26, from a read of the code and of the [ABC 2.1 standard](https://abcnotation.com/wiki/abc:standard:v2.1).
Nothing is implemented.

| Plan | What | Ships |
|---|---|---|
| [01-read-and-import.md](01-read-and-import.md) | `{start_of_abc}` blocks drawn as verbatim monospace text, hidden in lyrics-only mode; `.abc` files and pasted ABC imported, one song per tune, with a chord grid and lyrics derived from the tune | Any release, on its own; no dependency, no network, no file format change |
| A follow-up (below, not yet planned) | Staff notation drawn by [abc2svg](https://chiselapp.com/user/moinejf/repository/abc2svg), an open-source ABC renderer, run in QuickJS | After a spike settles the license, the platforms, and the drawing |

## Decisions taken in these plans

- **The library stays ChordPro.** An `.abc` file is an import format, like OnSong; a tune is kept whole inside
  `{start_of_abc}` in its `.cho`, which is what official ChordPro reads too. Sync, sharing, and file names don't change.
- **The ABC block is the source, the grid a one-off translation.** The import derives a `{start_of_grid}` section and a
  lyrics section once; editing the ABC later doesn't regenerate them.
- **We don't write an engraver.** Plan 01 reads only what a chord grid needs (bars and chord symbols) and needs no
  dependency. Staff notation comes from an open-source renderer we rely on (the follow-up); if none passes the spike,
  ABC stays verbatim text.

Considered and dropped:

- **Keeping `.abc` files in the library as their own type:** every importer, sync rule, export, and the
  file-name-is-identity rule would need a second case.
- **Hiding ABC blocks entirely:** the file names them, and a user who wrote one should see it is there.
- **An engraver of our own:** months of spacing, beaming, and line-breaking work to maintain for a narrow audience, when
  a mature renderer exists.

## Follow-up: staff notation through abc2svg (not yet planned)

Worth planning once plan 01 has landed and users ask for notation; the audience (folk melody players) is narrower than
the app's. Notes for whoever plans it:

### The approach

- **abc2svg** (Jean-François Moine, the author of abcm2ps) turns ABC into SVG in plain JavaScript. Unlike abcjs it
  needs no DOM to measure its layout, and it already ships a QuickJS runner (`abcqjs`), so it runs headless.
- **Android, iOS, and desktop:** abc2svg in QuickJS through a Kotlin Multiplatform binding, in its own module
  (`:abc:api` / `:abc:implementation`, depending on nothing of the app's, like `:metronome` and `:tuner`).
  Candidates: [quickjs-kt](https://github.com/dokar3/quickjs-kt) (Android, JVM, iOS) and
  [kuickjs](https://klibs.io/project/peacefulprogram/kuickjs) (QuickJS-ng, early).
- **Web:** no binding has a wasmJs target; the browser runs abc2svg itself, behind the same `expect`/`actual`
  interface (`wasmJsMain`).
- **Drawing:** the SVG string drawn into a Compose canvas: Skia's SVG DOM through Skiko on desktop, iOS, and the web,
  and a library such as AndroidSVG on Android. Drawn in Compose, the PDF needs no work of its own: `PrintRenderer`
  rasterizes the same drawing, a staff system one row the layout never splits, like a tab system. Keep notation out of
  the PDF's selectable text layer, as diagram labels are.
- Plan 01's `ChordProLine.Verbatim` is the hook: an `abc` run draws as notation instead of monospace text, `ly` stays
  monospace, and `svg` blocks could be drawn by the same SVG renderer.

### The spike, before any plan

1. **License.** Current abc2svg sources say LGPL (older ones GPL-2). Confirm the version we'd ship, and that an
   unmodified script bundled as a resource in an MPL-2.0 app is fine on every store, the App Store included.
2. **Bindings.** Which QuickJS binding is maintained, its license (QuickJS itself is MIT), its size per ABI, and
   whether it builds with our Kotlin version and on every target we ship (Windows included).
3. **Drawing.** Whether abc2svg's music glyphs (a font it references, or paths it emits) render in Skia's SVG DOM and
   AndroidSVG, and how to recolour the output for the theme and dark mode (`%%fgcolor`, or rewriting the SVG).
4. **Speed and layout.** The time to render a typical tune on the smallest supported phone, and to re-render at a new
   width: a resized window must reflow systems with an animation (root `CLAUDE.md`), and every line must stay
   reachable with the pedal.
5. **Credits.** Every shipped piece (abc2svg, the binding, QuickJS, the Android SVG library), transitive ones included,
   in `OpenSourceComponents.kt`, the licenses folder, and `THIRD_PARTY_SOFTWARE.md`.

### Questions for the plan

- **Transposition and capo:** plan 01 leaves the ABC text as written while the grid moves. abc2svg can transpose
  (`%%transpose`), so the staff can show what sounds, as chord names do; its chord symbols should then use the app's
  display notation, or be hidden in favour of the grid.
- **Chords over lyrics:** abc2svg's parsed tune (note times, `w:` syllables) might place chords over the `w:` lyrics,
  syllable by syllable, and put grid cells on beats, which plan 01 leaves to order only.
- **Not worth it:** playback (the app has no synthesizer, only the metronome's clicks and the tuner's tones) and ABC
  export.

### Considered and dropped

- **abcjs** (MIT): needs a DOM to measure and draw, so it runs only in the web build, or in a WebView elsewhere.
- **Verovio** (LGPL-3, C++): reads ABC, but is built around other score formats, and would need native builds for
  every platform.
- **abcm2ps** (GPL, C): PostScript and SVG for desktop use, under a license the stores don't take.
- **abc4j** (Java, Swing): unmaintained, and no Swing in a shared Compose UI.
