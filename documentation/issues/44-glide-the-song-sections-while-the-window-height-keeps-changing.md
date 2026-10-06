# Glide the song's sections while the window's height keeps changing, as they already do for its width

**Kind:** ux / performance  ·  **Severity:** low  ·  **Platforms:** desktop, web, Android/iPad freeform and split
screen; Android/iOS phones in landscape (short window)
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDetailsScreen.kt,
presentation/CLAUDE.md
**Challenged:** amended — named a third burst the change also turns into a glide (the song's metronome panel opening
or closing in the app bar, 250 ms of height changes, since the pager is under the bar in the same `Column`), with its
manual check; confirmed no new per-frame cost and no change to the pinch, the pedal's row snapping or the bar's
collapse itself.

## Problem

`SongDetailsScreen.kt:1057` and `:1083-1101` (8ee010b36):
```kotlin
val isChangingContinuously = rememberContinuousChange(maxWidth, currentFontScale)
...
availableHeight = maxHeight - topPadding - bottomPadding,
sectionMotion = if (isChangingContinuously) SectionMotion.GLIDE else SectionMotion.SPRING,
...
rowViewportHeight = maxHeight,
```
In a song laid out in rows of columns, every row is padded to the viewport height (`SongLyrics.kt`, `minRowPitch =
rowViewportHeightPx + rowGapPx`), and `availableHeight` is part of `SectionGridKey`, so every change of `maxHeight` moves
every row below the first and searches the grid again. The continuous-change detector watches only the width and the
font scale, so `sectionMotion` stays `SPRING` while the height changes every frame:

1. Desktop/web: dragging the window's bottom edge makes every frame a fresh `animateBounds` target, and the rows trail
   the edge — exactly what `GLIDE` exists to prevent ("a window edge being dragged", per the comment above it). Dragging
   a side edge glides correctly.
2. Phone in landscape (`SHORT_WINDOW_HEIGHT`): the title row collapses with `enterAlwaysScrollBehavior` (`:373`) while
   the song is dragged; the pager sits under the bar in a `Column` (`:374`), so its `maxHeight` grows over the frames of
   the collapse and every row springs under the finger.

3. Every platform: the metronome panel is the app bar's `bottomContent` (`SongDetailsScreen.kt` ~:678), opened and
   closed with a 250 ms tween (`MetronomePanel.kt`, `PANEL_ANIMATION_DURATION`), and the pager is under the bar in the
   same `Column`, so showing or hiding the panel is the same burst of height changes, and the rows spring after every
   frame of it today.

The reviewer's further suspicion — that a fling in case 2 can come to rest off its divider because the rows are
re-padded mid-fling — was not confirmed; it is left to the manual check below.

## Fix

`rememberContinuousChange(maxWidth, maxHeight, currentFontScale)`, and update the comment above it to name the height.
This covers all three: a burst of height changes (a dragged edge, a collapsing bar, the metronome panel opening or
closing) glides, a single one (a window
maximised) still springs. presentation/CLAUDE.md, where `SectionMotion`/the glide is described for a dragged window edge
or a pinch, add the height, the collapsing short-window app bar and the metronome panel.

What does not change: the width, the font scale and a pinch are detected exactly as before (`rememberContinuousChange`
restarts one effect when any of its keys changes, and a rotation changes width and height in the same frame, so it is
still one change and springs). The per-frame work goes down rather than up: the song is already laid out again on every
frame of a height change today (`availableHeight` is in `SectionGridKey`), and presentation/CLAUDE.md notes that
springing after every frame lays every line out twice a frame, which `GLIDE` does not. The extra cost is one
`LaunchedEffect` restart per frame of the burst, as for a width drag. `animatesControls` (the steppers of the song's first
section) is off during a glide, as during a width drag; nothing is stepped while a bar collapses or a panel opens. The
row snapping (`onRowsPlaced`, `keepReaderInPlace`) is fed the same rows; only how the sections reach them changes.

If the manual check still shows a fling coming to rest off a divider in landscape, a follow-up would compute
`availableHeight`/`rowViewportHeight` from the height with the bar expanded (the most the bar can free), so the rows
never move while the bar collapses; not part of this plan.

## Tests

None: `rememberContinuousChange` is a composable around a delay; nothing pure changes.

## Manual check

Desktop, a window at least 800dp wide on a song long enough for two rows of columns: drag the bottom edge slowly up and
down; the rows follow the edge smoothly instead of springing behind it, and settle with a spring once released.
A phone in landscape: scroll a multi-row song so the title row collapses; the rows do not spring while it collapses.
Fling it a few times: it comes to rest on a row divider.
Any window, a multi-row song: open and close the metronome panel from the bar's button: the rows move with the panel's
edge instead of springing after it, and settle with no further movement once the panel is still.
