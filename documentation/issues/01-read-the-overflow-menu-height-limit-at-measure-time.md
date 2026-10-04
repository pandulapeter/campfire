# Read the overflow menu's height limit at measure time, so the keyboard animation stops recomposing every menu on screen

**Kind:** performance  ·  **Severity:** medium  ·  **Platforms:** all (Android, iOS and web, where the keyboard animates)
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/OverflowMenu.kt

**Challenged:** sound

## Problem
`OverflowMenu` (every song card's menu through `ActionsMenu` in `components/SongActions.kt:175`, every setlist header,
every app bar overflow) reads the window height and the keyboard inset **in composition**, whether the menu is open or
not (`OverflowMenu.kt:53-58` at 800ebde0b):

```kotlin
val density = LocalDensity.current
val windowHeight = LocalWindowInfo.current.containerSize.height
val insets = WindowInsets.safeDrawing.union(WindowInsets.ime)
val availableHeight = with(density) {
    (windowHeight - insets.getTop(this) - insets.getBottom(this)).coerceAtLeast(0).toDp()
}
```

`getBottom` reads the IME inset's snapshot state, so every `OverflowMenu` composed on screen is invalidated on every
frame of the keyboard's slide — opening the songs screen's search or typing into any sheet over a list recomposes all
visible cards' menus about 20 times per animation. This is the pattern 44f370c8d removed from the search bar, and the
one `presentation/CLAUDE.md` describes avoiding with `KeyboardAwarePadding` ("so the keyboard's animation only
relayouts it").

## Fix
Keep the reads outside the popup (the comment above them explains why: the iOS popup layer can hand unbounded
constraints, and on Android the popup window has no insets of its own), but read the inset values while **measuring**,
not composing. Recommended: replace `Modifier.heightIn(max = availableHeight)` with a layout modifier that captures
the `WindowInsets` object and the window height and evaluates them in the measure block:

```kotlin
val windowHeight = LocalWindowInfo.current.containerSize.height // changes only on a resize
val insets = WindowInsets.safeDrawing.union(WindowInsets.ime)    // the object; its values are not read here
...
DropdownMenu(
    modifier = Modifier.layout { measurable, constraints ->
        // Read while measuring: the keyboard's animation then relayouts an open menu instead of recomposing every
        // closed one on screen.
        val limit = (windowHeight - insets.getTop(this) - insets.getBottom(this)).coerceAtLeast(0)
        val placeable = measurable.measure(constraints.copy(maxHeight = minOf(constraints.maxHeight, limit), minHeight = minOf(constraints.minHeight, limit)))
        layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
    },
    ...
)
```

`minOf(Constraints.Infinity, limit)` keeps the iOS unbounded case bounded, which is what the current `heightIn` is for.
Drop the now unused `LocalDensity` / `heightIn` imports. Update the comment above the block so it says the values are
read at measure time and why (no per-frame recomposition of closed menus).

Fallback if the layout modifier misbehaves inside the popup on some platform: compute `availableHeight` only inside
`if (isShown)` (and pass `Dp.Unspecified` otherwise), so only an open menu recomposes per frame.

## Tests
None: the change is a Compose read-site move with no pure logic to extract.

## Manual check
- Android emulator, songs screen with many songs: open the search (keyboard slides up) — with Layout Inspector's
  recomposition counts, the song cards' `OverflowMenu` counts stay flat during the slide (before: +1 per frame).
- Open a song card's menu with the keyboard up and down, in portrait and landscape: the menu still fits above the
  keyboard and scrolls when it is taller than the space; on iOS, rotate with a menu open and check it is still bounded.
