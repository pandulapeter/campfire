<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Start an empty Sheet music block with the header a tune needs to be drawn (`X:1`, the song's meter, `L:1/8`, and its key), the caret below it

**Kind:** usability · **Severity:** low · **Platforms:** all
**Challenged:** amended — the key is read as `:chordpro`'s own key reading does (`SongParts.keyOf`: a root letter in
either case, minor where the word after it starts with an `m` other than `ma` or `mix`), since the summary leaves
`a-moll`, `c-moll`, and `A minor` as written and the plan's rule gave them `K:C` / `K:A` (abc2svg reads a raw `K:A-moll`
as A major); modes are written as abc2svg's three-letter words; the meter comes from `ChordProTime.parse`, keeping `C`
and `C|` (every value it accepts was probed: all draw); more test cases; the capo limitation is stated.
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/toolbar/EditorInsertion.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/toolbar/EditorToolbar.kt`,
new `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/toolbar/SheetMusicHeader.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/LoadedSongEditor.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/toolbar/EditorInsertionWritesTest.kt`,
new `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/toolbar/SheetMusicHeaderTest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/toolbar/CLAUDE.md`

## Problem

The editor's Sheet music button writes an empty block, as any section does:

```kotlin
// EditorToolbar.kt, lineKindInsertions
EditorInsertion.section(stringResource(Res.string.song_editor_section_abc), name = "abc"),
```

```kotlin
// EditorInsertion.kt
fun section(label: String, name: String) = EditorInsertion(
    label = label,
    prefix = "{start_of_$name}\n",
    suffix = "\n{end_of_$name}",
    isOwnLine = true,
)
```

Probed in node through `campfire-abc-host.js` with the renderer's header in front: abc2svg draws nothing for a tune
without an `X:` line. `CDEF|` alone and `K:C\nCDEF|` both fail (no system, no message), so a melody typed into the
empty block stays monospace text forever, with nothing saying why; `X:1\nK:C\nCDEF|` draws, and so does `X:1\nCDEF|`
(key C by default). The Tab button writes a named staff to start from (`emptyTabStaffFor`), and Chord shape a
placeholder C, but Sheet music gives someone who does not know ABC nothing.

Also probed: `K:` must be in standard letters. `K:H` (a German reader's B) **throws** inside abc2svg ("Cannot read
properties of undefined"), so the tune fails to draw (the `H` is taken for a bagpipe key: "Unknown bagpipe-like key" is the branch it
crashes in); `K:Hm` draws, but through the same branch, not as B minor. `K:Bb`, `K:F#m`, `K:Ebm`,
`K:C#`, `K:Cb`, `K:D dorian`, and `K:Dm (capo 2)` all draw, and so do `M:3/4`, `M:6/8`, `M:C`, `M:C|`, and `M:7/8`.
A header with no notes under it draws one empty 28-unit-high system, so the preview shows a blank space until the first
note is typed.

## Fix

**Decision for the user — what an empty Sheet music block starts with.** Recommended:

```
{start_of_abc}
X:1
M:<the song's {time}, only when it has one>
L:1/8
K:<the song's key, else C>
|
{end_of_abc}
```

with `|` the caret, on an empty line below `K:`. `X:1` is the one field abc2svg needs; `K:` and `L:1/8` (the most
common unit, and the standard's default under a 4/4 meter, written so the field is there to change) show where the
key and the note length go. No `T:`: a tune titled as the song leaves its title to the page anyway (`AbcTitles.kt`),
and one titled otherwise would be drawn. Alternatives: only `X:1` (draws, teaches nothing); a commented example
melody (more to delete than to keep).

1. **`SheetMusicHeader.kt`** (new, pure, `internal`): `fun sheetMusicHeader(key: String?, time: String?): List<String>`
   building the lines above from the song's key and time **in standard notation** (the editor's `summary.metadata.key`,
   which the summary reads into standard whatever the field's notation, as the control row's comment in
   `LoadedSongEditor` says: a German `H` is `B`, a Latin `Lam` is `Am`, a bare `a` is `Am`; but a key spelled out in
   words keeps its words, and one starting with a lowercase note stays as written, so `a-moll`, `c-moll`, `A minor`,
   `G-dur`, and `Sol mayor` → `G mayor` all reach this function). Read it as `:chordpro`'s one reading of a `{key}`
   does (`SongParts.keyOf`, private there), so the tune starts in the key the page names:
   - the root: the first character, `A`–`G` in either case, uppercased, then `#` or `b`; anything else (or no key)
     gives `K:C`;
   - the word after it: the rest with leading whitespace and dashes dropped, lowercased
     (`ChordProChordRewriter.keyWordOf` does this but is internal to `:chordpro`; three lines here);
   - a word starting with `dor`, `phr`, `lyd`, `mix`, `aeo`, `loc`, or `ion` gives `K:<root> <those three letters>`
     (abc2svg draws `K:D dor` with no sharps and `K:D mix` with one, probed); otherwise one starting with `m` but not
     `ma` gives `K:<root>m` (`m`, `m7`, `m (capo 2)`, `minor`, `moll`, `menor`); anything else `K:<root>`
     (`maj7`, `major`, `dur`, `mayor`, `sus4`, a parenthesis).
   Never write the key's text itself: abc2svg reads `K:A-moll` as A major and `K:Fis` as F major (probed), and throws
   on `K:H`. A Central European note spelled with a suffix (`Fis`, `Esz-dúr`) reads as its letter, as it does
   everywhere else in the app (`SongParts.keyOf`), which is one key signature wrong but never a broken tune. The meter:
   `ChordProTime.parse(time)` (public in `:chordpro`, the reading `{time}` itself gets), and for a value it accepts
   `M:C` for `C`, `M:C|` for `C|` and `¢`, else `M:<beats>/<unit>` (so `6 / 8` is written `M:6/8`); nothing for a time
   it does not accept. Every value it accepts draws (all 80 fractions of 1–16 beats over 1, 2, 4, 8, and 16, and `C`
   and `C|`, probed in node through `campfire-abc-host.js`), and so does every root with `#`, `b`, or none, major,
   minor, or in each mode. A KDoc says why the letters are standard and the key rebuilt (abc2svg throws on `K:H` and
   misreads a key written in words). The key is the file's `{key}`, which names the chords' key: a capo chart whose
   `{key}` names the sounding key (`{key: G}{capo: 5}` over D shapes) starts a tune a capo's distance off, which the
   writer corrects with the rest of the tune; reading the key through `SongKeys` would need the parsed song, which
   the toolbar does not have.
2. **`EditorInsertion.sheetMusic(label: String, header: List<String>)`**, next to `tab`: `section(label, name = "abc")`
   with `emptyPrefix = prefix + header.joinToString("\n") + "\n"`, so the caret lands on the empty line below `K:`.
   With a selection it still only wraps it (most likely a pasted tune, which carries its own `X:`).
3. **`EditorToolbar`** takes `sheetMusicHeader: List<String>` (documented with `@param` beside `tabStaff`), passed to
   `lineKindInsertions`, which builds the button with `EditorInsertion.sheetMusic`. `LoadedSongEditor` passes
   `remember(summary.metadata.key, summary.metadata.time) { sheetMusicHeader(summary.metadata.key?.takeIf(ChordProTransposer::isKey), summary.metadata.time) }`.

**Sub-decision — the keyboard.** The brief compared this with Tab, but Tab does not bring the keyboard up either: per the
toolbar's `CLAUDE.md`, "sections, tabs, Repeat chorus, wrapping a selection, and the formatting buttons never do", and
`leavesValueToType` is true only inside braces or brackets or for a new selection. Recommended: keep it so (the
block is a section; what follows it is as often another button), which needs no change. The alternative, raising it,
means a case in `leavesValueToType` for a caret on the empty line of a new `abc` block, against that rule.

**`toolbar/CLAUDE.md`**: in "Shortcut rules", after the Tab bullet, one line: with nothing selected Sheet music writes
`X:1`, the song's meter, `L:1/8`, and its key rebuilt as root, minor, or mode in standard letters (abc2svg draws no
tune without `X:`, fails on `K:H`, and reads `K:A-moll` as A major), the caret below; with a selection it only wraps
it.

No string changes.

## Tests

- `SheetMusicHeaderTest`: no key and no time give `X:1`, `L:1/8`, `K:C`; `Bb` gives `K:Bb`; `F#m` gives `K:F#m`;
  `a-moll`, `A minor`, and `Am7` give `K:Am`; `Dm (capo 2)` gives `K:Dm`; `D dorian` gives `K:D dor` and
  `D Mixolydian` `K:D mix`; `Cmaj7`, `G-dur`, and `G mayor` give `K:C` / `K:G`; `S` (no key) gives `K:C`; a time of
  `6/8` adds `M:6/8` after `X:1`, `6 / 8` the same, `C` adds `M:C`, `¢` adds `M:C|`; a time `ChordProTime.parse`
  refuses (`5/3`, `waltz`) adds nothing.
- `EditorInsertionWritesTest`: `` `sheet music with nothing selected starts a tune header with the caret below it` ``
  (the text and the collapsed selection after `K:…\n`), and `` `sheet music around a selection only wraps it` ``.
