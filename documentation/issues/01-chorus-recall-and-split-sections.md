# 01 — A chorus cut by a comment is recalled half, and every cut section shows its heading twice

**Severity:** wrong rendering (all platforms) · **Area:** `:chordpro` (`ChordProParser`, `ChordProSerializer`,
`ChordProTransposer`, model) and `:presentation` (`screens/songDetails/SongLyrics.kt`)

**Read, not run.** This was found by reading the parser and the viewer at HEAD (2065e47f); it has not been reproduced
in a running build. The "Verification" section is how to confirm it, and confirming it is the first step of the work.

## What the user sees

Two symptoms of one cause.

1. A chorus with a comment, a break or a recall inside it:

   ```
   {start_of_chorus}
   [C]line one
   {comment: softly}
   [G]line two
   {end_of_chorus}

   {chorus}
   ```

   The chorus itself is drawn as two cards, **both headed "Chorus"**, with "softly" between them. The `{chorus}`
   recall at the end repeats **only "line two"** — the half after the comment.
2. The same duplicated heading on every other section that is cut this way: `{start_of_verse: Verse 1}` with a
   `{ci: …}`, `{column_break}` or `{new_page}` in the middle shows "Verse 1" twice; a Campfire 3 file's
   `{c: Verse 1}` heading followed by a line, a `{ci: …}` and more lines shows "Verse 1" twice; a
   `{start_of_tab: Riff}` outside every section with a comment in the middle of it shows "Riff" twice; a
   `{start_of_solo}` shows "Solo" twice.

An unlabelled verse is unaffected (it has no heading to repeat), which is why this is easy to miss.

## Cause

`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProParser.kt:301-317` — a comment, a break or
(inside a tab/grid) a recall is emitted as a block of its own by closing the running section and opening a new one of
the same type and label:

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

The two halves are indistinguishable in the model — `ChordProBlock.Section(type, label, lines)`
(`model/ChordProBlock.kt:21-25`) has nothing that says "this is the rest of the section before". The viewer
(`presentation/.../songDetails/SongLyrics.kt:1237-1278`) therefore treats each half as a section of its own:

```kotlin
            is ChordProBlock.ChorusRecall -> {
                val chorus = lastChorus
                sections += RenderSection.Lines(
                    header = block.label ?: chorus?.label ?: defaultLabels.chorus,
                    lines = chorus?.lines?.prepareForDisplay(shouldShowChords).orEmpty(),
                    isOnCard = true,
                )
            }

            is ChordProBlock.Section -> {
                if (block.type == SectionType.Chorus) lastChorus = block
                ...
                val header = block.header(defaultLabels)
```

— `lastChorus = block` is overwritten by the second half, so the recall repeats only that; and `header()`
(`:1283-1296`) gives the second half `label ?: "Chorus"` / `"Solo"` / … exactly as it gave the first.

## The change

Invoke the **`code-style`** skill before the first edit. Everything below is `commonMain`.

The model learns two things: that a section is the continuation of the one before it, and what a recall repeats. The
recall is resolved in `:chordpro` rather than in the viewer because plan 02 (`{transpose}` modulation) has to
transpose a recalled chorus by the offset in effect *where the recall stands*, and the viewer cannot transpose — so
the recall has to carry its chorus through `ChordProTransposer` like any other block.

### 1. Model — `chordpro/.../model/ChordProBlock.kt`

```kotlin
    data class Section(
        val type: SectionType,
        val label: String?, // "Verse 1" from {start_of_verse: Verse 1} or {sov: label="Verse 1"}
        val lines: List<ChordProLine>,
        /**
         * Whether this is the rest of the section before it, which a comment, a break or a chorus recall standing
         * inside that section cut in two. It is still the section the file wrote once, so it has no heading of its own
         * to show, and a recall of a chorus repeats it together with the part before it.
         */
        val isContinuation: Boolean = false,
    ) : ChordProBlock

    /**
     * {chorus} / {chorus: label}: repeat the most recent chorus. [blocks] is that chorus as the parser found it — its
     * first section, and where something cut it, whatever stood inside it and the continuations after — or empty where
     * no chorus came before. The renderer decides how to show it.
     */
    data class ChorusRecall(
        val label: String?,
        val blocks: List<ChordProBlock> = emptyList(),
    ) : ChordProBlock
```

### 2. Parser — `ChordProParser.kt`

`SectionBuilder` gets an `isContinuation` field, set only by the reopen in `addBlock`:

```kotlin
        private var isContinuation = false

        fun open(type: SectionType, label: String?, isExplicit: Boolean, headingText: String? = null, isContinuation: Boolean = false) {
            this.type = type
            this.label = label
            this.isExplicit = isExplicit
            this.headingText = headingText
            this.isContinuation = isContinuation
            lines.clear()
        }
```

`close()` (`:276-292`) passes it into the section and resets it:

```kotlin
            if (lines.isNotEmpty()) {
                blocks += ChordProBlock.Section(type = type, label = label, lines = lines.toList(), isContinuation = isContinuation)
            } else {
                headingText?.let { blocks += ChordProBlock.Comment(it, CommentStyle.PLAIN) }
            }
            this.type = null
            label = null
            isExplicit = false
            isContinuation = false
            headingText = null
```

and `addBlock` (`:312`) reopens with `open(type, label, isExplicit, isContinuation = true)`. Extend its KDoc: "The
reopened half is marked as the continuation it is, so that it is neither headed a second time nor left out of a
recall of its chorus."

Then fill the recalls in once the walk is over. `parseAsWritten` (`:41-57`) ends with
`return ChordProSong(metadata = metadata.build(), blocks = withChorusesRecalled(blocks))`, and:

```kotlin
    /**
     * [blocks] with every recall carrying the chorus it repeats: the last one that was over by the time the recall is
     * reached, all of it — a chorus cut in two by a comment is its first section, the comment and the continuation. A
     * recall standing inside a chorus (in a tab written there) repeats the chorus before that one, since the one it
     * stands in is not over yet. A recall inside the recalled chorus is left out of the copy, which would otherwise
     * repeat a chorus inside a repeat of itself.
     */
    private fun withChorusesRecalled(blocks: List<ChordProBlock>): List<ChordProBlock> {
        if (blocks.none { it is ChordProBlock.ChorusRecall }) return blocks
        // Every chorus, by the index of its last piece; ascending, since they are found in order.
        val choruses = mutableListOf<Pair<Int, List<ChordProBlock>>>()
        var pieces: MutableList<ChordProBlock>? = null
        var lastPieceIndex = -1
        blocks.forEachIndexed { index, block ->
            if (block !is ChordProBlock.Section) return@forEachIndexed
            val chorus = pieces
            if (chorus != null && block.isContinuation) {
                chorus += blocks.subList(lastPieceIndex + 1, index).filterNot { it is ChordProBlock.ChorusRecall }
                chorus += block
            } else {
                chorus?.let { choruses += lastPieceIndex to it }
                pieces = if (block.type == SectionType.Chorus) mutableListOf(block) else null
            }
            lastPieceIndex = index
        }
        pieces?.let { choruses += lastPieceIndex to it }
        var chorusIndex = -1
        return blocks.mapIndexed { index, block ->
            if (block !is ChordProBlock.ChorusRecall) return@mapIndexed block
            while (chorusIndex + 1 < choruses.size && choruses[chorusIndex + 1].first < index) chorusIndex++
            block.copy(blocks = choruses.getOrNull(chorusIndex)?.second.orEmpty())
        }
    }
```

A continuation always directly follows (through non-section blocks) the section it continues, since `addBlock` only
ever inserts non-section blocks between the two halves, which is what lets the walk not look at the type again. The
walk is linear. `scan` (the summary) needs nothing: it has no blocks.

### 3. Transposer — `ChordProTransposer.kt:66-75`

`rewriteChords` maps the top-level sections only, so a recalled chorus would reach the viewer in the file's spelling
and untransposed. Recurse:

```kotlin
    internal fun rewriteChords(
        song: ChordProSong,
        rewriteTabLines: (List<String>) -> List<String>,
        rename: (String) -> String,
    ): ChordProSong = song.copy(
        metadata = song.metadata.copy(key = song.metadata.key?.let(rename)),
        blocks = song.blocks.map { block -> rewriteBlock(block, rewriteTabLines, rename) },
    )

    private fun rewriteBlock(
        block: ChordProBlock,
        rewriteTabLines: (List<String>) -> List<String>,
        rename: (String) -> String,
    ): ChordProBlock = when (block) {
        is ChordProBlock.Section -> block.copy(lines = rewriteLines(block.lines, rewriteTabLines, rename))
        // The chorus a recall repeats travels inside it, so it is spelled and moved the way the chorus itself is.
        is ChordProBlock.ChorusRecall -> block.copy(blocks = block.blocks.map { rewriteBlock(it, rewriteTabLines, rename) })
        else -> block
    }
```

`writtenChordNames` (`:78-88`) stays on the top-level sections: a recall is a copy, and counting it would weigh the
chorus twice in `isWrittenInFlats` and in the German detection.

### 4. Serializer — `ChordProSerializer.kt:26-31, 66-97`

`parse(serialize(parse(x))) == parse(x)` is the module's promise and has a test over a fixture that *already* holds a
verse cut by a comment (`ChordProSerializerTest.EVERY_BLOCK_TYPE`, "Verse 1 … Split by a comment"). Written back as
two environments, that would come back as two sections without the flag, so a section and its continuations are
written back as the one environment they were read from, with whatever stood between them inside it:

```kotlin
    fun serialize(song: ChordProSong): String {
        val chunks = mutableListOf<String>()
        serializeMetadata(song.metadata).takeIf { it.isNotEmpty() }?.let { chunks += it.joinToString("\n") }
        var index = 0
        while (index < song.blocks.size) {
            val block = song.blocks[index]
            if (block is ChordProBlock.Section) {
                // A section a comment, a break or a recall cut in two goes back into the one environment it was read
                // from, with what cut it inside: written as two, it would come back as two sections.
                val end = sectionEnd(song.blocks, index)
                chunks += serializeSection(song.blocks.subList(index, end))
                index = end
            } else {
                chunks += serializeBlock(block)
                index++
            }
        }
        return chunks.joinToString("\n\n")
    }

    /** The index after the last continuation of the section at [start]; what follows its last piece is not its own. */
    private fun sectionEnd(blocks: List<ChordProBlock>, start: Int): Int {
        var end = start + 1
        for (index in start + 1 until blocks.size) {
            val block = blocks[index]
            if (block !is ChordProBlock.Section) continue
            if (!block.isContinuation) break
            end = index + 1
        }
        return end
    }

    private fun serializeSection(pieces: List<ChordProBlock>): String {
        val section = pieces.first() as ChordProBlock.Section
        // A paragraph has no environment of its own, so a label it carries came from the tablature or grid inside
        // it and has to go back onto that; see `SectionBuilder.openLineMode`.
        if (section.type == SectionType.Paragraph) return serializeLines(pieces, section.label)
        val body = serializeLines(pieces)
        ... // header / {end_of_…} exactly as now
    }
```

`serializeLines` takes the pieces and walks their lines and the blocks between them in order. A block is written
inside a tab or grid environment that is open when the next line after it is in that same environment — which is
where the parser found it (the reopened half keeps the environment open, `addBlock` `:309-313`), and which matters for
a Campfire 3 file: a `{comment: Verse 2}` written *outside* the tab it stood in would come back as a heading.

```kotlin
    private fun serializeLines(pieces: List<ChordProBlock>, environmentLabel: String? = null) = buildList {
        // The lines of every piece and the blocks between them, in the order the file had them.
        val items: List<Any> = pieces.flatMap { piece -> if (piece is ChordProBlock.Section) piece.lines else listOf(piece) }
        var openEnvironment: String? = null
        var label = environmentLabel
        items.forEachIndexed { index, item ->
            if (item is ChordProBlock) {
                val nextLine = items.subList(index + 1, items.size).firstOrNull { it is ChordProLine } as ChordProLine?
                if (openEnvironment != null && nextLine?.let { lineEnvironmentName(it, openEnvironment) } != openEnvironment) {
                    add("{end_of_$openEnvironment}")
                    openEnvironment = null
                }
                add(serializeBlock(item))
                return@forEachIndexed
            }
            val line = item as ChordProLine
            val environment = lineEnvironmentName(line, openEnvironment)
            if (environment != openEnvironment) {
                openEnvironment?.let { add("{end_of_$it}") }
                environment?.let { name ->
                    add(label?.let { "{start_of_$name: $it}" } ?: "{start_of_$name}")
                    label = null
                }
                openEnvironment = environment
            }
            add(serializeLine(line))
        }
        openEnvironment?.let { add("{end_of_$it}") }
    }.joinToString("\n")
```

A `ChorusRecall` is written as `{chorus}` / `{chorus: label}` as now; its `blocks` are not written, the parser works
them out again (which is what makes the round trip hold for them).

### 5. Viewer — `SongLyrics.kt:1237-1278`

Rewrite `toRenderSections` so that a continuation has no heading and a recall draws every piece of its chorus, the
heading on the first piece that is shown:

```kotlin
private fun ChordProSong.toRenderSections(
    shouldShowChords: Boolean,
    defaultLabels: DefaultSectionLabels,
): List<RenderSection> {
    val sections = mutableListOf<RenderSection>()

    /** Adds a section, or nothing where lyrics-only mode leaves it with nothing to show; true when it was added. */
    fun addSection(section: ChordProBlock.Section, header: String?): Boolean {
        // A section that is nothing but tablature or a grid goes away entirely in lyrics-only mode, its heading with it:
        // neither says anything without the chords, and a heading over nothing is worse than no heading at all.
        if (!shouldShowChords && section.lines.all { it.needsChords() || it.isBlank() }) return false
        val lines = section.lines.prepareForDisplay(shouldShowChords)
        // A section that ended up with nothing to show is dropped, unless its header still says something.
        if (lines.isEmpty() && header == null) return false
        sections += RenderSection.Lines(header = header, lines = lines, isOnCard = section.type == SectionType.Chorus)
        return true
    }

    blocks.forEach { block ->
        when (block) {
            is ChordProBlock.Break -> Unit // The column layout makes its own breaks.

            is ChordProBlock.Comment -> sections += RenderSection.Comment(text = block.text, style = block.style)

            is ChordProBlock.ChorusRecall -> {
                // The heading goes on the first piece of the chorus that is shown, and stays behind on its own when
                // none is: a recall has always said where the chorus is sung, even with nothing under it.
                var header: String? = block.label ?: (block.blocks.firstOrNull() as? ChordProBlock.Section)?.label ?: defaultLabels.chorus
                block.blocks.forEach { recalled ->
                    when (recalled) {
                        is ChordProBlock.Section -> if (addSection(recalled, header)) header = null
                        is ChordProBlock.Comment -> sections += RenderSection.Comment(text = recalled.text, style = recalled.style)
                        else -> Unit
                    }
                }
                header?.let { sections += RenderSection.Lines(header = it, lines = emptyList(), isOnCard = true) }
            }

            // The rest of a section a comment or a break cut in two was headed where it started.
            is ChordProBlock.Section -> addSection(block, if (block.isContinuation) null else block.header(defaultLabels))
        }
    }
    return sections
}
```

Update the KDoc above it (`:1231-1236`): "A `{chorus}` recall repeats the chorus the parser found for it
(`ChordProBlock.ChorusRecall.blocks`), every piece of it and the comments that cut it, headed once. The continuation
of a section a comment or a break cut in two carries no heading of its own."

## Tests

In `chordpro/src/commonTest` (run with `./gradlew :chordpro:desktopTest`):

- `ChordProParserTest`
  - `the half of a section after a comment is its continuation` — `{soc}\n[C]one\n{comment: softly}\n[G]two\n{eoc}`:
    three blocks; `blocks[0]` has `isContinuation == false`, `blocks[2]` has `isContinuation == true`, same type and
    label. Repeat with `{colb}`, `{ci: x}` and a legacy `{c: Verse 1}\n[G]a\n{ci: x}\n[C]b`.
  - `a recall carries every piece of the last chorus` — the repro above plus `\n{chorus}`: the recall's `blocks` are
    `[Section(Chorus, …[C]one), Comment("softly"), Section(Chorus, …[G]two, isContinuation = true)]`.
  - `a recall with no chorus before it carries nothing`, and `a recall inside a chorus repeats the chorus before it`
    (`{soc}\n[A]first\n{eoc}\n{soc}\n{sot}\ne|-0-|\n{chorus}\ne|-2-|\n{eot}\n{eoc}` — the recall's blocks are the
    first chorus only).
  - Update the three existing assertions that compare a recall by value: `chorus recall is a block of its own`
    (`:155-161`) and `an environment with a selector is the environment it selects` (`:616-627`) now expect
    `ChorusRecall(null, blocks = listOf(<the chorus section>))`; `a chorus recall inside a tab environment…`
    (`:361-366`) keeps `ChorusRecall(null)`, since no chorus precedes it.
- `ChordProSerializerTest`
  - `a chorus cut by a comment comes back as one environment` — the repro: `serialize` writes
    `{start_of_chorus}\n[C]one\n{comment: softly}\n[G]two\n{end_of_chorus}\n\n{chorus}`, and the round trip holds.
  - `a legacy heading name cut into a tab comes back inside the tab` —
    `{sot}\ne|---0---|\n{c: Solo}\ne|---3---|\n{eot}` round trips (the comment stays a comment).
  - The existing `serializing a parsed song and parsing it again yields the same model` must keep passing unchanged;
    it already covers a verse cut by a comment.
- `ChordProTransposerTest`: `a recalled chorus is transposed with the song` — transpose the repro by 2: the recall's
  pieces hold `D` and `A`; and `a recalled chorus is respelled in German notation` via `ChordProNotation.toGerman`.

The viewer has no tests (policy). Compile check:
`./gradlew :presentation:compileKotlinDesktop :presentation:compileKotlinWasmJs :presentation:compileDebugKotlinAndroid :presentation:compileKotlinIosSimulatorArm64`,
then the root unit test command.

## Verification

1. Confirm first. Put the repro from "What the user sees" in the desktop library as `recall-test.cho` with a `{title}`,
   `./gradlew :app:desktop:run`, open it. **Before:** two cards headed "Chorus" around "softly"; the last card shows
   "line two" only.
2. After the fix: the first card is headed "Chorus", "softly" follows, the next card has no heading; the recall at the
   end shows the whole chorus — card headed "Chorus" with "line one", "softly", card with "line two".
3. `{sov: Verse 1}` with a `{ci: x}` in the middle: "Verse 1" once. Same with a `{c: Verse 1}` legacy file.
4. Transpose +2: the recalled pieces move with the chorus. German notation on: a `[B]` in the chorus is `H` in the
   recall as well.
5. Lyrics-only mode with a chorus whose first piece is only a tab: the recall's heading moves onto the lyrics piece.
6. Open the song in the editor, Split pane: the preview agrees.

## Docs

- `chordpro/CLAUDE.md`, the `model/` bullet: after "A `{comment}`, a break or a `{chorus}` inside an environment cuts
  the section in two the way it does anywhere else, and the environment carries on in the second half", add: "The
  second half is marked `isContinuation`: it is the rest of a section the file wrote once, so the viewer does not head
  it again, and the serializer writes the pieces back into one environment with what cut them inside. A
  `ChorusRecall` carries the chorus it repeats (`blocks`) — every piece of the last chorus that was over where it
  stands, and what stood between them — resolved by the parser, so that the transposition and the notation reach it
  like any other block."
- `presentation/CLAUDE.md`, the `SongLyrics.kt` bullet: "(sections, chorus recall, …)" — add after the first
  sentence: "A recall draws the chorus `ChordProBlock.ChorusRecall` carries, every piece of it, headed once; the
  continuation of a cut section has no heading."
- `documentation/file-format.md:37-38` ("`{chorus}` to repeat the last chorus") is still true; no change.

## Files touched

- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/model/ChordProBlock.kt`
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProParser.kt`
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProSerializer.kt`
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposer.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProParserTest.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProSerializerTest.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposerTest.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongLyrics.kt`
- `chordpro/CLAUDE.md`, `presentation/CLAUDE.md`

## Depends on

Nothing. **Lands first in lane A**: 02, 03 and 05 build on the `isContinuation` flag, the serializer's piece walk and
`rewriteBlock` introduced here. Landing order for the lane: 01 → 03 → 02 → 05 → 06 → 04 → 08; 07 is independent.
