<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Take a Campfire export's title as it is printed, never splitting off a page word or an artist

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverter.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetCampfireLayoutTest.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/CLAUDE.md`

## Problem

`{title: Wide Chords}` printed and imported comes back as `{title: Wide}`; `{title: Hey - Jude}` would come back as
title `Jude`, artist `Hey`. `ChordSheetConverter.extractTitle` runs every title, a Campfire export's too, through the
web-page title shapes:

```kotlin
val title = (first..last).joinToString(" ") { lines[it].text.trim() }
val (name, named) = ChordSheetTitles.split(title)
```

`ChordSheetTitles.pageKind` strips a trailing `chords`/`tabs`/`lyrics`/`akkorde`/… (and takes what follows `by` or a
dash as the artist), and `split` reads `A - B` as artist `A`, title `B`. `convert/CLAUDE.md` documents that limit for
"the title shapes a copied page opens with"; Campfire's export, though, prints the file's `{title}` alone in the
title's type and the artist on a line of its own, so its title has no page word or credit to strip.

The finding's second half, `{artist: The Chords}` lost, is not this cause: it is the artist rule plan 05 replaces
(an artist longer than the title, with no details row under it, is not taken). Plan 05 fixes it.

## Fix

In `extractTitle`, take the joined title as it stands where the sheet is Campfire's export:

```kotlin
val (name, named) = if (lines[first].isCampfire) title to null else ChordSheetTitles.split(title)
```

(`Rendered.isCampfire` is set on every line of an export.) `named` is then null, so the artist under it is read by the
rules below as now. Pasted text and other PDFs keep the documented limit. Checked with a patched copy: all
`ChordSheetConverterTest` and `ChordSheetCampfireLayoutTest` cases pass, and nothing in the corpus round trip changes
but this.

Plan 06 (another writer's) changes the same `joinToString(" ")` to join a hard-cut title with nothing; the two edits sit
on adjacent lines. Plan 14 changes `extractTitle`'s first line and plan 05 the artist part below; land 06, then 14, 04,
and 05 in that order in one lane.

Docs: `convert/CLAUDE.md`, the `ChordSheetTitles` bullet ends: "…the same documented limit, which Campfire's own
export, its title printed alone, is not read by."

## Tests

`ChordSheetCampfireLayoutTest` (verified: fails at HEAD with `{title: Wide}`), with the file's helpers and
`at(rows, line)` for the baselines:

```kotlin
val sheet = ChordSheet(
    listOf(ChordSheet.Page(listOf(
        at(0.0, text("Wide Chords", size = 19.0, bold = true)),
        at(2.6, text("Verse", size = LABEL, bold = true)),
        at(4.0, chords("C" to 0, "G" to 6)),
        at(5.3, text("Hello world")),
    ))),
    isCampfireExport = true,
)
assertTrue(ChordSheetConverter.convert(sheet).single().startsWith("{title: Wide Chords}\n"))
```

and the same with `Hey - Jude`, expecting `{title: Hey - Jude}` and no `{artist`.
