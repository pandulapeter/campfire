<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Plan: engraving an ABC tune's tablature staves

**Status:** not started. Written against the code on 2026-11-09 (9d5202509 plus the part picker). `notation/` is
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/notation/`.

Notation programs export a guitar or a bass as a staff and a tablature voice of the same name
(`V:5 tab stafflines=6 strings=E2,A2,D3,G3,B3,E4 nostems nm="Guitar"`). abc2svg draws such a voice as tablature only
with its string tablature module (`strtab-1.js`, already bundled), which nothing but a `%%strtab` line loads. Without
it the voice is engraved as a bass-clef staff of notes with string numbers over them: notation that looks right and
is not what the file says. With it, the app's SVG reader refuses the output, so the whole tune falls back to text. Now
that a guitarist or a bassist sees their part alone (`AbcParts.kt`), the tablature is the half of it they read.

## What abc2svg writes with `%%strtab`

Measured on `Ver oor oseane.abc` (a MuseScore export, 11 voices):

- A `<filter id="bgN">` (an `feFlood` of `flood-color="white"` and an `feComposite` of the source over it) as a direct
  child of the `<svg>`, once per rendering, in the first system that needs it. The reader throws on any element it
  does not know, so this alone sends the tune back to text.
- A CSS rule `.bgN{filter:url(#bgN)}`, and each fret as `<text class="bgN" x=… y=…>3</text>` inside a
  `<g class="f8">` (`font:700 7.0px sans-serif`). The filter paints a white box behind the digit, cutting the string
  line under it, as the app's own ChordPro tabs break their strings around each fret.
- The clef as `<use xlink:href="#tab6">` of a `<defs>` text `<text id="tab6" x="-4,-4,-4" y="-14.5,-4,5.5"
  style="font:bold 13px sans-serif">TAB</text>`. The reader ignores the `style` attribute, so the text inherits the
  root's music font, and "TAB" is looked up as music glyphs and refused.
- Strings as ordinary staff lines (`stafflines`), and bar lines as the usual `bW` strokes, so the bar count of
  `NotationPlayedLines.kt` should hold; to be confirmed by its test.

## Steps

1. **Load the module.** `AbcRendererImpl.headerOf` adds `%%strtab` when the tune has a voice in the tab clef
   (`(?:^|\[)V:` … ` tab` or `clef=tab`). Test in `AbcRendererImplTest`: such a voice renders frets as digits.
2. **Read the filter.** `NotationDrawingParser` skips a top-level `<filter>` (and one inside `<defs>`), recording only
   that its id is a background flood: an `feFlood` and an `feComposite` with `operator="over"`, nothing else. A filter
   of any other shape still gives no drawing.
3. **Mark the knocked-out texts.** `parseNotationCss` reads `filter:url(#id)`; a text whose class names a background
   flood becomes `NotationText(isKnockedOut = true)`, next to the existing `isBoxed`.
4. **Read an inline `style`.** A text's `style="font:…"` is parsed with the same font shorthand and beats its classes
   and its parents, as CSS has it. Only `font` (and the declarations `parseNotationCss` already knows) is read; any
   other inline declaration is ignored, as it is in a class.
5. **Draw the knockout in the page's colour.** `drawNotationSystem` takes a `background: Color` and fills the text's
   bounds (the filter's region: 10% wider each side, its height less 10% top and bottom) before the digit. Colours stay
   given only when drawing, so the theme cross-fade and paper warmth cost no layout. The PDF (`print/PrintRenderer.kt`)
   passes white. The cross-fade in `SongNotationBlock` draws both renderings' boxes, faded with their text.
6. **Tests.** A fixture cut from a real rendering into `NotationFixtures.kt` (one tab system with its filter and
   `<defs>`): it parses, the frets are knocked out, the clef reads "TAB" in bold sans-serif, and the bar count is the
   staff's. `NotationReading` must not read the frets as words.
7. **Docs.** `ui/notation/CLAUDE.md` gains tablature beside the other checked subsets, and the knockout rule;
   `libs/abc/implementation/CLAUDE.md` names `%%strtab` as loaded by the renderer.

## Out of scope

- `%%tablature` (`tblt-1.js`, the other tablature module) is the tune's own choice and already loads itself.

## Other follow-ups of the part picker

Found while building and testing it (`AbcParts.kt`, `NotationPartChoices.kt`); each is small and independent of the
steps above.

1. **Drum noteheads.** The Drums part of `Ver oor oseane.abc` (`V:11 perc`, its heads mapped by `I:percmap … x` and
   `normal`) drew stems with no noteheads in a browser rendering of abc2svg's output, with the whole score as well as
   the part alone, so the picker did not cause it. Check what the app draws for it; if the heads are missing there too,
   find which glyphs abc2svg's percussion module asks for and whether `AbcMusicGlyphs.kt` (generated by
   `tools/fonts/abc_music_glyphs.py`) lacks them, or whether the file maps its heads to nothing.
2. **The PDF's part.** Print engraves every part (`print/PrintNotations.kt` renders `tune.text`), since it has no
   picker. Decide whether it should engrave the player's part where the instrument matches one, as the screen does, and
   whether the export screen should offer the choice; whatever is decided goes into `ui/print` and the notation
   `CLAUDE.md`.
3. **Choir voices named like instruments.** Matching is by name, so a choir's "Bass" (or a shared "Tenor / Bass" staff)
   is the bass player's part and hides the picker. Consider not matching a part whose staff holds more than one voice,
   or a score whose parts are all voice names (Soprano, Alto, Tenor, Bass), with a test in `AbcPartsTest`.
4. **Inline voice fields mixing shown and hidden voices on one line** (`[V:1] … [V:2] …`) keep the whole line, so the
   hidden voice still keeps time there and can pad the shown one as described in the notation `CLAUDE.md`. Cutting such
   a line at its fields would close the gap; only worth doing if a real file needs it.
