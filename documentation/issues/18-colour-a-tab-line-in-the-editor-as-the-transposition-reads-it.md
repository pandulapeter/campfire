<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Colour a tab line's chords in the editor by the rule the transposition renames them by, not the viewer's rule for converted text

**Kind:** bug  ·  **Severity:** medium (raised: a transposed song draws untransposed chord rows)  ·  **Platforms:** all
**Challenged:** amended — following the transposer alone would uncolour real chord rows (`G / / / | D`, `G D 2x`,
`(G D)`, `G ||: D :||`) that the viewer draws as chords but the transposition leaves untransposed (probed: a song moved
up a tone still shows G and D over them); the transposer's markers now widen to the viewer's, steps of the key aside,
so the viewer, transposition, chord list, and editor agree. Tests added for the transposer.
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/edit/ChordProHighlighter.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/tabs/ChordProTabTransposer.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/edit/CLAUDE.md`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/tabs/CLAUDE.md`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/edit/ChordProHighlighterTest.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/tabs/ChordProTabTransposerTest.kt`; with option 2 also
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/tabs/ChordProTabWrapper.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/tabs/ChordProTabWrapperTest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/SongTabBlock.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/TabStaffLines.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintTabEngraver.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintRows.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/CLAUDE.md`

## Problem

The editor colours a tab's beat-count line as chords. `ChordProHighlighter.tokenize` reads a tab line with the viewer's
rule:

```kotlin
scanned.environment == TAB && ChordProTabWrapper.isChordLine(line) -> tokens += tabChordTokens(line, lineStart)
```

```kotlin
private fun tabChordTokens(line: String, lineStart: Int): List<Token> = ChordProTokens.words(line)
    .filter { ChordProTabWrapper.isChordWord(it.value) }
```

`isChordWord` is `ChordProChordNames.isDisplayedChordName`, which also takes a step of the key
(`ChordProNashville.isDegree`), and whose KDoc says it is only for "what reads a song that has already been converted,
which is the viewer cutting a tab into rows". The editor's field is never in a numbering (`ChordProNotation`: "a text is
never converted into them, nor read as written in them"), so a count line inside `{start_of_tab}` such as
`1 & 2 & 3 & 4 &` or `1 2 3 4 5 6` is four or six CHORD tokens in the editor, while the transposition leaves it alone:
`ChordProTabTransposer.chordWords` accepts only `isChordName` / `latinExpanded` words and its markers (bar lines, `.`,
`%`, `%%`, `x2`, dashes, `N.C.`), so `1` makes the line prose. The parser also counts a tab's chords by the
transposer's rule (`ChordProParser`: `TAB -> ChordProTabTransposer.chordNames(listOf(rawLine))`), so the song's
Chords section, diagrams, and `hasChords` already disagree with the editor. The reverse also happens: `G N.C. D` is
renamed by a transposition but not coloured, since `N.C.` fails `isChordLine`.

`chordpro/edit/CLAUDE.md` says the editor colours "the names of a tab line the viewer draws as chords
(`ChordProTabWrapper.isChordLine`)", on purpose. The viewer draws the count line in chord style too (in every
notation), which is the same small defect on the screen and in the PDF.

## Fix

**Decision for the user** (the CLAUDE.md rule is deliberate, so either option changes a stated rule):

1. **(Recommended) The editor follows the transposition.** Make `ChordProTabTransposer.chordWords` `internal` (same
   module), and in the highlighter colour a tab line that is not a staff line by it:
   `scanned.environment == TAB && !ChordProTokens.isStaffLine(line) && !ChordProDirectives.hasBrackets(line)` and
   `ChordProTabTransposer.chordWords(line)?.takeIf { it.isNotEmpty() }` → one CHORD token per word's range; delete
   `tabChordTokens`. A line with brackets keeps the bracket branch below, as now. What is coloured is then exactly what
   a transposition renames and what the song's chord list counts, the rule grids already follow ("each as its reader
   decides": the grid's cells by the grid parser). Rewrite the CLAUDE.md sentence to "the names of a tab line the
   transposition renames (`ChordProTabTransposer.chordWords`)". Side effect: `G N.C. D` gets G and D coloured.

   **And the transposition reads every chord row the viewer draws as one** (the challenge's addition, not optional).
   Probed at HEAD (`ChordProTransposer.transposeText` by +2 inside `{start_of_tab}`), these rows are not staff lines,
   `isChordLine` is true for each, so the viewer and the PDF draw them in chord style, yet `chordWords` calls them prose
   and the transposition leaves them as written: `(G D)`, `(G  D) x2`, `G ||: D :||`, `G / / / | D`, `G , D`,
   `G ~ D`, `G D 2x`, `G 4x`. A transposed song therefore shows G and D as chords over a tab now in A, and the song's
   Chords section (the parser counts a tab by `chordNames`) leaves them out. Followed alone, the editor would stop
   colouring these real rows. So widen `chordWords`' markers to the viewer's (`ChordProTabWrapper.isChordLine`), less
   its steps of the key:
   - a word with no letter or digit (`||:`, `:||`, `/`, `,`, `~`, `&`; `isBar`, `.`, `%`, `%%`, and dashes are such
     words already, keep `isBar` for the voltas `1.`/`[2`);
   - a repeat count before or after its digits, in parentheses or not: `\(?[xX×]?\d+[xX×]?\)?` (the viewer's, which
     also takes a bare number; harmless here, since a line of markers alone has no chord word, renames nothing, and is
     coloured nothing, which is what `1 & 2 & 3 & 4 &` should get);
   - a chord name opening or closing a group across words (`(G`, `D)`): read it without its unmatched parenthesis, as
     `ChordProTabWrapper.isChordWord` does, and narrow the word's range to the name so that the rename and the CHORD
     token leave the parenthesis in place. `(G)` is already accepted whole (the rename unwraps it).
   `N.C.` and its abbreviations stay markers. What still differs from the viewer is only a step of the key (`1`, `IV`,
   `bVII`) and the viewer's lone Roman `I`: the viewer reads converted text, which may be in a numbering; the
   transposer, the parser, and the editor read the file, which never is. Update the `rewriteChordLine` KDoc (what a
   marker is) and add to `tabs/CLAUDE.md`: "A line that is not a staff line is a chord row when every word on it is a
   chord name or a marker, the rule the viewer draws rows by, steps of the key aside; the transposition, the chord list,
   and the editor's colours all read it so."
2. **Also fix the viewer**, separately and only outside numberings: the viewer reads converted text, where a converted
   `G C D` row is `1 4 5` under Nashville, so "refuse a line of bare Arabic steps" applied everywhere (the finding's
   first idea) would stop drawing real Nashville chord rows as chords. Instead `ChordProTabWrapper.isChordLine(line,
   isNumbering: Boolean)`: outside a numbering, a word that is only an Arabic step (`isDegree` but not `isChordName` and
   not Latin) is not a chord name. `wrapPreformatted`, `wrapLines`, and the four call sites (`SongTabBlock`,
   `TabStaffLines`, `PrintTabEngraver`, `PrintRows`) pass the reader's notation through; under Nashville a count line
   stays chord-styled, an ambiguity only the source line could settle. Worth it only if the user minds the viewer's
   count lines; recommended as a follow-up at most, not part of this fix.

Write option 1; option 2 only if the user asks for it.

## Tests

`ChordProHighlighterTest`:

- `{start_of_tab}\n1 & 2 & 3 & 4 &\n1 2 3 4 5 6\n{end_of_tab}` has no CHORD token.
- `{start_of_tab}\nG  N.C.  D | x2\ne|--0--1--|\n{end_of_tab}` has CHORD tokens for `G` and `D` only.
- The existing `the names of a line of a tab that holds only chord names are chords` (`Am    C  | x2`) still passes, and
  a Latin row (`Sol  Re`) inside a tab is still coloured.
- The incremental test (`assertIncremental`) over a song with such a count line, so `highlight` keeps equalling
  `tokenize`.
- `{start_of_tab}\n(G  D) x2\nG / / / | D\n{end_of_tab}` has CHORD tokens covering exactly `G` and `D` on each line
  (not the parentheses).

`ChordProTabTransposerTest`, `a chord row with the viewer's markers is transposed`: `ChordProTransposer.transposeText`
by 2 of `{sot}` over `(G D)`, `(G  D) x2`, `G ||: D :||`, `G / / / | D`, `G , D`, `G D 2x`, `G 4x`, then `e|--0--|`,
gives `(A E)`, `(A  E) x2`, `A ||: E :||`, `A / / / | E`, `A , E`, `A E 2x`, `A 4x` and `e|--2--|`; `1 & 2 & 3 & 4 &`
and `Tuning: E A D G B E` stay as written. Every row fails at HEAD (left as written, probed). The existing
`a prose line in a tab is still left alone` and `every abbreviation of no chord is a marker` still pass. In
`ChordProParserTest` (or wherever the tab chord count is tested), a tab holding `G / / / | D` lists G and D among the
song's chords.

With option 2, `ChordProTabWrapperTest`: `isChordLine("1 & 2 & 3 & 4 &", isNumbering = false)` is false,
`isChordLine("1 4 5", isNumbering = true)` true, `isChordLine("G C D", isNumbering = false)` true.
