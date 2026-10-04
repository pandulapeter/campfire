# Pad every bottom sheet's column above the keyboard outside its scrolling content, so a field focused with Next is brought above the keyboard

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** Android, iOS, web (any touch keyboard; tall windows only)
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/Dialogs.kt,
presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/TextFieldBottomSheet.kt (KDoc only),
presentation/CLAUDE.md

**Challenged:** amended — folds in the per-frame recomposition follow-up (isKeyboardVisible through derivedStateOf, as in 44f370c8d), adds retainSheetContentHeight's comment/KDoc to the docs to update, notes the visible bottom fade above the keyboard and iOS's FocusableAboveKeyboard pan, and makes the recorded edge-to-edge preference an explicit user confirmation.

## Problem
Live run on a 360×640dp Android emulator: in **New song**, pressing the keyboard's Next from Subtitle through Lyricist
to Year leaves the focused Year field under the keyboard (field at y=1102–1232px, keyboard top ~680px; screenshots
`09_year.png`, `11_year_typed.png`). Scrolling by hand reaches it (`13_scrolled_max.png`), so the content padding is
right; only bring-into-view is wrong. uiautomator shows the form's ScrollView bounds `[48,240][672,1280]`, running
under the keyboard.

Root cause, `CampfireBottomSheet` in `Dialogs.kt` (~2083-2125 at 800ebde0b). On a window taller than
`SHORT_WINDOW_HEIGHT` the sheet's column has no keyboard padding at all:

```kotlin
Column(
    modifier = Modifier.fillMaxWidth().then(
        if (isCompactKeyboard) {
            Modifier.heightIn(max = windowHeight).imePadding()
                .then(if (fadeBottomEdge) Modifier.fadingVerticalEdges(scrollState) else Modifier.fadingTopEdge(scrollState))
                .bounceVerticalScroll(scrollState)
        } else {
            Modifier
        },
    ),
) {
    ...
    val bottomPadding = WindowInsets.safeDrawing.exclude(consumedInsets)
        .only(WindowInsetsSides.Bottom).asPaddingValues()
```

and the keyboard's height reaches the content only as `contentPadding`, which every form applies **inside** its scroll
(`Modifier.fadingVerticalEdges(scrollState).bounceVerticalScroll(scrollState).padding(contentPadding)` in
`NewSongDialog`, `SetlistDetailsDialog`, `SongMetadataDialog`, `DeleteLibraryDialog`; the lists' `contentPadding`).
The scroll's viewport therefore extends down under the keyboard. When Next moves focus, the text field asks its
scrolling parent to bring its bounds into view; those bounds are already inside the viewport (which includes the area
the keyboard covers), so nothing scrolls. The short-window branch does not have the problem because it pads the column
with `imePadding()` outside its scroll.

## Fix
Take the keyboard out of the scrolling content on tall windows too, the way the short-window branch already does:
pad the sheet's outer column with `imePadding()` in both branches, keeping the compact branch's order (`heightIn` before
`imePadding`, so the column plus the padding stays within the window):

```kotlin
modifier = Modifier.fillMaxWidth().then(
    if (isCompactKeyboard) {
        Modifier.heightIn(max = windowHeight).imePadding()
            .then(...)
            .bounceVerticalScroll(scrollState)
    } else {
        // Outside the content's own scroll, so that its viewport ends at the keyboard and a field focused with Next is
        // scrolled above it; padding inside the scroll left the viewport under the keyboard, where the field counted as
        // visible.
        Modifier.imePadding()
    },
),
```

Nothing else needs to change: `imePadding()` consumes the IME inset, `onConsumedWindowInsetsChanged` on the inner column
already reports it, and `WindowInsets.safeDrawing.exclude(consumedInsets)` then leaves only what the keyboard does not
cover — 0 with the keyboard up (its inset includes the navigation bar), the navigation bar with it down — so the
content keeps scrolling under the navigation bar with 16dp after its last row exactly as now. The sheet's surface still
runs down under the keyboard (the padding is inside the sheet), so nothing changes visually; the forms' `weight(1f,
fill = false)` boxes simply shrink to the space above it. `imePadding` reads the inset at layout time, so the keyboard
animation still only relayouts.

Visible differences, all intended: with the keyboard up, a form or list that overflows now shows its bottom edge fade
(`fadingVerticalEdges`) just above the keyboard instead of under it, as the short-window branch already does; and on
iOS, whose `ComposeUIViewController` keeps the default `FocusableAboveKeyboard` behaviour (`CampfireViewController.kt`
sets no `onFocusBehavior`), the focused field is now already above the keyboard after bring-into-view, so the whole
view should no longer be panned up for it.

Fold in, in the same commit (same block of `CampfireBottomSheet`): `isKeyboardVisible` is read in composition
(`WindowInsets.ime.getBottom(LocalDensity.current) > 0`), so the open sheet's whole content lambda recomposes on every
frame of the keyboard's slide. Derive it, exactly as 44f370c8d did in `SearchableTopAppBar`:

```kotlin
val ime = WindowInsets.ime
val density = LocalDensity.current
// Derived, so that the sheet recomposes as the keyboard comes and goes rather than on every frame it slides.
val isKeyboardVisible by remember(ime, density) { derivedStateOf { ime.getBottom(density) > 0 } }
```

(`LocalDensity.current` is already read further down for `uncoveredTopInset`; reuse the one value.) With plan 02's
`imePadding()` in both branches, the only things that read the keyboard per frame are then layout-time reads.

Docs to update in the same commit:
- `retainSheetContentHeight` (`TextFieldBottomSheet.kt`): its KDoc ("without retaining the space occupied by the
  keyboard") and the comment in its measure block ("IME insets change during layout") assume the keyboard is part of
  `contentPadding`. The logic stays correct (the keyboard now shrinks `constraints.maxHeight` instead, and the retained
  height can only grow from heights measured above it), but reword both: the bottom padding read here is the
  navigation bar's, read at measure time because it changes as the keyboard slides over it; the keyboard is padded
  outside, by `CampfireBottomSheet`. Same for `TextFieldBottomSheet`'s `@param retainHeight` ("Keyboard and
  system-bar padding are not retained").
- `CampfireBottomSheet` KDoc: replace "the sheet runs down under the navigation bar and the keyboard instead of stopping
  above them, and only its content is kept clear of them" / "Bottom padding excludes insets already consumed by the
  short-window keyboard scroll" with: the sheet runs under both; the keyboard's height is padded outside the content's
  scroll (so a focused field is brought above it), and the navigation bar is handed to the content as `contentPadding`.
- `TextFieldBottomSheet` KDoc "Content keeps clear of the keyboard and system bars" stays true; no change needed beyond
  checking the wording.
- `presentation/CLAUDE.md` (the `ui/dialogs/` paragraph, "hands its content a `contentPadding` of the remaining bottom
  inset — the keyboard included — plus 16dp instead"): change to "of the remaining bottom inset (the navigation bar; the
  keyboard's height pads the sheet's column outside the content's scroll, on every window, so that bringing a focused
  field into view clears the keyboard) plus 16dp".

Option B (not recommended): keep the keyboard as content padding and teach bring-into-view about it with a custom
`BringIntoViewSpec` or `bringIntoViewResponder` that grows the requested rectangle by the keyboard's overlap. Much more
code, platform-sensitive, and it reproduces what the compact branch already gets from `imePadding()`.

**User decision needed before executing:** this departs from a recorded preference (the edge-to-edge memory and
`presentation/CLAUDE.md`) that scrolling containers "run under the navigation bar (and the keyboard)". The navigation
bar part — what the user actually rejected in 2026-09 — is untouched; under an opaque keyboard nothing is visible
either way, and the short-window branch already works this way. The one visible change is the bottom fade appearing
just above the keyboard on an overflowing form or list. If the user wants content to stay laid out under the keyboard,
use option B. Checked and unaffected: the song and setlist assignment pickers (pinned search; the `LazyColumn`'s
viewport ends at the keyboard instead of under it), the cover art sheet's pinned fields on ≥600dp windows (the layout
is chosen from the window, not the space above the keyboard, so nothing moves between pinned and scrolling), the
Delete library sheet (opens focused; the sheet's height is content + keyboard as before), the date picker (its input
mode is short), Song filters' `availableHeight` (maxHeight and the bottom padding both lose the keyboard, so the
difference is unchanged), and the web build (`ProvideKeyboardInsets` hands the VirtualKeyboard's height in as the IME
inset, which `imePadding` reads like Android's; Firefox and Safari report none, as now).

## Tests
None: inset handling inside a `ModalBottomSheet` has no pure logic to extract.

## Manual check
On the 360×640dp emulator (and on a phone with gesture navigation and one with three-button navigation):
1. Songs → + → New song; type a title and press Next repeatedly: every field, Year and Duration included, ends up above
   the keyboard as it gains focus.
2. Edit setlist: focus Description — it scrolls above the keyboard; close the keyboard — the sheet returns to its
   height with the last row 16dp above the navigation bar, and lists (Song assignments, Manage tags) still scroll under
   the navigation bar.
3. Rotate to landscape (short window): the header-scrolls-away behaviour is unchanged.
4. Web build on a phone browser with the VirtualKeyboard API, and the iOS simulator: same as step 1.
   On iOS the sheet itself is no longer panned up behind the focused field. With Layout Inspector, the sheet's
   content does not recompose per frame of the keyboard slide (the derived `isKeyboardVisible`).
