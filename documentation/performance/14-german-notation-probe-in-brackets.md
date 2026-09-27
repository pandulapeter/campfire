<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# 14 — Look for German chord names only where chord names can be

| | |
|---|---|
| Lane | B |
| Impact | medium for large libraries (the library scan at startup and every import), low per editor keystroke |
| Confidence | high (measured; result equivalence checked against 200,000 generated songs) |
| Platforms | all |
| Files | `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProParser.kt`, `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProParserTest.kt` |
| Depends on / conflicts with | — (12 changes `ChordProSummaryCache`, which calls `summarize`, but not this file) |
| Commit message | `Look for German chord names only inside chord brackets when summarizing a song.` |

## Problem
`ChordProParser.scan` backs both `summarize` and `parseMetadata`. Its German-notation probe is:
```kotlin
// ChordProParser.kt:188-196
val isLookingForChords = shouldDetectChords && !hasChords
val isLookingForNotation = !isGermanNotated && (GERMAN_LETTER in rawLine || GERMAN_LETTER.lowercaseChar() in rawLine)
if (!isLookingForChords && !isLookingForNotation) return@forEach
val names = writtenChordNames(rawLine, trimmedLine, environment)
...
if (isLookingForNotation) isGermanNotated = names.any(ChordProNotation::isGermanName)
```
- The probe looks for an `h` or `H` **anywhere** in the raw line. Nearly every English lyric line has one ("the", "that", "house").
- For a song in English notation `isGermanNotated` never becomes true. So `writtenChordNames` runs `parseLyrics`, with its `StringBuilder`, bracket walk and chord objects, on almost every body line. It keeps doing so after the chords have been found, which defeats the early exit the KDoc describes (`:153-156`).
- It also runs for `parseMetadata` (`shouldDetectChords = false`), whose KDoc promises it *"only scans directive lines, so that it is cheap"* (`:143`).

Measured on desktop JVM (Apple silicon, warmed up), with the demo "House of the Rising Sun" repeated to 357 lines / 10,961 chars:

| call | time |
|---|---|
| `summarize` | 77.6 µs |
| the same text with every h/H replaced | 34.4 µs |
| `parseMetadata` | 76.9 µs |

Callers:
- the library scan at startup, once per file (`SongLocalSourceImpl.kt:138`);
- an import's file naming (`:92`);
- the editor's summary on every non-reused keystroke (`ChordProSummaryCache`).

For a 1,000-song library that is about 40 ms of avoidable work on desktop JVM. On a low-end phone it is an estimated 0.4–0.8 s. That work is off the UI thread, but it keeps the launch screen up for longer.

## Fix
Where the names come from depends on the environment, exactly as `writtenChordNames` (`:212-217`) decides:
- in a tab or a grid they are bare words on the line;
- in a delegated environment there are none;
- everywhere else they are the contents of closed bracket pairs, trimmed, with annotations left out.

`ChordProNotation.isGermanName` (`ChordProNotation.kt:54-56`) is only true for a name that holds an `H` or an `h`: a note that upper-cases to `H`, or a lowercase `h` minor root. So the probe only has to look where the names come from:
```kotlin
val isLookingForNotation = !isGermanNotated && mayHoldGermanName(rawLine, environment)

/** Whether a chord name on [rawLine] could be German-notated, by where [writtenChordNames] reads its names from. */
private fun mayHoldGermanName(rawLine: String, environment: String?) = when (environment) {
    TAB, GRID -> GERMAN_LETTER in rawLine || GERMAN_LETTER.lowercaseChar() in rawLine
    in ChordProSyntax.delegateEnvironments -> false
    else -> ChordProSyntax.hasBrackets(rawLine) &&
        ChordProSyntax.brackets(rawLine).any { bracket -> bracket.content.any { it == GERMAN_LETTER || it == GERMAN_LETTER.lowercaseChar() } }
}
```
Only the condition changes. The early return, the `hasChords` logic and the key normalization after the loop stay as they are.

A line with an `H` in a bracket whose content is an annotation (`[*Hush]`) is still probed. The probe then finds no German name, exactly as today, so this costs no correctness.

**What must NOT change:**
- `summarize(text)` and `parseMetadata(text)` return exactly what they return today. The German flag decides nothing but the spelling of the summary's key, and neither `hasChords` nor the metadata is affected.
- The KDoc of `parseMetadata` becomes true rather than needing a change.

## Verification
- `./gradlew :chordpro:desktopTest`. Add these to `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProParserTest.kt`:
  1. **`summarize and parse agree about the key of generated songs`.** This is the equivalence test.
     - `parse` detects German notation through its own model walk (`ChordProNotation.normalized` / `isGermanNotated`), independently of `scan`'s probe. So a narrowed probe that missed a case would make the two disagree.
     - Build the songs from a fixed-seed `Random` joining 1–5 pieces, 20,000 songs or so, drawn from:
       ```
       {key: H}, {key: B}, {key: Bb}, {key: h}, {key: Hm}
       The house [H]here
       [h]oh [B]yes
       the [Am]hill
       [C/h]low, [C/H]low
       {start_of_grid}\n| H . B . |\n{end_of_grid}
       {start_of_grid}\nH | C . |\n{end_of_grid}
       {start_of_tab}\n H   B\ne|--1h2--|\n{end_of_tab}
       {start_of_abc}\n[H] abc\n{end_of_abc}
       {comment: [H] here}
       # [H] comment
       [*Hush] quiet
       [ H ]spaced
       [Hmaj7]x
       [Bb]x
       [Hello] word
       {start_of_verse}\n[H]verse\n{end_of_verse}
       plain the line
       ```
     - For each song, `assertEquals(ChordProParser.parse(text).metadata.key, ChordProParser.summarize(text).metadata.key, text)` and `assertEquals(ChordProParser.parse(text).metadata.key, ChordProParser.parseMetadata(text).key, text)`.
     - Against today's code, this exact corpus (200,000 songs) gives zero disagreements for both `summarize` and `parseMetadata`. That was checked with a throwaway harness against `chordpro/build/libs/chordpro-desktop.jar`. So the test pins current behavior and fails if the new probe misses anything.
  2. **Fixed expectations**, written as plain asserts so the intent reads without the generator:
     - `summarize("{key: B}\nthe house [Am]x").metadata.key == "B"` (an `h` in the lyrics does not make it German);
     - `summarize("{key: B}\n[Hm7]x").metadata.key == "Bb"`;
     - `summarize("{key: B}\n{start_of_grid}\n| H . |\n{end_of_grid}").metadata.key == "Bb"`;
     - `summarize("{key: B}\n{start_of_abc}\n[H] abc\n{end_of_abc}").metadata.key == "B"`.

     All four were confirmed against the current jar.
- Measurement: re-run the scratchpad benchmark (`summarize`, `parseMetadata` on the 357-line song). Both should fall from ~77 µs to roughly 35–40 µs.
- No `CLAUDE.md` change is needed. `chordpro/CLAUDE.md` describes what the summary answers, not how the probe finds it.
