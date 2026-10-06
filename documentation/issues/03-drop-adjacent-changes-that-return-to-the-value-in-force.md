# Drop a merged tempo or time change that returns to the values in force before it

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Challenged:** amended — `rejoin()` also restores the trailing blank lines the cut's `close()` trimmed and accepts a continuation that holds only blank lines (both made the "same as without the group" tests fail inside an environment); tests for both added.
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProParser.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProParserTest.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProSerializerTest.kt`, `chordpro/CLAUDE.md`

## Problem

Changes with no line of the song between them are merged into one `ChordProBlock.Timing`, the later replacing the
earlier (`ChordProParser.kt:360-366` at 1c52e5347):

```kotlin
private fun addTiming(change: ChordProBlock.Timing, blocks: MutableList<ChordProBlock>, section: SectionBuilder) {
    if (blocks.lastOrNull() is ChordProBlock.Timing && !section.hasContentLine) blocks[blocks.lastIndex] = change else section.addBlock(change)
}
```

`TimingChanges.consume` only compares each directive with the value in force *after* the earlier one of the group,
so a group whose second line returns to what was in force before the first one leaves a `Timing` that changes
nothing. Verified at 1c52e5347: `{tempo: 100}\n{time: 4/4}\n\nla\n{tempo: 120}\n{tempo: 100}\nlo` parses to
`[Section(la), Timing(tempo=100, time=4/4), Section(lo, isContinuation=true)]`. The song details screen draws a
timing line and starts a page there for nothing (the presentation's `timingStarts`), the PDF prints the line, and a
playing click restarts from beat one on that page. It also contradicts the module's rule that "one that restates the
value in force changes nothing" (the `TimingChanges` KDoc), and `ChordProSerializer` writes nothing for it (both
sides equal the in-force ones, so it falls back to `{time: 4/4}`), which `parse` reads as no change — so
`parse(serialize(parse(x))) == parse(x)` fails for this input.

## Fix

Remember what was in force before a group of changes, and drop the group's block when the merged change equals it:

- In `TimingChanges`, add `var beforeGroup: ChordProBlock.Timing? = null` and a way to read the values in force now
  (`fun inForce(metadata: MetadataBuilder) = ChordProBlock.Timing(tempo ?: metadata.tempo.value?.takeIf { ChordProTempo.parse(it) != null }, time ?: …)`,
  the same expressions `consume` computes as `inForceTempo` / `inForceTime`), plus a `fun restore(timing: ChordProBlock.Timing)`
  that sets the `tempo` / `time` fields back. Note that `TimingChanges.tempo` / `time` are null until the body changes
  them, so restoring stores the snapshot's values; that is equivalent, since `consume` falls back to the metadata only
  when they are null and the snapshot already holds that fallback.
- In `handleDirective`, before calling `consume`, capture `val before = timing.inForce(metadata)` and pass it to
  `addTiming(change, before, blocks, section, timing)`.
- In `addTiming`: when a *new* block is added (the `else` branch), set `timing.beforeGroup = before`. In the replace
  branch, compare the merged `change` with `timing.beforeGroup` as numbers (`ChordProTempo.parse` and
  `ChordProTime.parse` equality, as `consume` does, so `{time: C}` equals `4/4`); if both sides are equal, remove the
  block (`blocks.removeAt(blocks.lastIndex)`) and `timing.restore(beforeGroup)`, otherwise replace it as today.

The first change of the group already cut the running section (`SectionBuilder.addBlock`: `close()`, the block,
then `open(type, label, isExplicit, isContinuation = true)`), so removing the block alone leaves
`[Section(la), Section(lo, isContinuation = true)]` with nothing between them — which `ChordProSerializer` writes as
`la\nlo` and `parse` reads back as one section, so the round trip would still fail. Undo the cut as well: add
`SectionBuilder.rejoin()`, called after the removal, which — when the builder is a continuation (`isContinuation`)
that has no *content* line yet (`!hasContentLine`; it may already hold blank lines, which an explicit section or an
open tab keeps between two changes of a group) and `blocks.last()` is a `ChordProBlock.Section` of the builder's type
and label — removes that section from `blocks` and takes its lines back, keeping the open `lineMode` the cut carried
over.

The cut lost lines that have to come back too: `close()`, which `addBlock` calls to emit the first half, trims the
trailing blank lines of an explicit section or an open tab (`{sov}\nla\n\n{tempo: 120}\n{tempo: 100}\nlo` emits
`Section(la)` without the blank). So record how many blank lines that `close()` trimmed (e.g. `close()` stores the
count in a private `trimmedBlankLineCount`, which `addBlock` reads after it and carries over the reopen the way it
carries `lineMode`), and rejoin as `lines = removed.lines + List(trimmedBlankLineCount) { ChordProLine.Blank } + lines`
(the blanks the continuation already holds coming last), `isContinuation = removed.isContinuation`,
`hasEmittedLines = removed.isContinuation` (that is what `open` sets it to, and nothing else does),
`hasContentLine = true`. The `lineModeComments` indices stay valid, since only the last two blocks were removed and
those comments stand before them. Where the section was not cut (the group stood between sections, or the change came
right after a `{start_of_…}` with no line yet, so `addBlock` emitted nothing) there is nothing to rejoin.

Keep the `addTiming` KDoc's rule and add: "and a group that brings the song back to what was in force before it is no
change at all". Add the same clause to the `ChordProParser` entry of `chordpro/CLAUDE.md` after "a second one with no
line between them replaces the first, so a `{tempo}` followed by a `{time}` is one block".

## Tests

In `ChordProParserTest`:

- `{tempo: 100}\n{time: 4/4}\n\nla\n{tempo: 120}\n{tempo: 100}\nlo` parses to the same blocks as
  `{tempo: 100}\n{time: 4/4}\n\nla\nlo` (one section, no `Timing`); likewise with the group inside a
  `{start_of_verse}` and inside a `{start_of_tab}` (compared with the same text without the group), each also with a
  blank line before the group (`la\n\n{tempo: 120}\n{tempo: 100}\nlo`) and with one between its two lines
  (`la\n{tempo: 120}\n\n{tempo: 100}\nlo`), which the section must keep as `ChordProLine.Blank` where the text without
  the group has it.
- `{tempo: 100}\n\nla\n\n{tempo: 120}\n{tempo: 100}\n\nlo` (the group between sections) parses like
  `{tempo: 100}\n\nla\n\nlo`.
- `{tempo: 100}\n{time: 4/4}\n\nla\n{tempo: 120}\n{time: 3/4}\n{tempo: 100}\nlo` has exactly `Timing(tempo=100, time=3/4)`.
- `{time: 4/4}\n\nla\n{time: 3/4}\n{time: C}\nlo` has no `Timing`.
- `{tempo: 100}\n\nla\n{tempo: 120}\n{tempo: 100}\nlo\n{tempo: 90}\nli` has exactly `Timing(tempo=90, time=null)`.
- The existing `changes with no line between them are one` keeps passing.

In `ChordProSerializerTest`, add the first input to the plain round-trip list (the one asserting
`parse(serialize(parse(x))) == parse(x)`), not to `a tempo or time change survives serializing`, which also asserts
that a `Timing` exists.

## Manual check

On the song details screen, open a song whose body has `{tempo: 120}` directly followed by `{tempo: <the song's own
tempo>}`: no timing line or page break appears there, and a playing click does not restart.
