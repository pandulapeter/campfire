# Size the Link preview's QR code to its pane, so it is shown whole and clear of the floating button in a short window

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all (phones on their side, short desktop and web windows)
**Challenged:** amended — this collapses the button and moves the band to an end column only in the Shared state (Copy link), each state of `LinkPreview`'s `AnimatedContent` keeping its own padding, so Connect Dropbox / Create link keep their labels and their messages keep the band; `LinkButton` needs the `text`/`icon`/`expanded` overload, not its current content-lambda one; the UI test compares the code's clipped bounds with its size (`boundsInRoot` is clipped to the scroll viewport, so "inside the window" passes today) and uses `TestWindow.PHONE_ON_ITS_SIDE`.
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/export/LinkPreview.kt`,
`…/ui/screens/export/ExportScreen.kt`, `…/ui/screens/export/ExportScreenDecisions.kt`, `…/ui/screens/export/LinkButton.kt`
, `…/ui/screens/export/CLAUDE.md`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/export/ExportScreenDecisionsTest.kt`,
`tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/presentation/ui/screens/settings/SharingTabTest.kt` (or a new
`…/ui/screens/export/LinkPreviewTest.kt` beside it)

(`…/ui/` = `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/`.)

## Problem

The QR code is the point of the Link format: a friend scans it off the screen. In a short window it can never be seen
whole. `LinkPreview.kt`'s `SharedLink` draws it at a fixed width inside a scrolling column:

```kotlin
modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
...
QrCodeImage(
    modifier = Modifier.widthIn(max = QR_CODE_WIDTH).fillMaxWidth(),
```

with `private val QR_CODE_WIDTH = 240.dp`, and `ExportScreen.kt` gives the pane, side by side with the options, a bottom
band for the floating button:

```kotlin
// Beside the options the button floats over the end of this pane.
contentPadding = PaddingValues(bottom = if (isSideBySide) bottomInset + FLOATING_CONTROLS_CLEARANCE else 0.dp),
```

A 360 × 640 dp phone on its side is side by side (`optionsWidthFor`: `bodyWidth >= 520.dp -> 260.dp`), and the body
under the app bar is about 270 dp tall; less the 88 dp `FLOATING_CONTROLS_CLEARANCE`, the bottom inset, and the column's
32 dp of vertical padding, the viewport is about 150 dp. The 240 dp code is cut at every scroll position, so it cannot
be scanned. The PDF preview already handles this case (`pageFitFor`'s `isShortWindow` branch puts the controls in a
column at the pane's end and the page beside them, see `screens/export/CLAUDE.md` › Layout); the Link preview does not.
The stacked phone layout (`STACKED_PREVIEW_HEIGHT`, 432 dp) and wide windows are fine.

The obvious "end column instead of a bottom band" does not fit as it stands: on Link the floating control is
`LinkButton`, an `ExtendedFloatingActionButton` with a label ("Copy link", about 150 dp), so a column that wide at the
end of a ~330 dp pane leaves the code about 100 dp.

## Fix

Make the QR code's size a function of the room it has, and give it the room in a short window.

1. **Size from constraints**: wrap `SharedLink`'s content in a `BoxWithConstraints` *outside* the
   `verticalScroll`, and size the code `min(QR_CODE_WIDTH, maxWidth − 48.dp, maxHeight − 32.dp)` (the column's padding),
   floored at a readable minimum (say 120 dp, below which it scrolls as today). Put the rule in a pure function in
   `ExportScreenDecisions.kt` (`qrCodeSizeFor(paneWidth, paneHeight)`) so it is tested. The link text, day, and
   too-large note stay under it and scroll. This follows a resized window continuously, which moves nothing abruptly.
2. **Room in a short window** (**Decided 2026-10-21**): in a short window (`isShortWindow`, already computed in `ExportScreen`), side by side, with the
   Link format **in the Shared state** (action `COPY`), the `LinkButton` collapses to its icon and the shared link
   takes the PDF's short-window arrangement: no bottom band, an end column of `FLOATING_CONTROLS_CLEARANCE` (56 dp
   button + 2 × `PAGE_MARGIN`) instead, `bottomInset + PAGE_MARGIN` at the bottom. Every other state (Connect Dropbox,
   Create link, Try again, plan 01's `CopyMissing`, the failures) keeps its label, since a bare cloud or link icon does
   not say what it does, and so keeps today's bottom band, which holds its message clear of the labelled button.
   - `LinkButton` today is the content-lambda `ExtendedFloatingActionButton` with its own `AnimatedContent`; collapsing
   needs the `ExtendedFloatingActionButton(text, icon, expanded, …)` overload (whose collapse is animated), each slot
   keeping the crossfade between actions. Pass `isExpanded = !(isShortWindow && isSideBySide && action == COPY)`
   from `ExportScreen` (`isSideBySide` is computed inside the body's `BoxWithConstraints`; hoist it or the flag). The
   label stays the button's `contentDescription`, as it is today.
   - `LinkPreview` takes two paddings (or a function of the state) and picks one **inside** its `AnimatedContent`'s
   lambda for the state it draws (`shown`), so that the outgoing state fades out where it was rather than moving as
   the incoming one takes another padding. Put the rule beside `pageFitFor`
   (`linkPreviewPaddingFor(isSideBySide, isShortWindow, isShared, bottomInset)`) rather than inline.
   On the 360 × 640 phone on its side this gives the code about 210 dp, whole.
3. **Crossing the short-window threshold or the side-by-side width** (a desktop window resized past 480 dp tall or
   520 dp wide) switches the shared state's padding from a band to a column: animate the padding values
   (`animateDpAsState` on each side) so the code travels rather than jumps, per root `CLAUDE.md` › UI rules; the button's
   collapse is animated by `expanded`.
4. Update `screens/export/CLAUDE.md` › Layout (the short-window sentence) to say the Link preview follows it too, the
   code sized to its pane, and › Link if the button's collapse is chosen.

## Tests

- `ExportScreenDecisionsTest`: `qrCodeSizeFor` gives 240 dp in a large pane, the pane's height less padding in a short
  one, never more than the width allows, and the floor; and the short-window padding function returns an end column and
  no bottom band only for the shared state, side by side, in a short window.
- UI test in `tools/screenshots` (see `tools/screenshots/CLAUDE.md`), beside `SharingTabTest`'s "a shared song's show
  qr code opens its export screen on the link", which already reaches the shared state from the seeded mirror: in
  `TestWindow.PHONE_ON_ITS_SIDE` (640 × 360), open the export on Link and assert that the node with content description
  `Res.string.share_qr_code` is not clipped: its `boundsInRoot` (clipped to the scrolling column's viewport) is as tall
  and wide as its unclipped size (`fetchSemanticsNode().size`), and lies below the app bar. A check of `boundsInRoot`
  against the window alone passes today, since the clipped bounds of a code cut by the viewport are inside it. Also
  assert it does not intersect the bounds of the node with content description `Res.string.share_copy` (the collapsed
  button). Without the fix the code (240 dp) is clipped to a viewport of about 175 dp, so the size check fails.
