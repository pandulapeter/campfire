# Answer "every chord name in the song" with one internal visitor instead of two hand-written walks

**Challenged:** amended — the oracle test excludes `{define}` names (the rewrite renames definitions through `ChordRewrite.rewriteDefinition`, whose default calls `rename`, while neither walk visits them), and step 1 states the visitor shape plainly: `visit` returns `Boolean` (continue).

**Kind:** architecture  ·  **Severity:** low  ·  **Effort:** S  ·  **Risk:** low  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/.../chordpro/ChordProChords.kt` (`namesIn`, internal `forEachName`); `ChordProTransposer.kt` (internal `writtenChordNames`, private `rewriteBlock`, `rewriteLines`, `rewriteLine`, internal `rewriteChords`); `ChordProNotation.kt` (callers of `forEachName` and `writtenChordNames`); `ChordProParser.kt` (private `writtenChordNames(rawLine, trimmedLine, environment)` — read only, see Problem); new `chordpro/src/commonMain/.../model/ChordProSongChordNames.kt` or a function in `ChordProChords.kt`; tests `ChordProChordsTest`, `ChordProTransposerTest`, `ChordProNotationTest`; `chordpro/CLAUDE.md`
**Depends on:** none (land after the :chordpro split lane, which moves the rewrite helpers into `ChordProChordRewriter`)

## Problem

Two walks over a parsed `ChordProSong` list chord names, with deliberately different scopes, and each re-implements the
per-line part:

- `ChordProChords.forEachName(song, action: (name, offset) -> Unit)` — lyrics chords (non-annotation), grid cells,
  tab chord rows, **plus** bracketed chords in section labels, grid/tab labels and comments, **descends into
  `ChorusRecall` blocks**, tracks the `{transpose}` offset, and leaves the key out.
- `ChordProTransposer.writtenChordNames(song): Sequence<String>` — the **key**, then lyrics / grid / tab of
  top-level `Section`s only; no labels, comments or recalls (its KDoc on `rewriteBlock` explains why: "the library scan
  never reads a directive's value, and letting them decide the notation or the spelling would make the song list and
  the viewer disagree").

Both contain the identical line switch:

```kotlin
is ChordProLine.Lyrics -> line.chords.filter { !it.isAnnotation }.map { it.name }
is ChordProLine.Grid -> line.tokens.filterIsInstance<GridToken.Chord>().flatMap { ChordProSyntax.cellChords(it.name) }
is ChordProLine.Tab -> ChordProTabTransposer.chordNames(listOf(line.text))
ChordProLine.Blank -> Unit
```

and `writtenChordNames`' KDoc claims "Every name [rewriteChords] would hand to its rename", which is not true (the
rewrite also renames labels, comments and recalls). Adding a new line type or a new place chords may stand means
editing both walks and remembering the rewrite.

`rewriteBlock` / `rewriteLine` are transforms that build a new song, not walks; they stay as they are. The parser's
private `writtenChordNames(rawLine, trimmedLine, environment)` works on raw text during the summary scan and also stays.

## Fix

1. Add one internal visitor next to `forEachName`:
   ```kotlin
   internal fun visitChordNames(
       song: ChordProSong,
       includeKey: Boolean,
       includeBracketedText: Boolean,   // section/grid/tab labels and comments
       includeRecalls: Boolean,
       visit: (name: String, offset: Int) -> Unit,
   )
   ```
   with the per-line switch written once as a private `ChordProLine.chordNames(): List<String>`. `visit` returns
   `Boolean` — `true` to continue — because `writtenChordNames` is a lazy `Sequence` whose callers (`isGermanNotated`,
   `hasLatinName`) stop at the first hit; `writtenChordNames` keeps its `Sequence` signature by wrapping the visitor
   per call site that needs laziness (`any { … }` becomes a visitor returning `!predicate(name)`), or callers that need
   the full list (`ChordProNotation` line 167, `ChordProTransposer` line 398) collect it. A `sequence { }` wrapper is
   not possible, since `yield` cannot be called from the non-suspending `visit`.
2. Re-implement `forEachName` as `visitChordNames(song, includeKey = false, includeBracketedText = true, includeRecalls = true)`
   and `writtenChordNames` as `includeKey = true, includeBracketedText = false, includeRecalls = false`, keeping both
   names as thin wrappers so `ChordProNotation` call sites do not change.
3. Fix `writtenChordNames`' KDoc to say which names it leaves out and why (point at `rewriteBlock`'s KDoc).

## Tests

- New test in `ChordProChordsTest`, before step 2 so it pins today's behaviour: for one fixture holding a key, lyrics
  with an annotation, a grid with a label, a tab with a chord row and a label, a comment `Intro: [G] [Em]`, a recalled
  chorus and a `{transpose: 2}`, assert the exact `(name, offset)` list `forEachName` gives and the exact list
  `writtenChordNames` gives (literal expected values). After step 2 the same assertions must pass through the visitor.
- An oracle test tying the visitor to the rewrite it describes: run `ChordProTransposer.rewriteChords(song) { ChordRewrite(rewriteTabLines = recordTabs, rename = recordName, renameInKey = recordKey) }`
  on the fixture and assert that `recordName` ∪ `recordKey` ∪ `ChordProTabTransposer.chordNames(recorded tab lines)`
  equals the set `visitChordNames(includeKey = true, includeBracketedText = true, includeRecalls = true)` yields, plus
  any `ChordProBlock.Transpose.key` (renamed through `renameInKey` but not a chord anybody plays — list it explicitly).
  Build the `ChordRewrite` with `rewriteDefinition = { it }` (its default renames every `{define}`'s name through
  `rename`, and neither walk visits definitions, on purpose), or keep `{define}` out of the fixture.
  `ChordRewrite` is internal, so the test lives in `:chordpro`'s `commonTest`.
- Guards: `ChordProChordsTest` (`namesIn`), `ChordProNotationTest` (`isGermanNotated`, Latin detection),
  `ChordProTransposerTest`, `:presentation`'s `SongChordsTest`.

## Manual check

none — covered by tests.
