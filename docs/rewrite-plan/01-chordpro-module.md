# Step 01: the `:chordpro` module

**Goal:** a dependency-free Kotlin Multiplatform module that parses ChordPro text into a model, serializes the model
back, transposes chords, and extracts song metadata quickly. Unit-tested. Nothing else in the project uses it yet.

**Depends on:** nothing. Can run in parallel with steps 02 and 03.

## 1. Create the module

- Add `":chordpro"` to `settings.gradle.kts` (`include(...)`, alphabetically after `":app:web"`).
- Create `chordpro/build.gradle.kts`:
  ```kotlin
  plugins {
      id("campfire-library")
  }

  kotlin {
      sourceSets {
          commonTest.dependencies {
              implementation(kotlin("test"))
          }
      }
  }
  ```
- Sources go to `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/`, tests to
  `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/`.
- Everything the other modules need is `public`; helpers are `internal`. No Android, JVM or Compose imports anywhere.
- Update the "No tests exist in this repo" sentence in `CLAUDE.md` to say tests exist in `:chordpro` (and, after step
  03, in `:data:source:local:implementation`) and run with `./gradlew :chordpro:desktopTest`.

## 2. The model (`model/` package)

```kotlin
data class ChordProSong(
    val metadata: ChordProMetadata,
    val blocks: List<ChordProBlock>
) {
    /** True if any lyrics or grid line contains at least one real chord (annotations don't count). */
    val hasChords: Boolean
}

data class ChordProMetadata(
    val title: String? = null,          // {title} / {t}
    val subtitle: String? = null,       // {subtitle} / {st}
    val artist: String? = null,         // {artist}
    val composer: String? = null,
    val lyricist: String? = null,
    val album: String? = null,
    val year: String? = null,
    val key: String? = null,            // {key}, kept as written, e.g. "Am" or "Bb"
    val capo: Int? = null,
    val tempo: String? = null,          // {tempo}
    val time: String? = null,           // {time}, e.g. "3/4"
    val duration: String? = null,
    val transpose: Int = 0,             // {transpose: N}, applied by the renderer on top of the user's transposition
    val custom: Map<String, List<String>> = emptyMap() // {meta: name value} and unknown x_* directives, in order
)

sealed interface ChordProBlock {

    /** An environment or an implicit paragraph. */
    data class Section(
        val type: SectionType,
        val label: String?,             // "Verse 1" from {start_of_verse: Verse 1} or {sov: label="Verse 1"}
        val lines: List<ChordProLine>
    ) : ChordProBlock

    /** {chorus} / {chorus: label}: repeat the most recent chorus. The renderer decides how to show it. */
    data class ChorusRecall(val label: String?) : ChordProBlock

    data class Comment(val text: String, val style: CommentStyle) : ChordProBlock

    /** {column_break} / {new_page} and friends: a hint that the layout may break here. */
    data object Break : ChordProBlock
}

sealed interface SectionType {
    data object Verse : SectionType
    data object Chorus : SectionType
    data object Bridge : SectionType
    data object Tab : SectionType
    data object Grid : SectionType
    /** {start_of_<name>} for any other name, e.g. "intro", "solo", "outro", "pre-chorus". */
    data class Custom(val name: String) : SectionType
    /** Lines outside any environment, grouped by blank lines. Rendered like a verse without a label. */
    data object Paragraph : SectionType
}

enum class CommentStyle { PLAIN, ITALIC, BOX }

sealed interface ChordProLine {

    /** A lyrics line: text with chords anchored at character offsets. */
    data class Lyrics(val text: String, val chords: List<Chord>) : ChordProLine {
        data class Chord(
            val position: Int,           // offset into text where the chord sits
            val name: String,            // "Am7", "N.C.", ...
            val isAnnotation: Boolean    // [*text] annotations: shown like a chord, never transposed
        )
    }

    /** One line inside {start_of_tab}: monospaced, never transposed, never reflowed. */
    data class Tab(val text: String) : ChordProLine

    /** One line inside {start_of_grid}: tokens separated by whitespace. */
    data class Grid(val tokens: List<GridToken>) : ChordProLine

    /** An empty line inside an environment. */
    data object Blank : ChordProLine
}

sealed interface GridToken {
    data class Bar(val text: String) : GridToken        // "|", "||", "|:", ":|", "|."
    data class Chord(val name: String) : GridToken
    data object Beat : GridToken                        // "."
    data class Repeat(val text: String) : GridToken     // "%" (repeat previous cell), "%%"
    data class Text(val text: String) : GridToken       // anything else, e.g. a comment after the last bar
}
```

## 3. The parser (`ChordProParser`)

`object ChordProParser { fun parse(text: String): ChordProSong; fun parseMetadata(text: String): ChordProMetadata }`

`parseMetadata` only scans directive lines; it must be cheap because the song list calls it for every file at
startup. Add a third helper, `fun hasChords(text: String): Boolean`, that returns true on the first `[...]` run on a
lyrics line (outside tab environments) that is not an `[*annotation]` and not empty; step 05 uses it for the list.

Parsing rules, line by line (`\r\n` and `\n` both accepted; a trailing newline does not create a line):

1. A line whose first non-blank character is `#` is a source comment: ignored entirely.
2. A directive is a line whose trimmed form is `{name}` or `{name: value}` or `{name:value}` (regex
   `^\{\s*([\w-]+)\s*(?::\s*(.*?))?\s*\}$`). Names are case-insensitive. A name with a selector suffix such as
   `title-guitar` is ignored completely (conditional directives are out of scope). Attribute syntax in the value
   (`label="Verse 1"`, `key="A"`) is recognised: if the value contains `label="…"` use that as the label, otherwise the
   whole value is the label.
3. Directive table (long name / short names → action):
   - `title` / `t`, `subtitle` / `st`, `artist`, `composer`, `lyricist`, `album`, `year`, `key`, `tempo`, `time`,
     `duration` → metadata (last one wins). `capo` → `Int` or ignored if not numeric. `transpose` → `Int` (also
     accepts a leading `+`). `meta` → value split at first whitespace into name and value, appended to `custom`.
   - `start_of_chorus` / `soc`, `start_of_verse` / `sov`, `start_of_bridge` / `sob`, `start_of_tab` / `sot`,
     `start_of_grid` / `sog`, and any other `start_of_<name>` → open a `Section` of that type with the optional label.
     Opening an environment while one is open first closes the open one.
   - `end_of_chorus` / `eoc`, `end_of_verse` / `eov`, `end_of_bridge` / `eob`, `end_of_tab` / `eot`, `end_of_grid` /
     `eog`, any `end_of_<name>` → close the open section (ignored if none is open). Mismatched names still close.
   - `chorus` → `ChorusRecall(label)`; also closes an open paragraph.
   - `comment` / `c` → `Comment(PLAIN)`, `comment_italic` / `ci` → `ITALIC`, `comment_box` / `cb` → `BOX`. Exception:
     see the legacy heading rule below.
   - `new_page` / `np`, `new_physical_page` / `npp`, `column_break` / `colb` → `Break`.
   - `new_song` / `ns` → the parser treats the rest as the same song; splitting is done by `ChordProSplitter` (below).
   - `define`, `chord`, `image`, `columns` / `col`, `highlight`, `pagetype`, `titles`, every `*font`, `*size`,
     `*colour` / `*color`, and any unknown directive → ignored. Unknown `x_*` directives → appended to `custom`.
4. Any other line is content:
   - Inside a `Tab` section: `ChordProLine.Tab(rawLine)` verbatim (keep leading spaces, do not trim).
   - Inside a `Grid` section: split the trimmed line on whitespace into `GridToken`s: `|`, `||`, `|:`, `:|`, `|.`
     → `Bar`; `.` → `Beat`; `%`, `%%` → `Repeat`; anything after the last bar token on the line → `Text`; every other
     token → `Chord`.
   - Elsewhere: a lyrics line. Chords are `[...]` runs: `[*text]` → annotation, empty `[]` → dropped, otherwise a
     chord; `position` is the length of the lyrics text accumulated before the bracket (the current
     `parseLyrics` in `presentation/.../SongLyrics.kt` does exactly this; copy the logic). A line that trims to empty
     is `Blank` inside an environment; outside an environment it ends the current implicit `Paragraph`.
   - Outside an environment, consecutive content lines form a `Section(Paragraph, label = null, …)`.
5. **Legacy heading rule** (Campfire 3 dialect): a `comment` directive that appears while no environment is open and
   whose text starts with one of `Intro`, `Verse`, `Pre-Chorus`, `Chorus`, `Bridge`, `Solo`, `Outro`
   (case-insensitive, optionally followed by a suffix such as ` 1` or `:` ) closes the current paragraph and opens an
   implicit section: type `Chorus` for "Chorus", `Verse` for "Verse", `Bridge` for "Bridge", `Custom("intro")` etc.
   for the others, with the full comment text as label. The implicit section ends at the next blank line or
   directive that opens/closes/recalls something. This rule must not trigger inside an explicit environment.
6. Trailing `Blank` lines at the end of a section are dropped. Sections with no lines are dropped, except a `Tab` or
   `Grid` section (keep, so the user sees their empty environment in the preview).

Metadata fallbacks are **not** the parser's job (file name → title is done in step 05).

## 4. `ChordProSplitter`

`object ChordProSplitter { fun split(text: String): List<String> }` returns the text split at `{new_song}` / `{ns}`
directive lines (the directive line itself is dropped), trimming leading/trailing blank lines, and dropping parts that
contain no non-blank line. A text without `{new_song}` returns a single-element list.

## 5. `ChordProSerializer`

`object ChordProSerializer { fun serialize(song: ChordProSong): String }` writes the model back as canonical ChordPro:
metadata directives first (`{title: …}`, `{subtitle: …}`, `{artist: …}`, …, `{key: …}`, `{capo: N}`, `{tempo: …}`,
`{time: …}`, `{duration: …}`, `{transpose: N}` only if non-zero, then `{meta: name value}` for custom entries), a blank
line, then blocks separated by blank lines. Sections use the long directive names (`{start_of_verse: Verse 1}` … 
`{end_of_verse}`; `Paragraph` sections have no wrapper; `Custom(name)` → `{start_of_name: label}`). Lyrics lines
reinsert `[chord]` at their positions (`[*text]` for annotations). Grid lines join tokens with single spaces. Tab lines
verbatim. `ChorusRecall` → `{chorus}` / `{chorus: label}`. Comments → `{comment: …}` / `{comment_italic: …}` /
`{comment_box: …}`. `Break` → `{column_break}`.

This is used by "New song" templates and by tests (`parse(serialize(parse(x))) == parse(x)`). The editor edits raw text,
so the serializer does not need to preserve the user's formatting.

## 6. `ChordProTransposer`

```kotlin
object ChordProTransposer {
    /** Transposes a single chord name; returns the input unchanged if it is not a chord (e.g. "N.C."). */
    fun transposeChord(name: String, semitones: Int, preferFlats: Boolean): String

    /** Transposes every chord in the model (lyrics chords, grid chords; never tabs or annotations) and the key. */
    fun transpose(song: ChordProSong, semitones: Int): ChordProSong

    /** Transposes raw ChordPro text in place, keeping all formatting. Used by the editor's transpose action. */
    fun transposeText(text: String, semitones: Int): String

    /** Whether flats should be preferred when writing the chords of this song after the given transposition. */
    fun prefersFlats(song: ChordProSong, semitones: Int): Boolean
}
```

Rules:

- Root note: `[A-G]` optionally followed by `#`, `b`, `♯`, `♭` (normalise to `#` / `b` on output). `H` is treated as `B`
  (German notation; also keep the current mapping of `B` to index 11). Everything after the root is the suffix and is
  kept verbatim. A `/` splits off a bass note that is transposed the same way (current
  `TransposeRawSongDetailsUseCaseImpl` does this; port it). Anything that does not start with a root note is returned
  unchanged.
- `preferFlats` decides between `Db` and `C#` etc. `prefersFlats(song, semitones)`: if `metadata.key` is set, transpose
  it and prefer flats when the resulting key is one of `F, Bb, Eb, Ab, Db, Gb, Cb` or minors `Dm, Gm, Cm, Fm, Bbm, Ebm,
  Abm`; otherwise prefer flats if the song's chords contain more `b` than `#` accidentals; otherwise sharps.
- `transpose(song, 0)` and `transposeText(text, 0)` return their inputs unchanged (same instance / same string).
- `transposeText` must only touch: `[chord]` runs on lyric lines outside tab environments (not `[*annotations]`),
  chord tokens inside grid environments, and the value of `{key: …}`. It must not touch `#` comment lines, tab lines,
  or any other directive. Implement it by walking the lines with the same environment tracking as the parser.

## 7. Tests (`commonTest`, run with `./gradlew :chordpro:desktopTest`)

Write at least these, one `@Test` each, with small inline fixtures:

- `ChordProParserTest`: metadata long and short names; `{capo: 2}`; `{meta: tuning DADGAD}`; verse/chorus/bridge
  environments with labels in both `{sov: Verse 1}` and `{sov: label="Verse 1"}` forms; unclosed environment at end of
  file; `{chorus}` recall; the three comment styles; `#` lines ignored; implicit paragraphs split by blank lines;
  legacy `{c: Verse 1}` heading; legacy heading inside an explicit chorus is a plain comment; tab lines keep
  indentation; grid tokens (`| Am . . . | C . . . |` → Bar, Chord, Beat×3, Bar, Chord, Beat×3, Bar); annotation vs
  chord; `hasChords` false for a lyrics-only song and for a song whose only brackets are annotations; `\r\n` input;
  unknown directive ignored; `x_custom` goes to `custom`; `parseMetadata` matches `parse(...).metadata`.
- `ChordProSplitterTest`: two songs, `{ns}` short form, no `new_song`, empty parts dropped.
- `ChordProSerializerTest`: round trip `parse(serialize(parse(x))) == parse(x)` for a fixture that uses every block type.
- `ChordProTransposerTest`: `transposeChord("Am7/G", 2, false) == "Bm7/A"`; `Bb` up 1 with flats → `B`; `C#` down 1 →
  `C`; `H` → `C` up 1; `N.C.` unchanged; flats preferred when key transposes to `F`; `transposeText` leaves tab lines,
  annotations and `#` comments alone and updates `{key: …}`; `transpose(song, 12)` yields equal chord names.

## Verify

- `./gradlew :chordpro:desktopTest` passes.
- `./gradlew :chordpro:compileKotlinIosSimulatorArm64 :chordpro:compileKotlinWasmJs :chordpro:compileDebugKotlinAndroid`
  (or just the full build command from the README) succeeds: the module must compile for every target.

## Execution notes

_(filled in by the executing agent: deviations, surprises, follow-ups)_
