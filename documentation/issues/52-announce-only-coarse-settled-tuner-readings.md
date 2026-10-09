# Announce only coarse, settled changes of the tuner's reading, keeping the exact one for exploring

**Kind:** accessibility  ·  **Severity:** medium  ·  **Platforms:** all (Android TalkBack and iOS VoiceOver first)
**Challenged:** amended — the live node is composed before the Column so touch reads the exact reading (as the manual check expects), and the hold applies to null too so the live node never keeps a stale reading over plan 53's prompt and tone descriptions
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/tuner/TunerDisplay.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/tuner/TunerAnnouncement.kt` (new),
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/tuner/TunerAnnouncementTest.kt` (new),
`presentation/src/commonMain/composeResources/values/strings.xml`,
`presentation/src/commonMain/composeResources/values-hu/strings.xml`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/tuner/CLAUDE.md`

## Problem

The whole display is one live region whose description carries the exact cents (`tuner/TunerDisplay.kt:73-78` and
`:99-105` at b5c8ed3b5):

```kotlin
val description = when {
    reading == null -> null
    isInTune -> stringResource(Res.string.tuner_reading_in_tune, noteNameWithOctave(reading.note, notation))
    reading.cents < 0 -> stringResource(Res.string.tuner_reading_flat, noteNameWithOctave(reading.note, notation), abs(reading.cents).roundToInt())
    else -> stringResource(Res.string.tuner_reading_sharp, noteNameWithOctave(reading.note, notation), reading.cents.roundToInt())
}
…
.clearAndSetSemantics {
    description?.let { contentDescription = it }
    liveRegion = LiveRegionMode.Polite
},
```

The engine publishes a reading every `POLL_INTERVAL_MILLIS = 33L` (`TunerEngine.kt:176`), and a held string drifts by a
cent or two between them, so the description changes nearly every poll and the screen reader queues "E2, 12 cents
flat", "E2, 11 cents flat", "E2, 13 cents flat" … faster than it can speak, never catching up with the string. Worse,
the speech comes out of the phone's speaker into the microphone the tuner is listening through.

## Fix

Split what is explored from what is announced.

1. **The exact reading stays the display's `contentDescription`, no longer live.** Keep the `description` computed as
   today (plans 53–55 refine its wording), and remove `liveRegion = LiveRegionMode.Polite` from the Column's
   `clearAndSetSemantics`. Rename it `exactDescription` so the two are not confused.

2. **A second node carries a coarse, settled announcement under `liveRegion = Polite`.** New pure file
   `tuner/TunerAnnouncement.kt`:

   ```kotlin
   /** How far off a reading is, in steps a listener can act on without hearing every cent go by. */
   internal enum class TunerOffset { FAR_FLAT, FLAT, IN_TUNE, SHARP, FAR_SHARP }

   /**
    * The step of a reading [cents] off: in tune only once the tracker says so ([isInTune], which waits for the reading
    * to hold), then flat or sharp by its sign, and far beyond [FAR_CENTS] - the point past which a peg is turned rather
    * than nudged.
    */
   internal fun tunerOffsetOf(cents: Float, isInTune: Boolean) = when {
       isInTune -> TunerOffset.IN_TUNE
       cents < -FAR_CENTS -> TunerOffset.FAR_FLAT
       cents < 0f -> TunerOffset.FLAT
       cents > FAR_CENTS -> TunerOffset.FAR_SHARP
       else -> TunerOffset.SHARP
   }

   /** What the tuner announces: the note and its step, see [tunerOffsetOf]. */
   internal data class TunerAnnouncement(val note: Int, val offset: TunerOffset)

   private const val FAR_CENTS = 15f
   ```

   (A reading within ±5 cents that has not yet held long enough to be `isInTune` is "flat"/"sharp" by its sign; it
   becomes "in tune" 300 ms later, `PitchTracker.IN_TUNE_DELAY_MILLIS`.)

   In `TunerDisplay`, hold the announcement until it has stayed the same for a while, so a string swinging across a
   boundary is not narrated:

   ```kotlin
   val current = reading?.let { TunerAnnouncement(note = it.note, offset = tunerOffsetOf(it.cents, it.isInTune)) }
   var announced by remember { mutableStateOf<TunerAnnouncement?>(null) }
   LaunchedEffect(current) {
       // Said only once it has held: a reading crossing a step and back within a breath is not news to anyone tuning.
       if (current != null) {
           delay(ANNOUNCEMENT_HOLD_MILLIS)
           announced = current
       }
   }
   ```

   with `private const val ANNOUNCEMENT_HOLD_MILLIS = 700L`. `LaunchedEffect` restarts whenever `current` changes, so
   the assignment only happens after 700 ms without a change. Nothing heard keeps the last announcement (no live
   "nothing" announcement — plan 53 gives the display an explore description for that).

   The announced text (plan 55 swaps in the spoken note name):

   ```kotlin
   val announcedText = announced?.let {
       stringResource(
           when (it.offset) {
               TunerOffset.FAR_FLAT -> Res.string.tuner_reading_far_flat
               TunerOffset.FLAT -> Res.string.tuner_reading_coarse_flat
               TunerOffset.IN_TUNE -> Res.string.tuner_reading_in_tune
               TunerOffset.SHARP -> Res.string.tuner_reading_coarse_sharp
               TunerOffset.FAR_SHARP -> Res.string.tuner_reading_far_sharp
           },
           noteNameWithOctave(it.note, notation),
       )
   }
   ```

   **Where the live node sits.** `clearAndSetSemantics` on the Column clears its children, so the live node is a
   sibling: wrap the Column in a `Box(modifier = modifier)` (moving the `modifier` from the Column to the Box, the Column
   keeping `fillMaxWidth().padding(...)` and its semantics), and add after the Column

   ```kotlin
   Box(
       modifier = Modifier.matchParentSize().clearAndSetSemantics {
           announcedText?.let { contentDescription = it }
           liveRegion = LiveRegionMode.Polite
       },
   )
   ```

   It draws nothing. Do not hide it with `hideFromAccessibility()` — a hidden node announces nothing.

   **Challenged — put the live node first, and let it follow the reading back to nothing.** Placed after the Column it
   is the topmost node, so touching the display would read the *settled* announcement — contradicting this plan's own
   manual check ("Touching the display reads the exact 'E2, 3 cents flat'") — and, since nothing heard kept the last
   announcement, it would go on reading a stale "E2, flat" over plan 53's "Play a note" and "Reference tone, A4". So:
   - compose the live `Box(Modifier.matchParentSize()…)` **before** the Column inside the wrapping `Box`, so the Column
     (the explored, exact description) is the one touch finds;
   - let the hold apply to `null` as well — `LaunchedEffect(current) { delay(ANNOUNCEMENT_HOLD_MILLIS); announced =
     current }`, without the `if (current != null)` — so 700 ms after a string fades or a tone starts the live node
     holds no text (a description going away is not spoken), and the next pluck of the same string is announced again
     once it holds. Drop the sentence "Nothing heard keeps the last announcement" accordingly.

   `current` is a data class of the note and the step, so the effect restarts only when one of those changes, not with
   each 33 ms reading (nor when plan 58 makes `TunerDisplay` recompose per reading).

3. Imports to add: `Box`, `LaunchedEffect`, `getValue`, `mutableStateOf`, `remember`, `setValue`, `kotlinx.coroutines.delay`.

Strings, in the tuner block of both files, under the existing comment "How a screen reader reads the meter …" (after
`tuner_reading_in_tune`), with a comment of their own:

```xml
<!-- What a screen reader announces once a reading has settled: the note, and roughly how far it is off. -->
<string name="tuner_reading_far_flat">%1$s, far flat</string>
<string name="tuner_reading_coarse_flat">%1$s, flat</string>
<string name="tuner_reading_coarse_sharp">%1$s, sharp</string>
<string name="tuner_reading_far_sharp">%1$s, far sharp</string>
```

```xml
<!-- What a screen reader announces once a reading has settled: the note, and roughly how far it is off. -->
<string name="tuner_reading_far_flat">%1$s, nagyon mély</string>
<string name="tuner_reading_coarse_flat">%1$s, mély</string>
<string name="tuner_reading_coarse_sharp">%1$s, magas</string>
<string name="tuner_reading_far_sharp">%1$s, nagyon magas</string>
```

The Hungarian follows the tuner's own wording: `tuner_flat` "Mély", `tuner_sharp` "Magas", `tuner_reading_flat`
"%1$s, %2$d cent mély". The argument is the app's own note name, so `stringResource` (not `textResource`) is right.

Docs: `ui/tuner/CLAUDE.md`, the `TunerDisplay` / `TunerMeter` bullet: replace "Read out as one live region." with
"Explored as one node with the exact reading; a second node announces only the note and a coarse step (far flat,
flat, in tune, sharp, far sharp, `TunerAnnouncement.kt`, tested) once it has held for 700 ms, since a reading every
33 ms would be a queue of speech the microphone hears too."

## Tests

`TunerAnnouncementTest` in `presentation/src/commonTest/.../ui/tuner/`:
- `` `a reading the tracker calls in tune is in tune whatever its cents` `` — `tunerOffsetOf(3f, true)` and
  `tunerOffsetOf(-4.9f, true)` are `IN_TUNE`.
- `` `a reading within fifteen cents is flat or sharp by its sign` `` — `-15f`, `-1f`, `0.5f` (not in tune), `15f`.
- `` `a reading beyond fifteen cents is far flat or far sharp` `` — `-15.1f`, `-40f`, `15.1f`, `60f` (a preset's
  string further than a semitone away).

## Manual check

Android with TalkBack, and iPhone with VoiceOver: open the Tuner tab and hold a slightly flat string. The reader says
"E2, flat" once, not a stream of cents; turning the peg up to pitch says "E2, in tune" once it holds. Touching the
display reads the exact "E2, 3 cents flat". Repeat in the song's tuner sheet.
