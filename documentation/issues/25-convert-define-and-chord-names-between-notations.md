# Convert the chord named by `{define}` and `{chord}` along with the chords of the song
**Challenged:** amended — the fix was wired into the function that real transposition shares, which would have renamed a definition without moving its fingering; it is now an opt-in flag used by the notation conversion only, with a transposition test that proves definitions stay put.

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProNotation.kt`, `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposer.kt`, `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProNotationTest.kt`, `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposerTest.kt`, `chordpro/CLAUDE.md`

## Problem
`ChordProNotation.convertText` goes through `ChordProTransposer.rewriteChordNamesInText`, which rewrites brackets, `{key}`
and tab/grid lines but not the first word of `{define: B frets …}` / `{chord: B …}`. Verified at 8c267e01a: standard to
German turns `{define: B frets 1 1 3 3 3 1}\n{key: B}\n[B]x [Bb]y` into `{define: B …}\n{key: H}\n[H]x [B]y`; German to
standard turns `{define: H …}` into... unchanged `H` while the chords become `[B]`. A definition no longer matches
its chord, and a German user's typed `{define: B …}` (B flat) is saved as B natural.

## Fix
`rewriteChordNamesInText` is shared: `ChordProTransposer.transposeText` (a real transposition) calls it with a
transposing `rename` too, so renaming the definitions inside it would turn `{define: C frets x 3 2 0 1 0}` into
`{define: D frets x 3 2 0 1 0}` under +2, a fingering that no longer matches its name. The draft's "decide not to
transpose definitions" was therefore impossible as written. Add a parameter
`renameDefinitions: Boolean = false` to `rewriteChordNamesInText` and pass `true` only from
`ChordProNotation.convertText`. When it is true and the directive is `define` or `chord` (names are canonical after
`matchDirective`, which lowercases them), rename the first whitespace-delimited word of the value when
`ChordProChordNames.isChordName` accepts it (`rename` already returns anything else unchanged) and keep every other character of the
line, its indentation included (`rawLine.replaceFirst`-style on the value start from `ChordProSyntax.directiveValueStart(trimmedLine)`);
a `define`/`chord` inside a delegated environment is never reached (the delegated branch returns first, and
`matchDelegatedDirective` only yields a directive for an end or a colon one: add a test). Definitions do not vote
in `isGermanNotated` (it reads only chords in the song's lines), so a German file whose only `H` is in a `{define: H …}`
is still read as standard; say so in the CLAUDE.md bullet next to "`{define}` / `{chord}` names are converted with the
chords; the fingerings are never transposed and a real transposition leaves the definitions alone".

## Tests
`ChordProNotationTest`: both directions of the samples above; a `{define}` with a non-chord first word and one inside
`{start_of_textblock}` stay unchanged; round trip standard to German to standard returns the text; and in `ChordProTransposerTest` (or the same file) `transposeText` of `{define: C frets x 3 2 0 1 0}\n[C]x` by +2 leaves the definition line untouched.

## Manual check
None.
