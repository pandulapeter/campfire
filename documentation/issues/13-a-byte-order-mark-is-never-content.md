# 13 — A byte order mark is never content

## What the user sees

A file holding several songs — the shape Campfire itself exports and the shape somebody gets by concatenating
their `.cho` files — imports one song short of itself. Every song after the first loses its `{title}` line into its
own lyrics, so it is filed under the name of the file it arrived in (or, in a collection, under nothing at all),
and the title line shows up as the first line of the song's text.

The trigger is a byte order mark in the middle of the file. It gets there on its own: Windows editors prefix a
UTF-8 file with one, and `copy a.cho + b.cho c.cho`, `cat`, or any tool that joins files leaves the second file's
mark sitting where the join happened. Verified at HEAD:

```
ChordProSplitter.split("{title: A}\nla la\n{new_song}\n﻿{title: B}\nlo lo\n")
  ->  ["{title: A}\nla la", "﻿{title: B}\nlo lo"]

ChordProParser.parseMetadata("﻿{title: B}\nlo lo").title  ->  null
```

So the second song's name comes out of `importFileName`'s fallback instead of its own header, which is the one
invariant the root `CLAUDE.md` states about naming: "a file name is reproducible from its header alone".

Two songs that are otherwise identical, one with a mark and one without, also compare as different songs to the
import, so importing an export of a library that went through such a tool writes a `_2` copy of every song after
the first instead of recognising them.

## Cause

U+FEFF is not whitespace. `Char.isWhitespace` is `Character.isWhitespace || Character.isSpaceChar` on the JVM, and
U+FEFF is general category `Cf` (format), so both are false — verified. `String.trim()` therefore keeps it, and
every directive match in the module starts with a trimmed line.

`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProSyntax.kt:163-165`, verified at HEAD
`984861e4`:

```kotlin
    private fun walkDirective(trimmedLine: String): Pair<String, Int>? {
        val closeIndex = trimmedLine.length - 1
        if (closeIndex < 1 || trimmedLine[0] != DIRECTIVE_OPEN || trimmedLine[closeIndex] != DIRECTIVE_CLOSE) return null
```

`trimmedLine[0]` is the mark, not the `{`, so the line is not a directive and `ChordProParser` reads it as lyrics.

Neither of the two places that could have removed it does.

`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProSplitter.kt:17-39` removes **nothing**:

```kotlin
    fun split(text: String): List<String> {
        val parts = mutableListOf<MutableList<String>>(mutableListOf())
        ChordProSyntax.splitLines(text).forEach { rawLine ->
            …
        return parts.map { part -> part.dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }.joinToString("\n") }
            .filter { it.isNotBlank() }
    }
```

(`isBlank()` is `Char::isWhitespace` too, so a line holding only a mark is not even dropped as blank.)

`data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/LibraryText.kt:24-35` removes only
the leading ones:

```kotlin
    }.trimStart(BYTE_ORDER_MARK)
```

and its KDoc says so deliberately:

> Byte order marks at the start are stripped - a file that went through two tools that each added one has two -
> because editors on Windows like to prefix UTF-8 files with one and it is not part of the content.

That decision has a test defending it —
`data/source/local/implementation/src/commonTest/.../LibraryTextDecodingTest.kt:39`:

```kotlin
        assertEquals("{title: A}\n﻿x", "{title: A}\n﻿x".encodeToByteArray().decodeLibraryText())
```

## The change

Fix it in `:chordpro`, not in `decodeLibraryText`. **This is the recommendation and the reason matters**:
`decodeLibraryText` turns bytes into text and its job is to be faithful about everything but the encoding's own
preamble — the test above is a deliberate statement of that, and widening it would make the decoder quietly edit
content for every caller, including the setlist JSON path. `ChordProSplitter` is where a concatenation is taken
apart, so it is where the seam a concatenation leaves belongs. And every song on its way into the library goes
through it: `PrepareImportUseCaseImpl.kt:99` calls `ChordProSplitter.split` on **every** incoming song file,
whether or not it holds a `{new_song}`, so a single-song file with a mark in it is covered by the same edit.

### 1. The fold

`ChordProSplitter.kt`:

```kotlin
    /**
     * A byte order mark is never part of a song. Editors on Windows prefix a UTF-8 file with one, and joining two
     * such files — which is exactly what a file holding several songs is — leaves one sitting in the middle, where
     * `decodeLibraryText` does not reach and where `trim` will not touch it either: U+FEFF is a format character
     * and not whitespace, so a `{title}` line behind one is read as lyrics and the song loses its name.
     */
    private fun String.withoutByteOrderMarks() =
        if (BYTE_ORDER_MARK in this) filterNot { it == BYTE_ORDER_MARK } else this

    private const val BYTE_ORDER_MARK = '﻿'
```

applied in both functions, **before** the blank-line trimming so that a line holding nothing but a mark is then
correctly blank:

```kotlin
    fun split(text: String): List<String> {
        val parts = mutableListOf<MutableList<String>>(mutableListOf())
        ChordProSyntax.splitLines(text.withoutByteOrderMarks()).forEach { rawLine ->
            …
```

```kotlin
    fun comparable(text: String) = ChordProSyntax.splitLines(text.withoutByteOrderMarks())
        .dropWhile { it.isBlank() }
        .dropLastWhile { it.isBlank() }
        .joinToString("\n")
```

Both, and for the same reason the line endings are folded in both: `split` decides what is written, `comparable`
decides what counts as the same song, and `ImportPlanner` applies `comparable` to the incoming part **and** to the
text already in the library (`ImportPlanner.kt:66-68`, `:146`, `:170`). If only one side stripped, a library file
that still carries a mark would stop matching the import of itself.

`filterNot` over the whole text rather than `trimStart` per part: a mark can sit anywhere a join happened, and it
means nothing anywhere. The `if (BYTE_ORDER_MARK in this)` guard keeps the ordinary file — which has none — from
allocating a second copy of itself, which matters because an import walks the whole library.

### 2. What this does *not* change

`decodeLibraryText` and its tests: untouched. A mark still survives into the *text of a library file* that nobody
imported — a file the user dropped into the library folder by hand on desktop, read straight by the storage layer.
That file's title is still lost. Accept it and say so: the app never writes a mark, an import strips it, and the
remaining case is a file the user put there themselves with a tool that did something unusual in the middle of it.
If it ever needs covering, the place is `ChordProSyntax.walkDirective` skipping format characters along with
whitespace — not the decoder.

## Tests

`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProSplitterTest.kt`, next to
`a song compares equal whatever its line endings and the blank lines around it` (line 43) and
`a part of a collection compares equal to the file it was exported as` (line 52):

1. `a byte order mark between two songs is not part of the second one` —
   `split("{title: A}\nla\n{new_song}\n﻿{title: B}\nlo\n")` gives
   `["{title: A}\nla", "{title: B}\nlo"]`, and `ChordProParser.parseMetadata(parts[1]).title == "B"`. The second
   assertion is the one that says what the user sees.
2. `a byte order mark at the start of a single song is dropped` — `split("﻿{title: A}\nla")` is
   `["{title: A}\nla"]`. This is the case `decodeLibraryText` already covers for a file, and it has to hold for a
   string that reached the splitter some other way.
3. `a line holding nothing but a byte order mark is blank` —
   `split("{title: A}\nla\n{new_song}\n﻿\n{title: B}\nlo")` gives two parts and the second starts with its
   `{title}`.
4. `a song compares equal whether or not it carries a byte order mark` —
   `comparable("﻿{title: A}\nla") == comparable("{title: A}\nla")`, and the same with the mark in the middle.
   This is the assertion that keeps an import from writing `_2` copies.
5. `a file without a byte order mark is returned as it was` — an ordinary two-song split still gives exactly what
   it gives today (the existing tests cover it; run them).

`domain/implementation/src/commonTest/.../ImportPlannerTest.kt` — one case if the shape is easy there:
a collection whose second song carries a mark plans under the name its own header gives it, not under the file's.
Check what fixtures that suite has before adding; the splitter tests above are the load-bearing ones.

## Verification

```
./gradlew :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest
```

`:data:source:local:implementation:desktopTest` is in that list on purpose: it holds
`LibraryTextDecodingTest`, which must stay green **unchanged** — if it needed editing, the change went into the
wrong module.

Manual (`./gradlew :app:desktop:run`):

```
printf '\xEF\xBB\xBF{title: First}\nla la\n' > /tmp/a.cho
printf '\xEF\xBB\xBF{title: Second}\nlo lo\n' > /tmp/b.cho
printf '{ns}\n' > /tmp/ns.cho
cat /tmp/a.cho /tmp/ns.cho /tmp/b.cho > /tmp/both.cho
```

1. Import `/tmp/both.cho`. Two songs arrive, named **First** and **Second**, filed as `first.cho` and
   `second.cho`.
2. Open the second one: its first line is its `{title}` directive, not a line of lyrics.
3. Import `/tmp/both.cho` again: both songs are recognised as already there and disregarded, rather than arriving
   as `_2` copies.

## Docs

`chordpro/CLAUDE.md`, the `ChordProSplitter` bullet (lines 100-102) — it describes exactly the fold this adds to
and has to name it:

> - `ChordProSplitter` — splits a file that holds several songs at `{new_song}` / `{ns}`, trimming the blank lines around
>   each. `comparable` folds a text the same way, line endings included, which is what an import compares a part
>   against the file already on disk with: the same song, tagged in the app or written by hand, is not a conflict.

Add: a byte order mark is dropped wherever it sits, by both, since joining two files that each carry one leaves one
in the middle and U+FEFF is not whitespace to `trim`.

`data/model/CLAUDE.md` says of `decodeLibraryText` "and no byte order mark", which is true only of the leading
ones. It is worth making that sentence say "and no byte order mark at the start", so the division of labour
between the decoder and `ChordProSplitter` is written down where the next reader looks.

## Files touched

- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProSplitter.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProSplitterTest.kt`
- `chordpro/CLAUDE.md`
- `data/model/CLAUDE.md` (one clause)

## Depends on

Nothing.

## Rules

- Load the `code-style` skill before the first edit.
- `commonMain` stays JVM-free; `:chordpro` has no dependencies at all.
- A change to the dialect belongs in a test first.
- The per-module `CLAUDE.md` files are part of the change.
