# Page through the preview by swipe and by keys, and let it be zoomed

**Kind:** usability  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/PrintExportSheet.kt`, both `strings.xml`
**Challenged:** amended — keys go on the focusable preview pane, never on the whole sheet, and ignore Alt/Ctrl/Meta chords (browser Back); the page index stays a saveable `Int` that drives the pager; a zoom button for users without pinch or wheel; Ctrl+wheel is not relied on in the browser.

## Problem

The preview is one page with "Previous" / "Next" text buttons. No swipe on touch, no arrow or Page Up/Down keys on
desktop and web, and the page index (`rememberSaveable` inside `PrintPreview`, inside the `Crossfade`) resets whenever
the preview leaves composition. A whole A4 page in ~350 dp makes 12 pt text about 7 dp tall with no way to look
closer, so chord placement cannot be checked before printing. The page change is a hard cut.

## Fix

- `HorizontalPager` over the pages. The current page stays a hoisted `rememberSaveable` **`Int`** (`requestedPage`)
  and is the source of truth; the `PagerState` is created from it and follows it: a `LaunchedEffect(pageCount)` calls
  `scrollToPage(requestedPage.coerceIn(0, pageCount - 1))` when `pageCount > 0`, and the pager's `settledPage` is
  written back to `requestedPage` only while `pageCount > 0`. After a rotation the document is null (the layout
  restarts), so a pager state restored on its own would clamp the saved page to 0 against an empty page count and the
  user would lose their place. When the document changes (plan 21 holds the old one until the new is ready) the index
  is kept and clamped. Each page draws through the shared renderer; plan 21's crossfade is around the page's canvas
  inside the pager page, not around the pager.
- Replace the text buttons with the app's `ic_previous` / `ic_next` icon buttons (the existing `print_previous` /
  `print_next` strings as content descriptions) around the "Page n of m" label; keep them for mouse users.
- Keys: Left/Right and Page Up/Down change page, Home/End jump. They are handled by a `focusable()` modifier on the
  **preview pane only** (focus requested when the sheet opens on desktop and web), not by a handler on the sheet's
  column: an ancestor's preview handler would eat Left/Right before the options' checkboxes and segmented buttons got
  them for focus traversal. A press with Alt, Ctrl or Meta down is left alone, as `SongKeyboardShortcuts` does: Alt +
  Left is the browser's Back and Cmd + Left/Right are Back and Forward on a Mac, and on the web the browser's Back
  must keep closing the sheet. Escape is not touched. The song details key handler is not active under a sheet
  (`isUncovered`), so the two do not compete.
- Zoom: pinch and double-tap toggle between fit and 2.5× with pan (`transformable`), reset on page change; the
  pager's scroll is disabled while zoomed in. Add a zoom icon button next to the page controls (`ic_add` /
  `ic_subtract`, two new strings `print_zoom_in` / `print_zoom_out` in both languages) so keyboard, mouse and screen
  reader users can zoom without a gesture. Ctrl/Cmd + scroll is added on the desktop only: in the browser that chord
  is the page's own zoom and is not relied on there.
- The draw scale already follows the canvas size, so the zoomed page is re-rasterized sharp rather than upscaled:
  draw at `size.width * zoom / document.width` inside a clipped box instead of scaling the layer.

## Tests

None (UI).

## Manual check

Swipe through a setlist on a phone, rotate on page 3 (still page 3 once the preview is back), zoom into a chord line,
page with the keyboard on desktop and web (Left/Right in the options still move focus; the web's browser Back and
Alt + Left still close the sheet).
