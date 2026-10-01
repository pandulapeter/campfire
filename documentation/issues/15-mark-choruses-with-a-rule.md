# Set a chorus apart with a rule down its left side

**Kind:** output quality  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayout.kt`, `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayoutTest.kt`
**Challenged:** amended — `rowsFor` has to take the width it lays out to (every wrap, the chord-row check and the tab `characters` count read `columnWidth` today), the bar on the label's row starts at the label rather than over plan 13's gap above it, and a comment written inside a chorus carries the bar too, so the bar does not break where the chorus is cut.

Runs after plan 14 (uses `PrintRule`).

## Problem

A chorus looks like a verse with a different heading; on screen it is a card, and a printed lead sheet marks it with an
indent or a bar so a singer finds it from across a music stand.

## Fix

- `rowsFor(block, width)`: every use of `columnWidth` inside it — `wrapped()`, `wrapPrintText` of the lyrics and of a
  chord name, the `x + chordWidth > width` check, the tab `characters` count, plan 08's grid fill and plan 14's box
  (`width - 10`) — reads the parameter. Callers pass `columnWidth`; a chorus passes `columnWidth - 8`.
- For `SectionType.Chorus` sections (recalled ones included, and their continuations): lay out at `columnWidth - 8`,
  shift every row's parts and rules right by 8 pt, and attach to each row a rule
  `PrintRule(x = 0f, y = 0f, width = 1.5f, height = row.height)`, so the bar continues across column and page breaks
  without gaps. The label is indented too; on its row the bar starts at the label (`y = extra`, `height = row.height -
  extra`, `extra` being plan 13's `0.3 * fontSize`).
- A `ChordProBlock.Comment` that belongs to a chorus — placement `IN_SECTION` after a chorus section, or
  `START_OF_SECTION` before one — gets the same indent and bar, so a chorus cut by a comment keeps one bar. Between two
  blocks of one chorus (a piece and its comment, a comment and the continuation) the usual `space(0.65 * fontSize)` is
  replaced by an empty row of that height carrying the bar, appended to the earlier block's rows; after the chorus's
  last block the space stays outside the bar.

## Tests

`PrintLayoutTest`: every text of a chorus has `x >= columnX + 8`; the bar's rules' heights sum to the chorus' rows' heights less the label's `0.3 * fontSize`;
a verse has no rules; a chorus line wider than `columnWidth - 8` wraps (no text ends past the column); a chorus
cut by an `IN_SECTION` comment has a bar on the comment's rows too.

## Manual check

Export "Home on the Range": both choruses carry the bar, also where one continues on the next page.
