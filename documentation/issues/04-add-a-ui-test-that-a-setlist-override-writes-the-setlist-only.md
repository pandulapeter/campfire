# Add a UI test that a transposition set inside a setlist is written to the setlist and not to the song

**Challenged:** amended — the setlist resource exists (no new one), the JSON field and the stepper's semantics are
named, and the leak check reads the stepper's state description.

**Kind:** test  ·  **Severity:** medium  ·  **Platforms:** desktop (UI tests)
**Files:** new `tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/presentation/ui/playing/SetlistOverrideTest.kt`
(the package of `ui/playing`); no new resource

## Problem

Root `CLAUDE.md`: "Transposition, capo, and tempo are overridden per setlist or per device; only the time signature is
written". `PlayingOverrides`, `SongOverrides`, and `SetlistEntryEdits` are unit tested, but nothing checks which file the
running app writes: an override written into the `.cho`, or leaking from the setlist to the library-wide song, is
silent corruption of the user's song.

## Fix

Add a UI test with `runAppTest(setlists = listOf("campfire_tests-an_evening_by_the_fire.setlist.json"))`: that resource
already names `HOUSE_OF_THE_RISING_SUN` once, as `{"file": "...", "transposition": 0}` in its `songs` array.
1. Note the bytes of every library file (songs and setlists).
2. Open the Setlists tab (`hasText(string(Res.string.setlists)) and hasClickAction()`, as `GoldenRendersTest` does),
   the setlist, its house song; press the Transposition stepper's + once (content description
   `Res.string.song_details_transpose_up`). The write is immediate (`PlayingOverrides.changeTransposition`, no pending
   delay as the tempo has).
3. `waitForLibrary` until the setlist JSON's `songs[0].transposition` is 1 (decode with `Json.parseToJsonElement`,
   never a text search).
4. Assert every `.cho` is byte-identical and the setlist still has one entry naming the house song (decoded). Do not
   compare the setlist's other fields byte for byte: a rewrite may drop defaults the hand-written resource spells out.
5. Go back (content description `Res.string.back`), open the same song from the Songs tab, and read the stepper: the +
   button carries the value as its `SemanticsProperties.StateDescription` (`Stepper` gives both buttons the value
   label, `transpositionLabel`: `"0 · Am"` untransposed, `"+1 · …"` in the setlist). Assert it starts with `"0 "`, and
   check in step 2 that it started with `"+1 "` after the press, so the read is proven to see the override.

## Tests

This is the test.
