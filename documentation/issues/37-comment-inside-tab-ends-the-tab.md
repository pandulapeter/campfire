# 37 · A comment or a page break inside a tab or a grid turns the rest of it into lyrics

**Severity:** wrong behaviour (all platforms; any song whose tablature or chord grid carries a note to the player in the middle, which is common in tabs copied from the web) · **Area:** `:chordpro` — `ChordProParser.SectionBuilder`, `ChordProParser.handleComment`, `ChordProTransposer.transposeText`, `ChordProSyntax`

## Symptom
Write or import a song that has a directive in the middle of a tab:

```
{start_of_tab}
e|---0---2---|
{comment: Repeat x2}
e|---3---5---|
{end_of_tab}
```

1. Open it in the viewer. The first staff line is drawn as tablature (monospaced, cut at its columns); everything
   after the comment is drawn in the lyrics font and word wrapped, so the columns fall apart.
2. Transpose the song by +2 in the viewer. `0` and `2` become `2` and `4`; `3` and `5` stay where they were.
3. Do the same with **Transpose** in the editor: all four frets move. The same song transposes differently in the
   editor and in the viewer.

The same happens with `{c:}`, `{ci:}`, `{cb:}`, `{new_page}` / `{np}`, `{new_physical_page}` / `{npp}`,
`{column_break}` / `{colb}` and `{chorus}`, and the same inside `{start_of_grid}`: the grid rows after the directive
become lyrics lines holding `| Am . . . |` as words, so they are neither transposed nor respelled by the German
notation or the accidentals preference (a lyrics line only has chords where it has brackets).

## Cause
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProParser.kt` (line numbers as of `29820b93`).
The "how are the next lines written" state, `lineMode`, lives in `SectionBuilder`, and `close()` clears it
(`:237-238`). `addBlock` (`:261-272`) flushes the running section with `close()` and reopens it with `open(...)`,
which restores the type, the label and `isExplicit` but not the line mode:

```kotlin
fun close() {
    lineMode = null
    val type = type ?: return
    …
fun addBlock(block: ChordProBlock) {
    if (type != null && lines.isNotEmpty()) {
        val type = this.type!!
        val label = this.label
        val isExplicit = this.isExplicit
        close()
        blocks += block
        open(type, label, isExplicit)   // lineMode stays null: the next lines are parsed as lyrics
```

Two more paths lose it the same way:

- `handleComment` (`:140-146`): a plain `{c: Solo}` inside a tab that sits in an implicit paragraph (`{start_of_tab}`
  outside every section, which is the usual way to write one) is taken for a Campfire 3 legacy heading —
  `section.close(); section.open(type, text, isExplicit = false, headingText = text)` — so the tab ends there *and*
  the rest of it becomes a "Solo" section of lyrics.
- The `"chorus"` branch of `handleDirective` (`:120-123`): `section.close()` and nothing reopened, so a `{chorus}`
  inside a tab has the rest of the tab read as a new implicit paragraph of lyrics.

Every text-level walk keeps the environment open until its own `{end_of_…}` (or another `{start_of_…}`):
`ChordProTransposer.transposeText` (`ChordProTransposer.kt:111-141`), `ChordProParser.scan` and
`ChordProHighlighter.tokenize`. The model is the odd one out.

`{start_of_verse}` and `{end_of_verse}` style directives inside an open tab also go through `close()`, but there
clearing the line mode is *right*: the text walks replace or clear `environment` on any `start_of_` / `end_of_`
too, so the two already agree. Do not move the reset out of `close()`.

## Fix
All in `:chordpro`. Steps 1–3 fix the parser; step 4 keeps the promise the module already makes elsewhere (see the
test `a tab with a blank line in it is one fingerboard in the model as in the text`) that the model and the text
make the same octave decision for the same lines.

1. `ChordProParser.kt`, `SectionBuilder`: expose whether an environment is open, next to `isExplicit`:

   ```kotlin
   /** Whether a `{start_of_tab}` or a `{start_of_grid}` is open, which says how lines are read and not what section they are in. */
   val isInLineMode get() = lineMode != null
   ```

2. `ChordProParser.kt`, `SectionBuilder.addBlock`: carry the line mode over the flush.

   ```kotlin
   fun addBlock(block: ChordProBlock) {
       if (type != null && lines.isNotEmpty()) {
           val type = this.type!!
           val label = this.label
           val isExplicit = this.isExplicit
           // The block interrupts the section and not the tab or grid environment the file is in the middle of:
           // that one ends at its own `{end_of_…}`, which is where the text transposition, the summary and the
           // highlighter end it as well.
           val lineMode = this.lineMode
           close()
           blocks += block
           open(type, label, isExplicit)
           this.lineMode = lineMode
       } else {
           blocks += block
       }
   }
   ```

   The `else` branch needs nothing: it does not call `close()`, so a directive that comes before the first tab line
   (`{start_of_tab}` / `{comment: …}` / staff) already works. Extend the KDoc of `addBlock` with one sentence: "A tab
   or grid environment that is open carries on in the reopened half."

3. `ChordProParser.kt`, the two callers that bypass `addBlock`:

   - `handleComment` — a comment inside an environment is never a heading:

     ```kotlin
     // Inside a tab or a grid a comment is a note to the player: a Campfire 3 file had its headings between its
     // sections, and taking this one for a heading would end the environment that is still open around it.
     if (style == CommentStyle.PLAIN && !section.isExplicit && !section.isInLineMode) {
     ```

     `{c: Solo}` *followed by* `{start_of_tab}` must keep working as a heading (the line mode is not open yet when
     the comment is read, so it does; test 4 below pins it).

   - the `"chorus"` branch of `handleDirective`:

     ```kotlin
     "chorus" -> {
         val recall = ChordProBlock.ChorusRecall(ChordProSyntax.label(directive.value))
         if (section.isInLineMode) {
             // The environment still has lines to come, so the recall interrupts it the way a comment does.
             section.addBlock(recall)
         } else {
             section.close()
             blocks += recall
         }
     }
     ```

     Outside an environment `{chorus}` keeps ending its section exactly as today — do not route that case through
     `addBlock`, it would change how `{sov} … {chorus} … {eov}` parses.

4. Keep the text path and the model path on the same fingerboards. After steps 1–3 the model holds
   `Section[tab half 1]`, `Comment`, `Section[tab half 2]`, and `ChordProTransposer.rewriteLines` makes one octave
   decision per run inside one section — so two. The text path collects the whole environment into
   `tabLineIndices` and makes one. They only differ when a half has to move by an octave (a fret would leave
   0–24), but then they differ visibly: `{sot}` / `e|--0--|` / `{c: Higher}` / `e|--20--|` / `{eot}` by −2 is left
   alone by the text path and becomes `10` and `18` in the model. The model cannot tell one environment cut by a
   comment from two environments with a comment between them, so the text path follows the model:

   - `ChordProSyntax.kt`: split the existing private `bodyNames` (`:229-232`) so that the directives that become
     blocks have a name of their own. `blockNames` has to be declared above `bodyNames`, since an `object`
     initializes its properties in order:

     ```kotlin
     /**
      * The directives [ChordProParser] makes a block of their own out of — a comment, a break, a chorus recall —
      * and which therefore cut whatever section they stand in into two, a run of tablature included.
      */
     val blockNames = setOf(
         "chorus", "comment", "c", "comment_italic", "ci", "comment_box", "cb", "new_page", "np", "new_physical_page",
         "npp", "column_break", "colb",
     )

     private val bodyNames = blockNames + setOf("new_song", "ns")
     ```

   - `ChordProTransposer.kt`, `transposeText`, inside the existing `if (!ChordProSyntax.hasSelectorSuffix(directive.name))`
     block, after the two environment checks and before the `KEY` check:

     ```kotlin
     if (directive.name in ChordProSyntax.blockNames) {
         // The parser cuts the section in two here, and each half of the tab is a run of its own in the model;
         // moving them as one fingerboard would let the viewer and the editor disagree about the octave.
         lines.transposeTab(tabLineIndices, semitones, preferFlats)
     }
     ```

     `transposeTab` is a no-op for an empty index list, so this costs nothing outside a tab.

Do not touch `ChordProSerializer`: `[Section, Comment, Section]` already serializes to two environments with the
comment between them and parses back to the same three blocks.

## Tests
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProParserTest.kt`, after
`a grid environment inside a section does not break it up either`:

1. `a comment inside a tab environment does not end the tablature` — input
   `"{start_of_tab: Riff}\ne|---0---2---|\n{comment: Repeat x2}\ne|---3---5---|\n{end_of_tab}\nla [C]la"`.
   Expect 3 blocks: `Section(Paragraph, label "Riff", lines = [Tab("e|---0---2---|")])`,
   `Comment("Repeat x2", CommentStyle.PLAIN)`, and a `Section(Paragraph, "Riff")` whose lines are
   `Tab("e|---3---5---|")` followed by one `ChordProLine.Lyrics` (text `"la la"`, one chord `C` at position 3) —
   the last line proves `{end_of_tab}` still closes the mode.
2. `a break inside a grid environment does not end the grid` — input
   `"{start_of_grid}\n| Am . . . |\n{column_break}\n| C . . . |\n{end_of_grid}"`. Expect
   `[Section, ChordProBlock.Break, Section]`, and the single line of `blocks[2]` equal to
   `ChordProLine.Grid(listOf(GridToken.Bar("|"), GridToken.Chord("C"), GridToken.Beat, GridToken.Beat, GridToken.Beat, GridToken.Bar("|")))`.
   Repeat the assertion in a loop for `{new_page}`, `{np}`, `{colb}`, `{ci: x}` and `{cb: x}` in place of the break
   (the middle block is then `Break` or the `Comment` of that style).
3. `a legacy heading name inside a tab environment stays a comment` — input
   `"{sot}\ne|---0---|\n{c: Solo}\ne|---3---|\n{eot}"`. Expect `[Section(Paragraph, [Tab("e|---0---|")]),
   Comment("Solo", PLAIN), Section(Paragraph, [Tab("e|---3---|")])]`; in particular no `SectionType.Custom("solo")`.
4. `a legacy heading still opens the section a tab is written in` — input `"{c: Solo}\n{sot}\ne|---0---|\n{eot}"`.
   Expect a single `Section(type = SectionType.Custom("solo"), label = "Solo", lines = [Tab("e|---0---|")])`.
5. `a chorus recall inside a tab environment does not end the tablature` — input
   `"{sot}\ne|---0---|\n{chorus}\ne|---3---|\n{eot}"`. Expect `[Section([Tab]), ChorusRecall(null), Section([Tab("e|---3---|")])]`.
6. `summarize` needs no new case (it never lost the environment); the existing
   `summarize reports chords in lyrics and in grids but not in tabs` must stay green.

`ChordProTransposerTest.kt`, after `a tab with a blank line in it is one fingerboard in the model as in the text`:

7. `a comment inside a tab moves the frets on both sides of it in the model as in the text` —
   `text = "{sot}\ne|---0---2---|\n{comment: Repeat x2}\ne|---3---5---|\n{eot}"`.
   `assertEquals("{sot}\ne|---2---4---|\n{comment: Repeat x2}\ne|---5---7---|\n{eot}", ChordProTransposer.transposeText(text, 2))`,
   `assertEquals(listOf("e|---2---4---|", "e|---5---7---|"), ChordProTransposer.transpose(ChordProParser.parse(text), 2).tabLines())`.
8. `the two halves of a tab cut by a comment are fingerboards of their own in the model as in the text` —
   `text = "{sot}\ne|--0--|\n{c: Higher}\ne|--20--|\n{eot}"`, −2, `preferFlats = false`. Assert
   `transpose(parse(text), -2, false) == parse(transposeText(text, -2, false))`, and that the text result is
   `"{sot}\ne|--10-|\n{c: Higher}\ne|--18--|\n{eot}"` (the first half moves up an octave: `0 − 2 + 12`, and the wider
   number takes one of the two dashes after it; the second half moves by −2 as asked).
9. `a comment inside a grid leaves the rest of it a grid to transpose` —
   `parse("{sog}\n| Am . |\n{c: x}\n| C . |\n{eog}")` transposed by 2: `chordNames()` is `listOf("Bm", "D")`.

`ChordProNotationTest.kt`:

10. `a comment inside a grid does not hide the chords after it from the notation` —
    `ChordProNotation.toGerman(ChordProParser.parse("{sog}\n| Am . |\n{c: x}\n| B . |\n{eog}")).chordNames()` is
    `listOf("Am", "H")`. (Plan 38 later changes what `toGerman(song)` does for a German-notated file; this input has
    no `H` in it, so it is unaffected.)

## Verify
1. `./gradlew :chordpro:desktopTest`.
2. `./gradlew :app:desktop:run`, create a song in the editor with the text from the Symptom. In the preview and in
   the viewer both staff lines are monospaced and line up, with the comment between them.
3. Transpose +2 in the viewer: the frets read `2 4` and `5 7`. Transpose +2 in the editor: the same four numbers are
   written into the text.
4. Replace the tab with `{start_of_grid}` / `| Am . . . |` / `{column_break}` / `| B . . . |` / `{end_of_grid}`, turn on
   German notation in Settings: the second row shows `H`.
5. Compile checks for the other targets as in the README of this folder (nothing platform specific is touched).

## Docs
`chordpro/CLAUDE.md`, the `model/` bullet, after "A blank line inside an environment does not end its run for either
of the first two: …": add "A `{comment}`, a break or a `{chorus}` inside an environment cuts the section in two the
way it does anywhere else, and the environment carries on in the second half; a comment there is never read as a
Campfire 3 heading. The two halves of a tab are runs of their own, so the text transposition moves them as two
fingerboards as well." `documentation/file-format.md`: none.

## Touches
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProParser.kt`
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProSyntax.kt`
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposer.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProParserTest.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposerTest.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProNotationTest.kt`
- `chordpro/CLAUDE.md`

## Depends on
Nothing. Plans 38–41 edit the same three source files and are written against the result of this one; land it first.
