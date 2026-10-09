# Outline every resting beat block, so the bar's blocks stand out from the background at 3:1

**Kind:** accessibility  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/metronome/BeatRow.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/metronome/CLAUDE.md` (the
`BeatRow` sentence at line 19), `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/metronome/CLAUDE.md`
if it describes the blocks' colors

## Problem

The beat blocks rest in faint shades of the second accent color and flash in the full one
(`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/metronome/BeatRow.kt:183` at
b5c8ed3b5):

```kotlin
    val baseColor by animateColorAsState(
        when (level) {
            BeatLevel.ACCENT -> flashColor.copy(alpha = ACCENT_REST_ALPHA)
            BeatLevel.NORMAL -> flashColor.copy(alpha = NORMAL_REST_ALPHA)
            BeatLevel.MUTED -> Color.Transparent
        }
    )
    ...
                .then(if (level == BeatLevel.MUTED) Modifier.border(1.dp, outlineColor, shape) else Modifier)
    ...
private const val ACCENT_REST_ALPHA = 0.5f
private const val NORMAL_REST_ALPHA = 0.22f
```

Computed for the app's own palette (`theme/CampfireColorScheme.kt`: second accent `#A04F03` light / `#FE851E` dark,
background `#FAF8FE` / `#15121C`), a resting block against the background is:

| | light | dark |
| --- | --- | --- |
| plain (0.22) | 1.37:1 | 1.44:1 |
| accent (0.5) | 2.16:1 | 2.76:1 |

All under the 3:1 WCAG asks of a graphical object that is a control (non-text contrast, 1.4.11). The blocks are the
accent editor: a low-vision user cannot see where the plain beats are, nor tap them. Only the muted block, with its
`outline` border (4.26:1 light, 5.84:1 dark), passes.

## Fix

Options:

- **A. (recommended) Draw the muted block's 1dp `outline` border around every resting block**, keeping the fills.
  The block's edge then carries the 3:1 in every palette, since Material's `outline` role is specified against the
  surface for exactly this — the dynamic and the other palettes included, where the second accent is the primary
  color and its faint shades are no better. The flash keeps all of its strength.
- B. Raise the alphas (about 0.45 → plain, 0.75 → accent). That reaches 3:1 for the accent only from ~0.6 in the dark
  theme and ~0.65 in the light one, and it costs the flash what it gains: the full color against an accent resting at
  0.75 is 1.61:1, so the beat being heard barely shows. Rejected.

With A, replace the conditional border with one for every level:

```kotlin
                // Every block is outlined, not only a muted one: the faint shades it rests in are what lets the flash
                // stand out, and too faint to show on their own where the beats are.
                .border(1.dp, outlineColor, shape)
```

Muted, plain and accent stay told apart by their heights (0.3, 0.6, 1.0 of the row) and their fills. Update the
`BeatRow` KDoc ("a muted one only an outline" → "every one outlined, a muted one only that") and the sentence in
`ui/screens/metronome/CLAUDE.md` ("resting in fainter shades of the second accent color …") to say the blocks are
outlined.

## Tests

None: drawing only.

## Manual check

- Metronome tab and the song details panel, light and dark theme, the app's palette and a dynamic (Material You) one:
  every resting block has a visible edge; the flash on the beat is as strong as before; muted blocks still read as
  "off" (outline only).
- An accent block mid-flash shows no odd ring: the gray outline around the full color is barely noticed.
