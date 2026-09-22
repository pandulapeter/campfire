# 14 — Trim a bracket before deciding it is a chord

## What the user sees

In the song editor, an annotation written with a space inside its brackets — `[ *softly]`, `[  *N.C.]` — is
coloured as a chord. The editor says it is a chord; the viewer, which the editor is a view onto, draws it as an
annotation in the lyrics. The same two characters mean two different things one pane apart, and the user's only
way to find out which is right is to leave the editor.

`chordpro/CLAUDE.md` states the point of putting the highlighter in this module at all:

> - `ChordProHighlighter` — the typed spans an editor wants to colour (directive name, directive value, chord,
>   annotation, comment). It lives here rather than in the UI so that what counts as a chord is decided in exactly one
>   place

Two places decide it, and they disagree by a `trim()`.

Verified at HEAD:

```
ChordProHighlighter.tokenize("[ *note] la")  ->  [Token(type=CHORD, start=0, end=8)]
ChordProParser.parse("[ *note] la")          ->  Chord(position=0, name=note, isAnnotation=true)
```

## Cause

`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProHighlighter.kt:70-76`, verified at
HEAD `984861e4`:

```kotlin
                !isVerbatim -> ChordProSyntax.brackets(line).forEach { bracket ->
                    tokens += Token(
                        type = if (bracket.content.startsWith(ANNOTATION_PREFIX)) TokenType.ANNOTATION else TokenType.CHORD,
                        start = lineStart + bracket.range.first,
                        end = lineStart + bracket.range.last + 1,
                    )
                }
```

`bracket.content` is the **untrimmed** content — `ChordProSyntax.Bracket` is documented as "One closed bracket
pair and its untrimmed content" (`ChordProSyntax.kt:507-508`) — so a leading space makes `startsWith("*")` false.

Both other readers of a bracket trim first.

`ChordProParser.parseLyrics`, `:208-218`:

```kotlin
        ChordProSyntax.brackets(rawLine).forEach { bracket ->
            text.append(rawLine, consumedUntil, bracket.range.first)
            val content = bracket.content.trim()
            if (content.isNotEmpty()) {
                val isAnnotation = content.startsWith(ANNOTATION_MARKER)
```

`ChordProTransposer.rewriteLyricsLineChords`, `:330-333`:

```kotlin
            brackets.forEach { bracket ->
                val content = bracket.content.trim()
                val isChord = content.isNotEmpty() && !content.startsWith(ANNOTATION_MARKER)
```

Note the second half of both: an **empty** bracket is a chord to the highlighter and to neither of the others.
`tokenize("[] [ ] x")` returns two `CHORD` tokens, while `parse("[] [ ] x")` returns a `Lyrics` line with no
chords at all (verified).

## The change

One expression, and the empty case with it, since it is the same `if` and the same disagreement.

`ChordProHighlighter.kt:70-76`:

```kotlin
                !isVerbatim -> ChordProSyntax.brackets(line).forEach { bracket ->
                    // Trimmed, and empty brackets left out, because that is how the parser and the transposition read
                    // a bracket: a `[ *softly]` is the annotation the viewer will draw in the lyrics, and a `[]` is
                    // not a chord to anything downstream. What counts as a chord is decided in one place or in none.
                    val content = bracket.content.trim()
                    if (content.isNotEmpty()) {
                        tokens += Token(
                            type = if (content.startsWith(ANNOTATION_PREFIX)) TokenType.ANNOTATION else TokenType.CHORD,
                            start = lineStart + bracket.range.first,
                            end = lineStart + bracket.range.last + 1,
                        )
                    }
                }
```

The token's `start` and `end` stay the **whole** bracket, braces included — the highlighter colours the brackets
along with their content everywhere else (see the directive tokens at `:90-109`, which take in both braces), and a
half-coloured bracket would look like an unfinished one under the caret.

If dropping the empty-bracket token turns out to be unwanted — an editor may well want to show the user that the
`[]` they just typed is a bracket — keep it and colour it `CHORD` as today, but **write the reason down** next to
it, because it is then a deliberate divergence from the parser rather than an oversight. The recommendation is to
drop it: nothing downstream makes anything of an empty bracket, and the editor's own caret is what tells the user
where they are.

## Tests

`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProHighlighterTest.kt`, next to
`chords and annotations are told apart` (line 75):

1. `an annotation is an annotation whatever the spaces inside its brackets` —
   `spans("[Am]word [ *softly] more [*a ] end")` is
   `CHORD to "[Am]"`, `ANNOTATION to "[ *softly]"`, `ANNOTATION to "[*a ]"`.
2. `an empty bracket is not a chord` — `spans("[] [ ] [Am]")` is `CHORD to "[Am]"` and nothing else.
3. `a chord with spaces inside its brackets is still a chord` — `spans("[ Am ]word")` is `CHORD to "[ Am ]"`, so
   the trim does not start swallowing the real case.
4. `the highlighter and the parser agree about every bracket of a line` — the test that states the rule rather
   than the instance, and the one worth keeping: for a handful of lines (`"[Am]a [ *x]b [] c [ G/B ]d"` among
   them), compare the highlighter's `CHORD`/`ANNOTATION` tokens against
   `ChordProParser.parse(line)`'s `ChordProLine.Lyrics.chords` — same count, same order, same
   annotation/chord split. That is the invariant `chordpro/CLAUDE.md` claims, asserted.

## Verification

```
./gradlew :chordpro:desktopTest
```

Manual (`./gradlew :app:desktop:run`): open a song in the editor, type `[ *softly]` and `[]` on a line of lyrics,
and confirm the first is coloured as the annotation the viewer draws and the second is not coloured as a chord.
Switch to the viewer and back to check the two panes now say the same thing.

## Docs

`chordpro/CLAUDE.md`, the `ChordProHighlighter` bullet (lines 151-153) — the claim quoted at the top of this plan
becomes true rather than aspirational, so it needs no rewording. Add the rule it now keeps, in one clause: a
bracket is read trimmed, the way the parser reads it, and an empty one is not a chord.

The `TokenType.ANNOTATION` KDoc (`ChordProHighlighter.kt:29-31`) says:

> /** `[*text]`, which the viewer shows in the lyrics rather than as a chord. */

Widen it to `[*text]`, spaces inside the brackets allowed.

## Files touched

- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProHighlighter.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProHighlighterTest.kt`
- `chordpro/CLAUDE.md`

## Depends on

Nothing. Plan 10 rewrites the surrounding loop in the same function; land 10 first and this becomes a three-line
edit inside it.

## Rules

- Load the `code-style` skill before the first edit.
- `commonMain` stays JVM-free; `:chordpro` has no dependencies at all.
- A change to the dialect belongs in a test first.
- The per-module `CLAUDE.md` is part of the change.
