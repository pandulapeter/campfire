# Count a label that is just the kind's name ("Verse", "Verse 1") as one of the kind when numbering sections, so numbers never repeat

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SectionNumbering.kt, presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongLyrics.kt, presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SectionNumberingTest.kt, presentation/CLAUDE.md, presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayout.kt, presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/RenderSectionsTest.kt

**Challenged:** amended — a recall of a chorus with a bare kind label ("Chorus") now gets the number of the chorus it repeats (the recall branch numbers a bare-labelled first section too, and both recall headers, screen and PDF, become `block.label ?: recalled.header(…)`), which the plan had left reading "Chorus" under a "Chorus 2"; kind-label numbers capped at four digits with `toIntOrNull` (no overflow crash); the language-switch consequence (numbers, never folds, can shift) written into the KDoc; noted that `PrintLayout.sectionRows` already appends the number to a label; two tests added; written on top of plan 30.

## Problem

`withNumberedSections` (SectionNumbering.kt:26–63) numbers only sections with no label:

```kotlin
val counts = filterIsInstance<ChordProBlock.Section>().filter { it.isNumbered() }.groupingBy { it.type.numberingKey }.eachCount()
…
private fun ChordProBlock.Section.isNumbered() = label == null && !isContinuation && type != SectionType.Paragraph
```

A label that already *is* a kind name and number does not reserve that number, so a file that labels some sections and not others (common in hand-written and imported songs) shows duplicates. Proven at 800ebde0b with "Number sections" on (the default):

- `{sov: Verse 1}…{eov}{sov}…{eov}{sov}…{eov}` → "Verse 1", "Verse 1", "Verse 2".
- `{sov: Verse}…{eov}{sov}…{eov}` → "Verse", "Verse" (one unlabelled verse alone is not numbered).

The headers come from `ChordProBlock.Section.header` (SongLyrics.kt:2378), `label ?: when (type) { … }.withNumber(number)`, so a labelled section never shows a number.

## Fix

This is partly a product choice (flagged as a Decision). Options:

- **A (recommended): a label that names the kind is a member of the kind.** A label is a *kind label* when, trimmed and compared ignoring case, it is one of the kind's names — the heading the app gives an unlabelled section of that kind in the current language (`DefaultSectionLabels.verse` / `chorus` / `bridge`, `labelOf(Custom)`), or the kind's ChordPro name (`verse`, `chorus`, `bridge`, a custom kind's name with `_` read as a space) — optionally followed by whitespace and a number (`Regex("""^(.+?)(?:\s+(\d+))?$""")`, the first group matched against the names). Then:
  - A kind label **with** a number N keeps its label and reserves N for its kind.
  - A kind label **without** a number ("Verse") is numbered like an unlabelled section; it keeps its label as written (folds are keyed by it) and shows `label.withNumber(number)`.
  - A kind is numbered when it has more than one member (unlabelled sections, bare kind labels and numbered kind labels together).
  - Numbers are dealt in order with a running counter per kind: a numbered kind label sets the counter to `max(counter, N)`; an unlabelled or bare-labelled section takes the next number above the counter that no numbered kind label of the kind reserves. So `Verse 1, -, -` → "Verse 1", "Verse 2", "Verse 3"; `-, Verse 2, -` → "Verse 1", "Verse 2", "Verse 3"; `Verse, -` → "Verse 1", "Verse 2". Any other label ("Intro words") still takes no part, so the existing test stays green.
  - Recalls: a recall of a chorus labelled "Chorus 2" already shows that label. A recall of a chorus with a **bare** kind label ("Chorus", numbered 2 by the rule above) must show "Chorus 2" like the chorus it repeats — today's recall branch only numbers a recalled section whose label is null, and the parser's recalled copy carries no number, so without the change in step 1b below it would read "Chorus" under a "Chorus 2".
- **B: stop numbering a kind once any of its sections has a kind label.** Simpler, never wrong, but a song that labels only its first verse "Verse 1" loses all numbering.

Implementation of A:
Plan 30 lands first and changes the recall branch and the two recall headers; the steps below are written on top of it.

1. In `SectionNumbering.kt`, add a private `ChordProBlock.Section.kindLabel(labels): KindLabel` (sealed result: `None` = not a kind label, `Bare`, `Numbered(n)`), using a private `SectionType.kindNames(labels): Set<String>` (lowercase) built from the same names `header` uses plus the ChordPro name. Match the trimmed label with `Regex("""^(.+?)(?:\s+(\d{1,4}))?$""")` and read the number with `toIntOrNull()` (a label such as "Verse 99999999999" must not throw; with the digit cap it is simply not a kind label). Continuations and paragraphs are never members, as today. Replace `isNumbered()` with a membership check: unlabelled (as today) or `Bare`. Compute `counts` over members plus `Numbered` labels, collect `reserved: Map<key, Set<Int>>`, and deal numbers with the running counter described above. A member chorus (unlabelled or `Bare`) sets `lastChorusNumber` to its number as today; any other chorus start resets it to null as today.
1b. In the `ChorusRecall` branch (as plan 30 left it), number the first recalled section when its label is null **or** `Bare`, and its number is null: `if (first != null && first.number == null && (first.label == null || first.kindLabel(labels) == KindLabel.Bare))`.
2. In `SongLyrics.kt:2378`, change `header` so the number also follows a label: `(label ?: when (val sectionType = type) { … }).withNumber(number)`. Only `withNumberedSections` sets `number`, and it now only sets it on unlabelled or bare-labelled sections (and on a recall's first section of either kind), so no other label grows a number. `PrintLayout.sectionRows` already writes `(section.label ?: …)?.withNumber(section.number)`, so the PDF needs no change for sections.
2b. The recall headers, `SongLyrics.kt` (`toRenderSections`, the `ChorusRecall` branch) and `PrintLayout.recallRows`: replace `label ?: recalled?.takeIf { it.number != null }?.header(…) ?: <chorus>` with `block.label ?: recalled?.header(defaultLabels) ?: defaultLabels.chorus` (PrintLayout: `recall.label ?: recalled?.header(labels.sections) ?: labels.sections.chorus`). A recalled section is always a chorus, so for every case that exists today this is the same string (its label, or "Chorus" with its number), and it now also gives a bare-labelled recall its number. Keep the separate `label = block.label ?: recalled?.label` in `SongLyrics.kt` for the recall's fold key, which must stay the label as written.
3. Update the KDoc of `withNumberedSections` ("A section with a label of its own keeps it and takes no number" → describe kind labels, and add: "A label in the app's other language counts only while the app is in that language — the kind's ChordPro name counts in both — so switching the language can change which numbers the other sections get; the folds stay put, since they are keyed by the label as written.") and the sentence in presentation/CLAUDE.md (the `screens/songDetails/SongLyrics.kt` bullet, "only sections without a label of their own count, a lone one stays "Verse"") to: "only sections without a label count, and those labelled with the kind's own name — "Verse" is numbered with them, and "Verse 2" keeps its label and the number it names — so no two sections share a number; a lone one stays "Verse"".

## Tests

In `SectionNumberingTest` (its `headers` helper returns `label ?: number-based header`; change it to `section.header(labels)` for sections with a label or a number so bare labels show their number):
- `a label naming the kind and a number reserves it`: `{sov: Verse 1}\na\n{eov}\n{sov}\nb\n{eov}\n{sov}\nc\n{eov}` → `["Verse 1", "Verse 2", "Verse 3"]`.
- `a number reserved later is skipped`: `{sov}\na\n{eov}\n{sov: Verse 2}\nb\n{eov}\n{sov}\nc\n{eov}` → `["Verse 1", "Verse 2", "Verse 3"]`.
- `a label that is just the kind's name is numbered with the others`: `{sov: Verse}\na\n{eov}\n{sov}\nb\n{eov}` → `["Verse 1", "Verse 2"]`.
- `the kind's ChordPro name counts whatever the app's language`: with Hungarian-style labels (`verse = "Versszak"`), `{sov: verse 1}\na\n{eov}\n{sov}\nb\n{eov}` → `["verse 1", "Versszak 2"]`.
- `a recalled chorus with the kind's bare name carries its number`: `{soc}\na\n{eoc}\n{soc: Chorus}\nb\n{eoc}\n{chorus}` (the recall repeats the bare-labelled second chorus) → the chorus headers are `["Chorus 1", "Chorus 2"]` and the single `ChorusRecall`'s first `Section` has `number == 2`; in `RenderSectionsTest` (Hungarian labels, `shouldNumberSections = true`) `{soc}\na\n{eoc}\n{soc: Refrén}\nb\n{eoc}\n{chorus}` gives the recall's `Lines` header `"Refrén 2"`.
- `a kind label with an absurd number is just a label`: `{sov: Verse 99999999999}\na\n{eov}\n{sov}\nb\n{eov}` does not throw, and the bare verse stays unnumbered ("Verse"), since it is the kind's only member.
- The existing `a labeled section keeps its label and takes no number` stays as it is.

Run `./gradlew :presentation:desktopTest --offline`.

## Manual check

With "Number sections" on, open a song whose first verse is `{start_of_verse: Verse 1}` and the next two are bare `{start_of_verse}`: the headings read Verse 1, Verse 2, Verse 3 on the details screen, in the editor preview and in an exported PDF. Fold the labelled one, close and reopen the song: it is still folded.
