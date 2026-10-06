# Fold directive short forms and spacing when an import compares two songs

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProSplitter.kt,
chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProSyntax.kt,
chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProSplitterTest.kt,
domain/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/domain/implementation/ImportPlannerTest.kt,
chordpro/CLAUDE.md, CLAUDE.md
**Challenged:** amended — declaration order made explicit (`longNames` reads `metadataAliases`, `startShortNames` and `endShortNames` while the object initializes, so it must be declared after all three or it starts from a null map); otherwise sound: a probe parsing every pair the fold equates (`{t:X}`/`{Title X}`/`{title-guitar!: X}` vs `{title: X}`, `{key:}` vs `{key}`, `{soc:}`/`{EOC}`, `{cb:x}`, `{ci:  x  }`, `{lang:}`, `{st:}`, `{X_Foo:B}`, `{chorus:}`, a `{c:x}` inside `{sot}`) gave identical `ChordProSong`s, and `ChordProSplitter.comparable` has no caller but `ImportPlanner.formattedComparable`.

## Problem

An import disregards a song the library (or an earlier entry of the batch) already holds, "comparing both sides after
`ChordProPrettifier` and notation normalization" (root CLAUDE.md, Conventions). The comparison key is
(`ImportPlanner.kt:35` at 8ee010b36):

```kotlin
private fun formattedComparable(text: String) = ChordProSplitter.comparable(ChordProPrettifier.prettify(text))
```

`ChordProPrettifier.prettify` reorders the header and normalises blank lines, but writes every directive line as it was
typed (`output += trimmed`, `metadata += … to trimmed`), and `ChordProSplitter.comparable` (`ChordProSplitter.kt:54-57`)
only folds line endings, byte order marks, outer blank lines and the chord notation. So two files that differ only in
how a directive is spelled are two different songs to the import, although the parser reads them identically.

Probe (a throwaway `ImportPlanner.planSongs` test in `:domain:implementation`, library `x.cho` =
`{title: River}\n{artist: Band}\n\n[C]Hello [G]world\n`, the same song arriving as `x.cho`):

| Incoming difference | Status |
|---|---|
| extra blank lines only | IDENTICAL |
| `{t:River}` for `{title: River}` | **CONFLICTING** |
| `{title:River}` for `{title: River}` (no space) | **CONFLICTING** |
| `{soc}` / `{eoc}` for `{start_of_chorus}` / `{end_of_chorus}` | **CONFLICTING** |
| `{c: Intro}` for `{comment: Intro}` | **CONFLICTING** |

Against the library that is the conflict question (keep both / replace / skip) for a song the user already has; inside
one batch it is a silent `_2` copy — the live run's `big.zip` had ten songs differing only by `{t:X}` for `{title: X}`, and
all ten were written as `…_2.cho` next to their originals. Short forms and unspaced directives are common in ChordPro
files from other apps and older songbooks, so the same song from two sources meets this.

## Fix

Fold directive spelling in the comparison key only. **What Prettify writes does not change** — the user's file keeps
`{t:}` if that is what they wrote.

1. `ChordProSyntax`: one table of the short names ChordPro defines, and a canonical spelling of a directive line. Put it
   next to `metadataAliases` (around `:427`), reusing the existing maps:
   ```kotlin
   /** Every short directive name ChordPro defines, under its long one, see [canonicalDirective]. */
   private val longNames = metadataAliases + startShortNames.mapValues { (_, environment) -> START_OF_PREFIX + environment } +
       endShortNames.mapValues { (_, environment) -> END_OF_PREFIX + environment } + mapOf(
           "c" to "comment", "ci" to "comment_italic", "cb" to "comment_box", "np" to "new_page", "npp" to "new_physical_page",
           "colb" to "column_break", "col" to "columns", "ns" to "new_song", "g" to "grid", "ng" to "no_grid",
           "tf" to "textfont", "ts" to "textsize", "cf" to "chordfont", "cs" to "chordsize",
       )

   /**
    * [directive] as one spelling of it: its long name, and its value after a colon and a single space, or no value at
    * all for an empty one. Two lines with the same canonical spelling are read the same by [ChordProParser] (`{t:X}`,
    * `{title X}` and `{Title: X}` are all `{title: X}`), which is what an import asks when it holds two copies of a song
    * against each other. A name with a selector (`title-guitar`) is kept whole; a negated one has already been read as
    * the directive it is written on by [matchDirective].
    */
   fun canonicalDirective(directive: Directive): String {
       val name = longNames[directive.name] ?: directive.name
       val value = directive.value?.takeIf { it.isNotEmpty() }
       return if (value == null) "{$name}" else "{$name: $value}"
   }
   ```
   Declare `longNames` **below** `metadataAliases` (and so below `startShortNames` / `endShortNames`, which come
   earlier in the object): a `private val` of an `object` is initialized in declaration order, and one declared above
   a map it reads would start from null.
   (`START_OF_PREFIX` / `END_OF_PREFIX` are the constants `startOfEnvironment` / `endOfEnvironment` already use;
   `metadataAliases` holds `t`, `st` and `lang`.) Check the table against `knownNames` (`:87-95`) and the parser's
   `when` branches for the short names it accepts; add any short form the parser reads that is missing here.

2. `ChordProSplitter.comparable`: rewrite every directive line outside a delegated environment in its canonical
   spelling, tracking delegated environments exactly as `split` does (`:20-41`), so that LilyPond's `{ c d e }` or an
   ABC line is never touched:
   ```kotlin
   fun comparable(text: String): String {
       var delegatedEnvironment: String? = null
       return ChordProSyntax.splitLines(ChordProNotation.convertText(text.withoutByteOrderMarks(), ChordNotation.STANDARD, ChordNotation.STANDARD))
           .map { rawLine ->
               val trimmedLine = rawLine.trim()
               if (delegatedEnvironment != null) {
                   ChordProSyntax.matchDelegatedDirective(trimmedLine)?.name?.let { name ->
                       ChordProSyntax.startOfEnvironment(name)?.let { environment ->
                           delegatedEnvironment = environment.takeIf { it in ChordProSyntax.delegateEnvironments }
                       }
                       ChordProSyntax.endOfEnvironment(name)?.let { delegatedEnvironment = null }
                   }
                   return@map rawLine
               }
               val directive = ChordProSyntax.matchDirective(trimmedLine) ?: return@map rawLine
               delegatedEnvironment = ChordProSyntax.startOfEnvironment(directive.name)?.takeIf { it in ChordProSyntax.delegateEnvironments }
               ChordProSyntax.canonicalDirective(directive)
           }
           .dropWhile { it.isBlank() }
           .dropLastWhile { it.isBlank() }
           .joinToString("\n")
   }
   ```
   If the delegated-environment walk is worth sharing, extract it into one private helper used by both `split` and
   `comparable`; otherwise keep the duplication short and say in a comment that it mirrors `split`.
   Extend the KDoc of `comparable`: "and every directive in one spelling (`ChordProSyntax.canonicalDirective`): a
   short name, a missing space or a capital is how a file was typed, not what it says".

3. Nothing changes in `ImportPlanner`: `formattedComparable` already runs both sides (library and incoming, and the
   batch's own entries) through `ChordProSplitter.comparable`, so both sides fold alike — the key only ever matches
   more, never less, and two files it newly matches mean the same to the parser, so disregarding the incoming one
   loses nothing (the library keeps its own spelling).

4. Docs: root `CLAUDE.md` Conventions, "(for a song, comparing both sides after `ChordProPrettifier` and notation
   normalization)" → "(for a song, comparing both sides after `ChordProPrettifier`, notation normalization and with every
   directive in one spelling — `{t:X}` is `{title: X}`)". `chordpro/CLAUDE.md`, the `ChordProSplitter` entry
   ("`comparable` folds a text the same way, line endings included, …"): add that it also writes each directive outside a
   delegated environment in its long name and one spacing (`ChordProSyntax.canonicalDirective`), and that Prettify does
   not, since that would rewrite the user's file.

Option, not recommended (a product decision): have `ChordProPrettifier.prettify` itself write the canonical spelling,
so that Prettify in the editor and every import also expand `{t:}`, `{soc}` and the rest. That rewrites what users typed
in every imported and prettified file, makes a re-import of an old export differ from the library file it came from
(the library one not prettified yet) until both are folded anyway, and is not needed for the import to match.

Out of scope: `{meta: title X}` vs `{title: X}` (`ChordProSyntax.standardMeta` could fold it the same way; leave it unless
the user asks — it is rarer and the `meta` forms carry their own cases in `metadataKind`).

**Order:** after lane B's 15–17 (16 edits `ImportPlanner.kt`'s setlist comparison; this plan does not edit
`ImportPlanner.kt` at all, only its test file, which 16 also extends — add the new case at the end). The `:chordpro`
files are lane A's module: none of lane A's plans 01–06 touches `ChordProSplitter.kt`; plans 02 (prettifier) and 05
reference `ChordProSyntax` members without editing the region around `metadataAliases`.

## Tests

`ChordProSplitterTest` (commonTest):
- `` `a directive compares equal however it is spelled` ``: `comparable` of `{title: T}\n{start_of_chorus}\n[Am]a\n{end_of_chorus}\n{comment: Intro}`
  equals that of `{t:T}\n{soc}\n[Am]a\n{eoc}\n{c:Intro}` and of `{Title T}\n{SOC}\n[Am]a\n{EOC}\n{comment:  Intro}`.
- `` `an empty value is no value` ``: `{key:}` compares equal to `{key}`.
- `` `a selector is kept` ``: `{title-guitar: T}` does not compare equal to `{title: T}`.
- `` `delegated text is left alone` ``: `{start_of_ly}\n{ c d e }\n{end_of_ly}` compares equal to itself and not equal to
  `{start_of_ly}\n{c: d e}\n{end_of_ly}` (that line is LilyPond, not a comment), and a `{c:x}` after the
  `{end_of_ly}` is folded.
- `` `different values still differ` ``: `{t: A}` vs `{title: B}`.

`ImportPlannerTest`: library `x.cho` = `{title: River}\n{artist: Band}\n\n{start_of_chorus}\n[C]la\n{end_of_chorus}\n`,
incoming `x.cho` = `{t:River}\n{artist:Band}\n\n{soc}\n[C]la\n{eoc}\n` → `IDENTICAL`; and two batch entries differing
only that way, with no library file → the second is `IDENTICAL` with `repeatedEntryIndex = 0`.

## Manual check

Desktop: import a `.cho` with `{title: River}` and a chorus written `{start_of_chorus}`/`{end_of_chorus}`; then import a copy
written with `{t:River}` and `{soc}`/`{eoc}`: it ends in the "already there" snackbar, with no question and no `_2` file,
and the library file still reads `{title: River}`.
