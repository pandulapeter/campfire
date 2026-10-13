# Scroll the Chords tab's search field to the top of the page while it has the keyboard up

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** Android, iOS, web on phones (wherever a soft keyboard covers the window)
**Challenged:** amended — the effect reads the keyboard through `rememberUpdatedState` (iOS and the web hand out a new,
fixed `WindowInsets.ime` on every composition, so a captured one stops following the keyboard), the field is scrolled
to just under the 24dp top fade rather than under it, and a short window also takes the status bar away
(`CompactKeyboardEffect`), as every other field typed into there does.
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/chords/ChordsScreen.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/chords/ChordLibraryControls.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/chords/CLAUDE.md`

## Problem

The Chords tab is one `LazyVerticalGrid` whose first item is the controls (instrument, key chips, and the search field,
`ChordLibraryControls`), scrolling with the chords "so that a phone on its side keeps a row of diagrams above the
keyboard" (`screens/chords/CLAUDE.md`). The app is laid out under the keyboard rather than resized by it, and the tab
gets the keyboard only as content padding (`CampfireScreens.kt`: `contentPadding = shellContentPadding`, a
`KeyboardAwarePadding` whose bottom is the IME's height). Content padding lets the grid scroll further but does not
shrink its viewport, and nothing scrolls the field when it takes the focus (`components/SearchField.kt` is a plain
`OutlinedTextField`; the focused field's bring-into-view counts the area under the keyboard as visible).

Live run at 360×640dp: tapping "Find a chord" in portrait leaves the field under the keyboard (the key chips are the last
thing visible, `46-search.png`); scrolled by hand the field shows, but the results stay under the keyboard
(`47-search-scrolled.png`); on a phone on its side the keyboard covers everything under the instrument chips, field and
results alike (`79-chords-land-kb.png`). The player types blind, which the root `CLAUDE.md`'s short-window rule ("A short
window gives the keyboard everything it can") is there to prevent.

## Fix

Scroll the grid so the search field stands at the top of the viewport while it is focused and the keyboard is up, which
leaves the room between it and the keyboard to the results:

1. `ChordLibraryControls.kt`: give it `searchFieldModifier: Modifier = Modifier` (or an `onSearchFocusChanged:
   (Boolean) -> Unit` and an `onSearchFieldPlaced: (Int) -> Unit`) applied to the `SearchField`; there,
   `Modifier.onFocusChanged { … it.isFocused }` and `Modifier.onPlaced { … it.positionInParent().y.roundToInt() }` (the
   Column is the grid item's whole height, and `bleedHorizontally` moves nothing vertically, so that offset is the field's
   top inside the item).
2. `ChordsScreen.kt`: hold `isSearchFocused` and `searchFieldTop` (plain `mutableStateOf`/`mutableIntStateOf`), and
   the keyboard's visibility the way `SearchableTopAppBar.kt` and `CampfireBottomSheet.kt` read it:
   `val ime = WindowInsets.ime; val isKeyboardVisible by remember(ime, density) { derivedStateOf { ime.getBottom(density) > 0 } }`
   (a derived Boolean, so the screen recomposes twice per keyboard, not on every frame of it — the reason
   `KeyboardAwarePadding` gives). Then:
   ```kotlin
   // Read through a state: every platform but Android hands out a new, fixed instance on every composition (see
   // KeyboardAwarePadding), which a flow started once would never see move.
   val imeState = rememberUpdatedState(ime)
   LaunchedEffect(isSearchFocused && isKeyboardVisible) {
       if (isSearchFocused && isKeyboardVisible) {
           // Follows the keyboard as it rises, since the grid can only scroll as far as the padding it adds allows.
           snapshotFlow { imeState.value.getBottom(density) }.collectLatest {
               gridState.animateScrollToItem(0, (searchFieldTop - searchFieldScrollMargin).coerceAtLeast(0))
           }
       }
   }
   ```
   `searchFieldScrollMargin` keeps the field's outline out of the top fade: the grid places item 0 at its
   `PAGE_TOP_PADDING` minus the offset, `fadingTopEdge` paints `EDGE_FADE_SIZE` (24dp) over the top at full strength once
   scrolled, and `searchFieldTop` (measured on the modifier handed to `SearchField`) includes `SearchField`'s own 8dp top
   padding, so the outline lands just under the fade with a margin of `EDGE_FADE_SIZE - PAGE_TOP_PADDING - 8.dp` (8dp)
   in pixels. Without it the field's top edge is painted over by the fade.
   The `snapshotFlow` matters: the IME padding grows over the keyboard's animation and the grid cannot scroll past what
   it adds yet, so one scroll at the start would stop short; following it ends with the field at the top once the
   keyboard has settled. Scroll only once per focus (the effect's key), so a player who then scrolls the results by hand
   is not pulled back.

   In a short window (`LocalWindowInfo.current.containerDpSize.height < SHORT_WINDOW_HEIGHT`), also
   `CompactKeyboardEffect(isEnabled = isSearchFocused && isKeyboardVisible && isShortWindow)`, as
   `SearchableTopAppBar`, `CampfireBottomSheet`, and the editor do: the root `CLAUDE.md`'s short-window rule takes the
   status bar away while typing there, and on a phone on its side the field otherwise has barely its own height above
   the keyboard.
3. `screens/chords/CLAUDE.md`, "What it shows": add that the search field, focused with the keyboard up, is scrolled to
   the top of the page, so the chords it finds are above the keyboard, on its side too (where a short window also takes
   the status bar away while it is typed into).

Plans 20 and 22 also edit `ChordsScreen.kt`; land 20 first, then this, then 22.

## Tests

None: the scroll is driven by focus and window insets, which only a device has. (There is no pure decision to extract
beyond "focused and keyboard up".)

## Manual check

On the 360×640dp emulator (Pixel AVD at `wm size 720x1280`, density 320) and on a phone:

1. Portrait, Chords tab at the top: tap "Find a chord". As the keyboard rises the page scrolls so the field sits at the
   top; type `Am`: the results are visible between the field and the keyboard.
2. Landscape: same, with the status bar gone while typing (Android); the field is above the keyboard, not under the
   top fade, with as much of the first row of diagrams as the room leaves. The status bar comes back as the keyboard
   goes.
3. With the keyboard up, scroll the results by hand: the page stays where it was put. Hide the keyboard and tap the field
   again: it scrolls to the top again.
4. A search with no result (`xyz`): the field and the message are above the keyboard.
