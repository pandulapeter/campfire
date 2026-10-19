# Keep two scale groups from sharing a title, and claim a kind only where a group holds all of it

**Challenged:** amended — "%1$s and the parts without a heading" over a label of two or more names ("Verse, Chorus
and the parts without a heading") breaks the project's serial-comma rule for UI text; a second string for a label of
several names added. Checked sound: two verses held by one group stay "Verse" (fix B numbers only a heading that also
occurs outside the group); with numbering on, distinct headings get no numbers. Known oddity, accepted: numbering on,
a chorus recalled in key and a step up gives "Chorus 2 (1, 2)" / "Chorus 2 (3)" where the song has two chorus sections.

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/model/SongScaleGroups.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/SongScalesSection.kt`,
`presentation/src/commonMain/composeResources/values/strings.xml`,
`presentation/src/commonMain/composeResources/values-hu/strings.xml`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/SongScaleGroups.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/scales/SongScaleGroupsTest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/CLAUDE.md`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/songView/model/SongScaleGroupsTest.kt`

Runs after 02 (a section below the floor still gets no group, so "a group titled Verse while verse 3 is in none" stays
possible, only rarer — nothing here is moot) and 05 (`ScaleGroup.blocks` holds sections and recalls, a recall that
plays its chorus's chords sits in that chorus's group, and `ChorusRecall.header(labels)` heads a recall as the page
does). Plan 06 is withdrawn, so `SongScaleGroup` keeps its `rootName` and `scales`; this plan touches neither.

## Problem

`songScaleGroupsOf` (`SongScaleGroups.kt:41-58`, presentation) names each group by its sections' page headings:

```kotlin
val headers = groups.map { group -> group.sections.filter { !it.isContinuation }.map { it.header(labels) }.filter { it.isNotEmpty() } }
val kindCounts = headers.flatten().groupingBy(::kindOf).eachCount()
return groups.mapIndexed { index, group ->
    val ofKind = headers[index].groupingBy(::kindOf).eachCount()
    val names = headers[index].map { header ->
        val kind = kindOf(header)
        if (ofKind.getValue(kind) > 1 && kindCounts[kind] == ofKind[kind]) kind else header
    }
    SongScaleGroup(label = names.distinct().joinToString(", ").ifEmpty { null }, …)
}
```

1. **Two groups, one title.** The distinction rests on `withNumberedSections` numbering the headings. With section
   numbering off (`DefaultSectionLabels.shouldNumberSections = false`, which makes `withNumberedSections` return the
   blocks as they are) three verses are "Verse", "Verse", "Verse", so verses split between groups give two groups both
   titled "Verse". With numbering on, the same happens wherever the numbering does not reach: a file's own label
   repeated ("Refrain" ×3 — only unlabelled sections and the kind's own name are numbered), a Hungarian file's
   "Versszak" read with the English UI (a label in the other language is not the kind), two tab-only paragraphs headed
   "Tab". After 05, a chorus recalled a step up is a second group headed "Chorus", like the first.
2. **Two headless groups**, e.g. a pasted plain-text song (paragraphs only) that modulates: both labels are null and
   `SongScalesSection` shows each as `song_details_scales_other`, "The rest of the song".
3. **A kind claimed that is not all there.** `kindCounts` counts only headings that landed in a group; a section no
   scale holds is in none (02's floor). Verses 1–2 fit C, verse 3 fits nothing → the group is titled "Verse" as if
   verse 3 were in it.
4. **Headless sections dropped from a mixed label**: `filter { it.isNotEmpty() }` drops the headless paragraphs
   (`UNNAMED_SECTION_HEADER` is `""`), so a group of "Chorus" and two unnamed paragraphs is titled "Chorus" only.

## Fix

**A. Count over every section that plays a chord.** Add to `:chordpro` one public function,
`SongScaleGroups.playsChords(block: ChordProBlock): Boolean` — true for a `Section` whose lines play a chord (the
private `chordNames()` walk, so the two cannot disagree) and for a `ChorusRecall` whose sections do. In
`songScaleGroupsOf`, the song's *played headings* are, in play order, the header of every non-continuation section and
every recall for which `playsChords` is true (`header(labels)` / 05's `ChorusRecall.header(labels)`, empty ones
included as unnamed). `kindCounts` is taken over these instead of over the groups' headings.

**B. Number a heading another group or no group also has.** After the names are built as today (a kind held whole
said once), each name that is still a plain heading and occurs among the played headings outside this group gets the
1-based occurrence numbers of this group's blocks among the played headings equal to it, in play order:
`"$heading (${numbers.joinToString(", ")})"`. A kind said once is never numbered (it is held whole by definition).

- Numbering off, verses split 2 / 1: "Verse (1, 2)" and "Verse (3)".
- "Refrain" ×3, "Versszak" ×3, two "Tab" paragraphs: the same shape.
- Chorus, a recall in the key, a recall a step up (after 05): "Verse, Chorus (1, 2)" and "Chorus (3)".
- Verses 1–2 in C, verse 3 in no group: numbering on "Verse 1, Verse 2" (unchanged), numbering off "Verse (1, 2)".

The numbers and parentheses are language-neutral and need no string; in Hungarian "Versszak (1, 2)" reads as item
numbers. (Alternative, not recommended: a format string per locale for ordinals, "1st and 2nd verse" / "1. és 2.
versszak", which needs ordinal rules in English.)

**C. Name a headless group by where it starts, where there are several.** `SongScaleGroup` gains
`opening: String?`: for a group with no named heading, when more than one group has none, the first lyric of its first
block (the rule `RenderSection.Lines.firstLyric` uses: the first `ChordProLine.Lyrics` text, trimmed, non-empty), cut
to 32 characters at a word boundary with "…". `SongScalesSection` titles such a group
`stringResource(Res.string.song_details_scales_from, opening)` instead of `song_details_scales_other`; a lone headless
group keeps "The rest of the song", which is then true.

**D. Say that a mixed group also holds unnamed parts.** `SongScaleGroup` gains `hasUnnamed: Boolean` (a played heading
of the group is empty while another is not); `SongScalesSection` titles it
`stringResource(Res.string.song_details_scales_and_unnamed, label)` where the label is one name, and
`stringResource(Res.string.song_details_scales_and_unnamed_several, label)` where it joins several (the serial comma:
"Verse, Chorus, and the parts without a heading"). `SongScaleGroup` carries whether its label joins several names, so the UI
never tests the joined text for a comma (a heading may hold one).

Strings, after `song_details_scales_other` (with a comment each, as the file's neighbours have):
- `values/strings.xml`: `<string name="song_details_scales_from">From “%1$s”</string>` and
  `<string name="song_details_scales_and_unnamed">%1$s and the parts without a heading</string>` and
  `<string name="song_details_scales_and_unnamed_several">%1$s, and the parts without a heading</string>`
- `values-hu/strings.xml`: `<string name="song_details_scales_from">Innen: „%1$s”</string>` and
  `<string name="song_details_scales_and_unnamed">%1$s és a cím nélküli részek</string>` and
  `<string name="song_details_scales_and_unnamed_several">%1$s és a cím nélküli részek</string>` (Hungarian puts no
  comma before "és")

Update the KDoc of `songScaleGroupsOf` and `SongScaleGroup.label`, and `songView/CLAUDE.md` "Its groups are…": add
"a heading another group also has is numbered by occurrence ("Verse (3)"), several headless groups are named by their
first words".

## Tests

Presentation `SongScaleGroupsTest` (a second `labels` with `shouldNumberSections = false`):

- `verses split between groups are told apart without numbering` — numbering off, `{key: C}` + verse + verse + a D
  verse: labels `["Verse (1, 2)", "Verse (3)"]`.
- `a repeated label the numbering does not reach is numbered by occurrence` — three `{start_of_verse: Refrain}`, the last
  in D, numbering on: `["Refrain (1, 2)", "Refrain (3)"]`.
- `a kind is said once only where the group holds all of it` — numbering off, verses 1–2 in C, verse 3 whose chords no
  scale holds (e.g. `[G][B][C][Cm]`, below 02's floor), plus a D chorus: the first group's label is `Verse (1, 2)`.
- `a chorus recalled a step up is told from the chorus` — after 05's song (chorus, `{chorus}`, `{transpose: 2}`,
  `{chorus}`, transposed): `["Verse, Chorus (1, 2)", "Chorus (3)"]`.
- `several headless groups are named by their first words` — a paragraph-only song, the second paragraph in D: both
  labels null, `opening` the first lyric of each, cut at 32 characters.
- `a group with named and unnamed parts says so` — a named chorus and an unnamed paragraph in C, a D bridge:
  the first group's `hasUnnamed` is true and its label one name; with a named verse added to the C group, the label
  joins several names.
- Keep every existing test green (numbering on, distinct headings: no numbers added).

`:chordpro` `SongScaleGroupsTest`: `a section plays chords where any of its lines do` — `playsChords` true for a
lyrics line with a chord, a grid, a recall of a chorded chorus; false for a chordless verse, a recall of nothing.
