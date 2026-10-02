# Ignore `{new_song}` and `{ns}` inside the verbatim environments when splitting a file
**Challenged:** sound — the parser (`matchDelegatedDirective`) and the prettifier already treat these lines as literal, so the splitter is the odd one out; close the environment on `ChordProSyntax.endOfEnvironment(directive.name) == <the open one>` like the prettifier does.

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProSplitter.kt`, `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProSplitterTest.kt`, `chordpro/CLAUDE.md`

## Problem
`ChordProSplitter.split` splits at every `{new_song}`/`{ns}` line. chordpro/CLAUDE.md says the delegated environments
(`abc`, `ly`, `svg`, `textblock`) keep their lines verbatim, braces included, and the prettifier and parser honour that
(`ChordProPrettifier` test "delegated syntax … is literal" even has `{new_song}` inside). At 8c267e01a
`split("{title: A}\n{start_of_textblock}\nx\n{ns}\ny\n{end_of_textblock}\n")` returns 2 parts, cutting the environment.

## Fix
Track the open delegated environment in the line loop with `ChordProSyntax.matchDelegatedDirective` (the same helper
the parser uses; read its signature and mirror how `ChordProPrettifier` uses `ChordProSyntax.delegateEnvironments` and
`startOfEnvironment`/`endOfEnvironment`): while one is open, every line except its matching `{end_of_…}` is appended
unexamined. An unclosed environment swallows the rest of the file (same as the parser).

## Tests
`ChordProSplitterTest`: the sample yields one part; `{ns}` after `{end_of_textblock}` still splits; every delegated
name; an unclosed one yields one part.

## Manual check
None.
