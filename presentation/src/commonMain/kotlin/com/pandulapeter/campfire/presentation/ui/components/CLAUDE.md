<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# :presentation — ui/components

The shared building blocks of the screens.

### `ui/components/`

#### Menus and actions


### `ui/components/ChordsIcon.kt`

Fretboard for fretted instruments and Other, `ic_piano` for the keyboard, crossfading on change.

### `ui/components/CoverArtImage.kt` / `ProvideCoverArtImageLoader.kt`

- Coil 3 with no network artifact and **no disk cache of its own**: bytes come from `GetCoverArtUseCase` (the offline
  copy, through `:data:source:remote`'s one HTTP client). `ProvideCoverArtImageLoader` is called once by `CampfireApp`
  and resolves the use case there.
- `CoverArtImage` reserves its room from the first frame with a placeholder, so arrival moves nothing; never on a
  missing entry's row.
- In the details bar it is part of the title (tap scrolls to top / opens About the song, no press drawn). The editor's
  bar follows typed text only after 500 ms of no typing (`rememberSettledCoverArtUrl`), since each valid half-typed URL
  would be a download.
- Follows `isCoverArtEnabled` (off: nothing fetched or drawn); performance mode keeps covers.

### `ui/components/SaveShortcut.kt`

- `Modifier.saveShortcut`: Ctrl / Cmd + S, **not with Alt** (AltGr arrives as Alt). In a `CampfireBottomSheet` it presses
  the header's `BottomSheetConfirmButton` (`LocalSheetSaveShortcut`; library deletion opts out with `isSaveShortcut`),
  nothing while disabled or closing.
- Keys travel only along the focus path, so each surface takes focus with a bare focus target as it opens — a sheet a
  frame in and only if nothing has focus yet, so a form's first field keeps the caret. The key is always consumed.

### `ui/components/DelayedLoadingIndicator.kt`

Song details and editor only: fades in after 300ms, since one local file is nearly always read before then. Lists keep
the plain indicator (the launch screen covers their first read).

#### Edge fades (`EdgeFade.kt`)

- `fadingTopEdge`: over a **known opaque color**, paint that color over the edge rather than masking (a mask costs a
  viewport-sized offscreen buffer every frame). The color must be the one directly behind (`sheetContainerColor()` for
  sheets). Masks are kept only for alert dialogs, `ListTopFade`, `fadingUnderStartOverlay`, and overscroll.
- `fadingVerticalEdges`: lists inside a dialog; never a sheet's content.

## Scrolling performance

- Overscroll is Compose's own (none on desktop/web). Foundation skips it when content fits, so short containers use
  `bounceScrollableContent` / `bounceVerticalScroll` / `bounceHorizontalScroll` (`ui/platform/ContentOverscroll.kt`),
  offering drags to parents first so a fitting sheet still drags down. The vertical form draws its own top fade while
  pulled (scroll position stays zero, so `fadingTopEdge` is off); list screens pass their `ListTopFade`'s
  `OverscrollPull` instead.
