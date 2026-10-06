# Move the Shortcuts chevron into the editor's title row while a landscape keyboard leaves too little room for the control row

**Challenged:** amended — the title-row chevron's `overlappingAction` goes on the `AnimatedVisibility` (outside the animated width, as `ActionOverlap.kt` requires) and overlaps at its end, since it is the first action; platforms narrowed to where an IME inset is reported (never on the desktop, on the web only through the VirtualKeyboard API); manual checks for a hardware keyboard and a split-screen window added.
**Kind:** bug (layout) / docs  ·  **Severity:** high  ·  **Platforms:** Android, iOS (any phone held sideways with the keyboard up, or a short multi-window pane); the web on a phone browser with the VirtualKeyboard API (`ProvideKeyboardInsets`). Not the desktop, which never reports an IME inset, and not a hardware keyboard, which leaves `WindowInsets.ime` at 0 — the condition requires a non-zero inset, exactly like `isTypingInShortWindow`
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/SongEditorScreen.kt`, `presentation/CLAUDE.md`, `CLAUDE.md`

## Problem

On the smallest supported phone (360 × 640 dp) held sideways, the keyboard is about 248dp tall and leaves about
112dp of the 360dp window. The editor keeps its 48dp compact title row (`CampfireTopAppBar`'s `isCompact`) **and** the
whole control row under it (transposition stepper, Edit / Preview, Shortcuts — a 48dp segmented row plus 8dp bottom
padding), so the text field is left roughly one line (measured on the emulator: 48dp; screenshots
`android/46_land_editor_kb3.png`, `android/42_land_editor_kb.png` in the review's scratchpad). Typing a song in
landscape is therefore done through a one-line slot.

The root `CLAUDE.md` promises the opposite — "On Android, typing in a short window also takes the status bar away …
the only way a landscape keyboard leaves room for a field, a title row and three lines" — and `presentation/CLAUDE.md`
contradicts itself in one bullet of `screens/songEditor/`:

> **While the keyboard is up and leaves the window under 480dp** (`SHORT_WINDOW_HEIGHT`, which on the smallest phone is either way up), this row and the insertion rows below it animate away, so the title row is all that holds still over the field;

versus, later in the same bullet:

> On these small screens, and in short windows, the rows collapse once when the keyboard appears. Closing the keyboard does not restore an earlier expansion state. The control row stays available so the user can reopen Shortcuts while typing.

The first sentence is stale: commit ec0a404e5 ("Improve shortcuts auto-hide on the editor screen", 2026-10-03)
deliberately removed `AnimatedVisibility(visible = !isTypingInShortWindow)` around the control row so that the
Shortcuts can be reopened while typing. The code at dac1d9d59 (`LoadedSongEditor`, `bottomContent`) draws the row
unconditionally:

```kotlin
bottomContent = {
    // Transposing, switching pane and folding the insertions away are the things here that do not write at
    // the caret, so they are the ones that stay when the insertions leave.
    Row(
        modifier = Modifier.padding(
            start = contentPadding.calculateStartPadding(layoutDirection) + 16.dp,
            ...
    ) {
        TextTranspositionControls(...)
        SegmentedChoice(...)
        EditorToolbarToggle(
            isExpanded = isToolbarExpanded,
            isLabeled = windowSize != WindowSize.COMPACT,
            isEnabled = panes != EditorPanes.PREVIEW,
            onToggled = { isToolbarExpanded = !isToolbarExpanded },
        )
    }
```

That intent (Shortcuts reachable while typing) is fine in portrait, where the smallest phone keeps about 340dp above
the keyboard and the field gets ~185dp; it is what costs the landscape field all but one line.

Note: on Android the window is also panned at dac1d9d59 (no `windowSoftInputMode`, see plan 51), which is why the
title row is cut at the top in the screenshots; fix plan 51 first or together, and measure after it.

## Fix

Keep the user's intent — the Shortcuts can always be reopened while typing — but give the landscape field its lines
back by moving only the chevron where the control row does not fit:

1. Next to `isTypingInShortWindowState` derive one more state, read the same way (derived, so the keyboard animation
   does not recompose the editor every frame):

   ```kotlin
   // Above a phone's landscape keyboard the control row would leave the field a single line, so there it gives way
   // and only its Shortcuts chevron stays, in the title row; transposing and switching panes wait for the keyboard
   // to go.
   val isControlRowHiddenState = remember(ime, density, windowHeight) {
       derivedStateOf {
           val imeHeight = with(density) { ime.getBottom(this).toDp() }
           imeHeight > 0.dp && windowHeight - imeHeight < MIN_ROOM_FOR_CONTROL_ROW
       }
   }
   ```

   with `private val MIN_ROOM_FOR_CONTROL_ROW = 240.dp` next to `SMALL_SCREEN_SIZE` (KDoc: the 48dp compact title row,
   the 56dp control row and three 18sp lines with the field's padding; the smallest phone upright keeps ~340dp and so
   keeps the row, sideways ~112dp and so loses it).
2. Wrap the control `Row` in `bottomContent` in `AnimatedVisibility(visible = !isControlRowHidden)` (expand/shrink
   vertically + fade, the default `AnimatedVisibility` transition is fine — the change is caused by the user opening
   the keyboard, so it is narrated).
3. In the title row's `actions`, before the undo button, add
   ```kotlin
   AnimatedVisibility(
       // Outside the animated width (see overlappingAction's KDoc), and at its end: it is the first action, and the
       // undo button after it is drawn last, so a press on the overlap still reaches undo as it does today.
       modifier = Modifier.overlappingAction(start = 0.dp, end = ACTION_BUTTON_OVERLAP),
       visible = isControlRowHidden && panes != EditorPanes.PREVIEW,
       enter = expandHorizontally() + fadeIn(),
       exit = shrinkHorizontally() + fadeOut(),
   ) {
       EditorToolbarToggle(isExpanded = isToolbarExpanded, isLabeled = false, isEnabled = true, onToggled = { isToolbarExpanded = !isToolbarExpanded })
   }
   ```
   Undo, redo, save and the menu keep their modifiers unchanged. It is the same state, so tapping it opens the
   insertion rows under the title row exactly as the control row's chevron does.
4. Leave `isToolbarExpanded`, the once-per-keyboard collapse `LaunchedEffect` and `CompactKeyboardEffect` untouched.
5. Fix the docs:
   - `presentation/CLAUDE.md`, `screens/songEditor/` second bullet: replace "**While the keyboard is up and leaves the
     window under 480dp** (…), this row and the insertion rows below it animate away, so the title row is all that
     holds still over the field; only their visibility follows the keyboard, so the pane and the fold the user chose
     are what come back with it;" with: the insertion rows fold once as the keyboard appears (see below), and **where
     the keyboard leaves less than 240dp** (a phone on its side) this row gives way too, its Shortcuts chevron moving
     into the title row so the rows can still be opened while typing; only its visibility follows the keyboard, so the
     pane and the fold the user chose come back with it.
   - Same bullet, later: "The control row stays available so the user can reopen Shortcuts while typing." → "The
     chevron stays available while typing — in the control row, or in the title row where that row has given way."
   - Root `CLAUDE.md`, "A short window gives the keyboard everything it can": "the editor's Shortcuts collapse once
     when the keyboard appears, with the control row kept available to reopen them while typing" → "…, with their
     chevron kept available to reopen them while typing (in the title row where a landscape keyboard leaves no room
     for the control row)".

Alternatives considered: (b) letting the control row scroll away with the field — the field scrolls inside itself
(`BasicTextField` with its own `scrollState`), so the bar would need a nested-scroll connection, much more code for the
same result; (c) going back to hiding the control row with no chevron (pre-ec0a404e5) — contradicts the user's
decision that Shortcuts stay reachable while typing. (a) as above is recommended.

## Tests

None: this is composition and window-inset behavior, which the project does not test by code (root `CLAUDE.md`,
"Only pure logic is tested").

## Manual check

On the 360 × 640 dp emulator (see the smallest-supported-screen recipe), with plan 51 applied:
1. Open a song in the editor, rotate to landscape, tap into the text: the control row slides away, a chevron appears in
   the title row, and the field shows at least three lines.
2. Tap the title-row chevron: the insertion rows open under the title row; tap a Shortcut, it inserts at the caret
   and the keyboard stays up; tap the chevron again to fold them.
3. Close the keyboard: the control row comes back with the pane and fold as they were; the title-row chevron leaves.
4. Portrait on the same phone with the keyboard up: the control row stays (as today), no chevron in the title row.
5. A tablet in landscape with the keyboard up: control row stays.
6. The landscape phone with a hardware keyboard (emulator: keyboard attached, no soft keyboard shown): the control row
   stays and no chevron joins the title row.
7. Undo, right of the title-row chevron, still takes a press on the 12dp where the two overlap.
8. On the web (Chrome on Android, VirtualKeyboard API): the same as step 1 in landscape.
