<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Read a Campfire PDF's heading before a second column that starts beside it

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `data/formats/src/commonMain/kotlin/com/pandulapeter/campfire/data/formats/document/PdfLineLayout.kt`,
`data/formats/src/commonTest/kotlin/com/pandulapeter/campfire/data/formats/document/` (a layout test),
`presentation/src/desktopTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PdfRoundTripTest.kt`

## Problem

Printing corpus song `03-every-directive.cho` in two landscape columns (`PrintSettings(columns = 2, isLandscape = true)`)
puts the title, artist, and playing row at the top of the left column and the song's last sections (`Solo`, `Outro`)
at the top of the right one. `PdfTextExtractor` returns the lines in this order (baselines in points):

```
y=59 Solo (right)  y=64 title (left)  y=84 artist (left)  y=76 Am F C G (right)  y=100 Key: … (left)
… the rest of the left column …  y=103 Outro (right) …
```

The right column's first two rows are interleaved with the left column's heading, then the rest of it follows the left
column. The import then opens the song with a `Solo` section holding the title and artist as lyrics and finds no title
at all. In portrait two columns the same song reads back correctly. `PdfLineLayout.buildLines` cuts the page at every
row it takes for one crossing the gutter (`spanning`) and reads only the bands between those by columns; a band at the
top seems to be read whole (`rows`) or the heading's rows are taken as spanning. Confirm which before fixing.

## Fix

Find which rows land in `spanning` for this page (print them in a test). If a heading row is taken for spanning by its
glyphs' extent, measure a row as crossing the gutter only where its glyphs lie on both sides of it rather than reaching
into the band; if the top band is read by `rows`, make sure a band holding text on both sides of the gutter is split
into columns. Keep a real title set across both columns (a songbook's) read first, which `spanning` exists for.

## Tests

- A `data:formats` test laying out glyphs as above (a 22 pt title and 13 pt rows on the left, two short rows at the top
  of the right column) expects the left column's lines first.
- `PdfRoundTripTest`: add `PrintSettings(columns = 2, isLandscape = true)` to the settings it prints every song in.
