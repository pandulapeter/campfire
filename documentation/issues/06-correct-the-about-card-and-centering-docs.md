# Correct the About card's KDoc and the song details CLAUDE.md where they no longer say what the code does

**Kind:** docs  ·  **Severity:** low  ·  **Platforms:** —
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongInfoGroup.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/CLAUDE.md`
**Depends on:** 01 and 02 (same CLAUDE.md; run last in the lane)

## Problem

- `SongInfoGroup.kt`, the `songInfoTileMinHeight` KDoc: "A titled tile is that tall by its own layout; an untitled one
  … is held to it, what it holds centered in it." Since 21173fed6 the `heightIn(min = …)` applies to every tile,
  titled or not.
- `SongInfoGroup.kt`, the `isCompact` parameter doc: "measured by a row of buttons standing in their touch targets" —
  the link buttons now report their own 36dp rows (`SongInfoLinkButtons.kt`'s slack layout).
- `CLAUDE.md` (the About card paragraph, near "`songInfoTileMinHeight`, from `songInfoLinkRowHeight`"): the phrase
  "or their tile had empty space under them its title had none of" is garbled, and the `songInfoLinkRowHeight` source
  is only true for the non-compact card.
- `CLAUDE.md`, the paging section: "a page shorter than the screen is spread out over it like any other (below)" —
  spreading is described above it, and a page of joined rows is moved down whole rather than spread.

## Fix

Reword each to match the code, in the file's voice:
- min height KDoc: every tile is held to this height, what it holds centered in it.
- `isCompact`: "…measured by a row of link buttons at their own height".
- the About paragraph: "…otherwise their tile would have empty space under the buttons with nothing to match it above",
  and qualify the `songInfoLinkRowHeight` source as the sheet's (non-compact) one.
- the paging sentence: "is centered in the screen like any other (above): spread out into its gaps where it is a single
  row, moved down whole where it holds joined rows".
Re-read the hold paragraph after 01 and 02 landed and make sure it names where the hold is armed and recorded
(`ColumnCenteringHold.kt`, `SongSectionsLayout.kt`'s recording, `SongLyrics.kt`'s wiring), not only "that file and
`arrange`'s `heldSpreads`".

## Tests

None (docs only).

## Manual check

None.
