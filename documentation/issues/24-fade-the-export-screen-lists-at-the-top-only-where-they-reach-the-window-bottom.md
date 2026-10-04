# Fade the export screen's options list, and the zip contents beside it, at the top only, since they end at the window's bottom

**Challenged:** sound

**Kind:** ux  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/ExportScreen.kt`

## Problem

The app's rule (the user's, 2026-10-04): a scrolling area whose bottom is the window's bottom never fades at its bottom
edge, only at its top; `fadingVerticalEdges` is only for a list in the middle of something. The export screen's two
lazy lists fade both edges regardless of where they sit:

```kotlin
// PrintOptions
LazyColumn(modifier.bounceScrollableContent(state).fadingVerticalEdges(state), state = state, contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding)) {

// ZipContents
LazyColumn(modifier.bounceScrollableContent(state).fadingVerticalEdges(state), state = state, contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding)) {
```

Where they sit (`ExportScreen`, `PrintPanes`): the body is a `BoxWithConstraints` with `weight(1f)` under the app bar,
padded only horizontally, so it runs to the window's bottom edge, and the bottom inset is handed to the lists as content
padding (`bottomInset + SAVE_BUTTON_CLEARANCE` or `+ 8.dp`).

- **Options list** — on a phone it is the whole body (`Modifier.fillMaxSize()`), with the preview as its first item;
  from 520dp of width it is the left pane, `Modifier.width(optionsWidth).fillMaxHeight()`. Either way its bottom is the
  window's bottom, so the fade there contradicts the rule (it shows whenever the options are not scrolled to their end:
  the last options dissolve into the navigation bar area for no reason).
- **Zip contents** (Format: Zip, for a setlist) — beside the options (`isSideBySide`) it is the preview pane,
  `Modifier.fillMaxSize().padding(start = optionsWidth)`, again ending at the window's bottom: the same contradiction.
  On a phone it is inside the options list's first item, a fixed `Modifier.fillMaxWidth().height(360.dp)` box with
  more options below it — a list in the middle of something, where fading both edges is right.

The single song's ChordPro text in the same pane already does it right (`.fadingTopEdge(scrollState,
MaterialTheme.colorScheme.background)`), as does the PDF pager, which only fades its bottom when the options are below
it (`scrolledFromBottom = { if (areOptionsBelow) pastBottom() else 0 }`).

## Fix

1. `PrintOptions`: replace `.fadingVerticalEdges(state)` with `.fadingTopEdge(state)` (the `LazyListState` overload in
   `components/EdgeFade.kt`). Both layouts end at the window's bottom, so no flag is needed.

2. `ZipContents`: fade the bottom only where it is in the middle of the options list. Add a parameter, e.g.
   `isBottomAnchored: Boolean`, and use
   `if (isBottomAnchored) Modifier.fadingTopEdge(state) else Modifier.fadingVerticalEdges(state)` in the chain. Pass
   it through `FilesPreview` (a new parameter of the same name) from the `preview` lambda in `ExportScreen`, as
   `isBottomAnchored = isSideBySide` — the same condition that already picks `FilesPreview`'s `bottomPadding`.

3. Leave the single song text (`fadingTopEdge`) and `PrintPreview` as they are. If `fadingVerticalEdges` is no longer
   imported anywhere in the file after this, it still is (`PrintPreview` uses the lambda overload), so imports stay.

No string or CLAUDE.md change (`presentation/CLAUDE.md` describes only the pager's fade).

## Tests

None: modifier choice in composables.

## Manual check

On an Android phone, open a long setlist's **Export setlist**: scroll the options partway; the rows fade under the app
bar and nothing fades above the navigation bar / the Save button area. Pick **Zip**: the 360dp contents box at the top
of the options still fades at both its edges as it scrolls. Turn the phone sideways (options beside the preview): the
zip contents pane fades at its top only, and so does the options pane. Same on the desktop with a window wider than
760dp.
