# 39 · A crafted or corrupt line (`{c:` and a long run of spaces, or a long run of `[`) hangs the library scan, the import and the editor

**Severity:** performance — a hang, an ANR in the editor (all platforms; low likelihood: it takes a crafted file, a corrupt one, or a paste with a few hundred spaces after a `{name:`) · **Area:** `:chordpro` — `ChordProSyntax.directiveRegex` / `chordRegex` and everything that matches with them (`ChordProParser`, `ChordProHighlighter`, `ChordProTransposer`, `ChordProTabTransposer`, and through `matchDirective` also `ChordProHeader`, `ChordProTags`, `ChordProLanguages`, `ChordProSplitter`)

## Symptom
1. A song file holds a line that starts with `{name:`, goes on with a long run of spaces or tabs and does **not**
   end in `}` — `{c:` + 3000 spaces + `x`. Drop it into the library folder, or import it.
2. The startup scan stalls on the batch that file is in, so the rest of the list never arrives; the import stalls
   the same way and its sheet never closes.
3. Opened in the editor, the line is matched on the main thread on every keystroke and every caret move
   (`tokenize`, `summarize`, `declaredMetadata`): the app stops answering (an ANR on Android).
4. A second, smaller case: a line that is a long run of `[` with no `]` costs the square of its length.

Measured on a desktop JIT with the very patterns (`java.util.regex`, the engine behind `Regex` on Android and
desktop): 300 spaces 19 ms, 1000 spaces 0.7 s, 2000 spaces 5.5 s — cubic; 10 000 `[` 0.23 s, 40 000 `[` 3.7 s —
quadratic. Kotlin/Native and Kotlin/Wasm run their own backtracking engine, which is slower still. A closed
directive (`{c:` + 2000 spaces + `x}`) matches at once: it is the *failing* match that explodes.

## Cause
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProSyntax.kt:20-21` (as of `29820b93`):

```kotlin
val directiveRegex = Regex("^\\{\\s*([\\w-]+)\\s*(?::\\s*(.*?))?\\s*\\}$")
val chordRegex = Regex("\\[(.*?)]")
```

After the colon, `\s*`, the lazy `.*?` and the second `\s*` can all consume the same whitespace, so a match that is
going to fail tries every way of dividing the run between the three before it gives up. `\[(.*?)]` looks for its `]`
all the way to the end of the line again from every `[`.

Every use of the two, all in `:chordpro` (nothing outside the module sees them, `ChordProSyntax` is `internal`):

| Regex | Where | Reached from |
| --- | --- | --- |
| `directiveRegex` | `ChordProSyntax.matchDirective` (`:109-114`), its only user | everything below |
| `matchDirective` | `ChordProParser.parse` (`ChordProParser.kt:44`), `scan` (`:76`) | viewer, editor preview, `summarize` for every file at startup and on every keystroke, `parseMetadata` |
| | `ChordProHighlighter.tokenize` (`ChordProHighlighter.kt:55`) | every keystroke and selection change, main thread |
| | `ChordProTransposer.transposeText` (`ChordProTransposer.kt:117`) | the editor's transpose action |
| | `ChordProTabTransposer.rewriteChordLine` (`ChordProTabTransposer.kt:103`) | both transpositions, `ChordProNotation` |
| | `ChordProHeader.declaredMetadata` (`ChordProHeader.kt:43`) | every keystroke |
| | `ChordProSyntax.metadataInsertionIndex` (`ChordProSyntax.kt:209`, `:216`) | `ChordProHeader.insert`, `ChordProTags.addTag`, `ChordProLanguages.setLanguages` |
| | `ChordProTags` (`ChordProTags.kt:46`), `ChordProLanguages` (`ChordProLanguages.kt:63`) | a tag or a language chip tapped in the viewer |
| | `ChordProSplitter.split` (`ChordProSplitter.kt:20`) | every import |
| `chordRegex` | `ChordProParser.parseLyrics` (`ChordProParser.kt:175`) | `parse`, `scan` |
| | `ChordProHighlighter.tokenize` (`ChordProHighlighter.kt:65`) | every keystroke |
| | `ChordProTransposer.rewriteLyricsLineChords` (`ChordProTransposer.kt:217`) | `transposeText`, the bracketed branch of a tab's chord row |
| | `ChordProTabTransposer.rewriteChordLine` (`ChordProTabTransposer.kt:104`) | as above |

The other regexes of the module were checked and are linear (`chordNameRegex`, `labelAttributeRegex`,
`whitespaceRegex`, `staffPrefixRegex`, `\S+`, `-+`, `repeatCountRegex`, `noChordRegex`): none of them puts two
quantifiers that accept the same character next to each other. Leave them alone (plan 40 rewrites `chordNameRegex`).

## Fix
Replace both regexes with hand-written scanning. Possessive quantifiers and atomic groups would make the regexes
linear on the JVM, but Kotlin's own engine on Native and Wasm is not guaranteed to have them, and a scanner is
cheaper per keystroke on those two anyway. Both replacements below were run against the regexes over three million
random lines (braces, colons, spaces, tabs, word characters, `-`, `_`, brackets, `*`, a non-ASCII letter) and agree
on every one.

1. `ChordProSyntax.kt` — delete `directiveRegex` and `chordRegex` (`:20-21`) and rewrite `matchDirective` (`:108-114`):

   ```kotlin
   /**
    * Matches a directive line, or returns null for content. The name comes back lowercase, the value trimmed: null
    * where the directive has no colon, empty where it has one with nothing after it, and everything up to the brace
    * that ends the line otherwise, so a value may hold a colon or a brace of its own.
    *
    * It is a walk over the line and not a regular expression because it runs for every line of every file, on the
    * main thread while the user types, and has to cost the length of the line whatever the line holds.
    */
   fun matchDirective(trimmedLine: String): Directive? {
       val closeIndex = trimmedLine.length - 1
       if (closeIndex < 1 || trimmedLine[0] != DIRECTIVE_OPEN || trimmedLine[closeIndex] != DIRECTIVE_CLOSE) return null
       var index = 1
       while (index < closeIndex && trimmedLine[index].isWhitespace()) index++
       val nameStartIndex = index
       while (index < closeIndex && trimmedLine[index].isDirectiveNameCharacter) index++
       if (index == nameStartIndex) return null
       val name = trimmedLine.substring(nameStartIndex, index).lowercase()
       while (index < closeIndex && trimmedLine[index].isWhitespace()) index++
       return when {
           index == closeIndex -> Directive(name = name, value = null)
           trimmedLine[index] == DIRECTIVE_VALUE_SEPARATOR -> Directive(name = name, value = trimmedLine.substring(index + 1, closeIndex).trim())
           else -> null
       }
   }

   /** The characters a directive is named with. ASCII on purpose: it is the same set on every platform that way. */
   private val Char.isDirectiveNameCharacter get() = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9' || this == '_' || this == '-'
   ```

   with, next to the other constants,

   ```kotlin
   private const val DIRECTIVE_OPEN = '{'
   private const val DIRECTIVE_CLOSE = '}'
   private const val DIRECTIVE_VALUE_SEPARATOR = ':'
   private const val BRACKET_OPEN = '['
   private const val BRACKET_CLOSE = ']'
   ```

   Behaviour to keep, all of which the walk above has: `{}`, `{ }`, `{:}`, `{: x}`, `{title`, `title}`, `{title x}`,
   `{ti tle: x}` and `{title}}` are not directives; `{title}` has a null value and `{title:}` / `{title: }` an empty
   one (which `ChordProHeader.declaredMetadata` relies on); `{tag: a}b}` has the value `a}b` (which `ChordProTags`
   relies on); `{c: a: b}` has the value `a: b`. Do **not** use `isLetterOrDigit()` for the name: `\w` was ASCII, and
   `{cím: x}` has to stay the line of lyrics it is today.

   Two deliberate differences, neither reachable from a test or from a file anybody writes: the whitespace inside
   the braces is now what `trim()` — which every caller has already applied to the line — calls whitespace (a
   no-break space after the `{` no longer turns the directive into lyrics), and a value holding U+2028, U+2029 or
   U+0085 matches, where `.` refused them.

2. `ChordProSyntax.kt` — the replacement of `chordRegex`, after `matchDirective`:

   ```kotlin
   /**
    * Every `[…]` of a line from left to right: a `[`, the first `]` after it, and nothing of either inside another.
    * What is written between them is handed out as it is, untrimmed, since an empty pair of brackets and an
    * annotation are told from a chord by the callers, each for its own purpose.
    *
    * The search stops at the first `[` that nothing closes: no later one can be closed either, and that is what
    * keeps a line made of nothing but opening brackets as cheap as any other.
    */
   fun brackets(line: String): List<Bracket> {
       val brackets = mutableListOf<Bracket>()
       var index = 0
       while (true) {
           val openIndex = line.indexOf(BRACKET_OPEN, startIndex = index)
           if (openIndex < 0) break
           val closeIndex = line.indexOf(BRACKET_CLOSE, startIndex = openIndex + 1)
           if (closeIndex < 0) break
           brackets += Bracket(range = openIndex..closeIndex, content = line.substring(openIndex + 1, closeIndex))
           index = closeIndex + 1
       }
       return brackets
   }

   /** Whether [line] holds a `[…]` at all, for a caller that only has to decide how to read it. */
   fun hasBrackets(line: String): Boolean {
       val openIndex = line.indexOf(BRACKET_OPEN)
       return openIndex >= 0 && line.indexOf(BRACKET_CLOSE, startIndex = openIndex + 1) >= 0
   }
   ```

   and next to `Directive` at the bottom of the object:

   ```kotlin
   /** One `[…]` of a line: where it stands, brackets included, and what is written between them. */
   data class Bracket(val range: IntRange, val content: String)
   ```

   Update the object's KDoc if it mentions the regexes (it does not today) and nothing else in the file.

3. `ChordProParser.parseLyrics` (`ChordProParser.kt:171-190`): `ChordProSyntax.chordRegex.findAll(rawLine).forEach { match ->`
   becomes `ChordProSyntax.brackets(rawLine).forEach { bracket ->`, with `match.range` → `bracket.range` (twice) and
   `match.groupValues[1].trim()` → `bracket.content.trim()`. Nothing else in the function changes.

4. `ChordProHighlighter.tokenize` (`ChordProHighlighter.kt:65-71`):

   ```kotlin
   !isInsideTab -> ChordProSyntax.brackets(line).forEach { bracket ->
       tokens += Token(
           type = if (bracket.content.startsWith(ANNOTATION_PREFIX)) TokenType.ANNOTATION else TokenType.CHORD,
           start = lineStart + bracket.range.first,
           end = lineStart + bracket.range.last + 1,
       )
   }
   ```

   The content stays untrimmed here, as it was (`match.groupValues[1]`).

5. `ChordProTransposer.rewriteLyricsLineChords` (`ChordProTransposer.kt:215-224`; plan 38 leaves it as it is):

   ```kotlin
   /** Applies [rename] to every `[chord]` of a raw line, leaving the annotations, the empty brackets and the text. */
   internal fun rewriteLyricsLineChords(rawLine: String, rename: (String) -> String): String {
       val brackets = ChordProSyntax.brackets(rawLine)
       if (brackets.isEmpty()) return rawLine
       return buildString(rawLine.length) {
           var consumedUntil = 0
           brackets.forEach { bracket ->
               val content = bracket.content.trim()
               val isChord = content.isNotEmpty() && !content.startsWith(ANNOTATION_MARKER)
               append(rawLine, consumedUntil, if (isChord) bracket.range.first else bracket.range.last + 1)
               if (isChord) append(BRACKET_OPEN).append(rename(content)).append(BRACKET_CLOSE)
               consumedUntil = bracket.range.last + 1
           }
           append(rawLine, consumedUntil, rawLine.length)
       }
   }
   ```

   with `private const val BRACKET_OPEN = '['` and `BRACKET_CLOSE = ']'` among the transposer's constants. A chord
   written `[ Am ]` comes back as `[Bm]`, as it did.

6. `ChordProTabTransposer.rewriteChordLine` (`ChordProTabTransposer.kt:104`):
   `if (ChordProSyntax.chordRegex.containsMatchIn(line))` becomes `if (ChordProSyntax.hasBrackets(line))`.

7. After the change, `grep -rn "directiveRegex\|chordRegex" chordpro/` must find nothing.

Ordering: this plan is written against the result of 37 and 38. Neither of them touches the lines changed here —
38 rewrites the tail of `rewriteChordLine` *below* line 104 and makes `scan` call `parseLyrics`, which step 3 covers.

## Tests
New file `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProSyntaxTest.kt` (MPL header,
`class ChordProSyntaxTest`; `ChordProSyntax` is `internal`, which a test of the same module sees):

1. `a directive is matched by its braces its name and its colon` — `matchDirective(x)` equals `Directive(name, value)`:
   `{title: Song}` → `title`, `Song`; `{  Title :  Song  }` → `title`, `Song`; `{soc}` → `soc`, `null`;
   `{ soc }` → `soc`, `null`; `{title:}` → `title`, `""`; `{title: }` → `title`, `""`; `{c: a: b}` → `c`, `a: b`;
   `{tag: a}b}` → `tag`, `a}b`; `{start_of_verse: label="Verse 1"}` → `start_of_verse`, `label="Verse 1"`;
   `{title-guitar: x}` → `title-guitar`, `x`; `{x_custom:1}` → `x_custom`, `1`.
2. `a line that only looks like a directive is content` — `matchDirective` is `null` for `""`, `{`, `}`, `{}`, `{ }`,
   `{:}`, `{: x}`, `{title`, `title}`, `{title x}`, `{ti tle: x}`, `{title}}`, `{cím: x}`, `x{title: y}`.
3. `brackets are paired from the left` — `brackets(x).map { it.range to it.content }`:
   `[Am]la [G/B]la` → `(0..3, "Am")`, `(7..11, "G/B")`; `[]` → `(0..1, "")`; `[ Am ]` → `(0..5, " Am ")`;
   `[[Am]` → `(0..4, "[Am")`; `a]b[c]` → `(3..5, "c")`; `[Am] [C` → `(0..3, "Am")` only; `[Am`, `]`, `""` → empty.
   `hasBrackets` is true exactly for the inputs with a non-empty result.
4. `a crafted line costs no more than its length` — the time bound. Add to the test class:

   ```kotlin
   /**
    * A bound and not a benchmark: a walk over these lines takes a few milliseconds on the slowest machine that
    * builds this, and a backtracking match of the same lines more than half a minute on the fastest.
    */
   private fun <T> assertLinear(block: () -> T): T {
       val (result, duration) = measureTimedValue(block)
       assertTrue(duration < 5.seconds, "took $duration")
       return result
   }

   private val unclosedDirective = "{c:" + " ".repeat(4_000) + "x"
   private val unclosedBrackets = "[".repeat(100_000)
   ```

   (`kotlin.time.measureTimedValue`, `kotlin.time.Duration.Companion.seconds`.) The case asserts
   `assertLinear { ChordProSyntax.matchDirective(unclosedDirective) } == null`, the same for the run written with
   `"\t"`, for `"{c: a" + " ".repeat(4_000) + "b" + " ".repeat(4_000)` and for `"{" + " ".repeat(4_000) + "c"`, and
   `assertLinear { ChordProSyntax.brackets(unclosedBrackets) }.isEmpty()`, `hasBrackets` false, and for
   `"[".repeat(100_000) + "]"` exactly one bracket with the range `0..100_000`.
5. `nothing that reads a file stalls on a crafted line` — with
   `text = listOf("{title: Song}", unclosedDirective, unclosedBrackets, "[Am]la").joinToString("\n")`, each call in
   its own `assertLinear`, so that a regression fails at the first one instead of after all of them:
   - `ChordProParser.parse(text)`: `metadata.title == "Song"`, one `Section` with three `Lyrics` lines, the first
     two with no chords and the texts `unclosedDirective` and `unclosedBrackets`, the third `la` with `Am` at 0;
   - `ChordProParser.summarize(text)`: title `Song`, `hasChords == true`; `parseMetadata(text).title == "Song"`;
   - `ChordProHighlighter.tokenize(text).size == 4` (three for the title, one chord);
   - `ChordProTransposer.transposeText(text, 2)` equals `text` with `[Am]` replaced by `[Bm]`;
   - `ChordProSplitter.split(text) == listOf(text)`;
   - `ChordProHeader.declaredMetadata(text) == setOf("title")`;
   - `ChordProTags.addTag(text, "Live")` has `{tag: Live}` as its second line and `removeTag` of it gives `text` back;
   - `ChordProLanguages.setLanguages(text, listOf("en"))` has `{meta: language en}` as its second line;
   - `ChordProTabTransposer.transpose(listOf(unclosedDirective, unclosedBrackets, "e|--0--|"), 2, …)` (the third
     argument is whatever 38 left there: `preferFlats = false` before it, `rename = { it }` after it) returns the
     first two lines untouched and `e|--2--|`.

Every existing test of the module must pass unchanged — the two functions are drop-in replacements, and that is the
proof asked of them. In particular `ChordProHighlighterTest`, `ChordProHeaderTest` (`{title: }` counts as declared),
`ChordProTagsTest` (a tag with a `}` in it) and `ChordProParserTest` (`[]`, annotations, unclosed brackets).

## Verify
1. `./gradlew :chordpro:desktopTest :data:source:local:implementation:desktopTest`.
2. Write the crafted file: `printf '{title: Crafted}\n{c:%*sx\n[Am]la\n' 5000 '' > crafted.cho`. `./gradlew :app:desktop:run`,
   import it: the import finishes at once and the song shows its odd first line as lyrics. Before the fix the same
   import never finishes.
3. Open it in the editor and type: no stall; the first line is coloured, the crafted one is plain, `[Am]` is a chord.
4. Put the file into the library folder of the desktop build and restart: the whole list arrives.
5. Any ordinary song looks and highlights exactly as before (directives with and without values, `[*annotations]`,
   `[]`, a `{tag}` holding a brace).
6. The compile checks for Android, iOS and the web (common code only).

## Docs
`chordpro/CLAUDE.md`, the `ChordProSyntax` bullet: "the shared low-level rules (the directive and chord regexes, …"
becomes "the shared low-level rules (what a directive line and a `[…]` are — both found by a walk over the line and
not by a regular expression, since they run for every line on every keystroke and must stay linear whatever a file
holds — `chordNameRegex` for …". Nothing else.

## Touches
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProSyntax.kt`
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProParser.kt`
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProHighlighter.kt`
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposer.kt`
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProTabTransposer.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProSyntaxTest.kt` (new)
- `chordpro/CLAUDE.md`

## Depends on
37 and 38 (the same source files; nothing of their logic is needed, only their text to edit against). Plan 59 edits
how the editor *calls* the highlighter, not the highlighter, and is independent.
