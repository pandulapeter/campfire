# 21 · Web: one notch of Ctrl + scroll wheel (or a trackpad pinch) throws the song's text size straight to 250 % or 50 %

**Severity:** wrong behaviour (web only; likely — Ctrl + wheel is the browser's own zoom gesture and a trackpad
pinch on a laptop arrives as exactly that, so it is the first thing a desktop browser user tries on a song; the size
also persists, so the next song opens at 250 % too) · **Area:** `:presentation`
(`screens/songDetails/FontScaleGestures.kt`, a new `ui/platform` expect/actual)

## Symptom
1. Open the web build in Chrome or Edge (`./gradlew :app:web:wasmJsBrowserDevelopmentRun`), open a song.
2. Hold Ctrl (Cmd on macOS) and turn the mouse wheel by one notch towards you.

The text jumps from 100 % to the minimum (50 %); one notch the other way jumps to the maximum (250 %). There is
nothing in between. On a MacBook a two-finger pinch on the trackpad does the same within a fraction of a second, and
then flips between the extremes. On the desktop build the same notch changes the size by 5 %, which is what the
code intends.

## Cause
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/FontScaleGestures.kt:98-107`:

```kotlin
if (event.type == PointerEventType.Scroll && (event.keyboardModifiers.isCtrlPressed || event.keyboardModifiers.isMetaPressed)) {
    val delta = event.changes.fold(0f) { total, change -> total + change.scrollDelta.y }
    // Scrolling up (negative delta) zooms in, like in a browser.
    changeFontScale((pendingFontScale ?: fontScale()) - delta * SCROLL_SENSITIVITY)
```

with `SCROLL_SENSITIVITY = 0.05f` "per scroll wheel unit" (`:121`). The unit of `scrollDelta` is not the same
everywhere:
- desktop (AWT) and Android report wheel rotation: 1 per notch, fractions for a precise trackpad;
- the web passes the DOM `WheelEvent.deltaY` through unchanged (Compose Multiplatform 1.12.0, `ui-wasm-js`,
  `ComposeWindowInternal.web.kt`, `onWheelEvent`: `scrollDelta = Offset(x = horizontalScroll.toFloat(), y =
  verticalScroll.toFloat())` with `verticalScroll = event.deltaY`), which in `DOM_DELTA_PIXEL` mode — Chrome, Edge,
  Safari — is about 100 per notch, and a few pixels per event of a trackpad pinch (browsers deliver a pinch as
  `wheel` events with `ctrlKey` set). Compose's own scrolling copes because its web `ScrollConfig` converts by
  `deltaMode` (`foundation-wasm-js`, `JsScrollable.web.kt`, `JsConfig.calculateMouseWheelScroll`); this gesture reads
  the raw value.

So one Chrome notch is `100 × 0.05 = 5.0` of font scale, clamped by `setFontScale` into `MIN_FONT_SCALE..MAX_FONT_SCALE`
(`CampfireViewModel.kt:1948-1949`, 0.5–2.5).

## Fix
Convert the wheel event into notches per platform, and keep the formula in `fontScaleGestures` in notches.

1. `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/Platform.kt`, a new
   expect:

   ```kotlin
   /**
    * How far a scroll wheel event turned the wheel vertically, in notches (positive towards the user), which is the
    * unit a pointer event reports it in everywhere but the web: a browser hands over the DOM event's own delta, which
    * is in pixels there - about a hundred for a notch - or in lines.
    */
   internal expect fun PointerEvent.verticalWheelNotches(): Float
   ```

2. Actuals in `Platform.android.kt`, `Platform.desktop.kt`, `Platform.ios.kt`:
   `internal actual fun PointerEvent.verticalWheelNotches() = changes.fold(0f) { total, change -> total + change.scrollDelta.y }`.
3. `Platform.wasmJs.kt`:

   ```kotlin
   internal actual fun PointerEvent.verticalWheelNotches(): Float {
       val deltaY = changes.fold(0f) { total, change -> total + change.scrollDelta.y }
       return when ((domEventOrNull as? WheelEvent)?.deltaMode) {
           WheelEvent.DOM_DELTA_LINE -> deltaY / LINES_PER_WHEEL_NOTCH
           WheelEvent.DOM_DELTA_PAGE -> deltaY
           else -> deltaY / PIXELS_PER_WHEEL_NOTCH
       }
   }

   private const val PIXELS_PER_WHEEL_NOTCH = 100f // What Chrome, Edge and Safari report for one notch at 100 % zoom.
   private const val LINES_PER_WHEEL_NOTCH = 3f // What Firefox reports for one notch in line mode.
   ```

   Imports: `androidx.compose.ui.dom.domEventOrNull` (public in `ui-wasm-js` 1.12.0, `Events.web.kt`) and
   `org.w3c.dom.events.WheelEvent` (`kotlinx-browser`, already a `wasmJsMain` dependency). A synthetic event with no
   DOM event behind it falls into the pixel branch, which is harmless.
4. `FontScaleGestures.kt:103-105` becomes:

   ```kotlin
   // Towards the user (a positive delta) zooms out and away zooms in, like in a browser. Counted in notches, since
   // the web hands over the browser's own pixels.
   changeFontScale((pendingFontScale ?: fontScale()) - event.verticalWheelNotches() * SCROLL_SENSITIVITY)
   ```

   and the constant's comment says "Font scale change per notch of the scroll wheel."

A trackpad pinch then moves the size by 0.05 per 100 pixels of browser pinch delta — slow and precise, like the
touch pinch, which the KDoc of `fontScaleGestures` asks for. Do not divide by `devicePixelRatio` (browsers already
report CSS pixels), and do not change the desktop or Android behaviour.

## Tests
None (UI is untested).

## Verify
1. `./gradlew :app:web:wasmJsBrowserDevelopmentRun`: in Chrome, Edge and Safari one Ctrl / Cmd + wheel notch changes
   the text size by about 5 %; in Firefox by a few percent (Compose reads `deltaY` before anything reads
   `deltaMode`, which makes Firefox report the event in pixels rather than lines — the line branch is there for
   when it does not) — check the value in the stepper or the display options sheet. Never a jump to 50 % or 250 %.
2. On a MacBook trackpad in Chrome or Firefox (Safari reports a pinch as its own gesture events, not as Ctrl + wheel), pinch on a song: the size follows the fingers smoothly and stops at the limits, without
   flipping between them; the browser page itself does not zoom.
3. Desktop (`./gradlew :app:desktop:run`): unchanged, 5 % per notch.
4. Android with a mouse and Ctrl + wheel (emulator): unchanged.
5. Compile checks: `./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution`.

## Docs
`presentation/CLAUDE.md`, the `screens/songDetails/SongDisplayControls.kt` / `FontScaleGestures.kt` bullet: after
"and Ctrl / Cmd + scroll wheel" add "(counted in notches of the wheel: the web reports the browser's own pixel or
line delta, which `verticalWheelNotches` in `ui/platform` converts, or one notch would jump the size from end to
end)"; and the `ui/platform/Platform.kt` mention of expect/actual behaviour stays as it is.

## Touches
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/FontScaleGestures.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/Platform.kt`
- `presentation/src/androidMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/Platform.android.kt`
- `presentation/src/desktopMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/Platform.desktop.kt`
- `presentation/src/iosMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/Platform.ios.kt`
- `presentation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/Platform.wasmJs.kt`
- `presentation/CLAUDE.md`

## Depends on
Nothing. No other plan of this review edits `Platform.*.kt` or `FontScaleGestures.kt`; it does not touch `CampfireWebApp.kt`.
