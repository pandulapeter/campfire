# Draw the export screen's page buttons inside the preview on a phone, so they scroll away with it

**Kind:** bug (layout)  ·  **Severity:** low  ·  **Platforms:** all (phone-width windows, under 520dp)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/ExportScreen.kt`, `presentation/CLAUDE.md`

## Problem

On a window narrower than 520dp the export screen stacks: the PDF preview is the first item of the options' list,
360dp tall, and "scrolls away with them" (`presentation/CLAUDE.md`, Export screen). The page buttons pill, though, is
drawn by the screen's body `Box`, outside the list, pinned at its top end (ExportScreen.kt at dac1d9d59, after the
`AnimatedContent(content, …)`):

```kotlin
PageButtons(
    modifier = Modifier.align(Alignment.TopEnd).padding(PAGE_MARGIN),
    isVisible = content == PrintScreenContent.LOADED && !isFiles && hasPages,
    page = pagerState.currentPage,
    pageCount = pageCount,
    onTurn = { target -> pageScope.launch { pagerState.animateScrollToPage(target.coerceIn(0, pageCount - 1)) } },
)
```

So once the list is scrolled the raised pill ("Page 1 of 1", 6dp shadow) stays where it was and sits over the
Format title and the first options (`android/48_export_s1.png`), and it is the one thing on the screen that does not
scroll. `presentation/CLAUDE.md` describes the intended placement — "the page buttons are a raised pill at the top end
of the pane, under the app bar" — which holds beside the options (the preview *is* a pane there) but not on a phone,
where the preview is a list item.

## Fix

1. Compute the arrangement where the pill is placed: `isSideBySide` is decided inside the `LOADED` branch from
   `bodyWidth`/`bodyHeight`; hoist the `optionsWidth` / `isSideBySide` computation up to just after
   `val bodyHeight = maxHeight` in the `BoxWithConstraints`, so both the panes and the pill read the same value.
2. Keep the outer `PageButtons(...)` for the side-by-side arrangement only:
   `isVisible = isSideBySide && content == PrintScreenContent.LOADED && !isFiles && hasPages`.
3. In the `preview` lambda, when `!isSideBySide`, wrap the `PrintPreview(...)` in a `Box(Modifier.fillMaxSize())` and
   draw `PageButtons(modifier = Modifier.align(Alignment.TopEnd).padding(PAGE_MARGIN), isVisible = !isFiles && hasPages, …)`
   on top of it, with the same `page`/`pageCount`/`onTurn`. The preview item is the header of the options' list, so the
   pill now scrolls away with the page it turns. (Inside the `AnimatedContent(isFiles, …)` the PDF branch is the only
   one that shows it, so `!isFiles` there is just `true`; keep the fade from `PageButtons`'s own `AnimatedVisibility`
   for the page count appearing.)
4. When the arrangement changes (a phone rotated across 520dp) the pill is composed in the other place; both are
   `AnimatedVisibility` fades, which is an acceptable crossfade between the two positions.
5. `presentation/CLAUDE.md`, Export screen paragraph: "the page buttons are a raised pill at the top end of the pane,
   under the app bar, fading in and out with the PDF format" → "…at the top end of the preview — under the app bar
   where the preview is a pane, and inside the preview item on a phone, scrolling away with it — fading in and out with
   the PDF format".

Optional, for the user to decide (not part of the recommended fix): hide the pill altogether for a one-page document,
where both its arrows are disabled and it only covers the page's corner. Recommendation: keep it — "Page 1 of 1" is
the only place the page count shows before Save is tapped.

## Tests

None: placement in a composition.

## Manual check

On the 360 × 640 dp emulator: Export song (PDF) → scroll the options up: the pill leaves with the preview and never
covers the Format title or an option. With a multi-page setlist export, the arrows still turn pages while the preview
is on screen. Rotate to landscape (side by side): the pill is at the preview pane's top end, under the app bar, as
before. Switch the format to ChordPro / Zip: the pill fades out in both arrangements.
