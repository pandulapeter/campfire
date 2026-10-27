# Rename a chord written with markup inside its brackets by the name the parser reads, keeping the tags

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/syntax/ChordProMarkup.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/syntax/ChordProChordRewriter.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/chords/ChordProChords.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProParser.kt` (only if the shared helper below is
adopted there too), `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/chords/ChordProTransposerTest.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/chords/ChordProNotationTest.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/chords/ChordProChordsTest.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/syntax/ChordProMarkupTest.kt`, `chordpro/CLAUDE.md`

## Problem

Since d2cdc0c32 the parser reads a chord written with markup inside its brackets by its text alone
(`ChordProParser.parseLyrics`):

```kotlin
// ChordPro formats a chord as a whole, which a chord's name cannot carry, so it keeps only the text.
val content = ChordProMarkup.strip(bracket.content).text.trim()
```

so `[<b>Am</b>]` is the chord `Am` and `[<span color="red">C&#x266F;</span>]` is `C#` (pinned by
`ChordProMarkupTest` "markup inside a chord's brackets leaves the chord's name and formats nothing"). Every rewrite of
raw text still hands the raw content to the rename, in `ChordProChordRewriter.rewriteLyricsLineChords`:

```kotlin
val content = bracket.content.trim()
val isChord = content.isNotEmpty() && !content.startsWith(ANNOTATION_MARKER)
...
    .append(rename(content))
```

and `ChordProChords.bracketedNames` (the comment and label brackets the diagrams and degrees read):

```kotlin
private fun bracketedNames(text: String) = ChordProDirectives.brackets(text)
    .map { bracket -> bracket.content.trim() }
    .filter { name -> name.isNotEmpty() && !name.startsWith(ChordProVocabulary.ANNOTATION_MARKER) }
```

`transposeChord` and the notation renames leave a name they cannot read as it is, so `<b>G</b>` passes through
unchanged. What the user sees:

- **A wrong chord written into the file.** In the editor, `[<b>G</b>]la [C]la` transposed +2
  (`ChordProTransposer.transposeText` → `rewriteChordNamesInText` → `rewriteLyricsLineChords`) saves
  `[<b>G</b>]la [D]la`: the song now plays G where it should play A. A tab's chord row goes through the same function
  (`ChordProTabTransposer`, `rewriteLyricsLineChords(line, rename)`).
- **Notation conversion misses it.** `ChordProNotation.convertText` from standard to German leaves `[<b>B</b>]`
  (should be `[<b>H</b>]`), and back.
- **Comments shown with a transposition, capo, or notation override keep the old chord.** `rewriteBlock` runs
  `rewriteLyricsLineChords` on the raw comment text (`{c: Intro [<b>G</b>]}` keeps its markup in the model, as
  `chordpro/CLAUDE.md` says, "since its chords are rewritten there after parsing"), and
  `SongRenderSections` strips it only afterwards, so the comment reads `Intro [G]` in a song shown +2.
- **Diagrams and degrees skip it** in comments and labels: `bracketedNames` returns `<b>G</b>`, which no table names.

## Fix

Judge a bracket by the name the parser reads, and write the renamed name back over only the characters that name
came from, so the file keeps its tags ("Text edits keep every other byte", `chordpro/CLAUDE.md`).

1. **`ChordProMarkup.Stripped` learns where its characters came from.** Add a `private val sources: IntArray?` beside
   `offsets`, filled in `strip`: for each character appended to `result`, the index in `text` it was copied from, or
   `-1` where it was produced rather than copied (an entity's decoded character, a `<sym>`'s symbol, the space a `<br>`
   leaves). `null` on the early return with no markup, meaning identity. Add

   ```kotlin
   /**
    * The range of the text before [strip] that the characters from [start] until [end] of [text] were copied from, one
    * by one and with nothing between them, or null where a tag stands among them or one of them was decoded.
    */
   internal fun sourceRangeOf(start: Int, end: Int): IntRange?
   ```

   (`sources == null` → `start until end`; otherwise every `sources[k]` in the range is non-negative and each is the
   previous plus one.) Cost: one more `IntArray` only on text that has markup or an entity.

2. **One reading of a bracket's chord.** Add to `ChordProMarkup` (or `ChordProDirectives`, next to `Bracket`) an
   internal helper that says what the parser says, e.g.

   ```kotlin
   /** The text a chord's brackets hold as the parser reads it: without markup, trimmed. */
   internal fun bracketText(content: String): String = strip(content).text.trim()
   ```

   Use it in `bracketedNames` (`.map { bracket -> ChordProMarkup.bracketText(bracket.content) }`), and optionally in
   `ChordProParser.parseLyrics` so the three cannot drift again (recommended; behaviour there is unchanged).

3. **`rewriteLyricsLineChords`** reads each bracket as:

   ```kotlin
   val stripped = ChordProMarkup.strip(bracket.content)
   val name = stripped.text.trim()
   val isChord = name.isNotEmpty() && !name.startsWith(ANNOTATION_MARKER)
   ```

   and for a chord writes `[` + new content + `]`, where the new content is:

   - the raw `bracket.content` unchanged when `rename(name) == name` (a name no rename touches, e.g. `[<b>Chorus</b>]`
     or a notation that leaves the chord as it is, stays byte for byte);
   - otherwise, where `stripped.sourceRangeOf(nameStart, nameStart + name.length)` (with `nameStart` the index of the
     first non-whitespace character of `stripped.text`, `Char.isWhitespace`) is a range, `bracket.content` with that
     range replaced by the renamed name: `[<b>G</b>]` → `[<b>A</b>]`, `[ <i>G</i> ]` → `[ <i>A</i> ]`;
   - otherwise (a tag or an entity inside the name: `[C<b>#</b>]`, `[C&#x266F;]`) the raw content's leading
     whitespace, the renamed name, and its trailing whitespace, as today. The markup there formats nothing and is
     dropped; the entity is spelled out. Losing these bytes is the price of a correct chord, and only on a chord that
     was actually renamed.

   Without markup `sources` is identity and the trimmed range is exactly what the current code replaces, so every
   existing result stays the same; the annotation check now also sees `[<i>*softly</i>]` as the annotation the parser
   reads it as.

4. `chordpro/CLAUDE.md`, the Markup bullet: after "a chord's brackets lose their markup, formatting nothing" add that a
   rewrite renames such a chord by its text and keeps the tags around it where the name is one run of the file's
   characters.

Every caller (`rewriteChordNamesInText` for lines and directives with chords in their value, `rewriteBlock` for
comments and labels, `rewriteLine` for grid and tab labels, `ChordProTabTransposer`) goes through
`rewriteLyricsLineChords`, so no caller changes.

## Tests

In `ChordProTransposerTest`:

- `transposeText("[<b>G</b>]la [C]la", 2, preferFlats = false)` is `"[<b>A</b>]la [D]la"`; `[ <i>G</i> ]` keeps its
  spaces and tags; `[<b>*softly</b>]` and `[<b>Chorus x2</b>]` stay byte for byte;
  `[C&#x266F;]a` +1 becomes `[D]a` (the decoded sharp is a chord the transposer moves, so the entity gives way to the new name).
- A comment on the model: `ChordProTransposer.transpose(ChordProParser.parse("{c: Intro [<b>G</b>]}\n\n[G]a"), 2,
  preferFlats = false).blocks.first()` is `ChordProBlock.Comment("Intro [<b>A</b>]", CommentStyle.PLAIN)`, beside
  "the chords of a comment are transposed on the model".

In `ChordProNotationTest`: `convertText("[<b>B</b>]a", ChordNotation.STANDARD, ChordNotation.GERMAN)` is
`"[<b>H</b>]a"`, and converting back returns the original text.

In `ChordProChordsTest`: `ChordProChords.namesIn(ChordProParser.parse("{c: Intro [<b>Em</b>]}\n\n[G]a"))` contains
`Em`.

In `ChordProMarkupTest`: `sourceRangeOf` for `<b>Am</b>` (the name's range is `3..4`), for `C&#x266F;` (null), and
for plain text (identity).

Run `./gradlew :chordpro:desktopTest`.
