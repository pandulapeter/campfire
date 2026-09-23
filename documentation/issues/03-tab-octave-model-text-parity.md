# 03 — Two tab environments in one section move by different octaves in the viewer and in the editor

**Severity:** wrong transposition (all platforms, uncommon input) · **Area:** `:chordpro` (`ChordProParser`,
`ChordProTransposer`, `ChordProSerializer`, `model/ChordProLine.kt`)

**Read, not run.** Found by reading the two transposition paths at HEAD (2065e47f). The unit test below reproduces it
without the app; write it first and see it fail.

## What the user sees

A section holding two `{start_of_tab}` environments with nothing but a blank line (or nothing at all) between them:

```
{start_of_verse: Solo}
{start_of_tab}
e|--0--|
{end_of_tab}

{start_of_tab}
e|--12--|
{end_of_tab}
{end_of_verse}
```

Transposed down one semitone in the **viewer**, the two tabs are moved as one fingerboard: `{0, 12}` cannot go down
without the `0` falling off, so the whole of it goes up an octave instead, to `11` and `23`. The **editor's**
"Transpose" action on the same text moves them as two fingerboards: the first up an octave (`11`), the second simply
down (`11`). The file the editor writes then no longer shows what the viewer showed, and `chordpro/CLAUDE.md`
promises the two agree ("the transposer makes one octave decision for it on the model as it does in the text").

## Cause

The model path joins every run of tab lines of a section that only blank lines separate —
`ChordProTransposer.kt:111-120`:

```kotlin
        lines.forEach { line ->
            when {
                line is ChordProLine.Tab -> run += line
                line == ChordProLine.Blank && run.isNotEmpty() -> run += line
                else -> {
                    flushRun()
                    rewritten += rewriteLine(line, rename)
                }
            }
        }
```

That is right for a blank line *inside* one environment (the documented case), but the model cannot tell it from a
blank line *between* two environments: the parser produces `[Tab, Blank, Tab]` for both
(`ChordProParser.kt:319-337` adds a `Blank` in an explicit section and a `Tab` per line, and `{end_of_tab}` /
`{start_of_tab}` inside a section only switch `lineMode`, `:129-132`, `:139`). Two environments written back to back
(`{eot}` then `{sot}`) come out as `[Tab, Tab]`, one run.

The text path ends a run at every environment boundary — `ChordProTransposer.kt:233-241`:

```kotlin
                    ChordProSyntax.startOfEnvironment(directive.name)?.let {
                        lines.transposeTab(tabLineIndices, semitones, rename)
                        environment = it.lowercase()
                    }
                    ChordProSyntax.endOfEnvironment(directive.name)?.let {
                        lines.transposeTab(tabLineIndices, semitones, rename)
                        environment = null
                    }
```

## The change

Invoke the **`code-style`** skill before the first edit. The reviewer's option (a) — the model records where an
environment starts — is the right one; the text path cannot know which section it is in. The flag is put the other way
round from the reviewer's `startsEnvironment`, so that a one-line tab (almost every test and most files) still equals
`ChordProLine.Tab(text)`:

### 1. Model — `model/ChordProLine.kt:32`

```kotlin
    data class Tab(
        val text: String,
        /**
         * Whether the tab line before this one in the section was written in the same `{start_of_tab}` environment.
         * A run of tablature is moved as one fingerboard for as long as this holds; a second environment in the same
         * section, even one only a blank line away, is a fingerboard of its own, as it is to the text transposition.
         */
        val continuesEnvironment: Boolean = false,
    ) : ChordProLine
```

### 2. Parser — `ChordProParser.kt`, `SectionBuilder`

```kotlin
        /** Whether the tab environment that is open has had a line yet, see [ChordProLine.Tab.continuesEnvironment]. */
        private var hasTabLine = false
```

- `openLineMode` (`:267-270`): `hasTabLine = false` (a new environment starts).
- `open` (`:252-258`) and `close` (`:276-292`): `hasTabLine = false`.
- `addBlock` (`:301-317`): the reopened half keeps the environment the file is in the middle of, so it keeps this as
  well — save it next to `lineMode` and restore it after `open(...)`:

  ```kotlin
                val lineMode = this.lineMode
                val hasTabLine = this.hasTabLine
                close()
                blocks += block
                open(type, label, isExplicit, isContinuation = true)
                this.lineMode = lineMode
                this.hasTabLine = hasTabLine
  ```

  (the rest of the model splits there anyway — each section is transposed on its own — so this only decides what the
  serializer writes back, below).
- `addContent` (`:331-336`):

  ```kotlin
                LineMode.TAB -> ChordProLine.Tab(rawLine, continuesEnvironment = hasTabLine).also { hasTabLine = true }
  ```

### 3. Transposer — `ChordProTransposer.kt:97-123`

```kotlin
        lines.forEach { line ->
            when {
                line is ChordProLine.Tab -> {
                    // A second environment is a fingerboard of its own, whatever stands between the two.
                    if (!line.continuesEnvironment) flushRun()
                    run += line
                }
                line == ChordProLine.Blank && run.isNotEmpty() -> run += line
                else -> {
                    flushRun()
                    rewritten += rewriteLine(line, rename)
                }
            }
        }
```

`flushRun` (`:105-110`) rebuilds each tab line as `ChordProLine.Tab(tabLines.next())`, which would drop the flag and
make `parse(x)` and `transpose(parse(x), 0, forced)` differ in more than the chords. Keep it:

```kotlin
            rewritten += run.map { line -> if (line is ChordProLine.Tab) line.copy(text = tabLines.next()) else line }
```

Blank lines collected at the end of the previous run stay with it, which is where the file had them. Update the KDoc
of `rewriteLines` (`:90-96`): "A blank line does not end a run, a second environment does: …".

### 4. Serializer — `ChordProSerializer.kt`, `serializeLines`

Two environments with nothing between them would otherwise be written back as one and come back as one run. In the
line branch of the piece walk from plan 01:

```kotlin
            val line = item as ChordProLine
            val environment = lineEnvironmentName(line, openEnvironment)
            // A tab line that starts an environment of its own is written in one, even straight after another.
            val startsTab = line is ChordProLine.Tab && !line.continuesEnvironment && openEnvironment == "tab"
            if (environment != openEnvironment || startsTab) { … close and open as now … }
```

and in the block branch, keep a tab open across a block only when the tab line after it continues the environment
(`(nextLine as? ChordProLine.Tab)?.continuesEnvironment == true`), a grid when the next line is a grid line as in plan
01. Without plan 01 the same two conditions go into today's `serializeLines` (`:81-97`).

## Tests

`chordpro/src/commonTest`, `./gradlew :chordpro:desktopTest`:

- `ChordProTransposerTest`: `two tabs in one section are two fingerboards in the model as in the text` — the repro
  above and the same with `{eot}\n{sot}` adjacent:

  ```kotlin
  assertEquals(
      ChordProTransposer.transpose(ChordProParser.parse(text), -1, preferFlats = false),
      ChordProParser.parse(ChordProTransposer.transposeText(text, -1, preferFlats = false)),
  )
  assertEquals(listOf("e|--11-|", "e|--11--|"), ChordProTransposer.transpose(ChordProParser.parse(text), -1, preferFlats = false).tabLines())
  ```

  (`tabLines()` is the helper the file already has.) The existing
  `a tab with a blank line in it is one fingerboard in the model as in the text` (`:419-431`) must keep passing — one
  environment, one decision.
- `ChordProParserTest`: `the lines after the first of a tab environment continue it` —
  `{sot}\ne|-0-|\ne|-2-|\n{eot}` gives `Tab("e|-0-|")`, `Tab("e|-2-|", continuesEnvironment = true)`; two
  environments give `false` on both first lines. Update the assertions on the half after a comment inside a tab, which
  now continues the environment: `a comment inside a tab environment does not end the tablature` (`:318-329`,
  `secondSection.lines[0]`), `a legacy heading name inside a tab environment stays a comment` (`:344-350`),
  `a chorus recall inside a tab environment does not end the tablature` (`:361-366`) and the tab half of
  `a highlight is shown as a comment and never taken for a heading` (`:638-648`) expect
  `ChordProLine.Tab(…, continuesEnvironment = true)`.
- `ChordProSerializerTest`: `two tabs in one section come back as two` — the repro round trips, and
  `{sov}\n{sot}\ne|-0-|\n{eot}\n{sot}\ne|-2-|\n{eot}\n{eov}` serializes with the `{end_of_tab}\n{start_of_tab}` between.

## Verification

1. The failing unit test above is the confirmation.
2. In the app: the repro as a song, transpose −1 in the viewer and note the frets (`11`, `11`; `11` and `23` before the fix); open the editor,
   Transpose −1, save; the viewer at 0 shows the same frets.

## Docs

`chordpro/CLAUDE.md`, the `model/` bullet: "A blank line inside an environment does not end its run for either of
the first two: … the transposer makes one octave decision for it on the model as it does in the text." — append: "A
second environment does end it, even one only a blank line away or written straight after the first:
`ChordProLine.Tab.continuesEnvironment` is what tells the two apart, and the serializer writes the boundary back."

## Files touched

- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/model/ChordProLine.kt`
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProParser.kt`
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposer.kt`
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProSerializer.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposerTest.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProParserTest.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProSerializerTest.kt`
- `chordpro/CLAUDE.md`

`:presentation` constructs no `Tab` and matches it by type only (`SongLyrics.kt:467, 518, 537, 1290, 1317, 1322`),
so nothing there changes.

## Depends on

**01** (lands after it): the `addBlock` reopen with `isContinuation = true` and the serializer's piece walk are 01's.
It can land alone if the two snippets are applied to HEAD's `addBlock` and `serializeLines` instead. Must land
**before 02**, which touches the same `rewriteLines` / `rewriteChords` code.
