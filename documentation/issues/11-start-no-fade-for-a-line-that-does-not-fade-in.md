<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Start no animation, effect, or inner layer for a line that does not fade in

**Challenged:** amended — the folded run's call keeps its layer too (it is what keeps a tab's or a staff's drawing from
being recorded again while something else in its chunk redraws, a transposition's chord cross-fade for one), so lane G
also edits that one call in `SongSectionContent.kt`; the other callers (`SongScalesSection`, `SongChordsSection`) are
named.

**Kind:** performance  ·  **Severity:** low-medium  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/FadingIn.kt`,
`…/ui/songView/SongSectionChildren.kt` (the unit's call only), `…/ui/songView/SongSectionContent.kt` (the folded run's
`Modifier.fadingIn(isFadingIn = foldedRuns.hasBeenToggled(runFoldKey))` call only; lane N also edits this file)

Lane G.

## Problem

`fadingIn` is applied to every chunk of a song (`SongSectionChildren.kt`: `.fadingIn(isFadingIn = isFadingIn)`) and
again to every chunk's body and every folded run (`SongSectionContent.kt`:
`.fadingIn(isFadingIn = foldedRuns?.hasBeenToggled(sectionFold) == true)`, `Modifier.fadingIn(isFadingIn = foldedRuns.hasBeenToggled(runFoldKey))`):

```kotlin
val alpha = remember { Animatable(if (isFadingIn) 0f else 1f) }
val spec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
LaunchedEffect(alpha) { if (alpha.value < 1f) alpha.animateTo(1f, spec) }
return graphicsLayer { this.alpha = alpha.value }
```

So opening a song creates about two `Animatable`s, two launched coroutines, and two layers per line, though nearly none
of them fade (never on a page's first frame).

## Fix

Decide once, on first composition, whether this one fades (`val fades = remember { isFadingIn }` — exactly the
semantics of today, where only the initial value reaches the remembered `Animatable` and the effect runs once). When it
does not fade, create no `Animatable`, launch no effect, and add no layer — except where the caller asks to keep the
layer: give `fadingIn` a parameter for that (e.g. `keepsLayer: Boolean`), and pass it from `SongSectionChildren`'s
unit modifier and from the folded run's call in `SongSectionContent` (`runModifier`), which then get a plain
`graphicsLayer()`. **That outer layer stays on every chunk**: the chunks are
placed with `placeable.place(position)` (no layer of their own), and the layer is what keeps a gliding chunk's drawing
(a staff's `drawBehind`, a tab's text) from being recorded again on every frame of a glide. **The run's layer stays too**: a
run with a fold toggle is a tab or a grid among a section's lines, and its slots draw in `drawBehind` (a tab's
thousands of frets); without a layer of its own, anything else in the chunk that redraws on every frame — a lyric
line's chord names cross-fading after a transposition step, a chord shape cross-fading — records the run's drawing
again on every one of those frames, since an invalidated draw re-records the nearest layer whole. Those layers are few
(one per slot of a run with a fold toggle). The section body's call (`SongSectionContent`, `.fadingIn(isFadingIn = foldedRuns?.hasBeenToggled(sectionFold) == true)`),
`SongScalesSection`'s, and `SongChordsSection`'s keep their signature and lose their layer when not fading: what they
wrap is all of the chunk but its heading, so the unit's layer already does their work. When it fades, everything stays as today. Update the
KDoc to say both.

Must not break: "every user-caused change is animated" — a section or run unfolded, a chunk arriving with a single
change, still fades in; nothing fades on the first frame; staged changes (`arrivingWith`, `leavingWith`) untouched; the
order of the unit's modifiers (semantics, the fade's layer, arriving, leaving). Lane G merges before N and touches one
line of `SongSectionContent.kt`; lane N re-locates its own edits there by their quoted snippets.

## Tests

- The song details UI tests pass unchanged (`tools/screenshots`, `SongDirectionTest`, `PedalCoverageTest`,
  `SheetMusicReopenTest`). No unit test can see an absent coroutine; the gain is the allocation count, which the
  sweep's measurement method (an untracked `:tools:screenshots` test, deleted afterwards) can confirm by timing a warm
  open of a long plain song (8 ms measured). Lane report only.
