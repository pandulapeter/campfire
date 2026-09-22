# 33 · A chord grid wider than the column loses its last bars without any sign

**Severity:** wrong behaviour (all platforms. Likely on a phone: the bundled "House of the Rising Sun" grid is cut
from roughly 130-150% text size in the app, less on a 320dp phone or at a large system font, and any grid of more
than four bars is cut at 100%) · **Area:** `:presentation` (`ui/screens/songDetails/SongLyrics.kt`)

## Symptom
Open "House of the Rising Sun" on a phone and pinch the text up to about 140%. Its `{start_of_grid}` lines
(`|: Am . . | C  . . | D  . . | F  . . |`) no longer fit: the last bar shrinks to part of a chord and then disappears,
with no wrap, no scroll and no ellipsis to say anything is missing.

## Cause
`SongGridLine` (`SongLyrics.kt:656-678`) is

```kotlin
) = Row(modifier = Modifier.fillMaxWidth()) {
    line.tokens.forEach { token ->
        ...
        Text(modifier = Modifier.padding(end = GRID_TOKEN_GAP), text = text, style = style, softWrap = false, color = color)
```

A `Row` offers each child what is left of the width. Once it has run out, the remaining tokens are measured at zero
or a few pixels and clipped. The section is measured at exactly the column width (`SongSectionsLayout`), and the column
never grows past the window on a phone (the minimum column width is 384dp × text scale, wider than the window), so
nothing further out scrolls either. Tablature wraps its staff into systems (`SongTabBlock`) and a staff-less
`{start_of_tab}` scrolls sideways (`:455`); a grid does neither.

## Fix
Wrap a grid line between whole bars, the way a tab wraps whole systems: the tokens are grouped into bars, and the bars
flow in a `FlowRow`. `FlowRow` answers intrinsic height queries by running its wrapping, so the column search in
`SongSectionsLayout` (which works from intrinsic heights) already sees the extra lines.

Replace `SongGridLine` with:

```kotlin
/**
 * One `{start_of_grid}` line: bars, chords, beats and repeats laid out as a chord chart. A line wider than its column
 * breaks between bars rather than being cut off, the way a staff of tablature is broken into systems, so every chord
 * of it stays on the page at any text size.
 */
@Composable
private fun SongGridLine(
    line: ChordProLine.Grid,
    lyricsStyle: TextStyle,
    chordStyle: TextStyle,
) = FlowRow(modifier = Modifier.fillMaxWidth()) {
    line.tokens.bars().forEach { bar ->
        Row {
            bar.forEach { token ->
                val (text, style, color) = when (token) {
                    // (the existing five branches, unchanged)
                }
                Text(
                    modifier = Modifier.padding(end = GRID_TOKEN_GAP),
                    text = text,
                    style = style,
                    softWrap = false,
                    color = color,
                )
            }
        }
    }
}

/**
 * The tokens of a grid line cut into bars, each ending on the bar line that closes it, so that a wrapped line never
 * starts with a stray bar line. The line that opens the first bar stays with it, and whatever follows the last bar
 * line (a repeat count, a comment) is a piece of its own.
 */
private fun List<GridToken>.bars(): List<List<GridToken>> {
    val bars = mutableListOf<List<GridToken>>()
    var bar = mutableListOf<GridToken>()
    forEach { token ->
        bar += token
        if (token is GridToken.Bar && bar.size > 1) {
            bars += bar
            bar = mutableListOf()
        }
    }
    if (bar.isNotEmpty()) bars += bar
    return bars
}
```

Import: `androidx.compose.foundation.layout.FlowRow` (no opt-in: `Tags.kt` uses it without one in this Compose
version). `Row` is already imported.

A line that fits is laid out exactly as before (same tokens, same gaps, one row). A single bar wider than the column
(rare: one bar of many chords at a huge text size) is still clipped at its end, which is the same trade-off tab makes
for a single column wider than the page.

## Tests
None (UI).

## Verify
1. Android phone, "House of the Rising Sun", text size at 100%: the grid looks as before. Pinch to 150% and 250%:
   the lines wrap after a bar line, every chord and the final `:| x2` visible, nothing clipped.
2. Two columns on a tablet: the section heights account for the wrapped lines (no overlap with the section below, no
   clipped bottom).
3. The editor's preview shows the same while typing a long grid line.
4. Lyrics-only mode still drops the grid.
5. Compile: `:presentation:compileKotlinDesktop`, `:app:android:assembleDebug`.

## Docs
`presentation/CLAUDE.md`, in the song details paragraph, after "…so that one still scrolls sideways.": add "A grid
line wider than its column breaks between bars (`SongGridLine`, a `FlowRow` of whole bars), so no chord of it is cut
off at a large text size."

## Touches
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongLyrics.kt`
- `presentation/CLAUDE.md`

## Depends on
Nothing. 34 and 37 also edit `SongLyrics.kt`; run them one after another.
