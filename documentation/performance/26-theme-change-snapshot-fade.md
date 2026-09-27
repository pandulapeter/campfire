<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

> **Decision:** Decided by the user on 2026-09-27: execute option (A), the stepped lerp, only.
# 26 — Fade a user-initiated theme change in the draw phase instead of recomposing every frame (DECISION NEEDED)

| | |
|---|---|
| Lane | D |
| Impact | low-medium: a ~170 ms spring of full, non-skipping recompositions per tap on a colour or mode; worst on the Settings screen (all four pages composed) and on low-end Android |
| Confidence | high about the cost; **low about the fix being safe on all four platforms** |
| Platforms | all |
| Files | presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/theme/CampfireTheme.kt, presentation/CLAUDE.md |
| Depends on / conflicts with | 25 (land first; same `CampfireTheme` body) |
| Commit message | `Fade a change of theme as a picture of the old colors rather than by recomposing every frame.` |

**DECISION NEEDED.** The pixel-snapshot fade below is feasible with the Compose 1.12 `GraphicsLayer` API, but it behaves differently on Android than on the Skia platforms, it cannot include dialogs, sheets or menus, and it costs a full-window bitmap per tap. Pick one:
- **(A) Stepped lerp.** Keep today's approach but quantize `progress` to about 5 steps. That is 5 full recompositions instead of about 12, with a slightly stepped fade. Tiny and safe.
- **(B) Snapshot fade.** As specified below. Needs testing on all four platforms.
- **(C) Leave user-initiated changes as they are.** They are rare, user-initiated and short, and plan 25 already removes the startup case.

My recommendation is (A), or (C) if the ~170 ms of recomposition on a tap does not show up as dropped frames on a low-end Android device.

## Problem
Same mechanism as plan 25. `CampfireTheme.kt:84,90` provide a new `ColorScheme` and a new `LocalSecondAccentColor` on every frame of `progress`. Both are static composition locals: I checked `LocalColorScheme = staticCompositionLocalOf` in the `material3-desktop-1.12.0-alpha03` jar, where `ColorScheme`'s fields are `final long`. So every frame recomposes the entire app tree with skipping off.

Triggers after plan 25:
- A colour disc or a light/dark segment in Settings → General (`SettingsScreen.kt:461-500`). The `HorizontalPager` composes all four pages (`beyondViewportPageCount = 3`, `:344`).
- The same two controls in the welcome sheet.
- The system switching dark mode while the app follows it (including the desktop poll, plan 30).

## Fix (option B)
Research findings for the Compose 1.12 API: `org.jetbrains.compose.ui:ui-graphics-desktop:1.12.0` ships `androidx.compose.ui.graphics.layer.GraphicsLayer` with `record(density, layoutDirection, size, block)`, `toImageBitmap()` (suspend), and `DrawScope.drawLayer`. `rememberGraphicsLayer()` is in `ui`. So the API exists on all four targets.

1. **Capture pixels, not a display list.**
   - `GraphicsLayer.record { drawContent() }` records *references* to child layers. On Android those are `RenderNode`s, which the next frame re-records with the new colours. A recorded layer drawn over the app would therefore show the new colours wherever a child has its own layer: `graphicsLayer` modifiers, `AnimatedVisibility`, Navigation 3's transition layers, list item animations.
   - On the Skia platforms a child is embedded as an immutable `SkPicture`, so the same code would behave like a snapshot there.
   - The platforms diverge, so only `toImageBitmap()` gives the same result everywhere.
2. **Sequence:**
   - When the target scheme changes, keep providing the *old* scheme for one more frame and set `pendingCapture = true`.
   - A `Modifier.drawWithContent` on the root `Box` records `drawContent()` into `rememberGraphicsLayer()` on that frame.
   - A `LaunchedEffect` then calls `bitmap = layer.toImageBitmap()`, switches to the new scheme (one recomposition), and animates `overlayAlpha` from 1 to 0.
   - The same `drawWithContent` draws `drawImage(bitmap, alpha = overlayAlpha.value)` on top while the alpha is above 0.

   Draw it with `drawImage(alpha = …)`, not a `graphicsLayer { alpha }`: the web build does not redraw a layer whose alpha alone changes (see the FastScroller note in `presentation/CLAUDE.md`).
3. Drop the bitmap as soon as the fade ends. It is a full-window ARGB bitmap: about 10 MB on a phone, about 33 MB for a 4K desktop window.

## Risks (the reason for DECISION NEEDED)
- **Other windows are not in the picture.**
  - On Android, `ModalBottomSheet`, `AlertDialog` and `DropdownMenu` are separate windows. On desktop, iOS and web, dialogs and popups are separate compose layers or scenes.
  - The **welcome sheet** is where a new user changes the theme with the app visible behind it. The sheet would snap while the app behind it fades.
- **Latency and hitch.**
  - The change reaches the screen one or two frames later (capture, then switch).
  - `toImageBitmap()` rasterizes the whole window. On Android this is a hardware render, which is cheap. On the web (Wasm, Skia on a CPU surface) it can take tens of milliseconds on the tap frame, which would be the very jank this plan is meant to remove.
- **A ghost of the old frame.** For ~170 ms the overlay shows the old pixels while the live UI underneath responds. A scroll in that window shows a double image.
- **Interruption.** A second tap during the fade must capture the *currently blended* screen (overlay plus content) to continue from what is visible, as the existing code carefully does (`start = lerp(start, stop, progress.value)`). That is doable by recording the overlay into the new capture, but it is more code.
- **Untestable by unit tests.** The behaviour needs manual checks on four platforms.

## Fix (option A, if chosen)
Replace `progress.value` in the two `lerp` calls with a quantized value. That is about five composition-visible changes instead of about 12. Everything else stays the same:
```kotlin
val steppedProgress by remember { derivedStateOf { (progress.value * STEPS).roundToInt() / STEPS.toFloat() } }
```

## Verification
- `./gradlew :presentation:desktopTest` and each platform's run or link task.
- On a low-end Android device, use the Perfetto / Android Studio frame timeline while tapping through colour discs in Settings. Compare dropped frames and main-thread time before and after.
- For (B), also check on all four platforms: the welcome sheet, a second tap mid-fade, and dark-mode toggling from the system while on Settings.
- Update the `ui/theme/` paragraph of `presentation/CLAUDE.md` ("A change to either cross fades every color role … with one progress value") and the `CampfireTheme` KDoc.
