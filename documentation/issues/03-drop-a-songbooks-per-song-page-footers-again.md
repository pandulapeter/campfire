<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Drop a songbook's per-song page footers ("Page 1 of 2") from an imported PDF again

**Kind:** bug · **Severity:** low · **Platforms:** all
**Files:** `data/formats/src/commonMain/kotlin/com/pandulapeter/campfire/data/formats/document/PdfRunningLines.kt`,
its test in `data/formats/src/commonTest/kotlin/com/pandulapeter/campfire/data/formats/document/PdfRunningLinesTest.kt`,
`data/formats/CLAUDE.md` (its Running lines bullet)

**Challenged:** amended — the module `CLAUDE.md` bullet and the `runningLines` KDoc state the number rule this changes, so both are updated; the match is spelled out as `matchEntire` on the trimmed line text.

## Problem

Since ebc6c916c `PdfRunningLines.runningLines` keeps a repeated edge line out of the text only when each number in it is
the same on every page or rises with the page index:

```kotlin
numbers.values.map { it.getOrNull(position) }.distinct().size == 1 || offsets.distinct().size == 1
```

A songbook that numbers each song's pages ("Page 1 of 2", "Page 2 of 2", "Page 1 of 3", …) matches neither, and
`isPageCount` only takes the document's own page numbers, so those footers now land in the lyrics. At cfcf08150 they
were dropped by their signature (digits folded to `#`). The new test covers only the heading case ("Verse 2" /
"Verse 6") this rule was made for.

## Fix

Also take a signature for a running line when every occurrence's text is a page count of its own: it matches
`(?:page\s+)?N\s+of\s+M` or `page\s+N\s*/\s*M` (case-insensitive) with `1 <= N <= M`. Keep the bare `N / M` form out:
a time signature at the top of each song (`3/4`, `6/8`) has the same shape and must stay (see `isPageCount`'s KDoc).
`numbersBySignature` keeps only the numbers, so keep a parallel flag per signature, true while every occurrence
matched (`matchEntire` on the trimmed `line.text`: a footer with more words, `Amazing Grace — Page 1 of 2`, is left to
the number rule). The half-the-pages and two-pages conditions still apply. Update `runningLines`' KDoc and the Running
lines bullet of `data/formats/CLAUDE.md` ("A number in a running line is the same on every page or counts the pages") to
add: or the line is a page count of its own (`Page 1 of 2`), never a bare `1/2`.

## Tests

`runningLines` drops a per-song "Page N of M" footer repeated over four pages with restarting counts; a "3/4" at the
same place on every page is kept; the "Verse 2" / "Verse 6" case still is.
