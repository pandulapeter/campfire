# Never let the editor's placeholder chord shape silently override the song's own `{define}`

**Challenged:** amended — fix confirmed and made concrete (one `rangeOf` check on the name either branch picks, selecting
the existing line's frets as the chord-at-caret branch does); noted that `rangeOf` also answers a `{chord}` line where
there is no `{define}`, which the placeholder then respects too; tests pinned to the existing test file's helpers.

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/chords/ChordShapeInsertion.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/chords/ChordShapeInsertionTest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/chords/CLAUDE.md` ("The editor" paragraph)

## Problem

`chordShapeInsertion`:

```kotlin
val chordAtCaret = ChordProDefinitions.chordAt(text, caret)?.takeIf { ChordProChords.parse(it, notation) != null }
if (chordAtCaret != null) {
    ChordProDefinitions.rangeOf(text, chordAtCaret, instrument)?.let { return TextInsertion(offset = 0, text = "", selectionStart = it.first, selectionEnd = it.last + 1) }
}
val name = chordAtCaret ?: ChordProNotation.shownName(PLACEHOLDER_CHORD, notation)
```

The "already defined → select its values" check runs only for a chord at the caret; the placeholder C skips it.
`ChordProHeader.insertDefinition` puts the new line after the last definition, and the last `{define}` wins
(`ChordProDefinitions.rangeOf`: "the one that counts: the last `{define}`, or the last `{chord}` where there is none").
A song with `{define: C base-fret 3 frets x 1 3 3 3 1}`: the user picks Chord shape → Guitar on an empty line and clicks
back into the lyrics without typing over the selected "C": the song's barre C is now the open C.

## Fix

Run the existing check on whichever name is about to be written:

```kotlin
val chordAtCaret = ChordProDefinitions.chordAt(text, caret)?.takeIf { ChordProChords.parse(it, notation) != null }
val name = chordAtCaret ?: ChordProNotation.shownName(PLACEHOLDER_CHORD, notation)
// A chord the text already defines on this instrument, the placeholder's C included, gets no second line: the last one
// would win, silently replacing the song's own shape.
ChordProDefinitions.rangeOf(text, name, instrument)?.let { return TextInsertion(offset = 0, text = "", selectionStart = it.first, selectionEnd = it.last + 1) }
```

So with the caret in no chord and the text already defining the placeholder on that instrument (by `{define}`, or by a
`{chord}` line with a shape where there is none), nothing is inserted and that line's frets (keys on the keyboard) are
selected, exactly as for a defined chord at the caret: the existing line is the syntax to copy from. `rangeOf` compares
the name as written, and the placeholder is written in the field's notation (`Do` in Latin), as a chord at the caret is;
no new notation case.

Update the KDoc ("A chord the text already defines on [instrument] gets no second line" now says the placeholder too) and
the `ui/chords/CLAUDE.md` editor paragraph: "or it moves the caret to an existing line's frets for that instrument" holds
for the placeholder as well.

## Tests

In `ChordShapeInsertionTest`, next to `a chord already defined gets no second line, the caret goes to its frets`:

- `"{title: X}\n{define: C base-fret 3 frets x 1 3 3 3 1}\n\nHello"` with the caret in "Hello", guitar: the insertion's
  text is empty and the selection is `x 1 3 3 3 1`.
- The same text with the ukulele picked: a ukulele C placeholder line is still written, its name selected (`apply(...)`
  returns `"C"` as selected).
