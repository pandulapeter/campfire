# Head a chorus recall by the first section it repeats, not by its first piece, so a chorus that opens with a comment keeps its label and number when recalled

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SectionNumbering.kt, presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongLyrics.kt, presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayout.kt, presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SectionNumberingTest.kt, presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/RenderSectionsTest.kt, presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayoutTest.kt

**Challenged:** sound — land it before 31, which builds its recall changes (bare kind labels, recall headers) on top of this one.

## Problem

`ChordProParser.withChorusesRecalled` (chordpro/.../ChordProParser.kt:135) builds the blocks a `{chorus}` recall carries as the comments the chorus opens with *followed by* the chorus section:

```kotlin
pieces = if (block.type == SectionType.Chorus) (blocks.commentsOpening(index) + block).toMutableList() else null
```

So for a chorus written `{soc}\n{c: Softly}\na\n{eoc}` the recall's `blocks` start with a `ChordProBlock.Comment`, not the `Section`. All three consumers look only at the first piece:

- `SectionNumbering.kt:49` (`withNumberedSections`):
  ```kotlin
  val first = block.blocks.firstOrNull() as? ChordProBlock.Section
  if (first?.label == null && first?.number == null) {
      block.copy(blocks = block.blocks.mapIndexed { index, piece -> if (index == 0 && piece is ChordProBlock.Section) piece.copy(number = lastChorusNumber) else piece })
  ```
  `first` is null, and index 0 is the comment, so the number is never copied.
- `SongLyrics.kt:2273` (`toRenderSections`, details screen, lyrics-only mode and the editor preview):
  ```kotlin
  val recalled = block.blocks.firstOrNull() as? ChordProBlock.Section
  val label = block.label ?: recalled?.label
  var header: String? = label ?: recalled?.takeIf { it.number != null }?.header(defaultLabels) ?: defaultLabels.chorus
  val recallFoldKey = foldNameCounts.nextFoldKey(label ?: SectionType.Chorus.foldName)
  ```
- `PrintLayout.kt:526` (`recallRows`, the PDF):
  ```kotlin
  val recalled = recall.blocks.firstOrNull() as? ChordProBlock.Section
  var header: String? = recall.label ?: recalled?.label ?: recalled?.takeIf { it.number != null }?.header(labels.sections) ?: labels.sections.chorus
  ```

Effects (proven by a scratch run at 800ebde0b):
- `{soc}\n{c: Softly}\na\n{eoc}\n{soc}\n{c: Loud}\nb\n{eoc}\n{chorus}` with "Number sections" on shows "Chorus 1", "Chorus 2", then the recall as plain "Chorus" instead of "Chorus 2".
- Independently of numbering, `{soc: Refrain}\n{c: x}\nla\n{eoc}\n{chorus}` recalls as "Chorus" instead of "Refrain", on screen and in the PDF.
- The recall's fold key is `chorus#n` instead of `Refrain#n` for that second case, so it folds under the wrong name.

## Fix

Look for the first *section* among the recalled pieces in all three places:

1. `SectionNumbering.kt`, the `ChorusRecall` branch:
   ```kotlin
   block is ChordProBlock.ChorusRecall && block.label == null && lastChorusNumber != null -> {
       val sectionIndex = block.blocks.indexOfFirst { it is ChordProBlock.Section }
       val first = block.blocks.getOrNull(sectionIndex) as? ChordProBlock.Section
       if (first != null && first.label == null && first.number == null) {
           block.copy(blocks = block.blocks.mapIndexed { index, piece -> if (index == sectionIndex) first.copy(number = lastChorusNumber) else piece })
       } else {
           block
       }
   }
   ```
   Update the KDoc sentence about recalls to say the number goes on the first section the recall repeats (its opening comments come before it).
2. `SongLyrics.kt:2273`: `val recalled = block.blocks.firstOrNull { it is ChordProBlock.Section } as? ChordProBlock.Section`.
3. `PrintLayout.kt:526`: the same change.

Note for the executor and release notes: for a recall of a labelled chorus that opens with a comment, the fold key changes from `chorus#n` to `<label>#n` (the label as written), so a fold the user saved on such a recall unfolds once. That is the key the recall should always have had; no migration.

## Tests

- `SectionNumberingTest`: add `a recalled chorus that opens with a comment carries the number of the most recent one`: parse `{soc}\n{c: Softly}\na\n{eoc}\n{soc}\n{c: Loud}\nb\n{eoc}\n{chorus}`, run `withNumberedSections(labels)`, take the single `ChorusRecall`, and assert `recall.blocks.filterIsInstance<ChordProBlock.Section>().first().number == 2`.
- `RenderSectionsTest`: add a test using `prepareSongLyrics(…, labels = labels.copy(shouldNumberSections = true))` on the same text asserting the last `RenderSection.Lines` header is `"Refrén 2"` (that test's labels are Hungarian); and one on `{soc: Refrain}\n{c: x}\nla\n{eoc}\n\n{chorus}` (numbering off) asserting both `Lines` headers are `"Refrain"` and the recall's `foldKey` starts with `"Refrain#"`.
- `PrintLayoutTest`: add `aChorusRecallOpeningWithACommentIsHeadedByTheChorusLabel`: build `chorus = Section(SectionType.Chorus, "Refrain", lyrics(1))`, a `Comment("x", CommentStyle.PLAIN, placement = START_OF_SECTION)` (check the constructor in ChordProBlock.kt), and `ChorusRecall(null, listOf(comment, chorus))`; assert `"Refrain"` is printed twice and `"Chorus"` never.

Run `./gradlew :presentation:desktopTest --offline`.

## Manual check

Write a song with `{soc: Refrain}`, `{c: softly}` as its first line inside, one lyric line, `{eoc}`, then `{chorus}` further down. On the song details screen, in the editor preview and in a PDF export, the recall is headed "Refrain". With Settings → Songs → "Number sections" on, two unlabelled choruses that each open with a comment, followed by `{chorus}`, show the recall as "Chorus 2".
