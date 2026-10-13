# Bound the chord crossfade's offscreen layers to the diagram and reuse one Paint

**Kind:** performance  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongChordsSection.kt`

## Problem

While a cell of the song's Chords section crossfades to another shape (plan 10's tuning change, a shape picked in the
sheet), it is drawn twice, each time into an offscreen layer (`SongChordsSection.kt` at 72d48deb7):

```kotlin
private inline fun DrawScope.withLayerAlpha(alpha: Float, area: Size, block: () -> Unit) {
    val canvas = drawContext.canvas
    canvas.saveLayer(Rect(-area.width, -area.height, area.width * 2, area.height * 2), Paint().apply { this.alpha = alpha })
    block()
    canvas.restore()
}
```

The layer is three times the diagram's width and height, nine times its area, for a reason that needs a few pixels
(its KDoc: "a stroke on its edge is drawn half outside it"). Every fading cell allocates two of these and two `Paint`s
on every frame of the fade, and a tuning change fades every cell of the section at once (a 12-chord song: 24 layers of
nine diagrams each per frame). `drawChordDiagram` (`components/ChordDiagram.kt`) keeps everything inside the area but
for half a stroke: the grid starts at `size.width * SIDE_BAND`, the markers at `top / 2`, the base fret label is
clamped with `coerceAtLeast(0f)`, and the keyboard's outer keys are stroked on its edges.

## Fix

- Inflate the layer by a small margin instead of a whole diagram on every side, e.g.
  `val margin = minOf(area.width, area.height) * LAYER_MARGIN` with `LAYER_MARGIN = 0.1f` (comfortably more than any
  stroke `drawChordDiagram` draws, `LINE_WIDTH` and `NUT_WIDTH` included) and
  `Rect(-margin, -margin, area.width + margin, area.height + margin)`; keep the KDoc's reason, with the new extent.
- Reuse one `Paint` per draw pass rather than allocating one per layer: e.g. hold a `Paint()` in the block that draws
  the rows (`remember { Paint() }` in the composable that owns the draw lambda, passed down to `drawCell` with the other
  draw arguments) and set its `alpha` before each `saveLayer`.

Check both themes on the desktop, where Skia honours the layer bounds exactly: no clipped stroke on the outer strings,
the nut, the `×`/`○` markers, the base fret number, or a keyboard's first and last key during the fade.

## Tests

None: it is drawing code with no pure logic, and the bounds' effect is only visible on screen.

## Manual check

On the desktop and on a low-end Android phone, open a song of 10+ chords, pick another tuning (or another shape for one
chord) and watch the fade: nothing of a diagram is clipped at any moment of it, in the light and the dark theme, on the
guitar, the ukulele, and the keyboard. Optionally compare GPU frame times of the fade (Android "Profile HWUI rendering")
before and after.
