<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# :presentation — ui/screens

What every screen shares; each destination's package has its own notes.

## Insets and padding

- Song details and the editor own their `TopAppBar`; top level screens get the status bar inset from
  `TopLevelScreenSurface`. Every screen gets a `contentPadding` from the outer scaffold so lists scroll edge to edge.
- That padding follows the keyboard and the chrome's handover, read at use, not at creation (`KeyboardAwarePadding`), so
  they relayout lists without recomposing the app. Narrow it with `PaddingValues.only(…)`; taking it apart while
  composing brings the recomposition back.
- Song details' padding ignores the keyboard, or a dialog's keyboard would reflow the lyrics behind it and lift the
  setlist pager bar.

## List screen app bars

- "New song" / "New setlist" sit in the app bar, not a FAB, leaving the corner to the fast scroller. Each opens a
  dropdown (`components/menu/NewItemMenu.kt`) asking how the item is made; only "Create…" opens the naming sheet. Import
  is the same on both screens (an archive holds songs and setlists alike). Songs adds Paste from clipboard third.
  Ctrl / Cmd + N opens "Create…" without the menu wherever the menu is offered, except on the web.
- The menu leaves the bar while the empty state, loading indicator, or error is up (`allowsNewItemMenu`); each offers
  its own buttons. Both empty states start with the menu's buttons, same labels, same order.
- The bar is built afresh (`key`) when its list first leaves the loading placeholder, so the menu arriving with the
  library is not animated in, still clipped as the launch screen fades.

## Searching the list screens

- Searched from a button, not an ever-present field; Ctrl / Cmd + F opens it on any keyboard (a tablet's too), in place
  of the browser's find bar on the web.
- `SearchState` holds a `TextFieldState` so text and caret survive navigation, takes focus once per opening
  (`takeFocusOnOpen`), and `close` keeps the text so the field reads right while animating away, so lists combine
  `activeQuery`, gated on `isOpen`, never the raw text.
- Songs match by title, artist, and tags through `NormalizeSearchTextUseCase` on both sides (case, accents, spaces, and
  punctuation ignored: `ymca` finds `Y.M.C.A.`); tag-only matches rank after title/artist ones. The pickers and "Manage
  tags" search the same way.
- A setlist matches by title, description, or a matching song, and **is then shown whole**: three of twelve songs is not
  the list somebody wrote down.

## List animation and scroll position

- Every lazy list animates items through `listItemAnimation`, never `Modifier.animateItem()` directly, because it must
  be off:
  - **Until the library has arrived whole**: the first read's batches would animate as rearranging while the app opens.
    `rememberHasLoadedLibrary` draws the line as an effect, not `!isLoading`, since the last batch lands in the
    composition where it turns false. Until then the song list holds position by index, not key, or a later batch
    inserting above would open the app already scrolled.
  - **While the list is being scrolled** (by its spec, so a running placement isn't cut short): at the ends a lazy list
    offsets item animations by the requested scroll, not the actual one, flinging a row in from off screen. Except
    setlist rearranging (`isRearranging`), whose auto-scroll moves contents under the finger.
- `ScrollToTopWhenChanged` scrolls to the top when the applied search (closing counts) or the sort
  (`SongGroups.orderKey`) changes. A filter does not; the grid follows its first visible key.
- A tag or language tapped on a song row keeps that row in place (`ListAnchor`). The filter key travels with the list
  (`SongGroups.filterKey`), so key and list arrive together and a no-op filter still consumes the anchor. The jump is
  narrated by `anchoredTransition`: visible rows slide, the rest travel in with them, removed ones vanish, and rows still
  listed that leave by an edge travel off it.
- In the composition new contents arrive, lists hold by index: otherwise a grid following its first row down and then
  sent to the top takes the jump for a scroll and forgets item positions.
