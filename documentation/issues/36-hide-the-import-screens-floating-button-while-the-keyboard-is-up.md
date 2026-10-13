# Hide the import screen's floating button while the keyboard is up, so it no longer covers the rows being searched

**Kind:** bug (layout)  ·  **Severity:** low  ·  **Platforms:** Android, iOS (a soft keyboard); the short-window part all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/importReport/ImportReportScreen.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/importReport/ImportReportButton.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/importReport/CLAUDE.md`

## Problem

The floating button (Import N files, Try again, Done) is placed over the list at the bottom end of the window, padded
by the screen's content padding, which includes the keyboard (`ImportReportScreen.kt`, at 72d48deb7):

```kotlin
ImportReportButton(
    modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(contentPadding.only(bottom = true, extraBottom = 16.dp))
        .padding(end = 16.dp),
    …
)
```

The list keeps `FLOATING_BUTTON_CLEARANCE` (88dp) under its last row, so the last rows can always be scrolled clear of
it, but every row above them passes under the button. That only matters where the window has little height: on a
360 × 640dp phone with the search field focused, the keyboard leaves about 330dp, the bar takes 64dp, and the 56dp button
plus its 16dp margin sits over the end of whichever rows are left — exactly where each row's tick box and its
Keep both / Replace button are (live run: `82-review.png`, the button over a conflict row's subtitle; `85-review-land.png`,
a phone on its side, the button over the Names already taken header's count and tick box). Typing a search is when the
user is looking at those rows, and Import is not what they are about to press.

## Fix

Hide the button, animated, while the keyboard is up:

- In `ImportReportScreen`, read the keyboard the way `SearchableTopAppBar` and `CampfireBottomSheet` do, derived so that
  the sliding keyboard does not recompose the screen every frame:
  `val ime = WindowInsets.ime; val density = LocalDensity.current; val isKeyboardVisible by remember(ime, density) { derivedStateOf { ime.getBottom(density) > 0 } }`.
- Wrap the `ImportReportButton` call in `AnimatedVisibility(visible = !isKeyboardVisible, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut())`
  aligned `BottomEnd` (the same transition the button's own `AnimatedContent` uses between stages), and keep the
  padding modifiers on the `AnimatedVisibility`.
- Keep the list's `FLOATING_BUTTON_CLEARANCE` as it is: it is laid out once rather than following the keyboard (see the
  comment on `listPadding`), and with the keyboard up the extra 88dp at the end of the list is harmless.

The search's IME action (Search) and Back close the keyboard, and the button comes back with it. A hardware keyboard on
the desktop or the web reports no IME inset, so nothing changes there.

Considered and not recommended: collapsing the extended button to its icon (`ExtendedFloatingActionButton(expanded = …)`)
in a short window. It still covers the tick boxes at the end of the row, and "Import 3 files" is the one place the count
of what will be written is said next to the action.

Add one sentence to the import screen's `CLAUDE.md` (the paragraph ending "The floating button reads **Import N files**"):
"It steps aside while the keyboard is up, which is when the rows it would cover are being searched."

## Tests

None: this is the composition reading the IME inset; there is no pure logic to extract.

## Manual check

On a 360 × 640dp Android emulator (the Pixel_10_Pro_XL AVD at `wm size 720x1280`, density 320), import an archive with
a few songs and a taken name. Tap **Search files**: as the keyboard rises the floating button fades and shrinks out, and
every row's tick box and Keep both button can be tapped. Press the keyboard's Search key or Back: the keyboard leaves and
the button fades back in. Rotate the phone and repeat. On the desktop, typing in the search field leaves the button where
it is.
