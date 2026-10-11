# Crossfade the tuner's reading line only when its kind changes, not on every cent

**Challenged:** sound

**Kind:** performance  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/tuner/TunerDisplay.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/tuner/CentsText.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/tuner/CentsTextTest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/tuner/CLAUDE.md` (only if its wording of
the reading line needs it)

## Problem

The line under the meter that writes the reading ("+3 cents", "In tune", or nothing) is an `AnimatedContent` whose
target state is the finished string (`TunerDisplay.kt:319-341` at ab97d3105):

```kotlin
private fun TunerReadingLine(
    cents: Float?,
    isInTune: Boolean,
    color: Color,
) = AnimatedContent(
    modifier = Modifier.padding(top = 4.dp),
    targetState = when {
        cents == null -> null
        isInTune -> stringResource(Res.string.tuner_in_tune)
        else -> abs(cents.roundToInt()).let { pluralStringResource(Res.plurals.tuner_cents, it, signedCents(cents)) }
    },
    transitionSpec = { fadeIn() togetherWith fadeOut() },
    contentAlignment = Alignment.Center,
    label = "tunerReading",
) { text -> Text(text = text.orEmpty(), ...) }
```

The tuner publishes a reading every 33 ms while a string sounds, and the smoothed cents of a string being tuned cross
an integer boundary every few readings (a peg being turned crosses many in a row). Every new whole cent is a new target
state, so a new fade-in/fade-out pair (the default spring, a few hundred milliseconds) starts before the previous one has ended. While
somebody tunes, the line holds several `Text`s composed and laid out at once (up to about ten at thirty readings a
second), each animating its alpha, and the default `SizeTransform` animates the container's width whenever the number
of digits changes ("+9" to "+12"), which re-measures and re-lays out the line on every frame of every overlapping
transition. That is composition, layout, and draw work on every frame for as long as the tuner hears a string, on the
screen that is kept on while it listens; it costs battery on a phone tuning for minutes and makes the line visibly
smeared (several numbers half-faded over each other) rather than legible.

The crossfade is meant for the line changing what it says (nothing, a number, "In tune"), not for a number changing.

## Fix

Make the `AnimatedContent`'s target the kind of reading rather than its text, and write the live number inside the
reading state in a plain `Text`:

1. In `CentsText.kt` (beside `signedCents`, which is the display's other pure helper for this line), add

   ```kotlin
   /** What the reading line says: nothing while nothing is heard, that the string is in tune, or how far off it is. */
   internal enum class TunerReadingKind { NONE, IN_TUNE, CENTS }

   internal fun tunerReadingKindOf(cents: Float?, isInTune: Boolean) = when {
       cents == null -> TunerReadingKind.NONE
       isInTune -> TunerReadingKind.IN_TUNE
       else -> TunerReadingKind.CENTS
   }
   ```

   (`TunerAnnouncement.kt`'s `tunerOffsetOf` is the precedent: a small enum and a pure function beside the composable
   that uses it.)

2. In `TunerReadingLine`, remember the last cents heard and target `tunerReadingKindOf(cents, isInTune)`, building
   the text inside the content lambda:

   ```kotlin
   @Composable
   private fun TunerReadingLine(cents: Float?, isInTune: Boolean, color: Color) {
       // The number a fading-out reading keeps showing once the string has died, as the line does today.
       var lastCents by remember { mutableStateOf(0f) }
       if (cents != null) lastCents = cents
       AnimatedContent(
           modifier = Modifier.padding(top = 4.dp),
           targetState = tunerReadingKindOf(cents, isInTune),
           transitionSpec = { fadeIn() togetherWith fadeOut() },
           contentAlignment = Alignment.Center,
           label = "tunerReading",
       ) { kind ->
           Text(
               // The line keeps its height with nothing to say, so the frequencies under it do not move as a note arrives.
               text = when (kind) {
                   TunerReadingKind.NONE -> ""
                   TunerReadingKind.IN_TUNE -> stringResource(Res.string.tuner_in_tune)
                   TunerReadingKind.CENTS -> pluralStringResource(Res.plurals.tuner_cents, abs(lastCents.roundToInt()), signedCents(lastCents))
               },
               style = MaterialTheme.typography.titleMedium,
               color = if (isInTune) color else MaterialTheme.colorScheme.onSurface,
               textAlign = TextAlign.Center,
               maxLines = 1,
           )
       }
   }
   ```

   The content of the state being shown reads `lastCents`, a snapshot state, so the number updates in place on every
   reading with no transition, and only that `Text` recomposes. Writing `lastCents` during composition is a plain
   assignment of the current parameter (idempotent, like `rememberUpdatedState`); if the implementer prefers, use
   `val lastCents = remember { mutableFloatStateOf(0f) }` with a `SideEffect`, but a direct write keeps the first
   frame right, which `CLAUDE.md` asks for. A fading-out `CENTS` state therefore keeps the last number as it fades,
   exactly as today when the string dies.

3. Keep the `SizeTransform` default (`AnimatedContent` only animates size between target states), so a change of kind
   still resizes smoothly; between numbers the width simply follows the text, as any `Text` does. The "keeps its height"
   comment still holds, since `NONE` still lays out an empty one-line `Text` in the same style.

The color (`if (isInTune) color else onSurface`), the style, the alignment, and `maxLines = 1` are unchanged. The
semantics are unchanged too: the column above clears and sets its own description, so nothing in this line is read
out.

## Tests

In `CentsTextTest.kt`, a test of `tunerReadingKindOf`: `null` cents is `NONE` whatever `isInTune` says, a reading in
tune is `IN_TUNE` at any cents (including a nonzero one, since the tracker's in tune waits for a hold), and a reading
not in tune is `CENTS` at `0f`, at a negative, and at a positive value. That pins the one decision the line's
animation now hangs on. The composable itself is UI and is not tested by code (root `CLAUDE.md`).

## Manual check

On a phone, open the Tuner tab, allow the microphone, and pluck a string slightly out of tune while turning the peg
slowly. Before: the cents line under the meter smears, several numbers overlapping in a constant fade, and its width
wobbles. After: the number changes crisply in place; "In tune" still fades in when the string settles, and the line
still fades out when the string dies. With Android's "Profile GPU rendering" (or the Layout Inspector's recomposition
counts) on, the reading line's recompositions per second drop to the reading rate with one `Text`, not several.
