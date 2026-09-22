# 35 — Chords pile up at the right edge over right-to-left lyrics

**Severity:** wrong rendering (all platforms) · **Area:** `:presentation` (`screens/songDetails/SongLyrics.kt`)

**Read, not run.** This was found by reading the drawing code at HEAD; it has not been reproduced in a running
build. The "Verification" section below is how to confirm it, and confirming it is the first step of the work.

## What the user sees

A song whose lyrics are in Hebrew, Arabic, Persian or another right-to-left script, with chords over them: the first
chord of each line sits roughly where it belongs, and every chord after it is pushed further right until they stack
up against the right edge of the column, on top of each other. The lyrics themselves are laid out correctly.

Latin, Cyrillic, Greek and CJK songs are unaffected, as is a right-to-left *title* or *artist* (those are ordinary
`Text`s). It is only the chorded lyric lines, which are the one place in the app that positions text by hand.

## Cause

`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongLyrics.kt:1354-1376`
draws the chords into the space the line height leaves above the lyrics, at the horizontal position of the character
each is attached to, and keeps them from overlapping by never letting one start before the previous one ended:

```kotlin
                val gap = CHORD_GAP.toPx()
                var previousLineIndex = -1
                var previousChordEnd = 0f
                paddedLine.chords.forEachIndexed { index, chord ->
                    val chordLayout = chordLayouts[index]
                    val offset = chord.position.coerceIn(0, textLength)
                    val lineIndex = layout.getLineForOffset(offset)
                    if (lineIndex != previousLineIndex) {
                        previousLineIndex = lineIndex
                        previousChordEnd = 0f
                    }
                    val maxX = max(0f, size.width - chordLayout.size.width)
                    val x = max(layout.getHorizontalPosition(offset, usePrimaryDirection = true), previousChordEnd).coerceIn(0f, maxX)
                    drawText(
                        textLayoutResult = chordLayout,
                        color = if (chord.isAnnotation) annotationColor else chordColor,
                        topLeft = Offset(x, layout.getLineTop(lineIndex)),
                    )
                    previousChordEnd = x + chordLayout.size.width + gap
                }
```

`max(…, previousChordEnd)` assumes that x grows with the character offset. That holds only in a left-to-right
paragraph. The `Text` under it resolves its direction from its own content rather than from the app's layout
direction, so a Hebrew line is laid out right to left: the second chord's character sits **left** of the first one's,
`getHorizontalPosition` returns a smaller x, `max` throws it away and keeps `previousChordEnd` instead — and each
chord after that pushes the bound further right until `.coerceIn(0f, maxX)` clamps them all to the right edge.

The padding pass is not implicated: `padLyricsToFitChords` (`:1407-1425`) widens the piece of lyrics under each chord
by appending non-breaking spaces until it is at least as wide as the chord, which it does in *logical* order on
measured widths and is therefore direction-agnostic. Only the x that the drawing pass computes is wrong.

## The change

Invoke the **`code-style`** skill before the first edit. `commonMain` stays JVM-free — `ResolvedTextDirection` and
`kotlin.math.min` both are.

Take the paragraph direction at the offset and apply the same no-overlap rule the other way on a right-to-left line:
a chord hangs to the *left* of the character it belongs to, and the next one is kept to the left of it.

Add to the imports of `SongLyrics.kt` (it already has `kotlin.math.ceil`, `kotlin.math.max`,
`kotlin.math.roundToInt` at `:99-101` and `androidx.compose.ui.text.style.LineHeightStyle` at `:62`):

```kotlin
import androidx.compose.ui.text.style.ResolvedTextDirection
import kotlin.math.min
```

and rewrite the `drawBehind` body:

```kotlin
                val gap = CHORD_GAP.toPx()
                var previousLineIndex = -1
                // The edge the next chord of this line must not cross: where it has to start on a line that runs to
                // the right, where it has to end on one that runs to the left. One variable rather than two, since
                // there is one rule - a chord never sits on the chord before it.
                var previousChordEdge = 0f
                paddedLine.chords.forEachIndexed { index, chord ->
                    val chordLayout = chordLayouts[index]
                    val offset = chord.position.coerceIn(0, textLength)
                    val lineIndex = layout.getLineForOffset(offset)
                    // The direction of the paragraph the character is in, which the lyrics resolve from their own
                    // content rather than from the app's: a Hebrew or Arabic line is laid out from the right edge
                    // leftwards, so x falls as the offset grows and the chords have to be kept apart the other way.
                    val isRightToLeft = layout.getParagraphDirection(offset) == ResolvedTextDirection.Rtl
                    if (lineIndex != previousLineIndex) {
                        previousLineIndex = lineIndex
                        previousChordEdge = if (isRightToLeft) size.width else 0f
                    }
                    val maxX = max(0f, size.width - chordLayout.size.width)
                    val position = layout.getHorizontalPosition(offset, usePrimaryDirection = true)
                    val x = if (isRightToLeft) {
                        // The chord hangs to the left of its character, the way it hangs to the right of it in a line
                        // that runs the other way, so the position is its right edge.
                        (min(position, previousChordEdge) - chordLayout.size.width).coerceIn(0f, maxX)
                    } else {
                        max(position, previousChordEdge).coerceIn(0f, maxX)
                    }
                    drawText(
                        textLayoutResult = chordLayout,
                        color = if (chord.isAnnotation) annotationColor else chordColor,
                        topLeft = Offset(x, layout.getLineTop(lineIndex)),
                    )
                    previousChordEdge = if (isRightToLeft) x - gap else x + chordLayout.size.width + gap
                }
```

Notes for whoever implements it:

- The chords are walked in the order `ChordProLine.Lyrics.chords` holds them, which is ascending `position` (that is
  what `padLyricsToFitChords` at `:1414` already relies on when it takes the next chord's position as the end of the
  fragment). Ascending offset is descending x on a right-to-left line, which is why the rule simply reverses.
- `previousChordEdge` is reset per *visual* line, so a wrapped line keeps working. Resetting it to `size.width` on a
  right-to-left line is "no chord to the right of me yet", the mirror of `0f`.
- A mixed line — Hebrew lyrics with an English word in them — has one paragraph direction, so every chord of it takes
  the same branch. The per-offset call is still the right one to make: it costs nothing and it is the question being
  asked ("which way does the text at this character run").
- `TextLayoutResult` also offers `getBidiRunDirection(offset)`, which answers per run rather than per paragraph. Do
  **not** use it here: the rule being applied is "the next chord is further along the line than this one", and what
  "further along" means is the paragraph's direction, not the direction of the run one chord happens to land in. Two
  chords in one line answering differently would put them back on top of each other.
- `ResolvedTextDirection` is `androidx.compose.ui.text.style.ResolvedTextDirection`, with the constants `Ltr` and
  `Rtl` (checked against Compose Multiplatform 1.12.1's sources).

Extend the KDoc of `SongLineWithChords` (`:1318-1323`), which currently reads:

> Whenever a chord is wider than the piece of lyrics beneath it, that piece is padded with non-breaking spaces so
> that consecutive chords never overlap and the line wraps before the chords would run off the edge.

Add: "The chords are kept apart in the direction the line runs: a line whose content is right to left is drawn from
the right edge leftwards, and a chord then hangs to the left of the character it belongs to."

## Tests

- **No unit test is possible.** This is a `drawBehind` over a `TextLayoutResult`; `:presentation` has no test source
  set and the UI is untested by policy. There is no pure function to extract that would still be testing the bug —
  the whole of it is what `TextLayoutResult` reports for a bidirectional paragraph.
- Compile check for every target:
  `./gradlew :presentation:compileKotlinDesktop :presentation:compileKotlinWasmJs :presentation:compileDebugKotlinAndroid :presentation:compileKotlinIosSimulatorArm64`
- Run the root unit test command to confirm nothing else moved:
  `./gradlew :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest`

## Verification

Confirm the bug first — it was read, not run — then the fix, from the same song.

1. With the app closed, write a test song into the desktop library
   (macOS: `~/Library/Application Support/Campfire/library/songs/`), as `hebrew-test.cho`:

   ```
   {artist: בדיקה}
   {title: שיר}

   {start_of_verse}
   [Am]שלום [C]עולם [G]שיר [F]יפה
   [Dm]אנחנו [G]שרים [C]יחד
   {end_of_verse}
   ```

   Four chords on the first line is the point: the bug is invisible with one and obvious with four. A long Arabic
   line works as well.
2. `./gradlew :app:desktop:run`, open the song.
   - **Before the fix:** the first chord is near the right, and `C`, `G`, `F` are stacked at the right edge on top of
     one another, with the left of the line bare.
   - **After the fix:** each chord sits over the word it belongs to — `Am` above `שלום` at the right, `F` above
     `יפה` at the left — the chords spread across the line the same way the words do, and none overlaps the next.
3. Widen and narrow the window until the line wraps: the chords must follow their words onto the second visual line,
   and the first chord of each visual line must start from the right again.
4. Check that nothing changed for a left-to-right song — the demo songs are the check — at a small and a large text
   size (Ctrl / Cmd + scroll), where the padding rule is what keeps consecutive chords apart.
5. Web: `./gradlew :app:web:wasmJsBrowserDevelopmentRun`, import the same file, same two checks. The web uses a
   different text shaping stack than the desktop, so it is worth its own pass.
6. Android or iOS: one pass on a device or emulator if one is at hand, with the file imported through "open with".
   Not a blocker — the code is `commonMain` — but the three platforms shape bidirectional text with three different
   engines.

## Docs

- `SongLyrics.kt`'s own KDoc on `SongLineWithChords` becomes untrue as quoted above ("consecutive chords never
  overlap"); the clause to add is given in **The change**.
- `presentation/CLAUDE.md`, line 73, the `screens/songDetails/SongLyrics.kt` bullet, says of the chorded lines only
  that the measurements are cached and that each line carries its ChordPro form as a content description. Add one
  sentence after the sentence about `SongTextMeasurements`: "A chorded line is drawn in the direction its own content
  gives it (`TextLayoutResult.getParagraphDirection`): the chords of a Hebrew or Arabic line hang to the left of the
  characters they name and are kept apart leftwards, since x falls as the offset grows there."
- Nothing in `documentation/features.md` or the root `CLAUDE.md` claims anything about text direction; no change
  there.

## Files touched

- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongLyrics.kt`
- `presentation/CLAUDE.md`

## Depends on

Nothing. It touches no code any other plan in this batch touches.
