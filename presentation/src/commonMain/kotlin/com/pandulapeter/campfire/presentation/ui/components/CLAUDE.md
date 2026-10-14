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

#### Song cards

- A song inside a setlist prefixes its one-based place, missing files included.
- `CenteredSongCardContent` lays the body out by hand rather than as a `ListItem`, which pins a three-line item to its
  top and grows it to 88dp.
- `DragHandle` is a plain icon, narrower than the overflow button, so the button lands exactly where it does on the songs
  screen; the caller makes it draggable.
- `SongListItem` names the **sounding** key next to the artist (never the title, which must not be pushed out):
  `{key}` moved by the file's `{transpose}`, the listing's transposition, and its capo (`SongRenderer.renderKey`), so
  one song can read differently inside a setlist. The tempo follows (`EffectiveTempo.bpm`, 120 where nothing names one),
  in the muted color so it doesn't compete with the key. Both come from scanned metadata, never by parsing the file.
  "Lyrics only" replaces the key for a chordless song — but with the Chords switch off the place is empty. Changes
  crossfade (`SongCardNote`).
- **In a window under 480dp wide** card text uses 8dp padding (and headers follow, `songCardTextKeyline`) and titles get
  two lines; touch targets stay.
- **Column width** (`minSongColumnWidth`, from `UserPreferences.CardDetails` and the chords/metronome switches): 368dp
  base, +16dp each for key and tempo, +32dp for duration, so the default Songs card keeps 400dp and a phone on its side
  stays one column. Setlists start 24dp wider for the slot number (`MIN_SETLIST_COLUMN_WIDTH`). `CampfireScreens`
  computes both into `ListLayout.of`.
- `hasRoomForSidePanel`: the filter panel shows only while two columns still fit beside it, the fast scroller counted;
  otherwise the filters open in a sheet.

#### `SearchableTopAppBar`

- Not a `TopAppBar`: a row drawn *over* the list, because a `TopAppBar` is a `Surface` and would take every touch meant
  for the pinned header under it.
- Closed, it holds the actions on a tonal pill (`SearchPill`), the pinned `SectionHeader` standing where a title would.
  The pill animates between the buttons' row and the field as **lookahead rectangles of each settled state**, so each
  edge only travels one way.
- The pinned header's pill narrows to the room the buttons leave (`onReachChanged`, measured before the list via
  `ListUnderAppBar` so the first frame is right) and along `AppBarOverlap.coverage` as the search opens and closes.
- Cards fade out under the header row and back in over 24dp (`ListTopFade.kt`): each card masks itself,
  since an overlay would fade the sticky headers (items of the same list). Strength grows with scroll, so a list at rest
  shows its first cards whole. A card takes no press inside the gradient, but a drag there still scrolls.
- The fast scroller starts below `AppBarOverlap`'s reach so its thumb is never under a button.
- On Songs, opening search collapses all section headers (even with an empty query); section boundaries stay so rows
  keep their columns. `SongSearchScrollAnchor` keeps the visible section stationary and restores the exact scroll on
  closing an unchanged search; scrolling or new results release it. It checks for an active scroll both on mode change
  and before each correction, so an interrupted transition never resumes a stale one (tested). On Setlists the list moves
  down instead and headers pin under the bar.
- The "New" menu yields to the field (`closedSearchActions`); the selection menu stays
  (`areClosedSearchActionsKeptInSearch`). The bar stays filled while the list has no header (placeholder, ranked
  results). It spans the list only, never the side panel.
- `SearchBackHandler` uses `NavigationBackHandler` over a hoisted `NavigationEventState`, not `BackHandler` (which only
  hears a gesture on release), and previews the close via `SearchState.backProgress` (`SearchRecession`). The offset is
  layout, not a drawn translation, so `animateBounds` carries on from where the gesture left.
- `SearchAction` is **one button travelling** between the actions slot and the navigation slot (`movableContentOf` +
  `animateBounds` in a bar-only `LookaheadScope`), keeping its icon's animation state; bounds animate only while opening
  or closing, so it doesn't lag a window resize. `SearchActionSlot` reserves its room on the same spring.
- The field is a hand-laid `BasicTextField`, not `SearchBarDefaults.InputField` (fixed 56dp, no padding control). It
  never leaves its `Box` (emptied, zero width when closed) because `animateBounds` only animates a rectangle it has seen.
  It collapses into its end edge rather than sliding. The pill moves by `animateBounds` to `CLOSED_FIELD_OFFSET`, not an
  animated width fraction, which settles short and jumps on the last frame.
- `SearchToCloseIcon` is a `Canvas` because Compose Multiplatform resources read only static vectors; it transcribes
  `avd_search_to_close.xml` on **one linear timeline**, not springs (overlapping stages would drift), played faster than
  authored.

#### Menus and actions

- **An overflow menu expands into buttons wherever there is room** (`ActionsMenu`), one at a time in menu order, the
  overflow button going last. Neighboring icon buttons overlap touch targets by 12dp (`ActionOverlap.kt`'s
  `overlappingAction`).
- **Some entries stay in the menu whatever the room** (`ActionsMenuItem.isAlwaysInMenu`): a setlist's Edit details,
  Reorder, Archive, Duplicate, Export, and Delete; a song's Update file name and Duplicate; the editor's Revert. A song
  card never expands (`isExpandable = false`).
- `SongActions`: edit, Update file name (only where `Song.canUpdateFileName`), Duplicate song (songs screen cards only,
  `isDuplicable`), Export song, delete — **delete never from a setlist row or a details screen opened from a setlist**
  (`isDeletable`).
- Choose setlists is `SetlistAssignmentsButton` in front of the overflow on song rows and the details bar (moved into the
  menu as `setlistAssignmentsAction` when out of room); in a setlist row's menu after "Remove from setlist" (not on an
  archived setlist). From a details screen opened from a setlist, that setlist's box is disabled
  (`DialogType.SetlistPicker.setlistFileName`), since unticking it would pull the screen from under the reader.
- **Every overflow button opens a dropdown**, touch included. Long press (touch) and right click (`ContextClick.kt`,
  which beats the row's clickable since Android clickables answer a right click like a left) open the same dropdown from
  the row's button (`OverflowMenuState`); the button is always there, since a gesture announces itself to nobody.
- `SetlistActions`: only Choose songs ever becomes a header button.
- `OverflowMenu` counts open menus (the desktop Escape handler is outside the composition) and acts through
  `OverflowMenuState.select`, which runs an entry once per opening: a `DropdownMenu` stays tappable during its exit, and
  a double tap on Export would export twice.
- `SortMenu` (`SongSortMenu`, `SetlistSortMenu`) is shared by each screen and its picker sheet.
- `SongFilters` is a side panel or a sheet, and neither when there is nothing to filter by (`hasSongFilters`).
  `ControlsSidePanel`: only the list feeds the bar's nested scroll.

#### Other choices

- `SegmentedChoice` gives a selected label only the width its check mark leaves (Material cuts it off), and drops the
  mark where labels wouldn't fit beside it; `isInline` for the editor's panes. Constraint: "Keyboard", "Sharps (#)", and
  "Előnézet" must be whole at 360dp.
- `ColorChoice`: discs that wrap rather than scroll; app's own first, then system's, then hues, gray last. The selection
  is a ring in its own color, since an icon in the middle would read as the color's identity.

### `ui/components/ChordsIcon.kt`

Fretboard for fretted instruments and Other, `ic_piano` for the keyboard, crossfading on change.

### `ui/components/LabeledControlRow.kt`

The label sits beside a wide control while its longest word fits, else above it. The form never changes with the value:
`decidingLabel` names the widest form (measured, never drawn), and a row that stacked at a width stays stacked
(`isLabelBesideControls`, tested), so nothing moves under a dragging finger. Not animated.

### `ui/components/Languages.kt`

- `LANGUAGE_CODES` (~620: ISO 639-1 plus the 639-2/3 codes it lacks; no browser enumerates languages) is all the app
  ships. `languageName` asks the platform in the app's language (reactive via `currentLanguage`), capitalizes it (CLDR
  writes `angol`), falls back to English, then to the code in caps.
- `pickableLanguages`: sorted through `NormalizeTextUseCase`, the library's own languages first.
  `SongLanguage.UNKNOWN` is named from string resources.

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

### `ui/components/PagerIndicator.kt`

An indicator follows the pager's position (page plus offset), read during layout/draw so swipes don't recompose, and
never animates on its own.

### `ui/components/SaveShortcut.kt`

- `Modifier.saveShortcut`: Ctrl / Cmd + S, **not with Alt** (AltGr arrives as Alt). In a `CampfireBottomSheet` it presses
  the header's `BottomSheetConfirmButton` (`LocalSheetSaveShortcut`; library deletion opts out with `isSaveShortcut`),
  nothing while disabled or closing.
- Keys travel only along the focus path, so each surface takes focus with a bare focus target as it opens — a sheet a
  frame in and only if nothing has focus yet, so a form's first field keeps the caret. The key is always consumed.

### `ui/components/DelayedLoadingIndicator.kt`

Song details and editor only: fades in after 300ms, since one local file is nearly always read before then. Lists keep
the plain indicator (the launch screen covers their first read).

### `ui/components/TagFlowRow.kt` / `TagPill.kt`

- The one place a tag is drawn. In a song row (`SongLabels`) one line that scrolls sideways, edge fades masked with
  `BlendMode.DstIn` in an offscreen layer (to fade to the card, not punch through). Labels on **every song in the whole
  library** are omitted (`labelsOnEverySong`), counted over the whole library, not the filtered list, or a filter would
  hide the label it filters by.
- Tapping a row's tag or language toggles its filter chip without scrolling; no row chip edits tags. Setlist rows show
  no labels (`shouldShowLabels`).
- Pills are outlined (only a selected row pill is filled). Tags carry `ic_label`, languages `ic_language` — "Magyar" is
  otherwise ambiguous.
- `TagIcon` fills the mark with the tag's color under an outline in the content color, so color never stands alone;
  from `LocalTagColors`, provided once by `CampfireApp`. `TagColor`'s ids are what devices store; an unknown id draws as
  no color. Light/dark tones are chosen by surface luminance.
- Filter chips are `CountedFilterChip` on `SelectableChip`, not Material's `FilterChip` (its state layer hugs the label);
  the `indication` sits outside the padding under a `clip`. No chip in the app is a Material `FilterChip`.
- `LabelSortingToggle` sorts tags and languages on one preference; "Unknown" stays last.

#### `FilterGroupsLayout.kt`

- **The fold is decided by room, not a count**: groups report whole and fully collapsed heights as intrinsics, and
  `chipBudgets` shares the height like vessels. Selected chips are always visible; "Show all" opens the rest. Below the
  minimum the content scrolls.
- In a sheet, use the sheet's tallest height (`BottomSheetContentScope.uncoveredTopInset`): Material's offset-dependent
  top padding would otherwise make the layout oscillate every frame.
- The "any / every" choice (only with two selected) and "Reset all filters" are `FilterSlot.TRANSIENT`, out of the
  sharing, so selecting a chip never hides chips under the finger. The clear action sits in `FilterSectionTitle`, built
  to keep the plain title's height, so the sheet never resizes on a selection.
- Room is shared in the lookahead pass only; the approach pass animates bounds. Moving chips use `animateBounds` in one
  `LookaheadScope`.
- The language group appears only with more than one language (no-language songs counting as one).

### `ui/components/FastScroller.kt`

- The only scrollbar, on both lists; the letter bubble only on the song list. The `FAST_SCROLLER_WIDTH` column overlays
  the grid's end padding, so cards end before it while sticky headers run under it. The column stays whether or not the
  list scrolls, so rows never reflow.
- A press centers the thumb: at once for a mouse, but **for touch only after a long press or a mostly vertical move past
  slop** (`awaitThumbPress`), because the column is on Android's back-gesture edge. A tap jumps only on lift, and a lift
  the system consumed for back is ignored.
- Thumb and track fade by draw alpha, not a `graphicsLayer`: a layer would clip the bubble, and the web build doesn't
  redraw a `ModulateAlpha` layer whose alpha alone changed.
- Position is estimated from average item size, excluding the pinned sticky header (listed out of order).
- Haptics (touch only): a tick per row, a notch per section letter and at the ends (`FastScrollerDetent.kt`,
  `detentNotch`), the end decided by where the finger asked the thumb to be; read once a frame.

### `ui/components/CampfireTopAppBar.kt`

- Song details and editor bar: flat, background color, never tints or lifts. In a short window the details screen gives
  it `enterAlwaysScrollBehavior`; pages choose their grid from the settled height (`isAppBarSettled`, `settledHeight`) so
  a moving bar searches no grid. The editor's is `isCompact` (48dp) while the keyboard is up.
- `bottomContent` holds a second row inside the same surface (the editor's toolbar).

#### Edge fades (`EdgeFade.kt`)

- `fadingTopEdge`: over a **known opaque color**, paint that color over the edge rather than masking (a mask costs a
  viewport-sized offscreen buffer every frame). The color must be the one directly behind (`sheetContainerColor()` for
  sheets). Masks are kept only for alert dialogs, `ListTopFade`, `fadingUnderStartOverlay`, and overscroll.
- `fadingVerticalEdges`: lists inside a dialog; never a sheet's content.

#### Sticky section headers

- No background or divider; only the content takes touches, and **neither the header nor its menu button is
  focusable**: a lazy grid keeps a focused item placed after scrolling away, which stops it drawing later pinned headers.
- The pushed-away pinned header is a visual copy outside the grid, so the grid's clip can't cut it, keeping its pinned
  width. The row extends behind the fast scroller so pill and app bar reach measure from one edge.
- Clicking a header scrolls to its section start.

## Scrolling performance

- Overscroll is Compose's own (none on desktop/web). Foundation skips it when content fits, so short containers use
  `bounceScrollableContent` / `bounceVerticalScroll` / `bounceHorizontalScroll` (`ui/platform/ContentOverscroll.kt`),
  offering drags to parents first so a fitting sheet still drags down. The vertical form draws its own top fade while
  pulled (scroll position stays zero, so `fadingTopEdge` is off); list screens pass their `ListTopFade`'s
  `OverscrollPull` instead.
- `ListTopFade` caches an inverse gradient and draws with `DstOut` in its existing layer, translating rather than
  rebuilding a shader per card.
- `FastScroller` consumes a conflated channel once per frame so pointer events can't cancel the frame wait or a scroll;
  animation values are read while drawing.
