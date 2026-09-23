# 05 — Chords written in a comment are never transposed or respelled

**Severity:** wrong chords (all platforms) · **Area:** `:chordpro` (`ChordProTransposer`), `:presentation`
(`SongLyrics.kt`)

**Default chosen by the orchestrator, not by the user** (the user may override it): chords inside comments are
transposed and respelled, in the viewer and in the editor's text transposition. The reviewer marked the intent as the
least certain part of this finding; the alternative is to leave comments alone and document that they are.

**Read, not run.** Found by reading the transposer and the viewer at HEAD (2065e47f). The unit tests reproduce it.

## What the user sees

A very common way of writing an intro or an outro:

```
{key: G}
{comment: Intro: [G] [Em] [C] [D]}
[G]Some lyrics [Em]here
```

Transposed +2, the lyrics show `A` / `F#m` but the comment still says `Intro: [G] [Em] [C] [D]` — the player is told
to play the intro in the old key. With German notation on, a `[B]` in a comment stays `B` while every `B` around it
is `H`. The editor's Transpose action leaves the comment's chords in the file as they were too. The comment is also
drawn in the plain comment colour with its brackets, so nothing tells the reader those are chords.

## Cause

The model keeps a comment as one string — `ChordProParser.kt:154-158` builds `ChordProBlock.Comment(text, style)` from
the directive's value — and every rewrite skips it: `ChordProTransposer.rewriteChords` (`:66-75`) only maps
`ChordProBlock.Section` blocks (after plan 01, `rewriteBlock`'s `else -> block`). The viewer draws the string as it is,
`presentation/.../songDetails/SongLyrics.kt:387-394`:

```kotlin
    val text = @Composable { boxModifier: Modifier ->
        Text(
            modifier = boxModifier,
            text = comment.text,
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
```

The text path rewrites only one kind of directive, the key — `ChordProTransposer.kt:247-249`:

```kotlin
                    (ChordProSyntax.standardMeta(directive) ?: directive).takeIf { it.name == KEY }?.value?.takeIf { it.isNotEmpty() }?.let { key ->
                        lines[index] = transposeKeyLine(rawLine, key, rename)
                    }
```

## The change

Invoke the **`code-style`** skill before the first edit. A bracket in a comment is read exactly as a bracket in a line
of lyrics: `rewriteLyricsLineChords` (`:334-356`) already renames every non-annotation bracket and leaves `[*…]` and
`[]` alone, and the renames themselves (`transposeChord`, `ChordProNotation.toGerman`/`fromGerman`) leave a
`[Chorus x2]` that is not a chord name as it is. So the same function serves both paths.

### 1. Model — `ChordProTransposer.rewriteBlock` (introduced by plan 01, in its plan 02 form)

```kotlin
        is ChordProBlock.ChorusRecall -> …
        // A comment is where an intro or an outro is written down as a row of chords, so its brackets are chords too.
        is ChordProBlock.Comment -> block.copy(text = rewriteLyricsLineChords(block.text, rewrite.rename))
        else -> block
```

That one branch covers the viewer's transposition, the accidentals preference, German notation (`toGerman`), the
parser's normalization (`fromGerman`, the Unicode accidentals, the lowercase minors) and, through `ChorusRecall.blocks`,
a comment inside a recalled chorus.

The chords of a comment are renamed but **do not vote**: `writtenChordNames` (`:78-88`) is left without them, so they
decide neither the German detection nor the sharps-or-flats spelling. The reason is parity with the library scan:
`ChordProParser.scan` skips every directive line, so a song whose only `H` is in a comment would otherwise be German
to the viewer and English to the song list, and the two would name its key differently.

### 2. Text — `ChordProTransposer.rewriteText` (`:233-250`)

Inside the `!hasSelectorSuffix` block, after the key handling:

```kotlin
                    if (directive.name in commentNames) {
                        // The same brackets the model renames in a comment; the directive's name has none to rename.
                        lines[index] = rewriteLyricsLineChords(rawLine, rename)
                    }
```

with

```kotlin
    /** The directives [ChordProParser] shows as a comment, whose brackets are renamed like a line of lyrics. */
    private val commentNames = setOf("comment", "c", "comment_italic", "ci", "comment_box", "cb", "highlight")
```

`rename` there is already `keepingLowercaseMinors(…)` and, in a German-notated file, the German round trip, so a
comment keeps the file's own spelling exactly as its lyrics do. The `#` source comments stay untouched (they are
filtered at `:232` before any of this).

### 3. Viewer — `SongLyrics.kt`, `SongComment` (`:377-409`)

Draw the chords of a comment in the chord colour and weight, so they read as chords. What counts as a chord comes from
`:chordpro`, the same tokens the editor colours with:

```kotlin
    val chordColor = MaterialTheme.colorScheme.primary
    val annotatedText = remember(comment.text, chordColor) {
        buildAnnotatedString {
            append(comment.text)
            // The brackets of a comment are chords to the transposition, so they are drawn as chords too.
            ChordProHighlighter.tokenize(comment.text)
                .filter { it.type == ChordProHighlighter.TokenType.CHORD }
                .forEach { addStyle(SpanStyle(color = chordColor, fontWeight = FontWeight.Bold), it.start, it.end) }
        }
    }
```

and pass `text = annotatedText` to the `Text`. New imports: `androidx.compose.ui.text.SpanStyle`,
`androidx.compose.ui.text.buildAnnotatedString`, `com.pandulapeter.campfire.chordpro.ChordProHighlighter`
(`FontWeight` is already imported at `:60`). Lyrics-only mode leaves comments as they are, chords included — a comment
is the author's note and is not dropped by that mode today either.

## Tests

`chordpro/src/commonTest`, `./gradlew :chordpro:desktopTest`, in `ChordProTransposerTest`:

- `the chords of a comment are transposed in the text` —
  `transposeText("{c: Intro: [G] [Em] [*softly] [Chorus x2]}\n[G]a", 2, preferFlats = false)` ==
  `"{c: Intro: [A] [F#m] [*softly] [Chorus x2]}\n[A]a"`; the same for `{ci: …}`, `{cb: …}`, `{highlight: …}`.
- `the chords of a comment are transposed on the model` — `transpose(parse(same), 2, false).blocks.first()` ==
  `ChordProBlock.Comment("Intro: [A] [F#m] [*softly] [Chorus x2]", CommentStyle.PLAIN)`.
- `the model and the text agree about a comment` — `transpose(parse(x), 2) == parse(transposeText(x, 2))` for the
  repro.
- `a comment's chords are respelled in German notation but do not make a song German` —
  `ChordProNotation.toGerman(parse("{c: [B]}\n[B]a"))` has the comment `"[H]"`; `parse("{c: [H]}\n[B]a")` stays
  English (the lyric chord is `B`, the comment keeps `[H]`, and `ChordProNotation.isGermanNotated` is false).
- The existing `transposing text leaves comments and annotations alone and updates the key` (`:330-366`) keeps
  passing (its comment holds no bracket); rename it to `…leaves source comments, prose comments and annotations alone…`.

Viewer: compile check on the four targets.

## Verification

1. Confirm first: the repro as a song; viewer +2. **Before:** the comment still reads `[G] [Em] [C] [D]`.
2. After: `Intro: [A] [F#m] [D] [E]`, the chord names in the chord colour and bold.
3. German notation on, with `[B]` in a comment: shown as `[H]`.
4. Editor → Transpose +2 → save: the comment line in the file reads the new chords; the viewer at 0 agrees with step 2.
5. `{c: Chorus x2}` and `{c: [*softly]}` are unchanged and uncoloured / in the chord colour respectively — check the
   annotation: `ChordProHighlighter` tokens `[*softly]` as `ANNOTATION`, which this leaves plain.

## Docs

- `chordpro/CLAUDE.md`, the `ChordProTransposer` bullet: after "and leaves annotations alone.", add: "The brackets of
  a comment (`{comment}`, `{ci}`, `{cb}`, `{highlight}`) are read as a line of lyrics and moved the same way on the
  model and in the text, since that is where an intro is written down as a row of chords; they are renamed but do not
  vote on the spelling or the notation, which the library scan could not see."
- `presentation/CLAUDE.md`, the `SongLyrics.kt` bullet: after "(sections, chorus recall, the three comment styles,": add
  a sentence "A comment draws the chords in its brackets in the chord colour, from `ChordProHighlighter`'s tokens."

## Files touched

- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposer.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposerTest.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongLyrics.kt`
- `chordpro/CLAUDE.md`, `presentation/CLAUDE.md`

## Depends on

**01** and **02** (lands after both): the `Comment` branch goes into `rewriteBlock`, which 01 introduces and 02 gives
its `ChordRewrite` parameter. Without them it is a `ChordProBlock.Comment` branch in HEAD's `rewriteChords` block map.
