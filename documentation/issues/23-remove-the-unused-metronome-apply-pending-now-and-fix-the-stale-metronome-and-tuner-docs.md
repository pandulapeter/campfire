# Remove the unused `Metronome.applyPendingNow` end to end, and correct the stale metronome and tuner documentation

**Kind:** cleanup, docs  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `metronome/api/src/commonMain/kotlin/com/pandulapeter/campfire/metronome/api/Metronome.kt`,
`metronome/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/metronome/implementation/MetronomeEngine.kt`,
`metronome/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/metronome/implementation/ClickStream.kt`,
`metronome/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/metronome/implementation/MetronomeSequencer.kt`,
`metronome/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/metronome/implementation/MetronomeEngineTest.kt`,
`metronome/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/metronome/implementation/MetronomeSequencerTest.kt`,
`tools/screenshots/src/main/kotlin/com/pandulapeter/campfire/screenshots/Fakes.kt`,
`tuner/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/tuner/implementation/SampleRing.kt`,
`metronome/api/CLAUDE.md`,
`metronome/implementation/CLAUDE.md`

**Decision for the user:** this removes a public function from `:metronome:api`, an explicit-API module. Recommended:
option (a), remove it. If the user wants to keep the ability for a future "start the next song now" control, take
option (b) instead.

## Problem

1. **`applyPendingNow` has no caller.** d0fa48277 ("Improve general UI polish") deleted its only app caller,
   `MetronomeController.applyPendingMetronomeChange() = metronome.applyPendingNow()`, with the control that used it. At
   d650f3cb0 a search of every `.kt`, `.kts`, `.swift` and `.md` file finds only:
   - the contract, `Metronome.kt` L61–65:

     ```kotlin
     /**
      * Brings a change waiting for the next bar forward to the next beat, which becomes beat one of a new bar: the song
      * moved to starts now rather than after the rest of the bar. Does nothing when no change waits.
      */
     public fun applyPendingNow()
     ```
   - its implementation chain: `MetronomeEngine.applyPendingNow` (L91–93), `ClickStream.applyPendingBarChangeNow`
     (L42–44) and its `Command.ApplyPendingBarChangeNow` (L68, L87), `MetronomeSequencer.applyPendingBarChangeNow`
     (L57–60) with its `isBarChangeDue` flag (L41, L98, L108);
   - tests: `MetronomeEngineTest` `a change waiting for the next bar applied now is heard from the next beat` (L156–174),
     `MetronomeSequencerTest` `a change waiting for the next bar applied now makes the next beat beat one` (L124–135) and
     `applying now with nothing waiting changes nothing` (L136–146);
   - the screenshot tool's fake, `tools/screenshots/.../Fakes.kt` L110: `override fun applyPendingNow() = Unit`;
   - `metronome/implementation/CLAUDE.md` L23–24: "…a later change waits with it rather than bringing it forward, and
     `applyPendingNow` brings it forward to the next beat."

   Nothing in `presentation`, the app modules or the iOS Swift code calls it.
2. **`metronome/api/CLAUDE.md` L16–18 describes an older contract**:

   ```
   - `Metronome` — the one stateful interface: `playback` (`Stopped(reason?)` or `Playing(pattern, audioIssue?)`),
     `beats` (…), `start`, `update` (timing
     changes from the next beat, `restartBar` making it beat one; the sound, volume and accents from the next click),
   ```

   `update` takes `fromNextBar`, not `restartBar`, and its timing change then waits for the bar being played to end
   (`Metronome.kt` L52–59); `Playing` also has `pendingPattern` (`MetronomePlayback.kt` L26–30).
3. **`SampleRing.CAPACITY`'s comment is stale** (`SampleRing.kt` L61–62):

   ```kotlin
   /** Four of the largest window the detector reads, 8192 frames at 96 kHz. */
   const val CAPACITY = 32_768
   ```

   Since d9dbb82ba the detector reads `inputSize` frames, a window and a half (`PitchDetector.inputSizeFor`), which is
   12 288 frames at 96 kHz; the ring holds about two and two-thirds of it. That is still several windows, so the
   capacity is fine; only the comment is wrong.

## Fix

**Option (a), recommended — remove `applyPendingNow` end to end:**

- `Metronome.kt`: delete `applyPendingNow` and its KDoc.
- `MetronomeEngine.kt`: delete the `applyPendingNow` override. (If plan 22 has landed, it no longer has a
  `pendingBarChangeId` check to adjust.)
- `ClickStream.kt`: delete `applyPendingBarChangeNow`, `Command.ApplyPendingBarChangeNow` and its branch in `schedule`.
- `MetronomeSequencer.kt`: delete `applyPendingBarChangeNow` and `isBarChangeDue`; `applyPendingTiming`'s early return
  becomes `if (barChangeId != null && beatIndex != 0) return`, and its `isBarChangeDue = false` line goes.
- Delete the three tests named above.
- `Fakes.kt`: delete `override fun applyPendingNow() = Unit`.
- `metronome/implementation/CLAUDE.md` L23–24: end the sentence at "…a later change waits with it rather than bringing
  it forward." and drop "and `applyPendingNow` brings it forward to the next beat".

**Option (b) — keep it:** add to its KDoc that the app does not call it today (kept for a control that starts the song
moved to at once), and leave the code and tests. Not recommended: an unused path through the sequencer's timing is one
more state every change to it has to keep right.

**Either way:**

- `metronome/api/CLAUDE.md`: rewrite the `Metronome` bullet's `playback` and `update` parts to the current contract, e.g.
  "`playback` (`Stopped(reason?)` or `Playing(pattern, audioIssue?, pendingPattern?)`, the pattern heard and the one
  waiting for the bar to end) … `update` (the timing from the next beat, or with `fromNextBar` once the bar being played
  has ended, as beat one of a new count of bars; the sound, volume and accents from the next click)". With option (b),
  also list `applyPendingNow` there.
- `SampleRing.kt`: the comment becomes "Over two and a half of the largest input the detector reads, 12 288 frames at
  96 kHz (`PitchDetector.inputSizeFor`)."

Do not touch the root `CLAUDE.md` (its Metronome section names no API function).

## Tests

Nothing new: option (a) deletes the three tests of the removed path, and the remaining `MetronomeSequencerTest` and
`MetronomeEngineTest` cases (the next-bar ones above all) must pass unchanged. Run
`./gradlew :metronome:api:desktopTest :metronome:implementation:desktopTest :tuner:implementation:desktopTest :tools:screenshots:compileKotlin`
(the screenshot tool implements `Metronome`, so it must still compile; `tests.yml` compiles it too).

## Manual check

None beyond a smoke test: play the metronome on a song and page to the next one — it should still change at the next
barline, from beat one.
