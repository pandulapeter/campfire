<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->

# Build a song card's menu entries only when its menu opens

**Challenged:** amended — `SetlistActions` is an expandable header menu and stays on the `List` overload; `SongActions`' `leadingItems` / `fileEditItems` (built per card by `SongListRow` and `SetlistEntryActions`) must become builders too or most of the saving is lost; the scroll start/end rebuild claim corrected; `hasMenu` for each non-expandable caller checked.

**Kind:** performance  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/menu/ActionsMenu.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/actions/SongActions.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/actions/SetlistActions.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songs/SongListRow.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/setlists/SetlistEntryActions.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/menu/CLAUDE.md`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/actions/CLAUDE.md`, `.../ui/screens/songDetails/SongDetailsActions.kt`

## Problem

Every song card composes its whole menu up front: `SongActions.kt` calls `painterResource(Res.drawable.ic_rename)` and its
siblings (about seven) plus a `stringResource` per entry, and `SongListRow` adds `songLabelActions` (two more). For XML
vectors each `painterResource` builds a `VectorPainter`. They are drawn only once the menu opens, yet built for every song card scrolled in, on Songs and in every setlist (whose
rows use `SongActions` through `SetlistEntryActions`, adding `setlistRowActions` and `setlistAssignmentsAction`: up to four
more). (`SongList` keeps `listItemAnimation` on a `Box` of its own so that a scroll start and end do not recompose the row;
the remembered painters would be cheap then anyway, so the cost is the first composition of each card.) `SongListRow` already
hoists `selectSongsIcon` and `selectMultipleTitle` for this reason.

The review already spared non-expandable menus the `BoxWithConstraints` and the per-item `AnimatedVisibility`
(`ActionsMenu`'s `isExpandable` branch); this plan removes the item building itself.

## Fix

Add an `ActionsMenu` overload for non-expandable menus whose items are a builder, `items: @Composable () -> List<ActionsMenuItem>`,
invoked only inside the `DropdownMenu` content (it stays composed through the exit animation, so the entries do too). Keep
the `List` overload for every expandable caller: the song details app bar's own `ActionsMenu`s, the editor's `EditorMenu`,
`SongSelectionMenu`, and `SetlistActions`, which is a setlist header's menu (`isExpandable` by default: Choose songs becomes a
button) and so needs its items up front to count buttons; it is one per header, not per card, and is left alone.

Only `SongActions` (always `isExpandable = false`) and `SetlistEntryActions`' `Entry.Missing` menu move to the builder. For
the saving to reach the cards, `SongActions`' `leadingItems` and `fileEditItems` become builders as well
(`@Composable () -> List<ActionsMenuItem>`, default `{ emptyList() }`), so `SongListRow` passes
`{ songLabelActions(...) + ActionsMenuItem(selectMultipleTitle, ...) }` and `SetlistEntryActions` passes
`{ setlistRowActions(...) + setlistAssignmentsAction(...) }` without calling them while composing the card;
`SongDetailsActions` wraps its list the same way.

`hasMenu`: for the builder overload it is simply true. Both of its callers always have an entry (`SongActions` always
holds Export; a missing row always holds Remove from setlist), so the overflow button and the long press / right click
(`OverflowMenuState.open()`) behave as today, and the `LaunchedEffect(hasMenu, …)` dismissal is only kept on the `List`
path. State the "always has an entry" assumption in the overload's KDoc.

## Tests

None can be unit tested (composition). Check by hand on Songs and Setlists that every menu entry, its icon, enabled state
and the long press / right click opening still work, in read only mode too.
