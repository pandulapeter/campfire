# 02 — A `{transpose}` in the middle of a song moves the whole song

**Severity:** wrong chords (all platforms) · **Area:** `:chordpro` (`ChordProParser`, `ChordProTransposer`,
`ChordProSerializer`, model), `:presentation` (`CampfireViewModel.renderSong`, `SongLyrics.kt`), docs

**Decision D-A, taken by the user on 2026-09-23:** model the modulation. Only a `{transpose}` before the first body
line sets the whole-song transposition; a later one shifts the chords from that point on; a `{transpose}` with no
value restores the transposition before it. This holds in the viewer (and the editor's preview, which renders the
same way) and is kept consistent with the editor's text transposition.

**Read, not run.** Found by reading the parser and `renderSong` at HEAD (2065e47f). The unit tests below reproduce it.

## What the user sees

The ChordPro way of writing "last chorus up a whole step":

```
{title: Key Change}
{start_of_verse}
[C]one [G]two
{end_of_verse}

{transpose: 2}
{start_of_chorus}
[C]three [G]four
{end_of_chorus}
```

Campfire shows **every** chord of the song two semitones up — the verse as `D` / `A` as well as the chorus — and the
song list names the key two semitones up too. A second `{transpose}` further down replaces the first for the whole song
(last one wins); a `{transpose}` with no value, which the spec defines as "cancel the current transposition, possibly
restoring a preceding one", is ignored.

## Cause

The parser reads `{transpose}` as one song-wide number, the last one in the file winning —
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProParser.kt:377`:

```kotlin
                "transpose" -> value.removePrefix("+").toIntOrNull()?.let { transpose = it }
```

(a valueless or `2f`-style value is `toIntOrNull() == null` and dropped), and the viewer adds it to every chord —
`presentation/.../ui/CampfireViewModel.kt:1318-1328`:

```kotlin
    fun renderSong(text: String, transposition: Int, spelling: UserPreferences.ChordSpelling): ChordProSong {
        val parsed = parseChordPro(text)
        val semitones = parsed.metadata.transpose + transposition
        // A preferred spelling still respells a song nobody transposed, so only the two together mean there is nothing to do.
        val transposed = if (semitones == 0 && spelling.accidentals == UserPreferences.Accidentals.ORIGINAL) {
            parsed
        } else {
            transposeChordPro(parsed, semitones, spelling.accidentals)
        }
```

`renderKey` (`:1341-1344`) does the same with `Song.transpose`, which is the same number read by the library scan
(`data/source/local/implementation/.../mapper/SongMappers.kt:33`, `transpose = summary.metadata.transpose`), so the
list's key is off by the same amount.

The spec (chordpro.org, *Directives: transpose*): "This directive indicates that the remainder of the song should be
transposed the number of semitones according to the given value. … A `{transpose}` directive without a value will
cancel the current transposition, possibly restoring a preceding transposition." The value is the transposition of the
rest of the song, not an increment; a stack of them is what "restoring a preceding" one means.

## The change

Invoke the **`code-style`** skill before the first edit. This plan assumes **01** has landed (the `isContinuation`
reopen, `ChorusRecall.blocks`, `rewriteBlock` and the serializer's piece walk) and **03** (the `rewriteLines` flag).

### 1. Model — `model/ChordProBlock.kt`

```kotlin
    /**
     * `{transpose: N}` somewhere after the song has begun: from here on the chords are read [semitones] away from
     * where the song's own transposition ([ChordProMetadata.transpose], the `{transpose}` it opens with) puts them —
     * a key change written as a directive. 0 is back to that. It shows nothing; the transposition applies it.
     */
    data class Transpose(val semitones: Int) : ChordProBlock
```

`model/ChordProMetadata.kt:29`: `val transpose: Int = 0, // the {transpose} before the song's first line, applied by the renderer on top of the user's transposition; see ChordProBlock.Transpose for the later ones`.

### 2. Parser — `ChordProParser.kt`

A helper shared by `parseAsWritten` and `scan`, so that the library scan and the viewer agree on the whole-song value:

```kotlin
    /**
     * The `{transpose}` directives of a song, the way the spec reads them: each one is the transposition of the rest of
     * the song (not an addition to the one before it), and one with no value goes back to the one before it. What a
     * song opens with — every `{transpose}` before its first line — is the whole song's; a later one is a modulation.
     */
    private class Transposition {
        private val restorable = mutableListOf<Int>()
        private var current = 0
        private var lastReported = 0

        /** The transposition of the whole song: what was in effect when the body began, or at the end of a song with none. */
        var wholeSong = 0
            private set
        var isInBody = false
            private set

        fun startBody() {
            if (isInBody) return
            isInBody = true
            wholeSong = current
            lastReported = current
        }

        /** Reads one directive; the offset from [wholeSong] the chords after it are at, where that changed in the body. */
        fun consume(value: String?): Int? {
            val written = value?.trim().orEmpty()
            if (written.isEmpty()) {
                current = restorable.removeLastOrNull() ?: 0
            } else {
                // `2s` and `-3f` ask for sharps or flats as well; the reader's own spelling preference decides that here.
                val number = written.removePrefix("+").let { if (it.lastOrNull()?.lowercaseChar() in SPELLING_SUFFIXES) it.dropLast(1) else it }
                val semitones = number.trim().toIntOrNull() ?: return null
                restorable += current
                current = semitones
            }
            if (!isInBody || current == lastReported) return null
            lastReported = current
            return current - wholeSong
        }

        fun finish() = startBody()
    }
```

with `private val SPELLING_SUFFIXES = setOf('s', 'f')` next to the other constants.

**Where the body begins** — the first line that is not blank or a `#` comment, the first `{start_of_…}`, or the first
directive that makes a block (`ChordProSyntax.blockNames`: a comment, a highlight, a break, a recall). A stray
`{end_of_…}` or an unknown directive does not, since neither puts anything in the song.

`parseAsWritten` (`:41-57`):

```kotlin
        val transposition = Transposition()
        ChordProSyntax.splitLines(text).forEach { rawLine ->
            val trimmedLine = rawLine.trim()
            if (trimmedLine.startsWith(SOURCE_COMMENT)) return@forEach
            val directive = ChordProSyntax.matchDirective(trimmedLine)
            if (directive == null) {
                if (trimmedLine.isNotEmpty()) transposition.startBody()
                section.addContent(rawLine, trimmedLine)
            } else {
                handleDirective(directive, metadata, blocks, section, transposition)
            }
        }
        section.close()
        transposition.finish()
        return ChordProSong(metadata = metadata.build(transpose = transposition.wholeSong), blocks = withChorusesRecalled(blocks))
```

`handleDirective` (`:117-163`), right after the selector check at `:124`:

```kotlin
        if (name == TRANSPOSE) {
            // Inside a section the modulation cuts it in two, the way a comment does, and the rest is its continuation.
            transposition.consume(directive.value)?.let { semitones -> section.addBlock(ChordProBlock.Transpose(semitones)) }
            return
        }
        if (ChordProSyntax.startOfEnvironment(name) != null || name in ChordProSyntax.blockNames) transposition.startBody()
```

`scan` (`:73-107`) gets the same `Transposition`: in the directive branch (`:82-88`), inside the
`!hasSelectorSuffix` check, `if (directive.name == TRANSPOSE) transposition.consume(directive.value) else if (…start or
block name…) transposition.startBody()` before `metadata.consume(directive)`; a non-blank body line calls
`transposition.startBody()` **before** the early `return@forEach` at `:92` (that return skips lines once chords are
found, and the body has begun either way). It ends with `transposition.finish()` and
`metadata.build(transpose = transposition.wholeSong)`.

`MetadataBuilder` loses its `transpose` field and the `"transpose"` branch at `:377`; `build(transpose: Int)` takes it.
`"transpose"` must still reach `handleDirective` before the `else -> metadata.consume(directive)` at `:161` — it does,
since the check above returns first.

`withChorusesRecalled` (plan 01): the blocks copied from between the pieces of a chorus leave the modulations out as
well as the recalls — `filterNot { it is ChordProBlock.ChorusRecall || it is ChordProBlock.Transpose }` — since a
recalled chorus is played at the transposition of the place it is recalled at (below).

Add `private const val TRANSPOSE = "transpose"`.

### 3. Transposer — `ChordProTransposer.kt`

`rewriteChords` becomes offset-aware; the notation keeps calling the old signature, which ignores the offsets:

```kotlin
    /** What [rewriteChords] does to the chords of one stretch of a song. */
    internal class ChordRewrite(
        val rewriteTabLines: (List<String>) -> List<String>,
        val rename: (String) -> String,
    )

    internal fun rewriteChords(
        song: ChordProSong,
        rewriteTabLines: (List<String>) -> List<String>,
        rename: (String) -> String,
    ): ChordProSong = ChordRewrite(rewriteTabLines, rename).let { rewrite -> rewriteChords(song) { rewrite } }

    /**
     * [rewriteChords] for a rewrite that depends on where in the song a chord is: [rewriteAt] is asked for the one of
     * each stretch a `{transpose}` moved by the given offset, 0 before the first. A recall is rewritten with the
     * offset in effect where it stands, which is what makes a chorus recalled after `{transpose: 2}` the key change it
     * is written as.
     */
    internal fun rewriteChords(song: ChordProSong, rewriteAt: (Int) -> ChordRewrite): ChordProSong {
        var offset = 0
        return song.copy(
            metadata = song.metadata.copy(key = song.metadata.key?.let(rewriteAt(0).rename)),
            blocks = song.blocks.map { block ->
                if (block is ChordProBlock.Transpose) offset = block.semitones
                rewriteBlock(block, rewriteAt(offset))
            },
        )
    }
```

(`rewriteBlock` from plan 01 takes a `ChordRewrite` instead of the two functions.) The model transposition:

```kotlin
    fun transpose(song: ChordProSong, semitones: Int, preferFlats: Boolean? = null): ChordProSong {
        val isModulated = song.blocks.any { it is ChordProBlock.Transpose }
        if (semitones == 0 && preferFlats == null && !isModulated) return song
        val rewrites = mutableMapOf<Int, ChordRewrite>()
        return rewriteChords(song) { offset ->
            rewrites.getOrPut(semitones + offset) { rewriteBy(song, semitones + offset, preferFlats) }
        }
    }

    /**
     * Moving by [shift] semitones, spelled for the key the song is in once it has moved that far. No move and no forced
     * spelling leaves the chords exactly as written, as it does for a song with no modulation at all.
     */
    private fun rewriteBy(song: ChordProSong, shift: Int, preferFlats: Boolean?): ChordRewrite {
        if (shift == 0 && preferFlats == null) return ChordRewrite(rewriteTabLines = { it }, rename = { it })
        val flats = preferFlats ?: prefersFlats(song, shift)
        val rename = { name: String -> transposeChord(name, shift, flats) }
        return ChordRewrite(rewriteTabLines = { lines -> ChordProTabTransposer.transpose(lines, shift, rename) }, rename = rename)
    }
```

(this replaces the private `transpose(song, semitones, preferFlats: Boolean)` at `:47-54`). `prefersFlats(song, shift)`
already transposes the declared key by `shift`, so a modulated stretch is spelled for the key it modulated to.

**The text transposition needs no new logic.** Each `{transpose}` value is relative to the song as written, so moving
every chord of the text by N (what `transposeText` does) and leaving the directives alone keeps every stretch N away
from where it was, which is what the viewer does with the model: `transpose(parse(x), W + N)` and
`transpose(parse(transposeText(x, N)), W)` agree (W the whole-song value). One change is needed for tabs: the model now
splits a section at a mid-section `{transpose}`, so the text path must end a tab run there as it does at a comment —
`rewriteText` `:242-246`:

```kotlin
                    if (directive.name in ChordProSyntax.blockNames || directive.name == TRANSPOSE) {
```

(with `private const val TRANSPOSE = "transpose"`). An octave decision inside a modulated tab can still differ
between the two paths — the text moves the frets by N and the viewer then by the offset, each time keeping them on
the fingerboard, where the viewer alone moves them once by N + offset — which only a tab at the edge of the
fingerboard hits; it is left as it is.

### 4. Serializer — `ChordProSerializer.kt`

`serializeBlock` (`:54-64`) needs the whole-song value to write a modulation back as the absolute directive it was read
from; pass `song.metadata.transpose` down (`serialize` → `serializeSection` → `serializeLines` → `serializeBlock`):

```kotlin
        is ChordProBlock.Transpose -> "{transpose: ${wholeSongTranspose + block.semitones}}"
```

The header keeps writing `{transpose: W}` when W ≠ 0 (`:46`). Round trip: the header sets W, and every body directive
sets `current = W + offset` again, so the offsets come back the same.

### 5. Viewer

- `SongLyrics.kt`, `toRenderSections`: `is ChordProBlock.Transpose -> Unit // It moved the chords; there is nothing to draw.`
  The recall loop's `else -> Unit` already covers it.
- `CampfireViewModel.kt:1318-1328`: drop the shortcut — `ChordProTransposer.transpose` makes the same check and now
  also knows about the modulations (a song at 0 with the original spelling still has its modulated stretches to move):

  ```kotlin
      fun renderSong(text: String, transposition: Int, spelling: UserPreferences.ChordSpelling): ChordProSong {
          val parsed = parseChordPro(text)
          // The file's own {transpose} (the one it opens with), the reader's, and the modulations further down: all
          // three are the transposition's to apply, and it leaves a song none of them move exactly as it is.
          val transposed = transposeChordPro(parsed, parsed.metadata.transpose + transposition, spelling.accidentals)
          // Last, and on the model only: the file, and the editor's transposition below, stay in the app's own notation.
          return convertChordProNotation(transposed, spelling)
      }
  ```

  Update its KDoc ("applies the file's own `{transpose}`" → "…the `{transpose}` it opens with and the ones further
  down it"). `renderKey` is right as it is once `Song.transpose` is only the opening value: a list names the key the
  song *starts* in.

## Tests

`chordpro/src/commonTest`, `./gradlew :chordpro:desktopTest`:

- `ChordProParserTest`
  - `only a transpose before the first line transposes the whole song` — the repro: `metadata.transpose == 0`, and a
    `ChordProBlock.Transpose(2)` between the verse and the chorus; `summarize(repro).metadata.transpose == 0`.
  - `a transpose in the header is the whole song's, the last one winning` — `{transpose: 2}\n{transpose: 3}\n[C]a`
    → 3, and no `Transpose` block; `{transpose: 2}\n{transpose}\n[C]a` → 0; `{transpose: 2f}` → 2; same through
    `summarize`.
  - `a transpose with no value restores the one before it` — `{transpose: 1}\n[C]a\n{transpose: 5}\n[C]b\n{transpose}\n[C]c`:
    `metadata.transpose == 1`; blocks `Section(a)`, `Transpose(4)`, `Section(b, isContinuation = true)`,
    `Transpose(0)`, `Section(c, isContinuation = true)`.
  - `a recalled chorus leaves the modulations inside it out` (a chorus with a `{transpose}` in it, recalled).
- `ChordProTransposerTest`
  - `a modulation moves the chords after it only` — `transpose(parse(repro), 0)`: verse `C`, `G`; chorus `D`, `A`.
  - `a chorus recalled after a modulation is in the new key` —
    `{soc}\n[C]a\n{eoc}\n{transpose: 2}\n{chorus}`: the section keeps `C`, the recall's piece holds `D`.
  - `the text transposition agrees with the model across a modulation` — for the repro and N = 3:
    `transpose(parse(x), 3) == transpose(parse(transposeText(x, 3)), 0)`.
  - `a song with no modulation is returned as it is for no semitones` (the existing
    `transposing by zero returns the input untouched`, `:109`, must keep passing).
- `ChordProSerializerTest`: the repro and the restore case round trip; add
  `{transpose: 1}\n[C]a\n{transpose: 5}\n[C]b` to the `EVERY_BLOCK_TYPE`-style checks.

The `transpose` field of `ChordProMetadata` keeps its type, so `:data:source:local`'s mapper and its tests need no
change. Compile check for `:presentation` on all four targets, then the root unit test command.

## Verification

1. Confirm first: the repro as `key-change.cho`, desktop app. **Before:** the verse shows `D` / `A`.
2. After: the verse shows `C` / `G`, the chorus `D` / `A`; the list names the key the song starts in.
3. Viewer transposition +1: verse `C#`/`G#` (or its flat spelling per the key), chorus `D#`/`A#`.
4. Editor → Transpose +1, save, back to the viewer at 0: the same chords as step 3.
5. A `{transpose: 2}` before `{chorus}` at the end of a song: the recalled chorus is two up; a `{transpose}` after it
   brings the next section back.
6. A demo song (no `{transpose}`) is unchanged at 0 and transposed as before.

## Docs

- `chordpro/CLAUDE.md`, the `ChordProParser` bullet, after "a song that changes key is in the key its first `{key}`
  names;": "a `{transpose}` before the song's first line transposes the whole of it (`ChordProMetadata.transpose`, the
  last one there winning), and one further down is a modulation — a `ChordProBlock.Transpose` holding the offset from
  the whole-song value for everything after it, cutting the section it stands in the way a comment does — each value
  being the transposition of the rest of the song and a valueless one going back to the one before, as the spec has
  it;". The `ChordProTransposer` bullet: add "A modulation moves the stretch after it by its offset on top of the
  transposition asked for, spelled for the key it lands in, and a recall is moved by the offset where it stands; the
  text transposition leaves the `{transpose}` directives alone, which keeps them right, since each is relative to the
  song as written."
- `presentation/CLAUDE.md:81` — replace "`{transpose}`, which the renderer does add on top of the reader's own
  transposition but which the app has no reason to encourage" with "`{transpose}`, which the renderer honours — the
  one a song opens with on top of the reader's own transposition, a later one as a key change from there on — but
  which the app has no reason to encourage".
- `presentation/CLAUDE.md:54` (`SongListItem`): "`{key}` moved by the file's own `{transpose}`" → "…by the
  `{transpose}` the file opens with".
- `data/source/local/implementation/CLAUDE.md:100`: "`{transpose}` (which travels with the key …)" → "the
  `{transpose}` it opens with (which travels with the key …)".
- `data/model/.../domain/Song.kt:29-33` KDoc: "`{transpose}`, the amount the file asks to be read at" → "the
  `{transpose}` the file opens with, the amount it asks to be read at".
- `documentation/file-format.md`, the **Content** bullet: add "`{transpose: N}` before the first line moves the whole
  song, further down it moves the chords from there on (a key change), and `{transpose}` with no value goes back to
  the transposition before it."

## Files touched

- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/model/ChordProBlock.kt`
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/model/ChordProMetadata.kt`
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProParser.kt`
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposer.kt`
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProSerializer.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProParserTest.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposerTest.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProSerializerTest.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongLyrics.kt`
- `data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/Song.kt` (KDoc only)
- `chordpro/CLAUDE.md`, `presentation/CLAUDE.md`, `data/source/local/implementation/CLAUDE.md`,
  `documentation/file-format.md`

## Depends on

**01** and **03** (lands after both). **05** and **06** land after this one: both touch `rewriteChords` /
`rewriteBlock` in their `ChordRewrite` form. `CampfireViewModel.kt` is also touched by lane D plans (29, 30, 33, 34)
in other functions; `renderSong` is only touched here.
