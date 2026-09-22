# 10 — Keep a CR-only file's line endings, and highlight it

## What the user sees

A song file whose lines end with a bare CR — a classic Mac OS export, and what some old song-book programs still
write — is two things at once in Campfire:

**It is one line to the editor.** The song editor colours nothing: no directive name, no directive value, no
comment. Every `[…]` in the whole file is coloured as a chord, including the ones inside a `{start_of_tab}`
environment, because the environment tracking never sees the `{start_of_tab}` line. Verified at HEAD: tokenizing
`"{title: A}\r{c: x}\r[C]hello\r"` returns exactly one token, `CHORD` at 18..21.

**Every edit rewrites the whole file.** Tapping a tag chip, changing the song's languages, transposing in the
editor or inserting a `{title}` from the toolbar re-joins the lines with `\n`, so a file that arrived with 200 CR
endings comes back with 200 LF endings. Verified at HEAD:

```
transposeText("{title: A}\r{c: x}\r[C]hello\r", 2)  ->  "{title: A}\n{c: x}\n[D]hello\n"
addTag("{title: A}\r{c: x}\r[C]hello\r", "Demo")    ->  "{title: A}\n{tag: Demo}\n{c: x}\n[C]hello\n"
```

The song is intact, but every byte of it changed, so sync uploads the whole file, an import of the old copy now
reads as a different file to compare against, and a user who keeps the library folder under version control sees a
whole-file diff for a tag they put on.

The module's own promise is the opposite of this. `chordpro/CLAUDE.md`, on the text edits:

> `addTag` and `removeTag` edit the text rather than the model, for the same
> reason `ChordProTransposer.transposeText` does — the result is written straight back to the user's file, so their
> own formatting has to survive a chip being tapped in the viewer.

## Cause

Two separate places, one per half.

### Half A — `lineSeparatorOf` knows two of the three endings

`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProSyntax.kt:92-97`, verified at HEAD
`984861e4`:

```kotlin
    /**
     * The line separator [text] is written with: CRLF where any line of it ends that way, LF otherwise. It is one
     * answer for the whole file, so a file mixing CR-only or LF endings with CRLF ones comes out of an edit written with
     * CRLF throughout, which is the separator such a file was most likely meant to have.
     */
    fun lineSeparatorOf(text: String) = if (text.contains("\r\n")) "\r\n" else "\n"
```

A file with no CRLF anywhere falls to `"\n"`, whether or not it has a single `\n` in it. Everything that writes
text back goes through this one function, which is why the bug is everywhere at once:

- `joinLines` (`:135-139`), used by `ChordProTags.addTag`/`removeTag` (`ChordProTags.kt:31`, `:42`),
  `ChordProLanguages.setLanguages` (`ChordProLanguages.kt:48`) and `ChordProTransposer.rewriteText`
  (`ChordProTransposer.kt:250`).
- `ChordProHeader.insert` (`ChordProHeader.kt:58`), which writes the line break of a directive the editor's
  toolbar adds.

The rest of the module already handles a lone CR correctly, which is what makes this a one-line fix rather than a
rewrite: `splitLines` (`:86-90`) does `.replace('\r', '\n')`, `endsWithLineBreak` (`:128`) tests both, and
`lineStartOffsets` (`:104-125`) has an explicit `'\r' ->` branch.

### Half B — the highlighter splits on `\n` alone

`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProHighlighter.kt:48-56`:

```kotlin
        var lineStart = 0
        while (lineStart <= text.length) {
            val lineBreak = text.indexOf('\n', lineStart)
            val lineEnd = if (lineBreak == -1) text.length else lineBreak
            val line = text.substring(lineStart, lineEnd)
            val trimmed = line.trim()
            val trimmedDirectiveLine = trimmed.removeSuffix("\r")
            val directive = ChordProSyntax.matchDirective(trimmedDirectiveLine)
```

`indexOf('\n', …)` finds nothing in a CR-only file, so `line` is the whole document, `matchDirective` refuses it
(it is not one pair of braces), and the `!isVerbatim` branch runs `ChordProSyntax.brackets` over everything. The
highlighter is the one object in the module that does its own line walking rather than calling
`ChordProSyntax.splitLines`.

(`trimmedDirectiveLine` is already dead: `line.trim()` uses `Char::isWhitespace`, which strips a trailing `\r`, so
`removeSuffix("\r")` never has anything to remove. It goes away with the fix.)

## The change

### 1. `lineSeparatorOf` answers CR too

`ChordProSyntax.kt:92-97`:

```kotlin
    /**
     * The line separator [text] is written with: CRLF where any line of it ends that way, a bare CR where the file
     * uses those and no CRLF, LF otherwise. It is one answer for the whole file, so a file mixing its endings comes
     * out of an edit written with the one that wins, which is the separator such a file was most likely meant to
     * have. The CR-only case is an old Mac export, and it is answered so that an edit of one line leaves every
     * other byte of such a file alone, the way it does for the other two.
     */
    fun lineSeparatorOf(text: String) = when {
        text.contains("\r\n") -> "\r\n"
        text.contains('\r') -> "\r"
        else -> "\n"
    }
```

The order matters and is the existing documented rule: CRLF wins wherever any line ends that way, so a file mixing
CRLF and CR still comes out CRLF and the existing test
`a caret at the start of a line keeps it where joinLines unified the line endings`
(`ChordProTransposerTest.kt:459`, which mixes LF and CRLF) stays green.

Nothing else in `ChordProSyntax` changes. Check the two callers while implementing:

- `joinLines` (`:135-139`) — correct as written once the separator is right.
- `ChordProHeader.insert` (`ChordProHeader.kt:55-70`) — it computes `offset` from `lineStartOffsets`, which already
  understands a lone CR, and then writes `separator`. Correct once the separator is right.

`ChordProTransposer.transposedOffset` (`:155-173`) maps a caret across the rewrite and already measures the break
on the *new* text (`:167-170`, "The break is measured on the new text, since joinLines may have given a file that
mixed its endings a different separator"). A CR-only file now keeps its endings, so that path simply stops firing
for it — but add the caret test below, because it is the path this used to go down.

`ChordProSplitter.comparable` (`ChordProSplitter.kt:36-39`) deliberately joins with `"\n"` and **must not change**:
it is a fold, not an edit, and its whole job is that "the same song, tagged in the app or written by hand, is not a
conflict".

### 2. The highlighter walks lines the way the rest of the module does

Replace the hand-rolled `indexOf('\n')` loop with the two functions that already exist and are already tested.
**Recommended over teaching the loop about `\r`**: it deletes the second line-walking implementation in the module
rather than fixing it, which is the same reason `ChordProSyntax` exists.

`ChordProHighlighter.tokenize`, `:44-82`:

```kotlin
    fun tokenize(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        // Chords are not chords inside a tab or an environment handed to another program: the brackets there are part
        // of the tablature or of the notation, and the viewer leaves them alone too.
        var isVerbatim = false
        // The lines and their offsets come from ChordProSyntax rather than from a walk of their own, so that a file
        // written with any of the three line endings is highlighted the way it is parsed.
        val lines = ChordProSyntax.splitLines(text)
        val lineStarts = ChordProSyntax.lineStartOffsets(text)
        lines.forEachIndexed { index, line ->
            val lineStart = lineStarts[index]
            val trimmed = line.trim()
            val directive = ChordProSyntax.matchDirective(trimmed)
            when {
                trimmed.startsWith(SOURCE_COMMENT) -> tokens += Token(TokenType.COMMENT, lineStart, lineStart + line.length)
                directive != null -> { … }
                !isVerbatim -> ChordProSyntax.brackets(line).forEach { bracket -> … }
            }
        }
        return tokens
    }
```

and `directive.tokens(line = line, lineStart = lineStart, valueStart = ChordProSyntax.directiveValueStart(trimmed))`
— `trimmedDirectiveLine` and its `removeSuffix("\r")` are deleted.

`lineStartOffsets` is `internal` in the same module, so it is reachable. Two invariants to check when
implementing, both of which hold at HEAD and are worth a test each:

- `splitLines(text).size == lineStartOffsets(text).size` for every input, including `""`, `"\n"`, `"a\r\n"` and
  `"a\rb"` — both drop the phantom line after a trailing break. Assert it in `ChordProSyntaxTest`.
- `splitLines` normalizes CRLF to LF, so `line` no longer carries a trailing `\r`; every offset is still counted
  on the original text by `lineStartOffsets`, so `lineStart + line.length` is the end of the line's *content*,
  which is exactly what the old `lineEnd` was. The existing test
  `windows line endings do not shift the offsets` (`ChordProHighlighterTest.kt:113`) is the net under that.

## Tests

`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProSyntaxTest.kt`:

1. `the line separator of a file is the one it is written with` — `lineSeparatorOf` over `"a\rb"` is `"\r"`,
   over `"a\nb"` is `"\n"`, over `"a\r\nb"` is `"\r\n"`, and over a file mixing CR and CRLF is `"\r\n"`.
2. `every line starts where the offsets say it does` — for `""`, `"a"`, `"a\n"`, `"a\r"`, `"a\r\n"`, `"a\rb\nc"`,
   assert `splitLines(it).size == lineStartOffsets(it).size` and that each line equals the text from its start to
   the next start minus the break.

`ChordProTagsTest.kt`, next to `a new tag is written with the line endings of the file and keeps its trailing line
break` (line 111) and `removing a tag keeps the line endings of the file` (line 116):

3. `a new tag is written with a CR-only file's line endings` —
   `addTag("{title: A}\r[C]la\r", "Demo")` is `"{title: A}\r{tag: Demo}\r[C]la\r"`.
4. `removing a tag keeps a CR-only file's line endings` — the mirror.

`ChordProLanguagesTest.kt`:

5. `setting the languages of a CR-only file keeps its line endings`.

`ChordProTransposerTest.kt`, next to `transposing text keeps the line endings of the file` (line 388):

6. `transposing text keeps a CR-only file's line endings` —
   `transposeText("[C]la\r[G]lo\r", 1, preferFlats = false)` is `"[C#]la\r[G#]lo\r"`.
7. `a caret at the end of a CR line stays at the end of that line` — the mirror of
   `a caret at the end of a CRLF line stays at the end of that line` (line 451), using the `transposed` helper.

`ChordProHeaderTest.kt`:

8. `a directive added to a CR-only file is written with its line ending`.

`ChordProHighlighterTest.kt`, next to `windows line endings do not shift the offsets` (line 113):

9. `old Mac line endings do not hide the directives` — over `"{title: A}\r{c: x}\r[C]hello"`, assert the same
   span list the LF spelling produces: `{title:` / ` A` / `}` / `{c:` / ` x` / `}` / `[C]`.
10. `brackets inside a tab of a CR-only file are left alone` — the CR spelling of
    `brackets inside a tab are left alone` (line 88). This is the one that proves the environment tracking runs.
11. `a file mixing its line endings is highlighted line by line` — `"{title: A}\r\n{c: x}\r[C]la\nlo"`.

## Verification

```
./gradlew :chordpro:desktopTest
./gradlew :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest
```

Manual (`./gradlew :app:desktop:run`), needs a CR-only file, which is one shell command to make:

```
printf '{title: Old Mac}\r{artist: Somebody}\r{start_of_tab}\re|--0--2--|\r{end_of_tab}\r[C]la [G]lo\r' > /tmp/oldmac.cho
```

1. Import it. Open it in the editor: the directives are coloured, the `#` line (add one) is a comment, and the
   bracket inside the tab is not coloured as a chord.
2. Put a tag on it from the viewer, then compare the file byte for byte (`xxd`): only the inserted line is new,
   and it ends with a `\r`.
3. Transpose it in the editor and save: same check.

## Docs

`chordpro/CLAUDE.md`, the `ChordProTags` bullet (lines 71-74) — this sentence names two endings and has to name
three:

> What makes "every other byte" true for all of these
> text edits (tags, languages, the transposition) is `ChordProSyntax.joinLines`: the line separator is detected per
> file, CRLF where any line ends that way and LF otherwise, and a trailing line break is put back where the file had
> one; `ChordProHeader.insert` writes its line break with the same separator. A file mixing its endings comes out with the one separator that picked.

Rewrite the middle clause: CRLF where any line ends that way, a bare CR where the file uses those and no CRLF, LF
otherwise.

`chordpro/CLAUDE.md`, the `ChordProHighlighter` bullet (lines 151-153) — add that it reads the file's lines through
`ChordProSyntax`, so it agrees with the parser about where a line ends whichever of the three endings the file
uses.

The `ChordProSyntax` KDoc at `:92-97` is quoted above and is rewritten as part of the change.

## Files touched

- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProSyntax.kt`
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProHighlighter.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProSyntaxTest.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProTagsTest.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProLanguagesTest.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposerTest.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProHeaderTest.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProHighlighterTest.kt`
- `chordpro/CLAUDE.md`

## Depends on

Nothing. It touches `ChordProHighlighter.tokenize` in the same place plan 14 does — land one and rebase the other;
14 is three characters, so land this first.

## Rules

- Load the `code-style` skill before the first edit.
- `commonMain` stays JVM-free; `:chordpro` has no dependencies at all.
- A change to the dialect belongs in a test first.
- The per-module `CLAUDE.md` is part of the change.
