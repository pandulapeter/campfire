# Start the undo history over before the editor's Replace all rewrites a long document, as every other whole-document rewrite does

**Kind:** performance (memory)  ·  **Severity:** low  ·  **Platforms:** all
**Files:**
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/EditorSearch.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/EditorTextReplacement.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/EditorTextReplacementTest.kt` (new),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/CLAUDE.md`

## Problem

`EditorTextReplacement.kt` explains why every whole-document rewrite clears the undo history first in a long document:

```kotlin
/**
 * Replaces everything, for the rewrites that touch the whole document. The undo history records such an edit as
 * the whole text twice and keeps a hundred of them, which for a long document is more memory than a phone hands
 * out, so there the history starts over with the rewrite: it can still be undone, what came before it cannot.
 ...
internal fun TextFieldState.replaceAll(text: String, isSelectionMapped: Boolean) {
    val before = this.text.toString()
    if (before.length > LONG_DOCUMENT_LENGTH) undoState.clearHistory()
```

`replaceWithTransposition` and `replaceWithPrettification` carry the same guard. The editor's new Replace all does not:

```kotlin
// EditorSearch.kt
/** Writes the replacement over every match as one edit, so that a single undo takes all of them back. */
fun replaceAll() {
    val starts = matchStarts.takeIf { it.isNotEmpty() } ?: return
    val replacementText = replacement.text.toString()
    val length = query.text.length
    textFieldState.edit {
        // From the end, so that every replacement leaves the offsets of the ones still to be written where they were.
        starts.asReversed().forEach { start -> replace(start, start + length, replacementText) }
        selection = TextRange(starts.first())
    }
}
```

An edit with more than one change is recorded by Compose's text undo manager as one operation holding the whole text
before and after it (one change, a single match, is recorded as that change only), so in a document over
`LONG_DOCUMENT_LENGTH` (50,000 characters) each Replace all with two or more matches adds two copies of the whole
document to a history of up to a hundred entries: the memory problem the other rewrites were guarded against. It only
matters for very long documents (pasted books, imported PDFs), hence low.

## Fix

Move the body into `EditorTextReplacement.kt`, next to the other rewrites, with the same guard:

```kotlin
/**
 * Writes [replacement] over the [length] characters at each of [starts] (ascending, never overlapping) as one edit,
 * leaving the caret at the first of them. With more than one match the undo history records it the way it records a
 * [replaceAll], so a long document's history starts over the same way.
 */
internal fun TextFieldState.replaceMatches(starts: List<Int>, length: Int, replacement: String) {
    if (starts.isEmpty()) return
    if (starts.size > 1 && text.length > LONG_DOCUMENT_LENGTH) undoState.clearHistory()
    edit {
        // From the end, so that every replacement leaves the offsets of the ones still to be written where they were.
        starts.asReversed().forEach { start -> replace(start, start + length, replacement) }
        selection = TextRange(starts.first())
    }
}
```

(`@OptIn(ExperimentalFoundationApi::class)` as on its neighbours, if `undoState` still needs it.) `EditorSearch.replaceAll`
becomes `textFieldState.replaceMatches(starts, query.text.length, replacement.text.toString())`. The `starts.size > 1`
condition is optional; it keeps the history of a long document whose query occurs once, since that edit is recorded as
the one change. Recommended, as it costs nothing.

In `songEditor/CLAUDE.md`, extend the sentence "In a document of more than 50,000 characters a whole-document rewrite (a
transposition, a revert) starts the undo history over first" to name Replace all of more than one match too, and in the
Find section, after "Replace all writes every match in one edit, so one undo takes it back", add "(in a long document
the history before it is gone, as before a transposition)".

## Tests

`TextFieldState` and its `undoState` work headlessly in `commonTest` (`EditorFieldSaverTest` already builds them; an
`edit`, `undoState.undo()`, `canUndo`, and `clearHistory()` were checked to behave as expected without a composition). New
`EditorTextReplacementTest`:

- `replaceMatches` on `"a-b-a-b"` with starts `[0, 4]`, length 1, `"xy"` gives `"xy-b-xy-b"` with the selection at 0.
- A short document: one ordinary `edit` first, then `replaceMatches` with two starts, then `undoState.undo()` — the text
  is back to before the replacement and `canUndo` is still `true` (the earlier edit is kept).
- A document longer than `LONG_DOCUMENT_LENGTH` (`"ab ".repeat(20_000)`): the same steps leave `canUndo == false` after
  the one undo, and the text is back to before the replacement.
- The same long document with a single start: after the undo `canUndo` is still `true`.

## Manual check

Paste a text of more than 50,000 characters into the editor (or import a long PDF), type a few characters, then open Find
and replace and Replace all a word that occurs many times: one Undo takes the replacement back and the Undo button then
greys out. In a normal song, the same steps leave the earlier typing undoable.
