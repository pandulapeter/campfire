# Reveal the editor's current search match into the part of the field the search sheet actually leaves uncovered, measured from the sheet's top edge

**Challenged:** amended — the field's top is kept in snapshot state (a plain holder is never re-read when only it moves); the sheet's top is only written while a sheet is open; step 5 now grows the room before revealing into it and never shrinks it under the scroll in one frame (closing the sheet would snap the text down); `dialogs/CLAUDE.md` dropped from the files (lane O owns it and it lists no parameters); iOS and web added to the manual check.

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** Android and iOS phones (any window short enough for the sheet to cover more than the bottom 60% of the field)
**Files:**
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/ChordProTextField.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/EditorSearch.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/EditorSearchSheet.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/LoadedSongEditor.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/EditorReveal.kt` (new),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/CampfireBottomSheet.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/EditorRevealTest.kt` (new),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/CLAUDE.md`

(Not `ui/dialogs/CLAUDE.md`: lane O owns it, and it describes `CampfireBottomSheet` without listing its parameters, so
there is nothing to add there. The new parameter's KDoc in `CampfireBottomSheet.kt` is enough.)

## Problem

While the editor's Find or Find and replace sheet is open, `ChordProTextField` scrolls the current match into view with
`revealOffset`, which assumes the sheet covers at most the bottom 60% of the field:

```kotlin
// ChordProTextField.kt, revealOffset
val line = layout.getLineForOffset(offset.coerceAtMost(layout.layoutInput.text.length))
val top = layout.getLineTop(line)
val bottom = layout.getLineBottom(line)
if (top < scrollState.value || bottom > scrollState.value + scrollState.viewportSize * REVEALED_PART) {
    launch { scrollState.animateScrollTo((top - margin).roundToInt().coerceAtLeast(0)) }
}
...
/** How much of the pane from its top a revealed line is kept in, see [revealOffset]. */
private const val REVEALED_PART = 0.4f
private val REVEAL_MARGIN = 48.dp
```

The sheet's real height has nothing to do with 60% of the field. Adding up `EditorSearchSheet` and `CampfireBottomSheet`
(these are estimates from the layout code, not measurements):

- the header: 12dp top padding and a 48dp `IconButton` row, about 60dp;
- Find: 8dp top padding, the query `OutlinedTextField` with its always-present supporting text (about 76dp), the Match
  case `CheckboxListItem` (a one-line `ListItem`, 56dp) and `SHEET_BOTTOM_PADDING` (16dp): **about 216dp** in all;
- Find and replace adds the replacement field (8 + 56 + 8dp) and the Replace / Replace all row (8 + 40 + 8dp):
  **about 344dp**.

The field itself starts below the status bar, the editor's title row and its Edit / Preview row, roughly 130–140dp from
the top of the window. So, estimated:

- On the smallest supported phone (360 × 640dp) with the keyboard up (about 330dp above it), the Find sheet's top edge is
  about 114dp from the top of the window, which covers the field completely; Find and replace fills everything above
  the keyboard.
- With the keyboard down on the same phone, Find leaves about 290dp of a ~480dp field (0.4 × 480 = 192dp, so Find works),
  but Find and replace leaves about 160dp, and a line up to 192dp down — plus the 48dp `REVEAL_MARGIN` the scroll puts
  above it — is counted as "on screen" while it is behind the sheet.
- On a larger phone (about 412 × 915dp) with the keyboard up, Find and replace leaves a strip of roughly 100dp, against
  the 40% (~180dp) `revealOffset` accepts.

The user presses Next and the counter in the sheet moves on, but the highlighted match is under the sheet; in Replace
mode, Replace writes over a match the user cannot see. `animateScrollTo(top - margin)` also puts the line 48dp below the
field's top, which is already more than all of the strip in the worst cases.

There is a second, smaller hole: the scroll cannot go past `scrollState.maxValue`, and the only room after the last line
is the field's decorator (`bottom = restingBottomInset + 32.dp`). A match in the last few lines of a song therefore sits
at the bottom of the field, under the sheet, however the reveal is computed.

## Fix

1. **Report where the sheet starts.** Give `CampfireBottomSheet` an optional
   `onTopMoved: ((topOnScreen: Float) -> Unit)? = null` and call it from the outer `Column` inside `ModalBottomSheet`
   (the one carrying `saveShortcut` / `focusTarget`) with
   `Modifier.onGloballyPositioned { onTopMoved?.invoke(it.positionOnScreen().y) }`. Screen coordinates rather than
   window ones, since on Android the sheet is a window of its own; on the desktop, iOS, and the web both are the same.
   (Alternative: measure only inside `EditorSearchSheet` and add the header height, which `CampfireBottomSheet` does not
   expose — rejected as fragile.) `EditorSearchSheet` passes it on to a new snapshot state of `EditorSearch`,
   `var sheetTop: Float? by mutableStateOf(null)`, reset to `null` in `close()`. It is not saved: it is measured again on
   the first frame after a restore. `EditorSearchSheet` writes it only while `search.mode != null`, so that a layout of
   the sheet after `close()` (the dismiss runs at the end of the hide, and the sheet's window may still lay out once
   before the recomposition that removes it) cannot leave a stale top, and with it step 5's room, behind a closed sheet.
   The state is written on every frame of the sheet's slide, but it is read only inside the field's `snapshotFlow` (and,
   debounced, by step 5), never while composing, so the slide recomposes nothing.
2. **Reveal into the strip.** In `ChordProTextField`, record the vertical scroll container's own top on screen
   (`onGloballyPositioned { viewportTop = it.positionOnScreen().y }` on the modifier that carries
   `bounceVerticalScroll`, before it in the chain so that scrolling does not move it). Keep it in a
   `mutableFloatStateOf`, not in a plain holder like `TextLayoutHolder`: it is read only inside the `snapshotFlow` below,
   so it recomposes nothing, but a plain holder is not observed, and the field's top moves on its own while the sheet is
   up (the Shortcuts panel above it collapsing as the keyboard comes, the title row of a short window), which has to
   re-run the reveal like a moving sheet does. Writing the same value again is no change to a snapshot state, so the
   layouts of typing cost nothing. Pass a new parameter
   `searchSheetTop: () -> Float?` from `LoadedSongEditor` (`{ search.sheetTop }`). The uncovered height is
   `(sheetTop - viewportTop).coerceIn(0f, scrollState.viewportSize.toFloat())`; with `sheetTop == null` (not laid out
   yet) nothing is revealed until it is.
3. **Extract the decision** into a pure, internal function in a new `EditorReveal.kt`:

   ```kotlin
   /** Where to scroll so that the line from [lineTop] to [lineBottom] is inside the uncovered top of the pane, or null where it is there already or there is no room at all. */
   internal fun revealTarget(lineTop: Float, lineBottom: Float, scroll: Int, uncoveredHeight: Float, margin: Float): Int? {
       if (uncoveredHeight <= 0f) return null
       if (lineTop >= scroll && lineBottom <= scroll + uncoveredHeight) return null
       val usedMargin = minOf(margin, uncoveredHeight / 3)
       return (lineTop - usedMargin).roundToInt().coerceAtLeast(0)
   }
   ```

   `revealOffset` calls it in place of the `REVEALED_PART` test; `REVEALED_PART` goes. The sideways reveal is unchanged.
4. **Reveal again when the strip changes.** Replace the single `snapshotFlow` in `ChordProTextField` with one over the
   pair (current match offset, uncovered height). React to a new match at once; react to a height change only once it
   has settled (for example `debounce(150)` on the height alone, or `collectLatest` with a frame's wait), so that the
   sheet sliding in and the keyboard animating do not launch an `animateScrollTo` per frame. The reveal is still skipped
   when the line is already inside the strip, so a settled height that changes nothing moves nothing. The user cannot
   scroll the field while the modal sheet is up (its scrim takes the touches outside it), so these reveals do not fight
   a finger; a new `animateScrollTo` cancels one still running.
5. **Room after the last line (recommended, same plan).** While the sheet is open, add the covered part of the viewport
   (`viewportSize - uncoveredHeight`) to the decorator's bottom padding, so that a match in the last lines can be
   scrolled up into the strip. The decorator's comment explains why that padding must never change while the field is
   flung; the field is not flung while the sheet is up, but the padding must only change when the sheet opens, settles,
   or closes (the debounced height), never per frame. Two orderings matter:
   - **Grow before revealing.** `ScrollState` clamps every step of an `animateScrollTo` to the `maxValue` of that frame
     and drops what it could not consume, so a reveal launched in the same collect that grows the room ends short of its
     target. Apply the new room first, wait until `scrollState.maxValue` reflects it (a `snapshotFlow { maxValue }`
     first value at or above the expected one, or a frame), and only then compute and launch the reveal.
   - **Never shrink under the scroll in one frame.** When the room shrinks — above all when the sheet closes and
     `sheetTop` goes to `null`, right as `onDismiss` hands the focus back to the field — a field scrolled into the room
     would have its scroll clamped to the shorter content and the text would jump down by up to the covered height in
     one frame. Where `scrollState.value` is above the new maximum, first `animateScrollTo` that maximum, then shrink
     the room. (The focus going back to the field then brings the selected match into view as it already does.)

   If this proves troublesome, leave it out and say so in the CLAUDE.md line below.

On the smallest phone with the keyboard up, the uncovered height is zero in Find mode and nothing can be revealed; that is
expected after this fix (the user hides the keyboard with the IME action, after which the strip is ~290dp). Do not try
to shrink the sheet here.

Update `songEditor/CLAUDE.md`: the sentence "the current one is scrolled into the top part of the pane, which the sheet
leaves uncovered, unless it is there already" becomes one naming that the sheet reports its top edge
(`EditorSearch.sheetTop`), that the match is revealed into the strip above it with a margin of at most a third of it,
again whenever the strip settles at another height, and (if step 5 lands) that the field gains the covered height as room
after its last line while the sheet is open. Fix the KDoc of `EditorSearchSheet` ("towards the top of the pane") and of
`revealOffset` to match.

## Tests

New `EditorRevealTest` in `presentation/src/commonTest/.../ui/screens/songEditor/`, pinning `revealTarget`:

- a line wholly inside `[scroll, scroll + uncoveredHeight]` returns `null`;
- a line whose bottom is below the strip (but above `scroll + 0.4 × viewport`, the old rule) returns a target, which is
  the case the old code got wrong;
- a line above `scroll` returns `lineTop - margin`;
- with a 90px strip and a 144px margin, the target is `lineTop - 30` (the margin capped at a third of the strip);
- a target never goes below 0;
- `uncoveredHeight <= 0` returns `null`.

The measuring (`positionOnScreen`) and the debounce are UI and are not unit tested.

## Manual check

On the 360 × 640dp emulator (see the smallest-supported-screen recipe) and on a ~412 × 915dp phone, open a long song in
the editor and open Find and replace with a word that occurs in every verse:

- With the keyboard up on the larger phone, press Next repeatedly: every current match is highlighted in the strip
  between the editor's top bar and the sheet, never behind the sheet.
- Hide the keyboard (Done on the replacement field): the strip grows, and if the current match was covered it is
  scrolled into view once the sheet settles, without a jitter during the slide.
- Switch between Find and Find and replace (Ctrl / Cmd + F / R on a hardware keyboard, or the menu): the match stays
  visible as the sheet grows.
- Search for a word in the last line of the song: it is scrolled up above the sheet (step 5).
- On the desktop, in a short window, the same holds.
- On an iPhone and in the web build (a phone-sized browser window), Next keeps the match in the strip: these measure
  the sheet and the field in the coordinates of their own layers, which have to agree.
- After the last-line search, close the sheet: the text slides back down rather than jumping, and the match stays
  selected and in view.
