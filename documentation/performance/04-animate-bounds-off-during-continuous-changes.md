<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# 04 — Sections stop springing after continuous changes: a pinch, a window being dragged, the editor's preview

| | |
|---|---|
| Lane | A |
| Impact | medium–high on multi-column windows and in the editor's preview; low on a single-column phone |
| Confidence | high on the mechanism, medium on the magnitude |
| Platforms | all |
| Files | presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongLyrics.kt, presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDetailsScreen.kt, presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/SongEditorScreen.kt, presentation/CLAUDE.md |
| Depends on / conflicts with | After 02 and 01 (the same `SongDetailsPage` call site) and before 05, 08 and 07 (all of them edit `SongLyrics`'s parameters and its section loop). 10 relies on the flag added here. Order: 02 → 01 → 03 → **04** → 05 → 08 → 07 → 06 → 09 → 10. |
| Commit message | `Keep song sections from trailing behind a pinch, a window resize or typing in the editor.` |

## Problem
`SongLyrics.kt:262-265` gives every section `animateBounds` unless a navigation transition is resizing the page, or the section is too tall to animate:

```kotlin
val sectionModifier = if (extraWidth > 0.dp || sectionAnimations[index].isTooTallToAnimate) {
    Modifier
} else {
    Modifier.animateBounds(this@LookaheadScope).layoutId(AnimatedSectionLayoutId)
}
```

The KDoc for `extraWidth` (`:137-140`) already gives the reason for the navigation case: "the layout is following a width that changes on every frame, and springing after each of those only makes it lag behind". The same thing happens in three other cases that are not covered:

- **A pinch or a Ctrl + wheel zoom.** Column widths (`MIN/MAX_COLUMN_WIDTH * fontScale`), section heights and positions change on every frame.
- **A desktop or web window being dragged.** `extraWidth` is 0 there, while `columnWidthFor(width, …)` (`:1203`) changes on every frame.
- **The editor's preview** (`SongEditorScreen.kt:690-708`, the same `SongLyrics` with the default `extraWidth = 0.dp`). Every debounced refresh that changes a section's height starts a spring on every section below it. Sections are emitted by position without `key(...)`, so an inserted section shifts the content of every slot below it, and each of those animates too.

The cost comes from the library code (Compose 1.12.0-beta01, `AnimateBoundsModifier.kt:151-153`). In the approach pass, the inner content is measured with `Constraints.fixed(animatedSize.width, animatedSize.height)`, while the lookahead pass measures the same `Text` nodes at the target size. `TextStringSimpleNode` keeps a single-entry `ParagraphLayoutCache`. So whenever the width animates, each line is laid out twice per frame, once per pass, each pass evicting the other's layout. This continues for as long as the spring runs: all through the gesture, the drag or the typing, and about 300 ms after. The sections also visibly trail behind the fingers or the window edge.

## Fix
1. `SongLyrics` gains `animatesSections: Boolean = true`. The modifier condition becomes `if (!animatesSections || extraWidth > 0.dp || sectionAnimations[index].isTooTallToAnimate)`. Document it next to `extraWidth` in the KDoc, for the same reason.
2. **Editor preview** (`SongEditorScreen.kt`, `SongPreview`): pass `animatesSections = false`. The preview is there to show what is being typed, not to narrate it.
3. **Song details page** (`SongDetailsScreen.kt`, `SongDetailsPage`, inside its `BoxWithConstraints`): turn animation off for a burst of changes to the width or the scale. Leave it on for a single discrete change, such as a window maximised, a fold toggled, or a stepper tap.
   ```kotlin
   val burst = rememberContinuousChange(maxWidth, fontScale())   // fontScale is () -> Float after 02
   SongLyrics(…, animatesSections = !burst)
   ```
   Put the helper in the same file:
   ```kotlin
   /** True while [value] keeps changing: from its second change within [CONTINUOUS_CHANGE_MILLIS] of the last one until it holds still for that long. */
   @Composable
   private fun rememberContinuousChange(vararg values: Any): Boolean {
       var isContinuous by remember { mutableStateOf(false) }
       // Not state: only the effect below reads them.
       val tracker = remember { object { var isFirst = true; var isRecent = false } }
       LaunchedEffect(*values) {
           if (tracker.isFirst) { tracker.isFirst = false; return@LaunchedEffect }
           // A change that arrives while the previous one's delay was still running (it was cancelled by this
           // restart) is the second of a burst.
           if (tracker.isRecent) isContinuous = true
           tracker.isRecent = true
           delay(CONTINUOUS_CHANGE_MILLIS)
           tracker.isRecent = false
           isContinuous = false
       }
       return isContinuous
   }
   ```
   `CONTINUOUS_CHANGE_MILLIS` is about 200. The first change of a burst still starts a spring. The second change drops the modifier, which makes the sections snap to follow. This is the same trade the navigation case already makes.
4. **What must not change:**
   - A fold toggled on the details screen still animates the sections around it.
   - A single change of the column count (maximise, a rotation, a stepper tap across a column boundary) still animates.
   - `isTooTallToAnimate` and `AnimatedSectionLayoutId` keep working as they do now.

## Verification
- Run `./gradlew :presentation:desktopTest :app:desktop:run`.
- Desktop, a long song on a two-column-wide window:
  - Drag the window edge. The sections follow the edge instead of springing after it.
  - Double-click the title bar to maximise. The reflow still animates.
- Editor Split pane: type a line break in the first verse. The sections below move instantly and do not spring.
- Pinch on a tablet or emulator with a long song in two columns. Frame times (`dumpsys gfxinfo … framestats`) improve on top of 01, and no motion continues after the fingers lift.
- `presentation/CLAUDE.md`, in the `SongLyrics.kt` entry where it says "animate between layouts with `animateBounds`": add that they do not animate in the editor's preview, and not while the width or the text size keeps changing.
