# 35 — Android landscape: lists, lyrics and the editor run under the camera cutout

**Severity:** wrong layout (Android phones with a display cutout, landscape) · **Area:** `:presentation`
(`CampfireApp.kt`)

**Read, not run.** This was found by reading the code at HEAD (2065e47f) and the Material 3 and `androidx.activity`
sources the build resolves; it has not been reproduced on a device. The "Verification" section below is how to
confirm it, and confirming it is the first step of the work.

## What the user sees

A phone with a punch-hole or notch camera, turned to landscape. The app bars and the navigation rail keep clear of
the cutout, but the content next to them does not: on the side the cutout is on, the ends of the song rows, the
setlist rows and the fast scroller (with the camera on the end side), and the lyrics, the pager bar and the editor's
text (either side — those screens cover the rail) run under the camera. Turned the other way round, the other edge.

## Cause

`CampfireAndroidApp` calls `enableEdgeToEdge` (`androidMain/.../ui/CampfireAndroidApp.kt`), which in
`androidx.activity` 1.13.0 (`EdgeToEdge.kt`, `EdgeToEdgeApi28` / `EdgeToEdgeApi30`) sets
`layoutInDisplayCutoutMode` to `SHORT_EDGES` (API 28–29) and `ALWAYS` (API 30+): the window is laid out into the
cutout area, and whatever is drawn there is the app's to keep clear.

The chrome does: the Material 3 defaults the app relies on are built on `WindowInsets.systemBarsForVisualComponents`,
which on Android is `systemBars.union(displayCutout)` (`material3-android` 1.5.0-alpha22, which
`org.jetbrains.compose.material3` 1.12.0-alpha03 resolves to on Android,
`androidMain/androidx/compose/material3/internal/SystemBarsDefaultInsets.android.kt:25-26`) —
`TopAppBarDefaults.windowInsets`, `NavigationRailDefaults.windowInsets` (used at `CampfireApp.kt:637`) and the
navigation bar's.

The content does not. Every screen's `contentPadding` is built from the system bars alone,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireApp.kt:388-418`:

```kotlin
    // What is left of the window insets once the chrome has covered the edge it sits on. The screens hand these to
    // their lists as content padding, so that items scroll under the system bars instead of stopping short of them.
    val systemBars = WindowInsets.systemBars.asPaddingValues()
    ...
    val shellContentPadding: PaddingValues = KeyboardAwarePadding(
        start = if (windowSize.usesNavigationRail) 0.dp else systemBars.calculateStartPadding(layoutDirection),
        end = systemBars.calculateEndPadding(layoutDirection),
        ...
    val songDetailsContentPadding: PaddingValues = KeyboardAwarePadding(
        start = systemBars.calculateStartPadding(layoutDirection),
        end = systemBars.calculateEndPadding(layoutDirection),
        ...
```

`WindowInsets.systemBars` does not include the display cutout on Android. On the list screens the start edge is the
rail's (which pads itself by the cutout), so only the end edge shows it; the song details screen and the editor
cover the rail and use both edges.

**Corrected from the reviewer's fix**, which also asked for the top app bar, the rail and the navigation bar to be
given `displayCutout` insets explicitly. Those already include it on Android (above); changing them would change
nothing there and would add the cutout twice nowhere, so they are left alone. On iOS, desktop and the web the
Material defaults are `systemBars` only (`SystemBarsDefaultInsets.skiko.kt`), and iOS's `systemBars` is the safe area,
which already keeps clear of the notch in landscape — the reviewer's "iOS fine".

## The change

Invoke the **`code-style`** skill before the first edit.

Build the content paddings from the same insets Material builds the chrome from on Android. Add, next to
`KeyboardAwarePadding` in `CampfireApp.kt`:

```kotlin
/**
 * The edges the screens keep their content clear of: the system bars and, where the window is laid out into it, the
 * display cutout. Android's edge to edge window reaches into the cutout on every side, and Material's app bars, rail
 * and navigation bar already keep clear of it there (their default insets are these); a camera in the middle of a
 * landscape phone's long edge was otherwise drawn over the ends of the list rows and the lyrics. Elsewhere the cutout
 * is either nothing or inside the system bars already, which a union leaves as it is.
 */
internal val WindowInsets.Companion.contentEdges: WindowInsets
    @Composable get() = systemBars.union(displayCutout)
```

and at `:390`:

```kotlin
    val systemBars = WindowInsets.contentEdges.asPaddingValues()
```

(the local keeps its name, since everything below reads it; update the comment above it to "What is left of the
system bars and the display cutout once the chrome has covered the edge it sits on."). Imports:
`androidx.compose.foundation.layout.displayCutout`, `androidx.compose.foundation.layout.union`.

`internal` rather than `private` because plan 36 (the editor's keyboard padding) needs the same bottom edge.

The bottom sheets are already right: `CampfireBottomSheet` uses `safeDrawing`, which includes the cutout
(`Dialogs.kt:1316`, `:1329`), and so does the update gate's blocking screen.

## Tests

- **No unit test is possible** (UI, untested by policy).
- Compile check: `./gradlew :presentation:compileDebugKotlinAndroid :presentation:compileKotlinDesktop :presentation:compileKotlinWasmJs :presentation:compileKotlinIosSimulatorArm64`

## Verification

1. Android emulator with a cutout: a Pixel 8 / 9 image (punch hole), or Developer options → Display cutout →
   "Corner" / "Double" / "Tall" on any image. `./gradlew :app:android:assembleDebug`, install.
2. Rotate to landscape both ways (`adb shell settings put system accelerometer_rotation 0`, then
   `adb shell settings put system user_rotation 1` and `3`).
3. With the cutout on the **end** side:
   - Songs and Setlists: **before**, the overflow buttons of the rows and the fast scroller sit under the camera;
     **after**, they end short of it, the list still scrolling edge to edge at the bottom.
   - Song details and the editor: the lyrics, the pager bar and the text keep clear of the camera.
4. With the cutout on the **start** side: the rail keeps clear (as before); song details and the editor keep clear
   (before: the first characters were under the camera).
5. Portrait: nothing moves (the cutout is at the top, where the app bars already handle it).
6. iOS simulator, iPhone with a Dynamic Island, landscape: nothing moves compared with before.
7. Desktop and web: nothing moves (both insets are zero).

`documentation/testing/01-android.md` lists "the camera cutout" among what a real phone is for; worth adding a step to
AND-054 or a new AND entry ("Landscape both ways on a phone with a cutout: no row, lyric or editor text under the
camera").

## Docs

- `presentation/CLAUDE.md`, the `ui/screens/` bullet: "get a `contentPadding` for the bottom/horizontal insets from the
  outer scaffold" → "get a `contentPadding` for the bottom/horizontal insets — the system bars and the display cutout
  (`contentEdges`), which Material's own bars already keep clear of on Android — from the outer scaffold".
- `documentation/testing/01-android.md`: the new landscape cutout step above.

## Files touched

- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireApp.kt`
- `presentation/CLAUDE.md`
- `documentation/testing/01-android.md`

## Depends on

Nothing. Plan 36 uses `WindowInsets.contentEdges`, so this lands first. `CampfireApp.kt` order in lane D: 32,
**35**, 41, 44.
