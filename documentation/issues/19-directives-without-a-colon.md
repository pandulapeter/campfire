# 19 · Directives written with a space instead of a colon, the spec's own examples included, show up as lyrics

**Severity:** wrong behaviour (all platforms. Likely for files from other ChordPro tools and the spec's own examples: `{title Wonderwall}`, `{start_of_verse Verse 1}`, `{sov label="Verse 1"}`, `{start_of_grid shape="1+4x2+4"}`; a single such header line titles the song by its file name) · **Area:** `:chordpro` (`ChordProSyntax.matchDirective`, `ChordProSyntax.label`, `ChordProHighlighter`)

## Symptom
1. Import a file whose header is `{title Wonderwall}` / `{artist Oasis}` and whose verse opens with
   `{start_of_verse Verse 1}`.
2. The song list titles it by its file name with no artist, and the song screen draws `{title Wonderwall}`,
   `{artist Oasis}` and `{start_of_verse Verse 1}` verbatim as lyric lines, braces included. The verse is a plain
   paragraph with no label; a `{start_of_grid shape="1+4x2+4"}` never opens a grid, so its rows are lyrics.
3. The tag and language chips do not see `{tag Needs study}` / `{meta language en}`; the editor's toolbar keeps
   offering Title and Artist.

Related: `{sov: label='Verse 1'}` (single quotes, which the spec allows) is labelled `label='Verse 1'`, and
`{start_of_grid: shape="1+4x2+4"}` is labelled `shape="1+4x2+4"`.

## Cause
The spec (chordpro.org, *Directives*, "Arguments"): "the arguments are separated from the directive name by a colon
`:` and/or whitespace"; attributes may use single or double quotes. `matchDirective` accepts only a colon
(`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProSyntax.kt:140-145`):

```kotlin
while (index < closeIndex && trimmedLine[index].isWhitespace()) index++
return when {
    index == closeIndex -> Directive(name, null)
    trimmedLine[index] == DIRECTIVE_VALUE_SEPARATOR -> Directive(name, trimmedLine.substring(index + 1, closeIndex).trim())
    else -> null
}
```

Every caller (parser, `scan`, transposer, tag and language editors, header inserter, splitter, highlighter) goes
through it, so all of them miss the form. `label` (`:303-308`) matches only `label="…"` (`labelAttributeRegex`,
`:22`) and otherwise returns the raw value, attributes included.

The highlighter splits name from value at the first `:` of the line (`ChordProHighlighter.kt:89-93`), so it would
colour a whitespace-form directive as all name, and a value that contains a colon at the wrong place.

## Fix
Accept whitespace as the separator, but only after a name the app knows. A line in braces such as `{Verse 2}` or
`{Refrén 2x}` is lyrics today and must stay lyrics: unknown directives are dropped by the parser, so reading those
as directives would make the user's text vanish from the viewer.

1. `ChordProSyntax.kt`: replace `matchDirective` (`:130-146`) with a walk shared with the highlighter:

   ```kotlin
   /** Matches a directive in one linear walk, which keeps malformed input cheap while the user types. */
   fun matchDirective(trimmedLine: String): Directive? {
       val (name, valueStart) = walkDirective(trimmedLine) ?: return null
       return Directive(name, if (valueStart < 0) null else trimmedLine.substring(valueStart, trimmedLine.length - 1).trim())
   }

   /**
    * Where the value of the directive on [trimmedLine] starts: right after its colon, or at the first character after
    * the whitespace that separates it from a name written without one. Null for a line that is not a directive and
    * for one with no value. It is what splits a directive into its name and its value for the highlighter, which
    * cannot look for the colon, since a directive need not have one and a value may.
    */
   fun directiveValueStart(trimmedLine: String) = walkDirective(trimmedLine)?.second?.takeIf { it >= 0 }

   /**
    * The lowercase name of the directive on [trimmedLine] and the index its value starts at, -1 where it has none.
    *
    * ChordPro separates a value from the name with a colon "and/or whitespace", and the spec's own examples use the
    * second (`{start_of_verse Verse 1}`). Whitespace alone is only taken for a name the app knows, though: a line in
    * braces such as `{Verse 2}` has always been shown as the lyrics it is, and an unknown directive is dropped, so
    * reading it as one would take the user's text off the screen.
    */
   private fun walkDirective(trimmedLine: String): Pair<String, Int>? {
       val closeIndex = trimmedLine.length - 1
       if (closeIndex < 1 || trimmedLine[0] != DIRECTIVE_OPEN || trimmedLine[closeIndex] != DIRECTIVE_CLOSE) return null
       var index = 1
       while (index < closeIndex && trimmedLine[index].isWhitespace()) index++
       val nameStartIndex = index
       while (index < closeIndex && trimmedLine[index].isDirectiveNameCharacter) index++
       if (index == nameStartIndex) return null
       val nameEndIndex = index
       val name = trimmedLine.substring(nameStartIndex, nameEndIndex).lowercase()
       while (index < closeIndex && trimmedLine[index].isWhitespace()) index++
       return when {
           index == closeIndex -> name to -1
           trimmedLine[index] == DIRECTIVE_VALUE_SEPARATOR -> name to index + 1
           index > nameEndIndex && isKnownName(name) -> name to index
           else -> null
       }
   }

   /** Whether [name] is a directive ChordPro defines, a custom `x_` one or one of those with a selector suffix. */
   private fun isKnownName(name: String) = name in knownNames || startOfEnvironment(name) != null ||
           endOfEnvironment(name) != null || name.startsWith(CUSTOM_PREFIX) || hasSelectorSuffix(name)
   ```

   Add `private const val CUSTOM_PREFIX = "x_"` next to the other constants.

2. Extend `knownNames` (`:67-72`) with the spec names it lacks, so that a whitespace-form formatting directive is
   dropped like its colon form instead of drawn as lyrics (and so that their selector forms are recognised too):
   `"sorttitle", "arranger", "copyright", "grid", "g", "no_grid", "ng", "diagrams", "textfont", "tf", "textsize",
   "ts", "textcolour", "chordfont", "cf", "chordsize", "cs", "chordcolour", "tabfont", "tabsize", "tabcolour",
   "gridfont", "gridsize", "gridcolour", "titlefont", "titlesize", "titlecolour"`. Update its KDoc to "Every
   directive name ChordPro defines, used to detect (and drop) selector suffixes such as `title-guitar` and to accept a
   value separated from the name by whitespace alone."

3. Replace `labelAttributeRegex` (`:22`) and `label` (`:303-308`):

   ```kotlin
   private val labelAttributeRegex = Regex("(?:^|\\s)label\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')")
   private val attributesRegex = Regex("(?:\\s*[A-Za-z_][A-Za-z0-9_-]*\\s*=\\s*(?:\"[^\"]*\"|'[^']*'))+\\s*")
   ```

   ```kotlin
   /**
    * The label an environment directive gives its section: the whole value (`{sov: Verse 1}`), or its `label`
    * attribute where the value is written as attributes (`{sov label="Verse 1"}`, in either quotes). A value made of
    * attributes that has no label (`{start_of_grid shape="1+4x2+4"}`) gives none, rather than showing the attributes
    * as a heading. An empty label is null.
    */
   fun label(value: String?): String? {
       val trimmedValue = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
       if (!attributesRegex.matches(trimmedValue)) return trimmedValue
       val match = labelAttributeRegex.find(trimmedValue) ?: return null
       return match.groupValues[1].ifEmpty { match.groupValues[2] }.takeIf { it.isNotEmpty() }
   }
   ```

   `groupValues` gives `""` for the alternative that did not match on every platform (JS regexes report it as
   `undefined`, which `groups[i]` would turn into null only on some targets).

4. `ChordProHighlighter.kt`: pass the value start into `tokens` and drop the colon search.
   - In `tokenize` (`:55-63`):
     ```kotlin
     val trimmedDirectiveLine = trimmed.removeSuffix("\r")
     val directive = ChordProSyntax.matchDirective(trimmedDirectiveLine)
     ...
     tokens += directive.tokens(line = line, lineStart = lineStart, valueStart = ChordProSyntax.directiveValueStart(trimmedDirectiveLine))
     ```
   - `tokens` (`:85-104`) becomes:
     ```kotlin
     private fun ChordProSyntax.Directive.tokens(line: String, lineStart: Int, valueStart: Int?): List<Token> {
         val open = line.indexOf('{')
         val close = line.lastIndexOf('}')
         if (open == -1 || close <= open) return emptyList()
         // The trimmed line the value start was counted on begins at the opening brace.
         val nameEnd = if (valueStart != null && !value.isNullOrEmpty()) open + valueStart else close + 1
         ...
     ```
     with `hasValue` replaced by `nameEnd != close + 1` in the rest of the function (or keep a local
     `val hasValue = valueStart != null && !value.isNullOrEmpty()`). Update the KDoc's first sentence to "The name
     half runs from the opening brace to the colon, or to the whitespace after the name where the directive has no
     colon (or to the closing brace when it has no value), …".

Nothing else changes: the parser, `scan`, the transposer, `ChordProTags`, `ChordProLanguages`, `ChordProHeader` and
the splitter all read directives through `matchDirective`.

## Tests
`chordpro/src/commonTest/.../ChordProSyntaxTest.kt`:
- In `a line that only looks like a directive is content`, remove `"{title x}"` from the list and add `"{Verse 2}"`,
  `"{verse 2}"`, `"{Refrén 2x}"`.
- New `a value may be separated from a known name by whitespace alone`:
  `Directive("title", "Wonderwall")` for `{title Wonderwall}`, `Directive("start_of_verse", "Verse 1")` for
  `{start_of_verse Verse 1}`, `Directive("meta", "language en")` for `{meta language en}`,
  `Directive("x_note", "hi")` for `{x_note hi}`, `Directive("title", "a: b")` for `{title a: b}` (the walk reaches
  `a` after the whitespace, so the colon later in the value is not the separator).
- New `the value start is after the colon or after the whitespace`: `directiveValueStart("{title: X}") == 7`,
  `directiveValueStart("{title X}") == 7`, `directiveValueStart("{soc}") == null`,
  `directiveValueStart("{Verse 2}") == null`.
- New `a label is the value or its label attribute in either quotes`: `label("Verse 1") == "Verse 1"`,
  `label("label=\"Verse 1\"") == "Verse 1"`, `label("label='Verse 1'") == "Verse 1"`,
  `label("shape=\"1+4x2+4\" label=\"Solo\"") == "Solo"`, `label("shape=\"1+4x2+4\"") == null`,
  `label("  ") == null`, `label("label=\"\"") == null`.
- Add `ChordProSyntax.matchDirective("{title" + " ".repeat(4_000) + "x")` to `crafted lines cost no more than
  their length` (not closed → null) and `assertLinear { ChordProSyntax.label("a=\"b\" ".repeat(20_000) + "c") }`.

`ChordProParserTest.kt`, new `directives written with whitespace instead of a colon are read`:
```kotlin
val song = ChordProParser.parse("{title Wonderwall}\n{artist Oasis}\n{tag Needs study}\n{meta language en}\n\n{start_of_verse Verse 1}\n[Am]a\n{end_of_verse}\n\n{start_of_grid shape=\"1+4x2+4\"}\n| C . |\n{end_of_grid}")
```
assert title `Wonderwall`, artist `Oasis`, tags `["Needs study"]`, languages `["en"]`, block 0 is
`Section(Verse, "Verse 1", [parseLyrics("[Am]a")])`, block 1 is a `Paragraph` section with a `null` label whose
only line is a `ChordProLine.Grid`. And `{sov label='Verse 1'}` parses like `{sov: Verse 1}`; `{Verse 2}` stays a
lyrics line `Lyrics("{Verse 2}", [])`.

`ChordProHighlighterTest.kt`, new `a directive with no colon is split after its name`:
`spans("{title Song}") == [DIRECTIVE_NAME to "{title ", DIRECTIVE_VALUE to "Song", DIRECTIVE_NAME to "}"]`, and
`spans("{title: a: b}")` still splits at the first colon (`"{title:"`, `" a: b"`, `"}"`).

`ChordProTagsTest.kt`: `removeTag("{tag Needs study}\n[C]a", "needs study") == "[C]a"`.

Run `./gradlew :chordpro:desktopTest`.

## Verify
- Import a file with `{title Wonderwall}` / `{artist Oasis}` / `{start_of_verse Verse 1}`: the list shows
  "Wonderwall" by Oasis, the file is stored as `oasis-wonderwall.cho`, and the song screen shows a "Verse 1" section.
- In the editor, type `{title Test}`: it is coloured as a directive name and a value, and the preview's title follows.
- A line `{Verse 2}` in a song still shows as a lyric line.
- Compile `:chordpro` for all targets (`./gradlew :chordpro:desktopTest :chordpro:compileKotlinWasmJs :chordpro:compileKotlinIosSimulatorArm64`).

## Docs
- `chordpro/CLAUDE.md`, the `ChordProSyntax` bullet: after "long/short directive names," add "a value separated from
  a known directive name by a colon or by whitespace alone (the spec allows both, and a line in braces whose name
  the app does not know stays the lyrics it has always been shown as),"; change "`label="…"` attributes" to
  "`label` attributes in either quotes, and no label at all for a value made of other attributes".
- `documentation/file-format.md`, the **Environments** bullet: change "an optional label (`{sov: Verse 1}` or
  `{sov: label="Verse 1"}`)" to "an optional label (`{sov: Verse 1}`, `{sov Verse 1}` or `{sov: label="Verse 1"}`)".
  After the list add: "A value may follow the directive name after a colon or, as the spec allows, after whitespace
  alone (`{title Wonderwall}`)."

## Touches
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProSyntax.kt`
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProHighlighter.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProSyntaxTest.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProParserTest.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProHighlighterTest.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProTagsTest.kt`
- `chordpro/CLAUDE.md`, `documentation/file-format.md`

## Depends on
None. 21, 27 build on `walkDirective` / `isKnownName`; run 19 first. 24 and 21 edit the transposer's key line,
22 the grid parsing in the same file: run them one after another.
