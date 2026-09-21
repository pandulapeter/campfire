# 54 · A song with one very tall section (a whole-song tab, an unclosed `{start_of_verse}`) crashes the song screen every time it is opened

**Severity:** crash (all platforms, in practice phones: narrow column, high density, large text size; rare, but it repeats on every opening of that song because the text size is a saved preference, and the editor's preview of the same song crashes too) · **Area:** `:presentation` (`SongLyrics.kt`: `SongLyrics`, `SongSectionsLayout`, `SectionBounds`)

## Symptom
1. Have a song in which one section is enormous. Sections are never split, so any of these makes one:
   - a full-song guitar tab inside a single `{start_of_tab}` … `{end_of_tab}`;
   - an environment that is opened and never closed (`{sot}`, `{start_of_verse}`, `{soc}`), which takes the rest of
     the file with it — also what the editor's preview sees while somebody is halfway through typing one at the top
     of a long song;
   - a few hundred chorded lines with no blank line between them.
2. Open it on a phone with the text size turned up (2.0–2.5, the "read it from across the room" setting; Android's
   own font scale multiplies on top of it). On a 1080p phone a tab of about 40 systems, or about 240 chorded lines
   that each wrap to three, is enough. At the default text size on a desktop it takes about 6,000 chorded lines.

The app dies during the first measure pass with `IllegalArgumentException: Can't represent a width of 1016 and height
of 282240 in Constraints`. Nothing is wrong with the file and nothing the user can do inside the app gets them to the
song again, short of lowering the text size from another song's screen. Rotating to portrait or narrowing a desktop
window while such a song is open crashes the same way, since a narrower column makes the section taller.

## Cause
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongLyrics.kt:192`
puts `animateBounds` on every section:

```kotlin
val sectionModifier = if (extraWidth > 0.dp) Modifier else Modifier.animateBounds(this@LookaheadScope)
```

`animateBounds` is two layout nodes, and the inner one never measures its content with the constraints it is given
(`androidx/compose/animation/AnimateBoundsModifier.kt`, Compose Multiplatform 1.12.0):

```kotlin
resolveMeasureConstraints = { animatedSize, _ ->
    // For the target Layout, pass the animated size as Constraints.
    Constraints.fixed(animatedSize.width, animatedSize.height)
},
```

`approachMeasure` calls that on **every** approach pass, animating or not (`LayoutModifierNodeCoordinator.measure`
calls `approachMeasure` unconditionally; while nothing animates, `animatedSize` is the size the lookahead pass
measured). A `Constraints` packs its four values into 64 bits with 31 of them shared between a width and a height
(`androidx/compose/ui/unit/Constraints.kt`, `createConstraints`): a width under 8,191 px takes 13 bits and leaves 18,
so a height of 262,143 px or more throws; a width of 8,191 px or more leaves 16, so 65,535 px throws.

Nothing else on the way is limited like that, which is why only `animateBounds` fails:
- `SongSectionsLayout` measures a section with `Constraints(minWidth = columnWidth, maxWidth = columnWidth, maxHeight
  = constraints.maxHeight)` (`:730`), and both callers (`SongDetailsScreen.kt:523`, `SongEditorScreen.kt:557`) put
  `SongLyrics` inside a `verticalScroll`, so that maximum is `Constraints.Infinity`, which takes no bits.
- A measured *size* may be up to 16,777,215 px (`layout(width, height)`), so a `Column` of lines, a `SongTabBlock`
  and the scrolling container around them are all fine at 300,000 px.

So the whole song can be as tall as it likes, and a single section cannot.

The other constraints this module builds were checked for the same limit and are sound — leave them alone:
- `SongLyrics.kt:745` — the row dividers, fixed width and no height.
- `CampfireApp.kt:497` — `constraints.copy(minWidth = 0, minHeight = 0)` of the window's own constraints.
- `components/Search.kt:202` and `:515` — `constraints.copy(minWidth = width, maxWidth = width)` inside the app bar,
  and the two `animateBounds` there (`:194`, `:207`) are on a button and a field of the app bar's height.
- `SongDetailsScreen.kt:520`, `SongEditorScreen.kt:556` — `BoxWithConstraints` of the screen.
- The editor's `BasicTextField` scrolls by itself and is not inside a `LookaheadScope`.

## Fix
The modifier is chosen in composition and the height is only known in measurement, in the very pass that throws, so
"leave the modifier off tall sections" needs two halves: the layout **caps** the height of a section for as long as
the modifier is on it, which makes the throw impossible whatever the text does, and it **reports** a section that
reached the cap, so that the next composition takes the modifier off and the section is measured in full. A section
that tall has nowhere to animate to anyway: it is a column of its own, tens of screens long.

All of it is in `SongLyrics.kt`.

1. Constants, with the others at the bottom of the file:

   ```kotlin
   private const val MAX_ANIMATED_SECTION_HEIGHT = 1 shl 17
   private const val MAX_ANIMATED_WIDE_SECTION_HEIGHT = 1 shl 15
   private const val WIDE_SECTION_WIDTH = (1 shl 13) - 1
   ```

   and, next to `SectionBounds`:

   ```kotlin
   /**
    * The layout id of a section that carries `animateBounds`. It is part of the same modifier chain, so what
    * [SongSectionsLayout] reads can never disagree with what is actually attached, not even for the one frame
    * between a measurement and the composition that answers it.
    */
   private object AnimatedSectionLayoutId

   /**
    * The tallest a section of [columnWidth] is measured while it carries `animateBounds`, which measures its content
    * with `Constraints.fixed` of the section's own size on every pass. A `Constraints` has 31 bits for a width and a
    * height together: 18 of them are left for the height next to a width of less than [WIDE_SECTION_WIDTH], and 16
    * next to a wider one. Both limits are half of what would fit, since a spring that is turned around on its way
    * can carry the animated size past both of its ends.
    */
   private fun maxAnimatedSectionHeight(columnWidth: Int) =
       if (columnWidth < WIDE_SECTION_WIDTH) MAX_ANIMATED_SECTION_HEIGHT else MAX_ANIMATED_WIDE_SECTION_HEIGHT
   ```

   (131,072 px is about 55 phone screens at density 3. A column of 32,767 px or more would need a third tier and
   does not exist: the widest a column gets is 560 dp × 2.5.)

2. `SectionBounds` gets the verdict, and its KDoc a second sentence:

   ```kotlin
   /**
    * Where a section ended up inside [SongSectionsLayout]. The layout is the only one that knows this, and the sections
    * need it to scroll back to their own start when their header is clicked, so it is handed back to them through this.
    * It is also the only one that knows how tall a section is, which decides whether the section can be animated at
    * all (see [maxAnimatedSectionHeight]).
    */
   private class SectionBounds {

       var top by mutableIntStateOf(0)
       var isTooTallToAnimate by mutableStateOf(false)
   }
   ```

3. In `SongLyrics`, replace line 192:

   ```kotlin
   // A section the layout found too tall is measured in full only once this is off it: see maxAnimatedSectionHeight.
   val sectionModifier = if (extraWidth > 0.dp || bounds.isTooTallToAnimate) {
       Modifier
   } else {
       Modifier.animateBounds(this@LookaheadScope).layoutId(AnimatedSectionLayoutId)
   }
   ```

   New imports: `androidx.compose.ui.layout.layoutId`, `androidx.compose.runtime.mutableStateOf` (check whether it is
   there already). Extend the last sentence of `SongLyrics`' KDoc paragraph about sections: "A section is never split
   between columns, and sections animate to their new place when the column count changes (e.g. when a window is
   resized) — except one that is too tall for `animateBounds` to measure, which simply appears there."

4. In `SongSectionsLayout`, replace the `placeables` block (`:728-731`):

   ```kotlin
   val placeables = measurables.mapIndexed { index, measurable ->
       val columnWidth = columnWidths[grid.rows[index]]
       val heightLimit = maxAnimatedSectionHeight(columnWidth)
       // Only a section that is being animated is held to the limit. One that reaches it is cut short for the one
       // frame it takes the composition to take the animation off it, which happens far below the screen.
       val maxHeight = if (measurable.layoutId === AnimatedSectionLayoutId) minOf(constraints.maxHeight, heightLimit) else constraints.maxHeight
       val placeable = measurable.measure(Constraints(minWidth = columnWidth, maxWidth = columnWidth, maxHeight = maxHeight))
       // The approach pass of an animated section reports the size the animation is at, which says nothing about
       // the size it is going to: only the lookahead pass measures that.
       if (isLookingAhead) sectionBounds[index].isTooTallToAnimate = placeable.height >= heightLimit
       placeable
   }
   ```

   Why each part is the way it is:
   - A placeable's height is coerced into the constraints it was measured with by the framework, so with the
     modifier attached the lookahead size — which is what `Constraints.fixed` is built from — cannot exceed the cap,
     whatever `SongSectionContent`, `SongTabBlock` or `SongComment` do inside.
   - The verdict is `>=` in both branches, so it is stable: capped and at the cap → off; measured in full and still
     that tall → stays off; measured in full and shorter (smaller text, wider column) → back on next frame, where
     the capped measurement agrees. While `extraWidth > 0` nothing is animated, every section is measured in full,
     and the verdict is already right when the transition ends.
   - The layout must **not** read `isTooTallToAnimate`; it reads the layout id. The state is written in one frame
     and the composition that answers it runs in the next one, and a measure pass in between that believed the flag
     would measure a section in full with the modifier still on it.
   - Without the `isLookingAhead` guard the approach pass of the same frame would overwrite the verdict with the
     animated height and a capped section could stay capped.

5. Do **not**:
   - decide by the number of lines (the reviewer's suggestion, `section.lines.size > 60`): a line is as tall as it
     wraps, a tab line becomes six or seven rows on a phone, and a bound low enough to be safe at text size 2.5 with
     Android's font scale at 200 % takes the animation away from ordinary long verses;
   - ask for `maxIntrinsicHeight` before measuring to know the height in advance: that is a second text layout of
     every line of the song per new width, which plans 56 and 61 exist to remove;
   - split a tall section into several: "a section is never split" is what the column search relies on;
   - copy `AnimateBoundsModifier.kt` into the project to clamp it there.

Known and accepted: in the frame in which a section first reaches the cap, the song is shorter than it is going to
be by whatever was cut off, so a scroll position restored into that frame from more than 131,072 px down the song is
pulled up to the end of the shorter content. It takes returning to such a song at such a depth. A whole song taller
than 16,777,215 px would fail in `layout()` itself; plan 15's cap on the size of a file that is read keeps that out
of reach and nothing is done about it here.

## Tests
None (UI is untested).

## Verify
1. Make the file:
   ```bash
   { echo '{title: Tall}'; echo '[C]A short [G]verse'; echo; echo '{start_of_verse}'; \
     for i in $(seq 1 6000); do echo "[C]Line $i of a very long [G]verse"; done; echo '{end_of_verse}'; } > tall.cho
   ```
2. `./gradlew :app:desktop:run`, import `tall.cho`, open it. Before: the window dies with `Can't represent a width of
   … and height of … in Constraints`. After: it opens (slowly — 6,000 lines are composed) and scrolls to line 6000.
3. Set the text size to the maximum and back, and drag the window from its narrowest to full width and back: no crash;
   the short first verse still animates to its new place when the column count changes, the long one jumps.
4. Open the song in the editor with the preview visible (wide window): the preview renders; delete the
   `{start_of_verse}` line and put it back: no crash either way.
5. Android (`./gradlew :app:android:assembleDebug`), text size 2.5, system font size at its largest: a 1,000 line
   version of the same file opens, rotates both ways and scrolls to its end; the last line is there (the section is
   not left cut short).
6. An ordinary song on a wide window: resize across a column-count change — sections animate exactly as before.
7. Compile checks: `./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution`.

## Docs
`presentation/CLAUDE.md`, the `screens/songDetails/SongLyrics.kt` bullet: "Sections are never split and animate between
layouts with `animateBounds` inside a `LookaheadScope`." becomes "Sections are never split and animate between layouts
with `animateBounds` inside a `LookaheadScope` — all but one that is too tall for it: `animateBounds` measures its
content with `Constraints.fixed` of its own size, which cannot hold a height of 262,143 px next to any width, so
`SongSectionsLayout` measures an animated section no taller than half of that (`maxAnimatedSectionHeight`), and one
that reaches the limit loses the modifier in the next composition and is measured in full from then on."

## Touches
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongLyrics.kt`
- `presentation/CLAUDE.md`

## Depends on
Nothing. Plans 56, 57 and 61 edit the same file (`gridFor`, the chorded lines' text measurer, `flowIntoRows` and the
`SectionMeasurements` cache — none of the lines above); schedule the four one after another, in any order.
