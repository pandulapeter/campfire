# Correct three chord docs: who plays the chord over its strings, which tunings read the mandolin's table, and how the search counts the banjo's drone

**Kind:** docs  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/chords/ChordVoicings.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/model/ChordTuning.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/chords/ChordVoicingSearch.kt` (comment only),
`chordpro/CLAUDE.md`

## Problem

Three statements do not match the code at 72d48deb7.

1. `ChordVoicings.kt:30`, the object's KDoc: the bass rule is skipped "(not on the re-entrant ukulele, whose lowest
   string is in the middle)". It is skipped on every instrument that `playsChordOver`, which is the ukulele, the mandolin,
   and the banjo (`model/ChordVoicing.kt`: `UKULELE(…, playsChordOver = true)`, `MANDOLIN(…, playsChordOver = true)`,
   `BANJO(…, playsChordOver = true, droneString = 0)`); `chordpro/CLAUDE.md` already says so.
2. `ChordTuning.kt:22-23`, the enum's KDoc lists the tunings that read another's table: "(half and full step down, drop
   C♯ to drop B, open E and A, the ukulele's low G, D, and baritone tunings, and the mandolin's AEAE)". `tableTuning` also
   maps `MANDOLIN_HALF_STEP_DOWN -> MANDOLIN`, and "half and full step down" reads as the guitar's only.
3. `chordpro/CLAUDE.md`, the banjo paragraph: "The search offers it nothing else and does not count it among the strings
   that must sound or the open strings a hand high up the neck rules out". The second half holds (`hasOpenString` skips
   the drone), the first does not. `ChordVoicingSearch.search` (~:188) sets
   ```kotlin
   tuning.instrument.playsChordOver -> strings.size - if (drone == null) 0 else 1
   ```
   and compares it with `sounding.size`, which counts an open drone. So a banjo shape must sound four strings, of which
   the open drone may be one: a probe over 23 chord qualities on 12 roots in all five banjo tunings found five search
   answers with the first string muted and the drone open, for example banjo C tuning's C5 `0 0 0 1 x` (its first
   answer, a good shape) and double C's Gdim `0 1 3 1 x`. The subset filter does not remove them, since no five-string
   version of those shapes exists in the window.

## Fix

Change the words, not the code (for item 3 the code's answers are the better ones: requiring all four long strings
would make C tuning's C5 start at `0 7 5 8 5`).

1. `ChordVoicings.kt`, KDoc: replace "(not on the re-entrant ukulele, whose lowest string is in the middle)" with "(not
   on an instrument that plays the chord over its strings, [ChordInstrument.playsChordOver]: the re-entrant ukulele, the
   mandolin, and the banjo)".
2. `ChordTuning.kt`, KDoc: replace the parenthesis with "(the guitar's half and full step down, drop C♯ to drop B, open
   E and A, the ukulele's low G, D, and baritone tunings, and the mandolin's half step down and AEAE)".
3. `chordpro/CLAUDE.md`, banjo paragraph: replace "The search offers it nothing else and does not count it among the
   strings that must sound or the open strings a hand high up the neck rules out;" with "The search offers it nothing
   else; open, it is one of the four strings that must sound, so a shape may leave the first string muted where the drone
   sounds the chord (banjo C's C5 is `0 0 0 1 x`), and it is not among the open strings a hand high up the neck rules
   out;". Optionally add the same reason as a comment above `minimumStrings` in `ChordVoicingSearch.search`: "// On the
   banjo every string but one sounds, and an open drone counts as one of them."

## Tests

None: comments and documentation only. `./gradlew :chordpro:desktopTest` and `spotlessCheck` must still pass.

## Manual check

None.
