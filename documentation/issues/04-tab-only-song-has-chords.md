# 04 — A song written only as tablature is treated as a song without chords

**Severity:** wrong behaviour (all platforms) · **Area:** `:chordpro` (`ChordProParser.scan`, `ChordProSong.hasChords`);
effects in `:domain` and `:presentation` need no code change

**Read, not run.** Found by reading the chord detection at HEAD (2065e47f). The unit tests reproduce the cause; the
effects are reached through `Song.hasChords`.

## What the user sees

A riff or a solo written as a `{start_of_tab}` environment with no `[chords]` anywhere else in the file:

1. The song list says **"Lyrics only"** under it (`presentation/.../components/ListItems.kt:150-153`).
2. With "Show songs without chords" off, the song **disappears from the list**
   (`domain/implementation/.../GetScreenDataUseCaseImpl.kt:163-164`, `filter { it.hasChords }`).
3. The details screen offers **no transposition control** (`SongDisplayControls.kt:94`,
   `SongDetailsScreen.kt:271`, both on `song?.hasChords == true`) — although `ChordProTabTransposer` moves the frets of
   exactly such a song, and the editor's Transpose action is enabled for it only if the file declares a `{key}`
   (`SongEditorScreen.kt:394`).
4. Yet lyrics-only mode treats the tab as chords and hides it (`SongLyrics.kt:1317`, `needsChords()`), so the two
   halves of the app disagree about what the song is.

## Cause

The library scan never looks at a tab line — `chordpro/.../ChordProParser.kt:93-94`:

```kotlin
            val names = writtenChordNames(rawLine, trimmedLine, environment)
            if (isLookingForChords && environment != TAB) hasChords = names.isNotEmpty()
```

and the model says the same — `model/ChordProSong.kt:21-31`:

```kotlin
    val hasChords: Boolean
        get() = blocks.any { block ->
            block is ChordProBlock.Section && block.lines.any { line ->
                when (line) {
                    is ChordProLine.Lyrics -> line.chords.any { !it.isAnnotation }
                    is ChordProLine.Grid -> line.tokens.any { it is GridToken.Chord }
                    is ChordProLine.Tab -> false
                    ChordProLine.Blank -> false
                }
            }
        }
```

The KDoc of `scan` (`:68-72`) states the exclusion ("outside a tab environment is what counts as one") without a
reason, and the existing test pins it (`ChordProParserTest.kt:458-468`,
`summarize reports chords in lyrics and in grids but not in tabs`).

## The change

Invoke the **`code-style`** skill before the first edit. A tab environment counts when it holds something the
transposition moves: a staff line (`ChordProSyntax.isStaffLine`, which is what `ChordProTabTransposer` moves the frets
of) or a row of chord names above it (`ChordProTabTransposer.chordNames`, which `writtenChordNames` already returns for
a tab line). A `{start_of_tab}` used for plain preformatted text (`Tuning: DADGAD`, a note) does not count, as today.

`ChordProParser.kt:94`:

```kotlin
            if (isLookingForChords) {
                // A tab is chords to the transposition as long as it has a staff to move or chord names over it.
                hasChords = names.isNotEmpty() || (environment == TAB && ChordProSyntax.isStaffLine(rawLine))
            }
```

(`names` is already the tab line's chord names there, `writtenChordNames` `:110-115`.) KDoc of `scan` (`:68-72`):
"A real chord (not an `[*annotation]`, not an empty `[]`) is what counts as one, and so is a line of tablature or a row
of chord names in a tab environment, since the transposition moves those too; …".

`model/ChordProSong.kt:27`:

```kotlin
                    is ChordProLine.Tab -> ChordProSyntax.isStaffLine(line.text) || ChordProTabTransposer.chordNames(listOf(line.text)).isNotEmpty()
```

and its KDoc: "True if any lyrics or grid line contains at least one real chord (annotations don't count), or a tab
holds a staff or a row of chord names." Both helpers are `internal` in the same module.

Nothing downstream changes: `Song.hasChords` comes from `summary.hasChords` (`SongMappers.kt:36`) at every library
scan, so an existing library picks it up on the next launch; the list note, the filter, the transposition control and
the editor's Transpose button all read it.

## Tests

`chordpro/src/commonTest`, `./gradlew :chordpro:desktopTest`, `ChordProParserTest`:

- Replace `summarize reports chords in lyrics and in grids but not in tabs` (`:458-468`) with
  `summarize reports chords in lyrics, grids and tabs, but not in preformatted text`:
  - `{start_of_tab}\ne|--0--3--|\n{end_of_tab}` → `summarize(…).hasChords` and `parse(…).hasChords` both true;
  - `{start_of_tab}\nAm   G\n{end_of_tab}` → both true;
  - `{start_of_tab}\ne|--[Am]--|\n{end_of_tab}` (the old fixture, a staff line) → both true;
  - `{start_of_tab}\nTuning: DADGAD\nlet ring\n{end_of_tab}` → both false;
  - the lyrics and grid assertions as they are.
- `summarize and parse agree about chords` — for each fixture above, `summarize(x).hasChords == parse(x).hasChords`.

## Verification

1. Confirm first: a song with a `{title}` and only a `{start_of_tab}` riff. **Before:** "Lyrics only" in the list, no
   transposition control on the details screen.
2. After (restart the app, the scan runs at launch): the list shows the key or nothing, the control is there, +2 moves
   the frets; "Show songs without chords" off keeps the song.
3. A song whose tab holds only `Tuning: DADGAD` still says "Lyrics only".

## Docs

- `chordpro/CLAUDE.md`, the `ChordProParser` bullet: "`summarize` (the directives plus "does it have chords", from one
  walk …)" — add "; a tab with a staff or a row of chord names in it counts, since the transposition moves those".
- `presentation/CLAUDE.md:54` ("A song with no chords says "Lyrics only" in that place instead") stays true.

## Files touched

- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProParser.kt`
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/model/ChordProSong.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProParserTest.kt`
- `chordpro/CLAUDE.md`

## Depends on

Nothing. Touches `scan` next to plans 02 (transposition), 06 (key) and 08 (delegate environments) — different lines;
land after 06 in the lane order to keep rebases trivial.
