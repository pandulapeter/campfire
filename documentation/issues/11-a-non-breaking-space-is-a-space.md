# 11 — A non-breaking space is a space

## What the user sees

A chord chart copied from a web page — which is how most of them arrive — is full of non-breaking spaces. The page
used them to keep a chord from wrapping away from the syllable under it, and they survive the copy. To Campfire
those are not spaces, and two things break.

**A tab is half transposed.** Inside `{start_of_tab}`, the fret numbers move and the row of chord names above them
does not, so the tab is in the new key and the chords over it are in the old one. Verified at HEAD, with `<NB>` for
U+00A0, at +2 semitones:

```
{start_of_tab}            {start_of_tab}
Am<NB>   G          ->    Am<NB>   G        <- untouched
e|--0--2--|               e|--2--4--|
B|--1--3--|               B|--3--5--|
G|--2--0--|               G|--4--2--|
{end_of_tab}              {end_of_tab}
```

The chord row is left alone because `Am<NB>` is not a chord name and not a marker, which makes the whole line read
as prose to the transposer.

**A grid becomes one chord.** `{start_of_grid}` with `|<NB>Am<NB>.<NB>G<NB>|` parses as a single
`GridToken.Chord` holding the entire line, bar lines and beat dots included — verified at HEAD. The viewer draws
the whole line as one chord, and a transposition rewrites it as one name (which, after plan 08, means leaving it
alone entirely).

This is inconsistent inside the module rather than merely unlucky: `String.trim()` and
`ChordProSyntax.isStaffLine` use `Char.isWhitespace`, which **does** count a non-breaking space (Kotlin's
`Char.isWhitespace` is `Character.isWhitespace || Character.isSpaceChar`, and U+00A0 is category Zs — verified).
So the same line is "indented" to one half of the module and not to the other.

There is a second, quieter consequence. `\s` means different things to the three regex engines this module is
compiled for: the six ASCII spaces on the JVM and on Kotlin/Native, and **every Unicode space** in a browser's
`RegExp` (JS defines `\s` to include U+00A0, U+1680, U+2000–U+200A, U+202F, U+205F, U+3000 and U+FEFF). So a song
pasted with non-breaking spaces transposes one way on Android, iOS and the desktop and another way on the web —
and that is a difference sync carries between a user's own devices.

## Cause

Three regexes, verified at HEAD `984861e4`.

`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProTabTransposer.kt:179`:

```kotlin
    private val wordRegex = Regex("\\S+")
```

used by `chordWords`, `:117-128`:

```kotlin
    private fun chordWords(line: String): List<MatchResult>? {
        val chordWords = mutableListOf<MatchResult>()
        wordRegex.findAll(line).forEach { match ->
            val word = match.value
            when {
                ChordProChordNames.isChordName(word) -> chordWords += match
                isMarker(word) -> Unit
                else -> return null
            }
```

`\S` matches U+00A0 on the JVM, so the word is `"Am "`, which is neither a chord nor a marker, so the whole
line is refused.

`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposer.kt:394`:

```kotlin
    private val tokenRegex = Regex("\\S+")
```

used by `transposeGridLine`, `:349-368`, to get the **ranges** of the grid words, which it then pairs by index
with the tokens `ChordProSyntax.parseGridTokens` returns.

`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProSyntax.kt:24`:

```kotlin
    private val whitespaceRegex = Regex("\\s+")
```

used by `parseGridTokens`, `:454-468`:

```kotlin
    fun parseGridTokens(trimmedLine: String): List<GridToken> {
        val words = trimmedLine.split(whitespaceRegex).filter { it.isNotEmpty() }
```

So the two halves of the grid path each scan the same line with their own regex and are zipped by index — which
is why they have to agree character for character, and why the fix belongs in one shared function.

The module already knows that clever regexes are a liability here. `ChordProSyntax.kt:395-399`:

```kotlin
    /**
     * Whether [trimmedValue] is nothing but `name="value"` attributes. It is matched one attribute at a time rather than
     * with a single repeated group, which the JVM's regex engine matches recursively and overflows the stack on for a
     * long enough line.
     */
```

## The change

One hand-written scanner in `ChordProSyntax`, used by all three, and the same treatment for the staff prefix.

### 1. The shared word scanner

Next to `parseGridTokens` in `ChordProSyntax.kt`:

```kotlin
    /** One word of a line and the range it covers, see [words]. */
    data class Word(val range: IntRange, val value: String)

    /**
     * The words of [line]: every run of characters no whitespace separates, each with the range it sits in, so that
     * a caller rewriting one of them can put the answer back in the same columns.
     *
     * Scanned by hand rather than with a `\S+` regex. `\s` is the six ASCII spaces to the JVM's and to
     * Kotlin/Native's regex engines and every Unicode space to a browser's, so the same line would be read one way
     * on a phone and another on the web; and a chart pasted from a web page is full of non-breaking spaces, which
     * the ASCII answer glues onto the chord beside them. [Char.isWhitespace] is the same answer on every platform,
     * and the one `trim` and [isStaffLine] already give this module.
     */
    fun words(line: String): List<Word> {
        val words = mutableListOf<Word>()
        var index = 0
        while (index < line.length) {
            while (index < line.length && line[index].isWhitespace()) index++
            if (index == line.length) break
            val start = index
            while (index < line.length && !line[index].isWhitespace()) index++
            words += Word(range = start until index, value = line.substring(start, index))
        }
        return words
    }
```

Linear, allocation-per-word, no backtracking — which is what `crafted lines cost no more than their length`
(`ChordProSyntaxTest.kt:78`) is there to protect.

### 2. The three callers

`ChordProSyntax.parseGridTokens`, `:455`:

```kotlin
        val words = words(trimmedLine).map { it.value }
```

and `whitespaceRegex` is deleted.

`ChordProTransposer.transposeGridLine`, `:350`:

```kotlin
        val matches = ChordProSyntax.words(trimmedLine)
```

`match.range.first` / `match.range.last` / `match.value` all read the same on `Word` as on `MatchResult`, so the
body is unchanged. `tokenRegex` is deleted. **State the invariant in a comment while you are there**: the ranges
and the tokens are the same list because both now come from `ChordProSyntax.words`, which is the only reason
zipping them by index is safe.

`ChordProTabTransposer.chordWords`, `:117-128`: change the return type to `List<ChordProSyntax.Word>?` and the
iteration to `ChordProSyntax.words(line).forEach { word -> … }`. `rewriteChordLine`'s
`word.range to rename(word.value)` (`:110-112`) compiles unchanged. `wordRegex` is deleted.

### 3. The staff prefix, same problem

`ChordProTabWrapper.kt:191`:

```kotlin
    private val staffPrefixRegex = Regex("^\\s*[A-Za-z#]{0,2}\\s*\\|?")
```

A staff line indented with a non-breaking space is still a staff line to `isStaffLine` (`Char.isWhitespace`), but
this regex matches the empty string in front of it, so the line gets no prefix — and every continuation row of
that tab loses its string name. `systems()` (`ChordProTabWrapper.kt:108`) then compares empty names and stops
telling systems apart. Replace with the same kind of scan, keeping the character class **exactly** as it is
(ASCII letters and `#`, at most two of them — widening it to `isLetter()` would be a separate decision):

```kotlin
    private fun staffPrefix(line: String): String {
        var index = 0
        while (index < line.length && line[index].isWhitespace()) index++
        var letters = 0
        while (letters < MAX_PREFIX_LETTERS && line.getOrNull(index)?.isStringNameCharacter == true) {
            index++
            letters++
        }
        while (index < line.length && line[index].isWhitespace()) index++
        if (line.getOrNull(index) == BAR) index++
        return line.substring(0, index)
    }

    private val Char.isStringNameCharacter get() = this in 'A'..'Z' || this in 'a'..'z' || this == SHARP

    private const val MAX_PREFIX_LETTERS = 2
    private const val SHARP = '#'
```

### 4. The rest of the module's regexes, audited

Every remaining `Regex` in `chordpro/src/commonMain`, with a verdict. There is **no** `\b` anywhere in the module
(checked), which is the other construct whose meaning differs between the engines.

| where | pattern | verdict |
| --- | --- | --- |
| `ChordProTags.kt:51` `lineBreakRegex` | `[\r\n]+` | **Keep.** Spelled out character by character; identical on all three engines. |
| `ChordProTabTransposer.kt:180` `dashesRegex` | `-+` | **Keep.** No class. |
| `ChordProTabTransposer.kt:181` `repeatCountRegex` | `\(?[xX]\d+\)?` | **Keep.** `\d` is ASCII `0-9` on the JVM, on Native and in JS alike (JS only widens `\d` under the `u` flag with a property escape, which this is not). Once the words are scanned by `Char.isWhitespace`, no word reaching it carries a stray space. |
| `ChordProTabTransposer.kt:187` `noChordRegex` | `N\.?C\.?`, IGNORE_CASE | **Keep.** ASCII letters only. |
| `ChordProSyntax.kt:26` `voltaRegex` | `:?\|\d+>?` | **Keep**, same reasoning as `repeatCountRegex`. |
| `ChordProSyntax.kt:22` `labelAttributeRegex` | `(?:^\|\s)label\s*=\s*…` | **Leave, and say why in a comment.** It is matched against a directive *value*, and a non-breaking space in front of `label=` makes the whole value read as the label instead — the graceful direction: a heading shows the user's own text rather than nothing, and nothing is half-rewritten. |
| `ChordProSyntax.kt:23` `attributeRegex` | `\s*[A-Za-z_]…` | **Leave**, same reason: `isAttributes` returning false means `label()` hands the whole value back (`:388-393`). If you want it consistent anyway, skip whitespace by hand in `isAttributes` (`:400-406`) before each `matchAt` and drop the leading `\s*` from the pattern — a three-line change, and optional. |

One non-regex divergence found while auditing, **recommended to fix in the same commit** because it is the same
kind of bug: `ChordProTabTransposer.fretRanges` (`:57-75`) collects runs of `line[index].isDigit()`, which is
Unicode-aware on every platform, and then reads them with `toIntOrNull()`, which accepts Unicode digits on the JVM
(`Integer.parseInt("٣") == 3`, verified) and does not on Kotlin/Native or in the browser. A tab containing an
Arabic-Indic digit is therefore transposed on Android and the desktop and left alone on iOS and the web. A fret
number is ASCII; make the scan say so:

```kotlin
            if (line[index].isAsciiDigit) {
```

with `private val Char.isAsciiDigit get() = this in '0'..'9'`, applied in `fretRanges` and in `digitsEnd`
(`ChordProChordNames.kt:79`) — the latter for the same reason: `C٣` is not a chord.

## Tests

`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProSyntaxTest.kt`, next to
`crafted lines cost no more than their length` (line 78):

1. `words are separated by any space the platform calls one` — `ChordProSyntax.words("a b  c")` is three
   words with the ranges 0..0, 2..2, 4..4 (check the offsets against the literal), and `words("")`,
   `words("   ")`, `words(" ")` are empty.
2. `a grid separated by non-breaking spaces is still a grid` —
   `parseGridTokens("| Am . G |")` is `Bar`, `Chord("Am")`, `Beat`, `Chord("G")`, `Bar`.

`ChordProTransposerTest.kt`, next to `a grid keeps its margin labels and moves every chord of a cell` (line 186):

3. `a grid written with non-breaking spaces is transposed` — the line above at +2 keeps every space character it
   arrived with and moves `Am` to `Bm` and `G` to `A`. Assert the whole string, so that the spaces are part of
   the assertion.
4. `the ranges of a grid line and its tokens are the same list` — a regression net for the index zip: a grid line
   mixing ordinary and non-breaking spaces, transposed, must not throw `IndexOutOfBoundsException` and must come
   back with the same number of characters as it had plus or minus only what the chord names changed by.

`ChordProTabTransposerTest.kt`, next to `a chord row with no chord in it is still transposed` (line 18):

5. `a chord row written with non-breaking spaces is transposed with the frets` — the environment from **What the
   user sees**, asserted whole: the chord row moves to `Bm<NB>   A` and the staff to `--2--4--` etc.
6. `a prose line in a tab is still left alone` — the guard on the above: `Tuning: D A D G A D` written with
   non-breaking spaces must still come back byte for byte, because `Tuning:` is not a chord.

`ChordProTabWrapperTest.kt`:

7. `a staff indented with a non-breaking space keeps its string names in the continuation rows` — the wrap of a
   two-string system whose lines start with ` e|`, asserting the repeated prefix on the second row.

`ChordProChordNamesTest.kt`:

8. `a chord number is an ASCII digit` — `isChordName("C٣7")` is false (only if the `isAsciiDigit` change is
   taken).

## Verification

```
./gradlew :chordpro:desktopTest
./gradlew :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest
```

Because the point of the change is that the three regex engines disagree, this one is **not** proven by the
desktop tests alone. Build the other targets, and run the web one:

```
./gradlew :app:ios:linkDebugFrameworkIosSimulatorArm64
./gradlew :app:web:wasmJsBrowserDevelopmentRun
```

Manual: make a file with non-breaking spaces (one shell line:
`python3 -c "open('/tmp/nbsp.cho','w').write('{title: NBSP}\n{start_of_tab}\nAm    G\ne|--0--2--|\nB|--1--3--|\n{end_of_tab}\n{start_of_grid}\n| Am . G |\n{end_of_grid}\n')"`),
import it on the **desktop** and on the **web** build, transpose it up two in each, and confirm the two produce
the same text. That comparison is the whole point of the plan.

## Docs

`chordpro/CLAUDE.md`, the `ChordProSyntax` bullet (lines 38-44) — it lists what the object holds and now holds one
more shared rule:

> - `ChordProSyntax` — the shared low-level rules (the directive and chord regexes, … `isStaffLine` for "is this line of a tab environment
>   the staff or something written above it", and where a new one goes in a file the user wrote).

Add `words` to that list, with the reason in one clause: what counts as a space is decided once, by
`Char.isWhitespace` and not by a regex, because the three platforms' regex engines disagree about `\s` and a chart
pasted from a web page is full of non-breaking spaces.

`chordpro/CLAUDE.md`, the `ChordProTabTransposer` bullet (lines 112-121) — the sentence about what is and is not
touched is still true, but the reason a line is "prose" is now stated in terms of words rather than of a regex;
re-read it and adjust if it reads as though `\S+` were the rule.

## Files touched

- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProSyntax.kt`
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposer.kt`
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProTabTransposer.kt`
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProTabWrapper.kt`
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProChordNames.kt` (only for the
  `isAsciiDigit` change)
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProSyntaxTest.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposerTest.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProTabTransposerTest.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProTabWrapperTest.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProChordNamesTest.kt`
- `chordpro/CLAUDE.md`

## Depends on

Nothing, but it touches `ChordProChordNames` (the optional `isAsciiDigit` piece) and `ChordProTabWrapper`
(`staffPrefix`), which plans 09 and 12 also touch. Land 09 first, then this, then 12 — or rebase; the three do not
overlap line for line.

## Rules

- Load the `code-style` skill before the first edit.
- `commonMain` stays JVM-free; `:chordpro` has no dependencies at all.
- Prefer hand-written scanning to a clever pattern anywhere in this module: a previous review hit a JVM stack
  overflow with a regex here, which is why `ChordProSyntax.isAttributes` is a loop.
- A change to the dialect belongs in a test first.
- The per-module `CLAUDE.md` is part of the change.
