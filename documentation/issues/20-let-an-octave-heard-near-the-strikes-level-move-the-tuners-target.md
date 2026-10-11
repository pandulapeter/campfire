# Let an octave answer heard near the strike's level move the tuner's target, keeping the octave guard for a dying string only

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `tuner/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/tuner/implementation/PitchTracker.kt`,
`tuner/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/tuner/implementation/PitchTrackerTest.kt`,
`tuner/implementation/CLAUDE.md`

## Problem

d9dbb82ba ("Improve the tuner's accuracy on quiet strings…") added an octave guard to `PitchTracker.hear`
(`PitchTracker.kt` L76–84 at d650f3cb0):

```kotlin
val current = target
// A string that is ringing cannot change its octave, but its reading can: as it dies into the noise the
// detector finds twice the period now and then. Only a new strike moves the target by whole octaves; an
// octave struck too softly to be heard as one is read once the held note has been let go.
if (current != null && !isStruck && isOctaveOf(frequency, current, config)) {
    if (timeMillis - lastHeardTime <= HOLD_MILLIS) return
    release()
}
lastHeardTime = timeMillis
```

An ignored octave answer returns before `lastHeardTime = timeMillis`, but every answer that agrees with the target sets
it. So once a target is in the wrong octave, it is kept for as long as one answer in every 500 ms agrees with it, however
many answers in between say the other octave. `isStruck` is cleared the moment the first target is confirmed (L89–92,
L101–104), so the guard is active from the first reading of a pluck on.

That is the case the same commit's 100 Hz high-pass invites: a bass's E1 (41 Hz) has its fundamental cut by about 30 dB,
so the first answers after a pluck can read E2, the target becomes 40, and the correct answers (28) that follow are all
dropped as "octaves" while an occasional E2 answer keeps the wrong target alive. The player sees E2 for the whole note.

Re-proved with a throwaway probe (deleted): `steadyTracker(note = 40)`, then 120 answers 33 ms apart, 7 of every 8 at
note 28 and 1 at note 40 (all at the same level) — the reading stayed 40 for all 120 steps and 28 never appeared.

**The reviewer's suggested fix does not hold.** "Take the octave once it holds `TARGET_HOLD_COUNT` answers in a row" was
probed against the recordings in `tuner/implementation/src/desktopTest/resources/tuner/`, which are what the guard was
added for: the detector's raw answers on `B3 macbook unplugged electric.wav` include a run of **5** octave-down answers
in a row as the string dies (at about 1.2 s and again at 1.65 s), so a run threshold of 3 or 5 reads that B3 as B2 and
fails `RecordedStringsTest`; a threshold of 8 passes the recordings but no longer catches the 7-in-8 case above. A
majority over a sliding window fails the same way (B3's dying stretch is 7 of 8 octave answers within 500 ms). The count
of octave answers does not tell a dying string from a wrong first octave.

What does tell them apart is the level. In every recording the octave answers come when the string has died well below
its strike (B3 at about 0.13 of its peak level, G3 at about 0.2, D3 at about 0.35, E2 "again" at about 0.4), which is
exactly what the comment says the guard is for ("as it dies into the noise"); a wrong first octave is read while the
string still rings at its strike's level.

## Fix

Only ignore an octave answer while the sound is well below the level of the note's strike; one heard near it goes
through the median and the candidate count like any other answer, so a consistent octave takes over in
`TARGET_HOLD_COUNT` answers and a lone one is outvoted by the median of five.

In `PitchTracker`:

1. Add `private var strikeLevel = 0f`, the loudest level heard since the last onset. Set it to `0f` where an onset is
   detected in `step` (next to `recent.clear(); isStruck = true`) and in `release()`.
2. Pass the estimate's level into `hear` (`hear(frequency, estimate.level, timeMillis, elapsed, config)`), and at the
   top of `hear` raise `strikeLevel` to it (`if (level > strikeLevel) strikeLevel = level`).
3. Gate the guard on the level:

   ```kotlin
   if (current != null && !isStruck && level < strikeLevel * DYING_RATIO && isOctaveOf(frequency, current, config)) {
       if (timeMillis - lastHeardTime <= HOLD_MILLIS) return
       release()
   }
   ```

   with, in the companion, a KDoc'd `private const val DYING_RATIO = 0.5f` ("Half the strike's level, 6 dB under it:
   below it an answer an octave from the target is the string dying into the noise, above it a reading of its own").
4. Rewrite the comment above the guard to say why the level decides: a ringing string's octave misreadings come as it
   dies away; near the strike's level an octave is what the string is playing (a first reading of a low string can land
   an octave up, the high-pass having taken most of its fundamental), so it is read like any other note.

The probe ran `RecordedStringsTest`'s loop over every recording with this change: all nine stay on their note for any
`DYING_RATIO` from 0.35 to 0.95 (D3 fails below 0.35, which is the margin on that side), and with the guard removed
entirely B3, D3 and G3 fail — so the recordings still prove the guard. 0.5 is a round 6 dB inside that range; if
`RecordedStringsTest` fails at execution time with recordings added since, pick the value from the passing range rather
than dropping the gate.

Update `tuner/implementation/CLAUDE.md`'s `PitchTracker` bullet: "an answer a whole octave from the target ignored
until a new onset … or until the held reading is let go" becomes "an answer a whole octave from the target ignored while
the sound is more than 6 dB under its strike (a dying string is read at twice its period now and then), until a new
onset or until the held reading is let go; near the strike's level an octave is a reading like any other".

## Tests

In `PitchTrackerTest`:

- New: `a first reading in the wrong octave gives way to the one the string holds` — `steadyTracker(note = 40)`, then 24
  answers at the default level, `if (index % 8 == 7) 40 else 28`; assert the last reading is 28 (the probe saw 28 from
  the fifth answer on).
- Change `a ringing string read an octave away now and then keeps its note`: its octave answers are at `level = 0.15f`
  after a steady 0.2, which is within 6 dB of the strike and so, under the fix, a reading of its own. Make them what a
  dying string is, `level = 0.05f` (12 dB under), and say so in a comment. The probe confirmed it keeps 45 for all twelve
  answers at that level.
- `an octave struck anew is taken` and `an octave too soft to be heard as struck is taken once the held note is let go`
  pass unchanged (the second now reads the octave from the fifth answer rather than the eighteenth; its assertion is on the last
  of forty).
- `RecordedStringsTest` (desktopTest) must stay green: it is the guard's real test.

Run `./gradlew :tuner:implementation:desktopTest`.

## Manual check

On a phone or a laptop, chromatic mode: pluck a bass's open E (and a five-string bass's low B0, if one is at hand) several
times, letting each ring out. The reading should settle on E1 (not stay on E2) within a few
tenths of a second of each pluck, and should not drop to an octave lower as the string dies away. Repeat with a guitar's
six open strings to confirm none of them jumps an octave as it fades.
