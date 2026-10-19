# Count a chorus recalled after a key change as a part of its own

**Challenged:** amended — said what a recall part's `kind` is (`Part.kind` reads `sections.first()`, which a
`ChorusRecall` block does not have; without it a second transposed recall cannot find the first by kind) and that the
existing `SongScaleGroupsTest` helper `ScaleGroup.labels` (`SongScaleGroupsTest.kt:22`, `sections.map { … }`) and plan
02's and 07's tests move to `blocks`. Checked: a recall with no `{transpose}` adds no chords (01's scores and fits
unchanged); 07's "Chorus (1, 2)" / "Chorus (3)" needs exactly this recall-in-the-chorus's-group shape; the presentation
compiles through `songScaleGroupsOf` once its heading walk keeps the `isContinuation` filter for sections only.

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/SongScaleGroups.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/ScaleGroup.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/CLAUDE.md`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/scales/SongScaleGroupsTest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/model/SongScaleGroups.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/model/SongRenderSections.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songLayout/DefaultSectionLabels.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/songView/model/SongScaleGroupsTest.kt`

## Problem

`SongScaleGroups.partsOf` skips every recall:

```kotlin
// SongScaleGroups.kt:98
song.blocks.forEach { block ->
    when (block) {
        is ChordProBlock.Transpose -> key = block.key?.let(::rootOf) ?: key
        is ChordProBlock.Section -> { … }
        else -> Unit
    }
}
```

The commonest modulation is the last chorus a step up, written `{transpose: 2}` then `{chorus}`.
`ChordProChordRewriter.rewriteChords` rewrites a recall's blocks at the offset in force where the recall stands
("which is what makes a chorus recalled after `{transpose: 2}` the key change it is written as",
`ChordProChordRewriter.kt:74-77`), so the page draws that chorus in D. The Scales section does not see it. Measured at
HEAD: `{key: C}`, verse `C F G C`, chorus `F G C`, `{transpose: 2}`, `{chorus}`, run through
`ChordProTransposer.transpose(song, 0)` as a page does — the blocks are `[Section, Section, Transpose key=D,
ChorusRecall]` and `SongScaleGroups.of` gives **one group, C, two sections**: the D chorus has no scales.

## Fix

**When a recall is a part.** A recall is counted as a part of its own when its chords, as they stand, differ from
those of the chorus it repeats — that is, from the chord names of the most recent non-continuation `Chorus` part's
sections (kept in a local while walking). A recall that plays the same chords (no transpose, or a `{transpose: 0}`) is
still not counted twice: it is appended to that chorus's part as one more block, its chords **not** added again (plan
01's scores and the fit stay what they are without it), so the group that holds the chorus also names its recalls —
which plan 07's labels need to tell "Chorus (1, 2)" from "Chorus (3)". This rule reads the song as it stands, like the rest of `SongScaleGroups`: in a song the
transposer has not been through, the recall's chords equal the chorus's and nothing changes (the existing test `a
transpose is read for the key it names…` keeps its untransposed case at one group). Its key is the key in force
(`key`, as after a `Transpose`). Its chords are those of every `Section` in `recall.blocks`.

**How it is carried.** `ScaleGroup.sections: List<ChordProBlock.Section>` cannot say "this is a recall", and its
heading is the recall's, not the section's. Options:

- (1) **recommended:** `ScaleGroup.sections` becomes `blocks: List<ChordProBlock>`, documented as "each a
  `ChordProBlock.Section` or a `ChordProBlock.ChorusRecall`, the very instances of the song's blocks"; `Part` keeps a
  `blocks` list the same way.
- (2) a sealed `ScalePart` (`SectionPart`, `RecallPart`) in `:chordpro`: more types for the same information.
- (3) a copy of the recall's first section with the recall's label: breaks "the very instances", and the copy's number
  would be the chorus's.

**How it is labelled.** The page heads a recall with `block.label ?: recalled?.header(defaultLabels) ?:
defaultLabels.chorus` (`SongRenderSections.kt:136-138`). Move that rule into one function in `DefaultSectionLabels.kt`,
`internal fun ChordProBlock.ChorusRecall.header(defaultLabels: DefaultSectionLabels): String`, call it from
`toRenderSections` (behaviour unchanged) and from `songScaleGroupsOf`, which heads a recall block with it and a section
with `header(labels)` as now. So a `{chorus: Final chorus}` reads "Final chorus", and a bare `{chorus}` reads as its
chorus ("Chorus", or "Chorus 2" where `withNumberedSections` numbered it, since it gives the recall's first section the
chorus's number). Of the options — the chorus's heading again, or a new "Final chorus" string — the page's own heading
is recommended: the label must name what the page shows. Two groups then can both read "Chorus"; plan 07 disambiguates
repeated titles generally.

`kindOf` and the kind counting in `songScaleGroupsOf` treat a recall's heading like any other heading. The heading
walk becomes `group.blocks.mapNotNull { block -> when (block) { is Section -> block.takeIf { !it.isContinuation
}?.header(labels); is ChorusRecall -> block.header(labels); else -> null } }`: the continuation filter stays on sections.

In `:chordpro`, `Part.kind` reads the first `ChordProBlock.Section` among the part's blocks (for a recall part, the first
section of `recall.blocks`, the chorus's own), so a recall part is of its chorus's kind: a second recall after the same
`{transpose}` joins the first recall's group by the kind rule (its chords differ from the chorus's too, since the
comparison is always with the last chorus *section*, never with a recall).

Update the `SongScaleGroups` KDoc ("a chorus recall left out since it plays the chorus again") and the scales
`CLAUDE.md` sentence "Chorus recalls are not counted again" to: "A chorus recall is counted again only where its chords
differ from the chorus's (one after a `{transpose}`)".

## Tests

`SongScaleGroupsTest`: the helper `ScaleGroup.labels` (line 22) reads `blocks.filterIsInstance<ChordProBlock.Section>()`
(or maps a recall to its label), and every `.sections` in plan 02's `ScaleExceptionsTest` and plan 01's new tests reads
`.blocks`.
- `two recalls after one transpose are one group` — `{transpose: 2}`, `{chorus}`, `{chorus}`, transposed: two groups,
  the second's `blocks` both recalls.
- `a chorus recalled after a transpose is a part in the new key` — the song above through
  `ChordProTransposer.transpose(song, 0)`: two groups, roots `C` and `D`, the second's `blocks` a single
  `ChordProBlock.ChorusRecall`; its first scale major.
- `a chorus recalled in its own key is not counted twice` — the same without `{transpose: 2}`: one group whose blocks are
  the verse, the chorus and the recall, with the same root and scales as the song without the recall.
- `an untransposed song reads its recall as written` — the first song not through the transposer: one group, C.

Presentation `SongScaleGroupsTest`:
- `a recall is named as the page heads it` — `{key: C}` + verse + chorus + `{transpose: 2}` + `{chorus: Final chorus}`
  (transposed with `ChordProTransposer.transpose(song, 0)` first): labels `["Verse, Chorus", "Final chorus"]`; with a
  bare `{chorus}` the second label is `Chorus` (plan 07 then makes the two distinct).
