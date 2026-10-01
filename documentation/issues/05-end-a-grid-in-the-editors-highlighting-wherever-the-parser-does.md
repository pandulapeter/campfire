# End a grid, a tab and a delegated environment in the editor's highlighting wherever the parser ends them

**Kind:** bug (editor highlighting)  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProHighlighter.kt`, `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProHighlighterTest.kt`
**Challenged:** amended — the proposed code left a delegated environment (`abc`, `ly`, `svg`, `textblock`) open across an `{end_of_verse}` (or any other end) that the parser, the summary and the transposition all treat as ending it; the fix now clears all three flags on every end of environment, which is what those three do.

## Problem

The highlighter leaves each mode only on its own end:

```kotlin
ChordProSyntax.endOfEnvironment(directive.name)?.let {
    if (it == TAB_ENVIRONMENT) isInTab = false
    if (it == GRID_ENVIRONMENT) isInGrid = false
    if (it in ChordProSyntax.delegateEnvironments) isInDelegate = false
}
```

Everything else that reads the file ends the mode on *any* end of environment:
- The parser (`ChordProParser.handleDirective`): `if (lineMode(environment) == null) section.close() else section.closeLineMode()`;
  both set `lineMode = null`, and `LineMode.VERBATIM` (a delegated environment) is a line mode like `TAB` and `GRID`
  (`SectionBuilder.isDelegated get() = lineMode == LineMode.VERBATIM`).
- The summary (`ChordProParser.scan`) and `ChordProTransposer`: `endOfEnvironment(…)?.let { environment = null }`.

Inside a delegated environment an `{end_of_…}` is always read as a directive (`ChordProSyntax.matchDelegatedDirective`),
in the highlighter as in the parser. So:
- `{start_of_grid}` / `| Am |` / `{end_of_verse}` / `[Am]La` — the song shows `[Am]La` as a lyric line with a chord, the
  editor still treats it as grid words and leaves `[Am]` uncoloured; the same after a `{start_of_tab}` for a staff line.
- `{start_of_abc}` / `X:1` / `{end_of_verse}` / `[Am]La` — the parser ends the verbatim mode there and parses `[Am]La`
  with its chord, the editor keeps it verbatim.

The starts already agree: `startOfEnvironment` sets all three flags from the new environment, as the parser's
`openLineMode` / `close` + `open` replace the mode.

## Fix

```kotlin
// Any end of an environment ends the way the lines were being read, whichever environment it names: the parser,
// the summary and the transposition all read the lines after it as ordinary ones.
ChordProSyntax.endOfEnvironment(directive.name)?.let {
    isInTab = false
    isInGrid = false
    isInDelegate = false
}
```

(`let` keeps the null check; the environment name is no longer needed.)

## Tests

In `ChordProHighlighterTest`, using the file's `spans` helper filtered to `TokenType.CHORD`:
- `{start_of_grid}\n| Am |\n{end_of_verse}\n[Am]La` colours `[Am]` on the last line as a chord (and the grid's `Am`
  as before);
- `{start_of_tab}\ne|--[3]--|\n{end_of_chorus}\ne|--[3]--|` colours the second `[3]` (a staff line outside a tab is an
  ordinary line to the parser) and not the first;
- `{start_of_abc}\n[CEG]\n{end_of_verse}\n[C]` colours only the last `[C]`.
The existing `{start_of_abc}\n[CEG]\n{end_of_abc}\n[C]` test is unchanged.

## Manual check

None.
