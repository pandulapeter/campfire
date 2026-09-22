# 37 · Chords and tablature are invisible to screen readers

**Severity:** minor, accessibility (all platforms with a screen reader bridge: Android and iOS for certain. Certain
for a TalkBack / VoiceOver user: no chord is ever announced, and a tab staff is skipped as if it were not there) ·
**Area:** `:presentation` (`ui/screens/songDetails/SongLyrics.kt`)

## Symptom
With TalkBack or VoiceOver on, a song's lyrics are read (with odd pauses where the non-breaking-space padding under a
wide chord is), but no chord is announced; a line that is only chords (an intro) reads as nothing. A tablature block
is skipped entirely. The song sounds the same with chords shown as in lyrics-only mode; the only way to the chords is
the raw ChordPro in the editor.

## Cause
Chords are painted with `drawText` in the `drawBehind` of the lyric `Text` (`SongLyrics.kt:1303-1335`,
`SongLineWithChords`), which is a deliberate layout choice (documented there), and nothing gives the `Text` a
description that carries them: its semantics are the padded lyrics. `SongTabBlock` (`:537-565`) is an empty `Layout`
drawn in `drawBehind`, with no semantics at all, so it produces no node. (Grid lines are real `Text`s and are read.)

## Fix
Small and local: describe each chorded line with its chords in place, and give a tab block its text.

1. `SongLineWithChords`: after `paddedLine`, build the spoken form once per line, and put it on the `Text`:
   ```kotlin
   // The chords are drawn rather than composed, so a screen reader would read the padded lyrics alone. It is given
   // the line the way ChordPro writes it instead, each chord in brackets where it falls.
   val description = remember(line) { line.withChordsInline() }
   Text(
       modifier = Modifier
           .fillMaxWidth()
           .semantics { contentDescription = description }
           .drawBehind { ... },
   ```
   and a private helper near `padLyricsToFitChords`:
   ```kotlin
   /** The line with each chord written into it in brackets, where it sits: "[Am]There is a [C]house". */
   private fun ChordProLine.Lyrics.withChordsInline() = buildString {
       var start = 0
       chords.sortedBy { it.position }.forEach { chord ->
           val position = chord.position.coerceIn(start, text.length)
           append(text, start, position)
           append('[').append(chord.name).append(']')
           start = position
       }
       append(text, start, text.length)
   }
   ```
   `SongLineWithChords` is only reached while chords are shown (lyrics-only mode strips them in `prepareForDisplay`),
   so nothing extra is needed for that mode. The chord names are the rendered ones, so a transposition or the chosen
   spelling is what is read.
2. `SongTabBlock`: on the `Layout`'s modifier, before `drawBehind`,
   ```kotlin
   // Drawn rather than composed (see above), so the lines are handed to a screen reader here, as they stand in the file.
   modifier = modifier
       .semantics { contentDescription = lines.joinToString(separator = "\n") }
       .drawBehind { ... },
   ```
   Tablature read aloud is of limited use, but it is no longer silently missing, and a reader can step through it.

Imports: `androidx.compose.ui.semantics.semantics`, `androidx.compose.ui.semantics.contentDescription`.

Not done: a spoken chord vocabulary ("A minor"). It would need localized names for every chord quality, and the
bracketed ChordPro form is what the editor shows the same user anyway.

## Tests
None (UI).

## Verify
1. Android with TalkBack: the lines of "House of the Rising Sun" are read with their chord names in place, however
   TalkBack voices the brackets. A chord-only line is read as its chords. The tab of a song that has one is
   read (and no longer skipped).
2. Transpose by +2 and read the same line: the new chord names.
3. Lyrics-only mode: lines read as plain lyrics, as before.
4. iOS VoiceOver: the same.
5. Sighted rendering unchanged (semantics only).
6. Compile: `:presentation:compileKotlinDesktop`, `:app:android:assembleDebug`.

## Docs
`presentation/CLAUDE.md`, in the song details paragraph where the chords being drawn in `drawBehind` is explained,
add: "Since they are drawn, each chorded line carries the ChordPro form of itself (`[Am]There is a…`) as its content
description, and a tab block its lines, so a screen reader reads the chords too."

## Touches
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongLyrics.kt`
- `presentation/CLAUDE.md`

## Depends on
Nothing. 34 and 33 also edit `SongLyrics.kt` (other functions); run them one after another.
