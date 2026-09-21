# 60 · Every frame of the keyboard sliding in or out recomposes the whole app, from the navigation display down to the song list

**Severity:** performance (Android 11+ and iOS, where the keyboard inset is animated frame by frame; one extra recomposition elsewhere; not measured) · **Area:** `:presentation` (`CampfireApp.kt` `CampfireContent`; `SongsScreen.kt`, `SetlistsScreen.kt`, `components/Controls.kt`)

## Symptom
On a phone with a large library, tap the search action of the Songs screen. The keyboard slides in over ~250 ms
while the search bar's own choreography runs (the button travelling across the bar, the field growing out of its end
edge): the animation drops frames, more so the larger the library and the slower the phone. The same happens when the
keyboard leaves, and when it comes up in the editor.

## Cause
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireApp.kt:312-327`, inside the
content lambda of `NavigationChromeScaffold`, which is the composition that holds the whole app:

```kotlin
val systemBars = WindowInsets.systemBars.asPaddingValues()
val imeHeight = with(density) { WindowInsets.ime.getBottom(density).toDp() }
val shellContentPadding = PaddingValues(…, bottom = maxOf(…, (imeHeight - navigationBarHeight).coerceAtLeast(0.dp)))
val songDetailsContentPadding = PaddingValues(…, bottom = maxOf(systemBars.calculateBottomPadding(), imeHeight))
```

`WindowInsets.ime.getBottom(…)` is a snapshot state read (Android's `WindowInsetsHolder`, and on iOS
`PlatformInsets(getBottom = keyboardOverlapHeight)` over a `mutableStateOf`), and it happens while composing, so every
frame of the keyboard's animation invalidates this scope. Each of those frames then builds two new `PaddingValues`
with a new value, a new `navigationMetadata` map and a new `entryProvider { … }` (`:341-404`), hands `NavDisplay` a
provider it has not seen, and recomposes every screen on the back stack's scene with a changed `contentPadding` —
down to `SongList`, which takes the padding apart with `calculateBottomPadding()` in its own body
(`SongsScreen.kt:286-290`, `:379-383`).

The inset is only ever *used* as padding: by the lists' `contentPadding`, by `Modifier.padding`, by the editor's
field. All of those are layout, and a state read during layout only lays out again.

## Fix
Keep the numbers exactly as they are and move the read of the keyboard from "when the padding is made" to "when the
padding is used". The root stops reading `WindowInsets.ime` altogether; the two list screens, which are where the
keyboard comes up most, pass the padding on without taking it apart. Everything else keeps working unchanged and
merely recomposes its own small scope instead of the app.

1. `CampfireApp.kt`: add, next to `CampfireContent`:

   ```kotlin
   /**
    * The window insets a screen hands to its scrolling content: what the system bars and the chrome leave of the
    * edges, and at the bottom the keyboard wherever it reaches higher than that.
    *
    * The keyboard is asked about when the padding is used rather than when it is made. Its inset is animated, so a
    * composition that reads it runs again on every frame the keyboard is moving for, and the composition these are
    * made in holds the whole app - the navigation display, every screen on it and the song list with them. What
    * uses a padding is a layout, and a layout that reads a state that has changed is only laid out again.
    *
    * @param coveredHeight How much of the keyboard's height is taken by chrome it slides over rather than pushes
    *   away, which is the navigation bar's.
    * @param ime Held as a state because the platforms other than Android hand out a new instance on every
    *   composition, and two of these have to be equal for as long as nothing but the keyboard has moved, or every
    *   screen would be recomposed whenever this composition is.
    */
   @Stable
   private class KeyboardAwarePadding(
       private val start: Dp,
       private val end: Dp,
       private val bottom: Dp,
       private val coveredHeight: Dp,
       private val ime: State<WindowInsets>,
       private val density: Density,
   ) : PaddingValues {

       override fun calculateLeftPadding(layoutDirection: LayoutDirection) = if (layoutDirection == LayoutDirection.Ltr) start else end

       override fun calculateTopPadding() = 0.dp

       override fun calculateRightPadding(layoutDirection: LayoutDirection) = if (layoutDirection == LayoutDirection.Ltr) end else start

       override fun calculateBottomPadding() = maxOf(bottom, with(density) { ime.value.getBottom(this).toDp() } - coveredHeight)

       override fun equals(other: Any?) = other is KeyboardAwarePadding &&
               start == other.start &&
               end == other.end &&
               bottom == other.bottom &&
               coveredHeight == other.coveredHeight &&
               ime === other.ime &&
               density == other.density

       override fun hashCode() = listOf(start, end, bottom, coveredHeight, density).hashCode()
   }
   ```

2. `CampfireContent`, replace `:313-327` (the `systemBars` line above them stays, the system bars do not animate):

   ```kotlin
   // Never read here, see KeyboardAwarePadding.
   val ime = rememberUpdatedState(WindowInsets.ime)
   val shellContentPadding: PaddingValues = KeyboardAwarePadding(
       start = if (windowSize.usesNavigationRail) 0.dp else systemBars.calculateStartPadding(layoutDirection),
       end = systemBars.calculateEndPadding(layoutDirection),
       bottom = if (windowSize.usesNavigationRail) systemBars.calculateBottomPadding() else 0.dp,
       // The keyboard covers the navigation bar instead of pushing it away, so only what is left of it counts.
       coveredHeight = navigationBarHeight,
       ime = ime,
       density = density,
   )
   val songDetailsContentPadding: PaddingValues = KeyboardAwarePadding(
       start = systemBars.calculateStartPadding(layoutDirection),
       end = systemBars.calculateEndPadding(layoutDirection),
       bottom = systemBars.calculateBottomPadding(),
       coveredHeight = 0.dp,
       ime = ime,
       density = density,
   )
   ```

   The arithmetic is the old one: `maxOf(bottom, ime - covered)` with a non-negative `bottom` is
   `maxOf(bottom, (ime - covered).coerceAtLeast(0.dp))`. `WindowInsets.ime` is a composable getter, so it is still
   *called* here; what no longer happens here is `getBottom`, which is the state read.

3. New file `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/PaddingValuesSides.kt`
   (MPL header, package `…presentation.ui.components`):

   ```kotlin
   /**
    * Some of the sides of [source], each asked of it at the moment it is used rather than when this is made, with
    * [extraTop] added above.
    *
    * The paddings the app hands its screens follow the keyboard (see `CampfireApp`), and they are only cheap for as
    * long as nobody takes them apart while composing: `PaddingValues(bottom = padding.calculateBottomPadding())`
    * reads the keyboard right there, and the screen doing it is recomposed on every frame the keyboard moves for. A
    * list or a `Modifier.padding` given this instead asks while it is being laid out.
    */
   @Immutable
   private data class PaddingValuesSides(
       private val source: PaddingValues,
       private val hasStart: Boolean,
       private val hasTop: Boolean,
       private val hasEnd: Boolean,
       private val hasBottom: Boolean,
       private val extraTop: Dp,
   ) : PaddingValues {

       override fun calculateLeftPadding(layoutDirection: LayoutDirection) =
           if (if (layoutDirection == LayoutDirection.Ltr) hasStart else hasEnd) source.calculateLeftPadding(layoutDirection) else 0.dp

       override fun calculateTopPadding() = (if (hasTop) source.calculateTopPadding() else 0.dp) + extraTop

       override fun calculateRightPadding(layoutDirection: LayoutDirection) =
           if (if (layoutDirection == LayoutDirection.Ltr) hasEnd else hasStart) source.calculateRightPadding(layoutDirection) else 0.dp

       override fun calculateBottomPadding() = if (hasBottom) source.calculateBottomPadding() else 0.dp
   }

   /** The named sides of these paddings and nothing on the others, see [PaddingValuesSides]. */
   internal fun PaddingValues.only(
       start: Boolean = false,
       top: Boolean = false,
       end: Boolean = false,
       bottom: Boolean = false,
       extraTop: Dp = 0.dp,
   ): PaddingValues = PaddingValuesSides(
       source = this,
       hasStart = start,
       hasTop = top,
       hasEnd = end,
       hasBottom = bottom,
       extraTop = extraTop,
   )
   ```

4. Use it where the list screens take the padding apart today:
   - `SongsScreen.kt:286-290` and `SetlistsScreen.kt:264-268`, the grids:
     `contentPadding = contentPadding.only(start = true, bottom = true, extraTop = SECTION_HEADER_GAP),`
   - `SongsScreen.kt:379-383` and `SetlistsScreen.kt:434-438`, the fast scrollers:
     `modifier = Modifier.padding(contentPadding.only(top = true, end = true, bottom = true)),`
   - `components/Controls.kt:166-175`, `besideSidePanel` — no longer a composable, since it no longer needs the
     layout direction:

     ```kotlin
     internal fun PaddingValues.besideSidePanel(isSidePanelVisible: Boolean) =
         if (isSidePanelVisible) only(start = true, top = true, bottom = true) else this
     ```

   Remove the `layoutDirection` locals and the imports (`calculateStartPadding`, `calculateEndPadding`,
   `LocalLayoutDirection`, `PaddingValues`) this leaves unused in those three files — check each with a search, since
   `songListColumnCount` and `ControlsSidePanel` still use some of them.

What is deliberately left as it is:
- `songListColumnCount` (`Controls.kt:128`) reads the start and the end padding while composing. Those come from the
  system bars, not from the keyboard, so nothing animates them.
- `ControlsSidePanel` (`Controls.kt:150-158`), the editor (`SongEditorScreen.kt:352-372`, `:478`, `:555`), the
  settings pages and the song details screen still call `calculateBottomPadding()` in composition. After step 2 that
  recomposes the composable that makes the call and nothing above it; the panel only exists beside a list at
  ~1400dp, the editor's scope is the field's wrapper, and the other two never have a keyboard up. Converting them is
  the same one-line change whenever a profile asks for it.
- Do not `remember` the `entryProvider` or the metadata map on hand-picked keys. With the keyboard out of this
  composition it only runs again for a back stack, generation or window change, which are the occasions `NavDisplay`
  needs a new provider for anyway, and a provider remembered on an incomplete key list shows a stale screen.
- Do not replace any of this with `Modifier.imePadding()` on the lists: that shrinks the list's viewport, and the
  rows would stop above the keyboard instead of scrolling under the bars and past it (the edge-to-edge rule).
- `Messages` keeps its eager `systemBars.calculateBottomPadding()`; the snackbar is not lifted by the keyboard today
  and this plan does not change that.

## Tests
None (UI is untested).

## Verify
1. Android emulator or device (API 30+), `./gradlew :app:android:assembleDebug`, install the `.debug` build:
   - Songs screen, open the search: the keyboard comes up, the last rows of the list can still be scrolled clear of
     it, the fast scroller's thumb stops above it, and with the search closed again the list ends under the
     navigation bar as before. Same on the Setlists screen.
   - Editor: place the caret on the last line with the keyboard up — the line is above the keyboard, and the text
     scrolls under the system bar when the keyboard is down.
   - Rotate to landscape and repeat (the rail takes the place of the bar, `coveredHeight` is 0).
   - Layout Inspector with recomposition counts on: open and close the keyboard on the Songs screen.
     `CampfireContent`'s lambda, `NavDisplay`, `SongsScreen` and `SongList` must not count up during the animation
     (before the change each counts roughly one per frame).
2. iOS simulator (`xcodebuild … build`, `simctl install/launch`): the same three checks with the software keyboard
   toggled (Cmd+K).
3. Desktop (`./gradlew :app:desktop:run`) and web: no keyboard inset exists; the lists end where they did, RTL is not
   offered by the app but `only(start = …)` must still put the start padding on the left.
4. `./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution`

## Docs
`presentation/CLAUDE.md`, the `ui/screens/` bullet: after "…get a `contentPadding` for the bottom/horizontal insets
from the outer scaffold so lists scroll edge to edge." add: "That padding follows the keyboard, and asks about it when
it is used rather than when it is made (`KeyboardAwarePadding` in `CampfireApp.kt`), so the keyboard sliding in lays
the lists out again without recomposing the app around them; a screen narrows it with `PaddingValues.only(…)` rather
than by taking it apart while composing, which would bring the recomposition back."

## Touches
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireApp.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/PaddingValuesSides.kt` (new)
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/Controls.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songs/SongsScreen.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/setlists/SetlistsScreen.kt`
- `presentation/CLAUDE.md`

## Depends on
Nothing. Shares `SongsScreen.kt` with plan 03, `SetlistsScreen.kt` with plan 58 and `CampfireApp.kt` with the shell
plans that edit it (53 among them), so it cannot run alongside those.
