# Give the compact tuner display's note label a fixed width, so a long prompt cannot squeeze the meter

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all (the sheet on phones, the tab in a short window)
**Challenged:** amended — the compact label's width scales with the font scale and the note never wraps, since a fixed 128dp breaks a displayMedium note at large text
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/tuner/TunerDisplay.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/tuner/CLAUDE.md`

## Problem

In compact mode (always in the sheet, and on the tab in a window under `SHORT_WINDOW_HEIGHT`) the note label sits
beside the meter (`tuner/TunerDisplay.kt:108-115` at b5c8ed3b5):

```kotlin
if (isCompact) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        noteLabel(Modifier.widthIn(min = 96.dp))
        Column(modifier = Modifier.weight(1f)) {
            meter(Modifier.fillMaxWidth())
            frequencies()
        }
    }
}
```

The label has a minimum and no maximum, and a `Row` measures its unweighted children first, so the label takes all the
width it wants and the meter gets what is left. With nothing heard the label is the prompt in `titleLarge`
(`TunerDisplay.kt:139-146`): "Play a note" is about 120 dp, but the Hungarian "Szólaltass meg egy hangot" is about
270 dp — on a 360 dp phone's sheet (328 dp inside its 16 dp padding, less the 16 dp gap) that leaves the meter and the
frequencies some 40 dp, a stub. The moment a note is heard the label shrinks to the note (`displayMedium`, under 110 dp
even for Latin "Sol#4") and the meter jumps back to full width — and back again as the string fades.

## Fix

Options:
- **A (recommended): a fixed label width in compact mode**, so the meter's width never depends on what the label says:
  `noteLabel(Modifier.width(COMPACT_NOTE_WIDTH))` with `private val COMPACT_NOTE_WIDTH = 128.dp` (fits the widest note,
  Latin "Sol#4" in bold `displayMedium`, about 105 dp). In `TunerNoteLabel`, the prompt in compact mode uses
  `MaterialTheme.typography.titleMedium`, `maxLines = 2` and a vertical padding of `4.dp` instead of `12.dp`, so two
  lines of it (2 × 24 sp + 8 dp) stay within the label's 56 dp minimum height and the row's height does not change
  either when a note arrives; the non-compact prompt is unchanged. The note and the prompt keep crossfading through the
  existing `AnimatedContent`, now in a box of one size, so nothing moves. "Szólaltass meg" is close to 128 dp in
  `titleMedium`; if the manual check finds the Hungarian prompt breaking into three lines, use `bodyLarge` for the
  compact prompt (same 24 sp line height, slightly narrower glyphs at 16 sp regular) or 136 dp, not a third line.
- B: put the prompt above the row in compact mode — two layouts to switch between, and the sheet's height would change
  as the prompt comes and goes (animated, but movement the user did not cause).

**Challenged — the fixed width has to grow with the text size.** `displayMedium` is in sp: at 2.0x "E2" alone is about
110dp and Latin "Sol#4" about 210dp, so a 128dp box would break the note across lines (or clip it), where today the
label at least grows. Scale the width with the font scale and never wrap the note:
`noteLabel(Modifier.width(COMPACT_NOTE_WIDTH * LocalDensity.current.fontScale))`, and the note's two `Text`s
`softWrap = false, maxLines = 1`. At one text size the box is still one width whatever it says, so the meter never moves
when a note arrives or fades, which is what this plan is for; at a large text size the meter is narrower, as everything
is. Check the 360dp sheet at 1.3x and 2.0x as well (the meter must keep at least the frequencies' width).

Import `androidx.compose.foundation.layout.width`; drop `widthIn` if nothing else uses it. Name the constant beside
`TunerDisplay` with a KDoc saying what it fits ("The compact label's width: the widest note the notations write, Latin
`Sol#4`, with room to spare, and two lines of the prompt.").

Docs: `ui/tuner/CLAUDE.md`, the `TunerDisplay` / `TunerMeter` bullet: "Compact (note beside meter, in a label of one
width so the meter never moves) in a short window and in the sheet."

## Tests

None: layout.

## Manual check

A 360 × 640 dp phone (the smallest supported screen), app in Hungarian: open a song, overflow → Tuner. With nothing
heard the prompt is two lines at the left and the meter keeps its width; play a note and stop: the note crossfades in
and out and the meter does not move. Repeat in English, and with Settings → chord notation Latin and a G sharp
("Sol#4" fits). On a phone held sideways, the Tuner tab's compact display looks the same.
