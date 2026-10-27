<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Move an `abc` block's tunes with the editor's transposition by writing their own `%%transpose`, and name their chords with it

**Challenged:** amended — the written value is the plain sum (not `rem 12`, which jumped the staff by up to eleven
semitones at the wrap and broke up-then-down for a tune that had its own line; abc2svg's real limit, ±36, now bounds
it); tune lines found with `AbcLines.field`; the caret pairs the lines that are not transpose lines rather than
walking greedily (an unreadable `%%transpose M2` right after `X:` would have shifted every line below); the limits of
step 3 (several tunes moved differently in one block, a line before `X:`) are stated; the lane is N, after plan 08.

**Kind:** bug · **Severity:** medium · **Platforms:** all
**Lane:** N, last (after 08). The writing is `:chordpro`, naming the drawn chords `:presentation`'s `ui/notation`
(`renderNotationDrawing`, which plans 05–08 leave in place). Land both in one commit, or the chordpro part first:
without step 3 the staff is right but its chord names fall back to abc2svg's (still the right chords, in sharps and
standard letters).
**Files:** new `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/abc/AbcTranspositions.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/chords/ChordProTransposer.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/syntax/ChordProOffsetMapping.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/notation/SongNotations.kt`
(`renderNotationDrawing`), new `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/abc/AbcTranspositionsTest.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/chords/ChordProTransposerTest.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/notation/NotationChordNamesTest.kt`,
`chordpro/CLAUDE.md`, `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/chords/CLAUDE.md`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/abc/CLAUDE.md`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/notation/CLAUDE.md`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/CLAUDE.md`

## Problem

The editor's transposition stepper rewrites the file (`SongRenderer.transposeText` → `ChordProTransposer.transposeText`
→ `ChordProChordRewriter.rewriteChordNamesInText`), and skips every line of a delegated environment:

```kotlin
// ChordProChordRewriter.rewriteChordNamesInText
environment == GRID -> lines[index] = rewriteGridLine(rawLine, line.trimmed, rename)
environment in ChordProEnvironments.delegateEnvironments -> Unit
```

which `ChordProTransposerTest` pins:

```kotlin
@Test
fun `an abc block is left alone by the transposition`() {
    val text = "{start_of_abc}\n[CEG]2\n{end_of_abc}\n[C]la"

    assertEquals("{start_of_abc}\n[CEG]2\n{end_of_abc}\n[D]la", ChordProTransposer.transposeText(text, 2))
```

So after transposing a song up two semitones and saving, its lyrics' chords and `{key}` say E where the tune drawn
beside them (`AbcRendererImpl` puts `%%transpose <reader's transposition>` in front of the untouched tune) is still
in D, its key signature, notes, and chord symbols included. While a block was shown as text this was a harmless
leftover; now it is music drawn next to chords that contradict it, for anyone who transposes in the editor.

## What abc2svg does with a tune's own `%%transpose` (probed in node, through `campfire-abc-host.js`)

With the renderer's header line `%%transpose h` in front and the tune `X:1 … K:D "D"d2fd …`:

| header `h` | tune's own line (after `X:`) | chords drawn |
|---|---|---|
| 0 | none | D G A |
| 2 | none | E A B |
| 0 | `%%transpose 2` | E A B |
| 2 | `%%transpose 2` | F♯ B C♯ (adds) |
| 3 | `%%transpose -1` | E A B (adds) |
| 0 | `%%transpose 2` twice | F♯ (each line adds) |
| 0 | `I:transpose 2` | E (the same) |
| 0 | `%%transpose 2` after `K:` | E (applies from there on) |
| 2 | `%%transpose 2` **before** `X:` | E (file level: replaces the header's) |

So a line inside the tune (after its `X:`) **adds** to the reader's transposition, and does not leak into the next tune
of the block (a second `X:` tune without one was drawn in D). abc2svg also reads `+2`, `2#` (sharps), and `-2b`
(flats). A block with no `X:` line renders nothing at all (`CDEF|` alone, or `K:C\nCDEF|`, fail), so it has nothing
to move.

Re-probed by the challenge (`abc2svg-1.js`'s `case"transpose"` and `get_transp`): before `X:` (parse state 0) the
line sets `cfmt.transp=0` and then adds, so it replaces the header; in the tune header (state 1) it adds; after `K:`
it sets the voice's shift. `get_transp` rejects a value outside −36…36 (`%%transpose 37` draws the tune as written,
the line ignored) and anything not starting with a digit or a sign (`%%transpose M2` is ignored). Header 2, a line
before `X:` of 3, and one after `X:` of 1 draw `D` as F♯ (3 + 1: the reader's 2 is lost to the line before `X:`, as
it is today). Header 2 with tune 1 carrying `%%transpose 1` and tune 2 none draws F, then E.

## Fix

**Decision for the user — how the tune follows the transposition.** Options:

- **A (recommended): write the move into the tune** as its own `%%transpose`, which abc2svg adds to the reader's
  transposition. The ABC text, notes and chord symbols, stays as written (as `{transpose}` already works for
  ChordPro); small, exact, and undone by stepping back.
- B: rewrite the ABC itself: `K:` (modes included), every note against the new key signature (accidentals, octave
  marks, grace notes, chords in `[]`, inline `[K:]` fields, several voices), and the chord symbols. abc2svg has no
  serializer, so this is a new ABC writer in `:chordpro`, many times the size of A, with every engraving edge case
  (accidentals carried through a bar, ties across a key change) a way to corrupt the user's tune. Not recommended.
- C: leave it, and say so in the editor. Not recommended: the contradiction is what the reader sees.

The rest describes A.

1. **`chordpro/abc/AbcTranspositions.kt`** (new, `public object`, KDoc on the public member as the `api` rules ask):
   - `public fun of(tune: String): Int`: the semitones the first tune of [tune] moves itself by: the sum of the
     `%%transpose <n>` and `I:transpose <n>` lines between its first `X:` line and its first `K:` line, `<n>` read as
     abc2svg reads it (`[+-]?\d+` within −36…36, an optional `#` or `b` after it, an optional `%` comment; anything
     else counts 0, as abc2svg ignores it); 0 where there is no `X:` (abc2svg renders none). Find the fields with
     `AbcLines.field` (`X`, `K`, `I`) as `AbcTunes` does, not `startsWith`. Lines before `X:` are not counted: they
     replace the renderer's header rather than adding to it (a tune with one ignores the reader's transposition today;
     out of scope here).
   - `internal fun moved(text: String, semitones: Int): String`: the document with each `abc` block's tunes moved:
     walk the lines with `ChordProLineScanner` (environment `abc`), and for each `X:` line inside a block find that
     tune's header (up to its first `K:`, the next `X:`, or the block's end). If the header has a transpose line whose
     value reads as above, rewrite its number to `old + semitones`, keeping its spelling (`%%` or `I:`), a `+` sign
     where it had one, accidental suffix, and comment; otherwise insert `%%transpose <semitones>` on the line after
     `X:`. A result of 0 **removes the line** when it holds nothing else (no suffix, no comment), so stepping up and
     back down returns the file byte for byte and leaves the editor with no unsaved changes.
     **The value is the plain sum**, the semitones the user stepped, so every step is undone by the opposite one
     wherever the walk started (a tune imported with `%%transpose 5`, stepped up 7 and down 7, is `5` again) and the
     staff moves one semitone per step, never more. Twelve steps up draw the tune an octave up, which is what was asked
     of it; twelve back return it. (The draft's `rem 12` made the staff of a tune at +11 drop eleven semitones on the
     next step up, and turned that `5` into `-7`, an octave below where it started.) Only a sum that would leave
     −36…36, which abc2svg ignores, is brought back by an octave toward 0 (37 steps one way; never met in practice).
     Lines are split and joined with `ChordProLines.splitLines` / `joinLines(lines, text)` so the file's separators and
     trailing break are kept.
2. **`ChordProTransposer.transposeText`**: apply `AbcTranspositions.moved(…, semitones)` to the result of both
   `rewriteText` calls (only when `semitones != 0`; a respelling with 0 semitones moves nothing). The model's
   `transpose` stays as it is (it never touches `Verbatim` lines; the reader's transposition reaches the tune through
   the renderer's header). Update the KDoc: the text transposition also moves each tune of an `abc` block, through its
   own `%%transpose`.
3. **Naming the drawn chords** (`presentation/.../ui/notation/SongNotations.kt`, `renderNotationDrawing`): abc2svg now
   moves the tune by `chords.semitones + AbcTranspositions.of(tune)`, but the renaming matches drawn chords against the
   written symbols moved by `chords.semitones` alone (`isTransposedChord`), so it would match nothing and keep
   abc2svg's names. Keep rendering with `transposition = chords.semitones` (the tune adds its own), and name with
   `val named = chords.movedBy(AbcTranspositions.of(tune))`: `abcChordNames(tune, symbols, named)` and
   `withChordNames(symbols, …, named.semitones)`. `abcChordSymbols` already skips `%` lines and `I:` fields. This also
   fixes tunes imported with a `%%transpose` of their own (`AbcTunes.split` copies a file header's `%%transpose` right
   after each tune's `X:`, so it adds), whose chords today keep abc2svg's names. Proven in node: header 2 plus a tune
   line of 1 draws `"D"` and `"G"` as F and B♭, which `isTransposedChord` matches at 3 and not at 2.
   Limits, kept (each leaves abc2svg's names, which are still the right chords): a block of several tunes moved by
   different amounts is named by its first tune's (the stepper adds the same amount to every tune, so it never makes
   them differ; only a hand-written block can); a line before `X:` replaces the reader's transposition, which the
   names do not follow.
4. **The caret** (`ChordProOffsetMapping.transposedOffset`): it gives up and keeps the raw offset when the line count
   changed (`if (beforeStarts.size != afterStarts.size) return clamped.coerceAtMost(after.length)`), which an inserted
   or removed transpose line now causes, so a caret below a tune would land a line off. When the counts differ, pair
   the lines by leaving the transpose lines (`AbcTranspositions`' matcher, `internal`, shared) out on both sides: the
   other lines are the same in number and order (a transposition adds or removes nothing else), so the k-th of
   `before` pairs with the k-th of `after`, and a caret on one maps as today (`transposedColumn`). A caret on a
   transpose line pairs with the transpose line at the same place among those between the same two partnered lines
   when both sides have as many there (a rewritten `3` → `5`); otherwise (the line it stood on was removed) it goes
   to the start of the partner of the next other line. If the other lines still differ in number, keep the early
   return. Do not walk the two sides greedily: a header with an unreadable `%%transpose M2` right after `X:` gets a
   new line inserted above it, and a walk pairing transpose line with transpose line would then shift every line
   below by one. Update `transposedOffset`'s KDoc ("A transposition keeps every line" is no longer true of `abc`
   tunes).
5. Optional (sub-decision, recommended yes): the stepper is enabled by `summary.hasChords || movable key`; a song whose
   only music is an `abc` block without a chord grid stays disabled. Enabling it there too (the summary answering
   whether the text has an `abc` block with an `X:` line) makes such a song transposable; skip if it grows the plan.

**CLAUDE.md**: `chordpro/CLAUDE.md`'s "Delegated environments … are kept verbatim" gets "except that a transposition
writes each `abc` tune's own `%%transpose`"; `chords/CLAUDE.md`'s `ChordProTransposer` bullet says the same next to
"leaves `{transpose}` alone"; `abc/CLAUDE.md` names `AbcTranspositions` (a tune's own `%%transpose` lines add to the
reader's transposition; lines before `X:` replace it and are not read); `ui/notation/CLAUDE.md`'s "Chord names are the
page's" adds the tune's own `%%transpose` to the semitones the names are moved by; the editor's "Transposition, caret,
and undo" says the stepper also moves `abc` tunes, through a `%%transpose` line it writes, updates, or removes.

Plans 05–07 of this sweep also edit `SongNotations.kt`; step 3 touches only `renderNotationDrawing` and merges with
them by hand if needed.

## Tests

- `AbcTranspositionsTest` (chordpro): `of` sums the header's `%%transpose` and `I:transpose` lines, ignores lines
  before `X:` and after `K:`, reads `+2`, `2#`, `-2b`, and a trailing comment, and is 0 without `X:`.
- `ChordProTransposerTest`: replace `` `an abc block is left alone by the transposition` `` with
  `` `an abc tune is moved by a transpose line of its own` `` (`"{start_of_abc}\nX:1\nK:C\n[CEG]2\n{end_of_abc}\n[C]la"`
  by 2 gives `X:1\n%%transpose 2\nK:C\n[CEG]2` and `[D]la`, the notes untouched), keeping its second assertion (the
  model's `transpose` leaves `Verbatim` lines alone). Add: an existing `%%transpose 3` becomes 5; one brought to 0 is
  removed and up-then-down is the original text; `I:transpose 1 % up` keeps its spelling and comment; two tunes in one
  block each get their own line; a block without `X:` is unchanged; a respelling with 0 semitones changes no block;
  CRLF text keeps CRLF; `` `a tune stepped across an octave comes back as it was` `` (`%%transpose 5`, seven steps
  of +1 then seven of −1, one call each, is the original text, and the sixth step up reads `11`, the seventh `12`);
  `%%transpose 36` stepped up once reads `25`.
- `ChordProTransposerTest`, through its `transposed(before, semitones, offset)` helper: a caret on a lyric line below a
  tune keeps its place when a transpose line is inserted, and when one is removed; a caret on the line under
  `%%transpose M2` (inserted above it) stays on that line; a caret inside a rewritten `%%transpose 3` stays on it.
- `NotationChordNamesTest`: with a spelling of 2 semitones and a tune carrying `%%transpose 1`, `withChordNames` over
  a drawing whose chord reads `D♯` (3 up from `C`, as abc2svg may spell it) renames it to the page's name for `C` moved by 3; with only 2 it
  keeps the drawn name (pins why the tune's own value is added). Test the pure pieces (`abcChordNames`,
  `withChordNames`, `AbcTranspositions.of`), as the renderer is not available in `:presentation`'s tests.

Run `./gradlew :chordpro:desktopTest :presentation:desktopTest`.
