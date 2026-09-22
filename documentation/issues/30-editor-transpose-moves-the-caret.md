# 30 · Transposing in the editor moves the caret somewhere else, so whatever is typed next lands in the wrong place

**Severity:** wrong behaviour (all platforms; likely — every editor transposition with the caret or a selection
below a chord that changes length; the next keystroke then edits the wrong spot, often inside another line or a
chord name) · **Area:** `:presentation` (`SongEditorScreen.kt`: `replaceAll`, the stepper's `onTransposed`),
`:chordpro` (`ChordProTransposer`)

## Symptom
1. Open a song in the editor whose chords change length when transposed, e.g.
   ```
   [C]Amazing [F]grace, how [C]sweet the sound
   That [C]saved a [G]wretch like [C]me
   [C]I once was [F]lost, but [C]now am found
   ```
2. Put the caret right after `found` on the third line (or select the word `wretch`).
3. Tap the editor's transpose **+** once (C → C♯ / D♭: every chord name grows by one character).
4. Type `!`.

The `!` does not land after `found`: the caret stayed at the same *character offset* while every chord before it got
one character longer, so it has drifted left by the number of chords in front of it (here nine): it now sits in the
middle of `now am`, and in a real song with 30–60 chords above the caret it lands one or two lines
higher. A selection is collapsed to a caret at the old start offset. Transposing down (C♯ → C) drifts the other way.
Repeated taps (the stepper is made to be tapped several times) compound the drift. Undo does restore the text, but
the user rarely notices the misplaced keystroke before typing on.

## Cause
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/SongEditorScreen.kt:704-712`:

```kotlin
private fun TextFieldState.replaceAll(text: String) {
    if (this.text.length > LONG_DOCUMENT_LENGTH) undoState.clearHistory()
    edit {
        val caret = selection.start.coerceAtMost(text.length)
        delete(0, length)
        insert(0, text)
        selection = TextRange(caret)
    }
}
```

called by the transposition stepper at `:373-375`:

```kotlin
onTransposed = { semitones ->
    textFieldState.replaceAll(viewModel.transposeText(textFieldState.text.toString(), semitones, chordSpelling.accidentals))
},
```

The whole document is replaced and the caret is put back at the old offset, which only means the same place when
nothing before it changed length. A transposition changes the length of chord names (`C`→`C#`, `Bb`→`B`,
`F#m7`→`Gm7`, tab frets `9`→`11`) all over the text. It never adds or removes a line, though:
`ChordProTransposer.rewriteText` (`chordpro/.../ChordProTransposer.kt:134-167`) rewrites `splitLines(text)` in place
and joins them back with `ChordProSyntax.joinLines`, so a line index is a stable anchor, and on a lyrics line only
the bracket contents change.

(Revert uses the same `replaceAll` at `:694`; there the old and new texts are unrelated and keeping the offset is the
best there is — leave the revert behaviour alone.)

## Fix
1. **`:chordpro`, `ChordProTransposer`** — add a pure function next to `transposeText`:

   ```kotlin
   /**
    * Where [offset] of [before] is in [after], for an [after] that [transposeText] made of [before]: an editor that
    * transposes the document under the caret keeps the caret next to the text it was next to, rather than at the
    * same character count, which every chord name that grew or shrank above it would have moved.
    *
    * A transposition keeps every line and changes nothing but chord names (and the frets of a tab), so the offset
    * keeps its line, and inside the line it keeps its place between the brackets; a line with no brackets to go by
    * (a `{key}`, a grid or a tab line) keeps the text in front of its first change and behind its last one.
    */
   fun transposedOffset(before: String, after: String, offset: Int): Int
   ```

   Algorithm (all of it private helpers in the same object):
   - `val clamped = offset.coerceIn(0, before.length)`; `if (before == after) return clamped`.
   - Line starts of both texts, computed with the same line-break rule as `ChordProSyntax.splitLines` (`\r\n` is one
     break, a lone `\r` or `\n` is one). `ChordProHeader.lineStartOffset` already walks the text that way; move that
     walk into `ChordProSyntax` as `internal fun lineStartOffsets(text: String): IntArray` (index `i` = start of line
     `i` of `splitLines(text)`) and have `ChordProHeader.lineStartOffset` use it, so the two can never disagree.
   - If the two texts do not have the same number of lines, return `clamped.coerceAtMost(after.length)` (cannot
     happen for a real transposition; it keeps the function total).
   - `line` = the last line whose start is `<= clamped` (binary search), `column = clamped - start`. The line's content
     is `splitLines(text)[line]` on both sides. A column past the line's content (the caret on the line break, or
     between the `\r` and `\n` of one, or past the final line break, which `splitLines` does not count as a line)
     maps to `afterLine.length + (column - beforeLine.length)`, capped at the next line's start in `after` minus one
     (at `after.length` on the last line) — `joinLines` may have changed the separator of a mixed-ending file, so
     compute it from `after`'s own line starts rather than assuming the break's length.
   - Inside the line, `transposedColumn(beforeLine, afterLine, column)`:
     - equal lines → `column`;
     - `ChordProSyntax.brackets` of both lines have the same count and at least one: walk them in order with a
       running `shift` (initially 0); for bracket `i` with old range `b` and new range `a`: if `column <= b.first`
       return `column + shift`; if `column == b.last` (the caret right before the `]`, i.e. after the whole chord
       name) return `a.last`, so it stays after the whole new name; if `column < b.last` (inside the name) return
       `(a.first + (column - b.first)).coerceAtMost(a.last)`; otherwise `shift = a.last - b.last`. After the last
       bracket return `column + shift`;
     - otherwise the common prefix `p` and common suffix `s` of the two lines (the suffix limited to
       `min(before.length, after.length) - p` so the two do not overlap): `column <= p` → `column`;
       `column >= before.length - s` → `after.length - (before.length - column)`; in between →
       `(after.length - s).coerceAtLeast(p)` (the end of the changed stretch, i.e. just after the rewritten fret or
       key).
2. **`:presentation`, `SongEditorScreen.kt`** — the transposition gets its own rewrite instead of `replaceAll`:

   ```kotlin
   /**
    * Replaces the document with its transposition, keeping the caret and the selection next to the text they were
    * next to (see ChordProTransposer.transposedOffset) rather than at the same character count.
    */
   @OptIn(ExperimentalFoundationApi::class)
   private fun TextFieldState.replaceWithTransposition(transposed: String) {
       val before = text.toString()
       if (before == transposed) return
       if (before.length > LONG_DOCUMENT_LENGTH) undoState.clearHistory()
       edit {
           val start = ChordProTransposer.transposedOffset(before, transposed, selection.start)
           val end = ChordProTransposer.transposedOffset(before, transposed, selection.end)
           replace(0, length, transposed)
           selection = TextRange(start, end)
       }
   }
   ```

   and `onTransposed = { semitones -> textFieldState.replaceWithTransposition(viewModel.transposeText(textFieldState.text.toString(), semitones, chordSpelling.accidentals)) }`.
   `TextRange(start, end)` keeps a reversed selection reversed. The early return also stops a tap that rewrites
   nothing (a song whose only "chords" are not chord names) from adding an undo step. `replaceAll` stays as it is,
   for the revert. The editor already calls `:chordpro` objects directly (`ChordProParser.summarize`,
   `ChordProHeader.insert`), so calling `ChordProTransposer` here follows that precedent; do not route it through
   a new use case just for an offset.

Do **not**: put a sentinel character into the text at the caret before transposing (it breaks the chord or tab
column it lands in); run a general character diff over the whole document (quadratic on a long song, and the line
structure already gives the answer); or apply the transposition as many small `replace` calls to let
`TextFieldBuffer` shift the selection (same alignment work, spread over the UI code, untestable).

## Tests
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposerTest.kt`, a group for
`transposedOffset`, each asserting against `transposeText` output rather than a hand-written `after`:
- caret after the last lyric of the third line of the song above, +1: maps to the same lyric position (assert that
  `after.substring(mapped - 5, mapped) == "found"`);
- caret between two chords on one line (`[C]Hel|lo [G]world`), +1 and −1;
- caret right after a chord name (`[C|]`), +1 → right before the `]` of the transposed chord (after `C#`/`Db`,
  not between its letters); caret between the letters of a longer name (`[F|#m7]`) → inside the new brackets,
  never past their `]`;
- caret at offset 0 and at `before.length` (maps to `after.length`);
- caret on a `{key: C}` line right after the value, +1 (the value grows to two characters) → right after the new
  value, before the `}`;
- a tab line whose fret `9` becomes `11`: a caret after the change keeps its distance from the line end;
- CRLF file, caret at the end of line 2 → end of line 2 in `after`;
- a mixed-endings file (`\n` and `\r\n`), whose separators `joinLines` unifies: caret at the start of line 3 → start
  of line 3 in `after`;
- `before == after` → identity; out-of-range offset → clamped.
Run with `./gradlew :chordpro:desktopTest`.

## Verify
1. `./gradlew :chordpro:desktopTest`.
2. `./gradlew :app:desktop:run`, open a song with many chords in the editor, put the caret after a word near the end,
   tap transpose + four times, type a character: it lands after that word. Select a word, transpose: the same word is
   still selected. Undo four times: the text returns to the original.
3. Revert still works (caret goes to the old offset, as before).
4. Compile checks: `./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution`.

## Docs
- `chordpro/CLAUDE.md`, the `ChordProTransposer` bullet: add "`transposedOffset` maps a caret through a text
  transposition (same line, same place between the brackets), which is what keeps the editor's caret next to the
  text it was at."
- `presentation/CLAUDE.md`, the editor's stepper bullet ("The stepper is the details screen's own
  `TextTranspositionControls`…"): add "A transposition keeps the caret and the selection next to the text they were
  at (`ChordProTransposer.transposedOffset`), since every chord name that grows or shrinks above them would move them
  otherwise; a revert has nothing to map by and keeps the offset."

## Touches
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposer.kt`
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProSyntax.kt` (`lineStartOffsets`)
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProHeader.kt` (use `lineStartOffsets`)
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposerTest.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/SongEditorScreen.kt`
- `chordpro/CLAUDE.md`, `presentation/CLAUDE.md`

## Depends on
Nothing. Same files as other plans, different lines — schedule one after another: 31, 20 and 11 also edit
`SongEditorScreen.kt`; 46 also edits `ChordProSyntax.kt` (add `lineStartOffsets` next to whatever it changes).
