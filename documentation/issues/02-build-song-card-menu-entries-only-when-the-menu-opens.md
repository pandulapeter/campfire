<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->

# Build a song card's menu entries only when its menu opens

**Kind:** performance  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/menu/ActionsMenu.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/actions/SongActions.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/actions/SetlistActions.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songs/SongListRow.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/setlists/SetlistEntryActions.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/menu/CLAUDE.md`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/actions/CLAUDE.md`

## Problem

Every song card composes its whole menu up front: `SongActions.kt` calls `painterResource(Res.drawable.ic_rename)` and its
siblings (about seven) plus a `stringResource` per entry, and `SongListRow` adds `songLabelActions` (two more). For XML
vectors each `painterResource` builds a `VectorPainter`. They are drawn only once the menu opens, yet built for every card
scrolled in and again at every scroll start and end (the row recomposes for `listItemAnimation`). `SongListRow` already
hoists `selectSongsIcon` and `selectMultipleTitle` for this reason.

The review already spared non-expandable menus the `BoxWithConstraints` and the per-item `AnimatedVisibility`
(`ActionsMenu`'s `isExpandable` branch); this plan removes the item building itself.

## Fix

Let `ActionsMenu` take its items as a builder, `items: @Composable () -> List<ActionsMenuItem>`, invoked only where they are
needed: inside the `DropdownMenu` content, and up front only for a menu that lets items out (`isExpandable`), which needs
them to count buttons. `SongActions` and `SetlistActions` pass builders. Keep the `LaunchedEffect(hasMenu, …)` dismissal
working: for a non-expandable menu `hasMenu` becomes "the builder may return something, or there is a footer", which
every song card's menu always has (Export is always visible).

Recommended: keep the `List` overload for the expandable callers (song details app bar, editor) and add the builder
overload for the non-expandable ones, so the larger callers are untouched.

## Tests

None can be unit tested (composition). Check by hand on Songs and Setlists that every menu entry, its icon, enabled state
and the long press / right click opening still work, in read only mode too.
