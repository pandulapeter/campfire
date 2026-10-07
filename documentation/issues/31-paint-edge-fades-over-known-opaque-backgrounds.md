# Paint the edge fades of sheets, the export and import screens and the settings pager over their known opaque background instead of masking the whole viewport offscreen

**Kind:** performance (rendering)  ·  **Severity:** medium  ·  **Platforms:** all (most on the desktop's software
raster and on tiling Android GPUs)
**Lane:** U  ·  **Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/EdgeFade.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/Dialogs.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/CoverArtSearchSheet.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/SongMetadataDialog.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/SongPlayingDialog.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/ChordShapesSheet.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/SongLinksDialog.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/ExportScreen.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/importReport/ImportReportScreen.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/Controls.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songs/SongsScreen.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/settings/SettingsScreen.kt`,
`presentation/CLAUDE.md`

## Problem

`fadingVerticalEdges` (`EdgeFade.kt:301-338` at 491c4254a) fades content by masking it. Every plain `fadingTopEdge`
overload goes through it:

```kotlin
.graphicsLayer {
    compositingStrategy = if (scrolledFromTop() > 0 || scrolledFromBottom() > 0) CompositingStrategy.Offscreen else CompositingStrategy.Auto
}
.drawWithContent {
    drawContent()
    …
    drawRect(brush = …, size = Size(size.width, height), blendMode = BlendMode.DstIn)
```

While the container is scrolled at all, this renders the whole viewport into an offscreen buffer on every frame it
draws, then composites it. The trigger is any scroll, not only scrolling in progress. For a lazy list or grid,
`scrolledFromTop` is `Int.MAX_VALUE` once the first item is gone, so the layer stays on for as long as the list rests
there. Every redraw inside it pays: a ripple, a checkbox animation, a caret blinking in a field inside the faded
scroll, a cover image fading in.

The painted overload already avoids this. `fadingTopEdge(scrolled, backgroundColor)` (`EdgeFade.kt:262-276`) draws a
24dp gradient strip in the background color over the content and needs no layer. It is used only by the song details
pages, Settings' pages, both editor panes and the export screen's text preview. Everything else in a container with
one known opaque backing still masks:

- **Sheets** (`CampfireBottomSheet`, whose container is `campfireBottomSheetContainerColor()`: `Dialogs.kt:2088`,
  `private`, either `colorScheme.background` or, in the dark theme, `BottomSheetDefaults.ContainerColor`; the
  `ModalBottomSheet` has `tonalElevation = 0.dp`, so that color is what is painted):
  - `Dialogs.kt:2058`, `PickerList`: Choose songs and Choose setlists. This is the hot one, a long list scrolled
    a lot.
  - `Dialogs.kt:1459` (tags sheet) and `Dialogs.kt:1610` (languages sheet).
  - `Dialogs.kt:877, 1033, 1245, 1316, 2247` (the sheet's own scroll in a short window) and `Dialogs.kt:2506`
    (About the song).
  - `CoverArtSearchSheet.kt:407` (the result grid) and `CoverArtSearchSheet.kt:555`.
  - `SongMetadataDialog.kt:95`, `SongPlayingDialog.kt:153`, `ChordShapesSheet.kt:114` and `SongLinksDialog.kt:113`.
  - `Controls.kt:255`, `SongFilters`, when it is shown in the sheet (`Dialogs.kt:360`).
- **Screens on `colorScheme.background`**:
  - `Controls.kt:255`, `SongFilters` in the Songs screen's side panel (`SongsScreen.kt:243`; `ControlsSidePanel`
    has no background of its own).
  - `ImportReportScreen.kt:456` (inside `ScreenSurface`).
  - `ExportScreen.kt:948` and `ExportScreen.kt:1280` (inside the export screen's `Surface(color =
    colorScheme.background)`, `ExportScreen.kt:578-584`).
- **The settings pager**: `SettingsScreen.kt:425-434` in `SettingsTabPager`:

  ```kotlin
  val fadeAlpha = animateFloatAsState(
      if (isNavigationRailVisible && (pagerState.isScrollInProgress || pagerState.currentPageOffsetFraction != 0f)) 1f else 0f
  )
  HorizontalPager(
      modifier = … .fadingLeftEdge(fadeAlpha.value),
  ```

  `fadingLeftEdge` (`EdgeFade.kt:424-442`) puts the whole visible pager offscreen for every frame of a tab swipe
  while the rail is visible. Both `pagerState.currentPageOffsetFraction` and `fadeAlpha.value` are also read in
  composition, so `SettingsTabPager` recomposes on every frame of the swipe and of the 300 ms fade after it. The
  pager stands on `TopLevelScreenSurface`'s `colorScheme.background`.

Estimated cost (not measured): one viewport-sized offscreen layer per drawn frame is 1–3 ms of GPU time on a tiling
Android GPU. On Skia's software raster, which a low-end Windows 2-in-1 without a usable GPU driver falls back to, it
is 2–4 ms of CPU per frame. In a long Choose songs list that is most of a 60 Hz frame budget while scrolling.

The painted fade looks the same as the mask wherever the content stands directly on one opaque color. Masking content
to transparent over an opaque color C shows C. Painting C at the same alpha over the content shows the same pixels.
Rows with their own background, a selected row's highlight, a card and a cover image in the search grid are all
covered with the backing color either way. So nothing changes visually, as long as each fade's color is the color
of the surface directly behind that container.

## Fix

**Recommended (B): give each call site its backing color explicitly**, the way the song details, the editor and
Settings already do:

1. In `EdgeFade.kt`, add a `backgroundColor: Color` parameter to `fadingVerticalEdges(scrolledFromTop,
   scrolledFromBottom, …)` and to its `ScrollState`, `LazyListState` and `LazyGridState` overloads, plus a painted
   `fadingTopEdge(listState, backgroundColor)`. The lambda callers, the cover grid and the import report, already
   have the painted `fadingTopEdge(scrolled, backgroundColor)`. The painted versions use `drawWithCache` with two cached `Brush.verticalGradient`s
   (`backgroundColor` to transparent at the top, transparent to `backgroundColor` at the bottom), each drawn over a
   24dp strip at `alpha = strength`, as the existing painted `fadingTopEdge` does, with no `graphicsLayer`. Keep the
   masking versions for containers whose backing is not one known color (the two `AlertDialog`s, `WhatsNewDialog`
   `Dialogs.kt:553` and `WelcomeDialog` `Dialogs.kt:647`/`719`: rarely shown, and on `AlertDialog`'s own container).
   Leave `ListTopFade`, `fadingUnderStartOverlay` and `ContentOverscroll.kt:78`'s pull fade alone. The first two
   were settled by the earlier performance review (plans 16 and 17), and the pull fade is only on while a finger
   pulls.
2. In `Dialogs.kt`, rename `campfireBottomSheetContainerColor()` to an `internal` `sheetContainerColor()`, keeping
   its KDoc, and pass it as the fade color at every sheet site listed above. Check each one while doing it: the
   faded container must stand directly on the sheet's surface, with no `Surface`, card or tinted `Box` of another
   color in between. All the listed ones do at 491c4254a.
3. `SongFilters` (`Controls.kt:240`) gets a `fadeBackgroundColor: Color` parameter. `SongsScreen.kt:243` passes
   `MaterialTheme.colorScheme.background` and `Dialogs.kt:360` passes `sheetContainerColor()`.
4. `ImportReportScreen.kt:456` and `ExportScreen.kt:948, 1280` pass `MaterialTheme.colorScheme.background`.
   `ExportScreen.kt:1521`'s preview pager can take the same if it stands on the screen's surface. Check before
   changing it.
5. Settings: replace `fadingLeftEdge(alpha: Float)` with a painted `fadingLeftEdge(alpha: () -> Float,
   backgroundColor: Color)`. It draws a horizontal gradient from `backgroundColor` to transparent over the 24dp strip
   at `alpha()`, read in the draw phase, with no layer. In `SettingsTabPager`, move the condition out of composition:
   `val isSwiping by remember(pagerState) { derivedStateOf { pagerState.isScrollInProgress ||
   pagerState.currentPageOffsetFraction != 0f } }`. Then animate
   `animateFloatAsState(if (isNavigationRailVisible && isSwiping) 1f else 0f)` and pass `{ fadeAlpha.value }`, so the
   composable only recomposes when the swipe starts and when it ends.
6. Update `presentation/CLAUDE.md`'s `CampfireTopAppBar` paragraph. It currently says "the song details' pages, both
   editor panes and Settings draw it in their known opaque background color … while the song filters and pickers'
   lists keep the transparent mask". It should now say that every fade over one known color is painted (screens on
   the background, sheets on `sheetContainerColor()`, the settings pager's swipe fade), and that the mask is kept
   only for the alert dialogs, the list screens' cards (`ListTopFade`), sideways-scrolling rows
   (`fadingUnderStartOverlay`) and the overscroll pull.

Keep the no-bottom-fade-at-the-window-bottom rule: no call site gains a bottom fade. The `fadingVerticalEdges`
callers that fade both ends today are lists in the middle of something. Every top-only caller stays top-only.

**Alternative (A): a `LocalEdgeFadeBackground` CompositionLocal**, provided by `ScreenSurface`,
`TopLevelScreenSurface`, `CampfireBottomSheet` and the export screen's `Surface`, which the plain overloads would
paint with when it is set. It is less code at the call sites. But Material's `Surface`, `Card` and `ElevatedCard` do
not reset it, so a fade added later inside a card would silently paint the screen's color over the card. B fails
visibly in review instead. Not recommended.

## Tests

None. These are drawing modifiers, and the only logic is the existing strength clamp, which does not change.

## Manual check

On each surface, in the light and the dark theme, compare against the previous build. There must be no visible
difference.

1. Open Choose songs from a setlist and scroll the list. The rows fade out under the search field as before, a
   checked row's highlight included. On a low-end Android phone with "Profile GPU rendering" / "Debug GPU overdraw",
   or the desktop build on a software-rendered 2-in-1, scrolling should be noticeably smoother.
2. Open the tags sheet, the languages sheet, About the song, the cover search (scroll the grid while covers load),
   the song filters both as a sheet (narrow window) and in the side panel (wide window), the import report, and the
   export screen's options and zip contents. Scroll each.
3. In a short window (a phone on its side) open New song with the keyboard up and scroll the sheet. The header and
   fields fade at the top as before, and the caret keeps blinking.
4. In Settings on a wide window with the rail, swipe between tabs. The left edge fades during the swipe and fades
   back after, exactly as before.
