# Read the metronome icon's pulse color in the draw phase, so a pulse repaints the mark instead of recomposing it every frame

**Kind:** performance  ·  **Severity:** low  ·  **Platforms:** all
**Lane:** M  ·  **Files:**
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/metronome/MetronomeIcon.kt`

Plan 40 (which would have changed the beat animations) was declined by the user (D-40): the swing, pulse and flash stay as they are. This plan holds regardless: the pulse
stays.

## Problem

`ui/metronome/MetronomeIcon.kt` at 491c4254a reads the animated pulse in composition:

```kotlin
) = Box(
    modifier = modifier.graphicsLayer { … beat.pulse … },
) {
    val color = lerp(tint, MaterialTheme.colorScheme.primary, beat.pulse.coerceIn(0f, 1f))
    Icon(painter = painterResource(Res.drawable.ic_metronome_body), contentDescription = contentDescription, tint = color)
    Icon(modifier = Modifier.graphicsLayer { … }, painter = painterResource(Res.drawable.ic_metronome_pendulum),
        contentDescription = null, tint = color)
}
```

`Box` is inline, so `beat.pulse` (an `Animatable`'s snapshot state) is read in `MetronomeIcon`'s own restart scope:
every frame of every pulse recomposes `MetronomeIcon`, and both `Icon`s get a new tint, which rebuilds their
`ColorFilter` and paint modifier. The scale and the pendulum's rotation are already read in `graphicsLayer` and cost no
recomposition. While a click plays with Animate on this is one recomposition per frame per visible mark (the navigation
item, and the song details bar button) for the length of each pulse —
roughly 0.1–0.3 ms a frame on a low-end machine. Small, but it is composition work on the UI thread during exactly the
frames the song is being scrolled under a playing click.

## Fix

Draw both drawables from the draw phase with the tint computed there, keeping the rest:

```kotlin
@Composable
internal fun MetronomeIcon(
    modifier: Modifier = Modifier,
    beat: MetronomeIconBeat,
    contentDescription: String?,
    tint: Color = LocalContentColor.current,
) {
    val body = painterResource(Res.drawable.ic_metronome_body)
    val pendulum = painterResource(Res.drawable.ic_metronome_pendulum)
    val pulseColor = MaterialTheme.colorScheme.primary
    // Read while drawing rather than while composing, so a pulse repaints the mark instead of recomposing it every frame.
    val colorFilter = { ColorFilter.tint(lerp(tint, pulseColor, beat.pulse.coerceIn(0f, 1f))) }
    Box(
        modifier = modifier
            .graphicsLayer { /* the scale, unchanged */ }
            .size(ICON_SIZE)
            .then(
                if (contentDescription == null) Modifier
                else Modifier.semantics { this.contentDescription = contentDescription; role = Role.Image }
            ),
    ) {
        Spacer(Modifier.matchParentSize().drawBehind { with(body) { draw(size, colorFilter = colorFilter()) } })
        Spacer(
            Modifier
                .matchParentSize()
                .graphicsLayer { /* rotationZ and transformOrigin, unchanged */ }
                .drawBehind { with(pendulum) { draw(size, colorFilter = colorFilter()) } },
        )
    }
}

/** The size Material's `Icon` gives a 24-unit drawable, which both of the mark's drawables are. */
private val ICON_SIZE = 24.dp
```

Notes for the executor:
- This replicates what `Icon` did for these two painters: the size (`Icon` takes a painter's intrinsic size, which
  is 24dp for both of these vectors), `Role.Image` semantics with the description on the outer box, and
  the tint as `ColorFilter.tint`. The vector painters mirror themselves for RTL from the `DrawScope`'s layout direction
  as before.
- `tint` changing (the song details button's `animateColorAsState` when the panel opens) still recomposes, which gives
  the `drawBehind` lambdas the new value — correct and rare.
- Use `drawBehind`, not `drawWithCache`'s cache block, for the read: a state read in the cache block rebuilds the cache
  every frame, one in `onDrawBehind`/`drawBehind` only redraws.
- Keep the KDoc; follow the code-style skill (trailing commas, `modifier` first).

## Tests

None: a composable's draw path; nothing pure to test.

## Manual check

1. With a click playing and Animate on, the navigation item's mark and the song details bar button still grow and turn
   the primary color on every beat (more on an accent) and fade back, in light and dark theme, in every palette; the
   button is still primary while the panel is up and animates its color when the panel opens or closes.
2. The mark is the same size and in the same place as before in the navigation bar, both rails and the song details bar
   (compare with a screenshot from before the change), and the pendulum still turns about the crossbar.
3. A screen reader still announces the song details button's description (the tempo the click would start at) and the
   navigation item only by its label.
4. Optional: the Compose layout inspector / recomposition counts on Android show `MetronomeIcon` no longer recomposing
   during a pulse.
