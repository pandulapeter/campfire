# End a delegated environment in `ChordProSplitter` on any `{end_of_…}`, as the parser does

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Challenged:** amended — the splitter must also follow a `{start_of_…: label}` written inside delegated text (the parser switches environment on it, through `matchDelegatedDirective`), not only any end; the fix now mirrors the parser's start/end handling, and a test for the start case is added.
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProSplitter.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProSplitterTest.kt`,
`chordpro/CLAUDE.md`

## Problem

Inside an environment ChordPro hands to another program (`abc`, `ly`, `svg`, `textblock`), a `{ns}` is that
program's text, so the splitter does not split there. It leaves the environment only on the end that matches its
start:

```kotlin
if (delegatedEnvironment != null) {
    val end = ChordProSyntax.matchDelegatedDirective(trimmedLine)?.name?.let(ChordProSyntax::endOfEnvironment)
    if (end == delegatedEnvironment) delegatedEnvironment = null
    parts.last() += rawLine
    return@forEach
}
```

The parser and the summary end the environment on **any** `{end_of_…}`:

- `ChordProParser.handleDirective`: `ChordProSyntax.endOfEnvironment(name)?.let { environment -> if (lineMode(environment) == null) section.close() else section.closeLineMode(); return }` — whichever environment the end names.
- `ChordProParser.scan`: `ChordProSyntax.endOfEnvironment(directive.name)?.let { environment = null }`.

`chordpro/CLAUDE.md` states the same rule ("Any `{end_of_…}` ends a tab, a grid or a delegated environment, whichever
environment it names, since the parser, the summary and the transposition read the lines after it as ordinary ones"),
and the splitter's own comment says it reads `{ns}` "as the parser reads it".

So in a file whose delegated block is closed by a mismatched end — a typo, or a block copied from another song —
the splitter disagrees with the parser. Verified with a probe test at a5798b31b:

- `ChordProSplitter.split("{start_of_abc}\nX\n{end_of_verse}\n{ns}\n{title: B}\n[C]b")` returns **1** part.
- `ChordProParser.parse("{start_of_abc}\nX\n{end_of_verse}\n{title: B}\n[C]b").metadata.title` is `B` — the parser
  has left the `abc` block at `{end_of_verse}` and reads `{title: B}` as a directive.

An import of such a file therefore makes one song where the parser would see two, the second song's `{title}`
overwriting the first's, and every song after the bad end merged into it.

## Fix

The parser does not only leave a delegated environment on any end: inside delegated text `matchDelegatedDirective`
also returns a directive written with a colon, and `handleDirective` acts on a `{start_of_…: label}` there like
anywhere else — it closes the `abc` section and opens the new one (`section.close(); section.open(…)`), and the
summary (`scan`) and the transposer (`rewriteChords`) set `environment` from it the same way. So
`{start_of_abc}\nX\n{start_of_verse: V}\n{ns}\n…` is a verse with a `{ns}` in it to the parser, while the splitter
still thinks it is inside `abc` and does not split. Mirror the parser rather than only widening the end check:

```kotlin
if (delegatedEnvironment != null) {
    // Read the way the parser reads delegated text: an end or a `name: value` directive is one, and any start or end
    // moves the environment on, whichever one it names.
    ChordProSyntax.matchDelegatedDirective(trimmedLine)?.name?.let { name ->
        ChordProSyntax.startOfEnvironment(name)?.let { delegatedEnvironment = it.takeIf { e -> e in ChordProSyntax.delegateEnvironments } }
        ChordProSyntax.endOfEnvironment(name)?.let { delegatedEnvironment = null }
    }
    parts.last() += rawLine
    return@forEach
}
```

(Directive names are already lowercased by `walkDirective`, and `hasSelectorSuffix` is false for every
`start_of_`/`end_of_` name, so neither check the parser makes changes anything here.) A `{ns}` that follows on the
same pass is then split by the ordinary branch. Update the comment above the block: "only its own end closes it"
becomes "any `{end_of_…}` closes it and any `{start_of_…}` moves it on, as they do for the parser". Update the
`ChordProSplitter` entry in `chordpro/CLAUDE.md` the same way.

`ChordProPrettifier` (`if (delegated && end != environments.last())`) still only leaves on the matching end, so after
a mismatched end it copies the following lines as they are instead of formatting them. That only leaves those lines
unformatted and changes no song, so leave it out of this plan.

## Tests

In `ChordProSplitterTest`, add:

```kotlin
@Test
fun `any end directive closes a delegated environment, as the parser reads it`() {
    assertEquals(
        listOf("{start_of_abc}\nX\n{end_of_verse}", "{title: B}\n[C]b"),
        ChordProSplitter.split("{start_of_abc}\nX\n{end_of_verse}\n{ns}\n{title: B}\n[C]b"),
    )
}
```

```kotlin
@Test
fun `a start directive with a value inside a delegated environment moves it on, as the parser reads it`() {
    assertEquals(
        listOf("{start_of_abc}\nX\n{start_of_verse: V}\na", "{title: B}\n[C]b"),
        ChordProSplitter.split("{start_of_abc}\nX\n{start_of_verse: V}\na\n{ns}\n{title: B}\n[C]b"),
    )
    // Another delegated environment started inside one keeps the `{ns}` part of it.
    assertEquals(1, ChordProSplitter.split("{start_of_abc}\n{start_of_ly: L}\n{ns}\nx").size)
}
```

The existing `new_song inside a delegated environment is part of it` and `an unclosed delegated environment takes the
rest of the file` tests must keep passing. Run `./gradlew :chordpro:desktopTest`.

## Manual check

Import a `.cho` file containing
`{title: A}\n{start_of_abc}\nX\n{end_of_verse}\n{ns}\n{title: B}\n[C]b`: the library gets two songs, `A` and `B`.
