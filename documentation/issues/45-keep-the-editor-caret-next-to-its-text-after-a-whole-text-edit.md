# Keep the editor's caret and selection next to their text after an edit made from a sheet or by Save

**Kind:** ux  ·  **Severity:** low  ·  **Platforms:** all (most visible on desktop/web, where the field keeps the focus)
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/SongEditorScreen.kt,
presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/EditedOffset.kt (new),
presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/EditedOffsetTest.kt (new),
presentation/CLAUDE.md
**Challenged:** amended — a single changed stretch sends a caret lying between two changes to the end of the last one,
which for a save respelling chords on several lines (or a sync change followed by the field) moves it many lines down,
worse than today's one-character drift: where the line count is unchanged the offset is now mapped line by line; the
stdlib's prefix/suffix already keep surrogate pairs whole; two more tests.

## Problem

`SongEditorScreen.kt:1043-1051` (8ee010b36):
```kotlin
private fun TextFieldState.replaceAll(text: String) {
    if (this.text.length > LONG_DOCUMENT_LENGTH) undoState.clearHistory()
    edit {
        val caret = selection.start.coerceAtMost(text.length)
        delete(0, length)
        insert(0, text)
        selection = TextRange(caret)
    }
}
```
`EditOnRequest` (`:975-989`) applies every edit requested from the editor's menus — Manage tags/languages/links, Edit
song details, cover art (`CampfireViewModel.kt:2063`, `_editorTextEdits.tryEmit`) — and the fix-up a save makes when it
writes a typed chord the one way the notation allows (`:2273`, e.g. a German `[Bb]` written as `[B]`) through
`replaceAll`, which keeps the absolute caret offset and collapses the selection. Those edits change text above the caret:
adding a tag inserts `{tag: Rock}\n` (13 characters) in the header, so a caret in a verse line ends up 13 characters
earlier, inside the line before. Ctrl/Cmd+S with a typed `[Bb]` above the caret, in the German notation, moves the caret
one character right of where it was while the field keeps the focus, and the next keystroke lands in the wrong place.
Transposition and Prettify already map the caret (`ChordProTransposer.transposedOffset`,
`ChordProPrettifier.prettifiedOffset`); these paths do not. presentation/CLAUDE.md documents only that "a revert has
nothing to map by and keeps the offset". (Whether focus returns to the field after a sheet closes on desktop was not
confirmed; the Save path is certain.)

## Fix

1. New pure helper `screens/songEditor/EditedOffset.kt`:
   ```kotlin
   /**
    * Where [offset] in [before] is in [after]. An edit that kept the number of lines (chords respelled by a save on
    * any number of lines, a header value changed) is mapped line by line, so that the caret stays on its line next to
    * its text however many other lines changed; one that added or removed lines (a tag line put in) is mapped as one
    * changed stretch: an offset in the unchanged start stays, one in the unchanged end moves by the change in length,
    * and one inside the changed stretch goes to the end of what replaced it. A document rewritten throughout maps its
    * own way (transposition, Prettify).
    */
   internal fun editedOffset(before: String, after: String, offset: Int): Int {
       val clamped = offset.coerceIn(0, before.length)
       if (before.count { it == '\n' } != after.count { it == '\n' }) return stretchOffset(before, after, clamped)
       val lineStartBefore = before.lastIndexOf('\n', clamped - 1) + 1
       val line = before.substring(0, lineStartBefore).count { it == '\n' }
       // The start of the same line in the edited text.
       var lineStartAfter = 0
       repeat(line) { lineStartAfter = after.indexOf('\n', lineStartAfter) + 1 }
       val lineBefore = before.substring(lineStartBefore, before.indexOf('\n', lineStartBefore).let { if (it < 0) before.length else it })
       val lineAfter = after.substring(lineStartAfter, after.indexOf('\n', lineStartAfter).let { if (it < 0) after.length else it })
       return lineStartAfter + stretchOffset(lineBefore, lineAfter, clamped - lineStartBefore)
   }

   /** [offset] across one changed stretch of [before], see [editedOffset]. */
   private fun stretchOffset(before: String, after: String, offset: Int): Int {
       val prefix = before.commonPrefixWith(after).length
       val suffix = before.commonSuffixWith(after).length.coerceAtMost(minOf(before.length, after.length) - prefix)
       val changedEndBefore = before.length - suffix
       val changedEndAfter = after.length - suffix
       return when {
           offset <= prefix -> offset
           offset >= changedEndBefore -> offset + (after.length - before.length)
           else -> changedEndAfter
       }.coerceIn(0, after.length)
   }
   ```
   (Plain stdlib, JVM-free. `commonPrefixWith` and `commonSuffixWith` already step back rather than end inside a
   surrogate pair, and the suffix's coercion only shortens it to end at the prefix's boundary or at the end of the
   shorter text, so no boundary splits a pair; the test below pins it. The function is O(length), one pass or two, at
   most twice per edit — the field already copies the whole text for these edits.)
2. A variant of `replaceAll` for these paths:
   ```kotlin
   private fun TextFieldState.replaceAllKeepingSelection(text: String) {
       val before = this.text.toString()
       if (before.length > LONG_DOCUMENT_LENGTH) undoState.clearHistory()
       edit {
           val start = editedOffset(before, text, selection.start)
           val end = editedOffset(before, text, selection.end)
           delete(0, length)
           insert(0, text)
           selection = TextRange(start, end)
       }
   }
   ```
   Use it in `EditOnRequest` and `FollowFileWhileUntouched`; keep `replaceAll` for `RevertOnRequest` (whose behaviour is
   documented). If `replaceAll` is then used only by revert, inline the variant's logic as an option rather than keeping
   two copies of the history handling.
3. presentation/CLAUDE.md, the caret sentence of the editor section: add that an edit from the menus' sheets and a save's
   respelling keep the caret and selection next to their text (`editedOffset`).

## Tests

`EditedOffsetTest`:
- a line inserted above the caret (`"a\nverse"` → `"a\n{tag: x}\nverse"`, caret in `verse`) moves by the inserted length;
- an offset before the change stays;
- an offset inside a replaced span (`"[Bb]"` → `"[B]"`, caret after `b`) lands right after the new chord name, before `]`;
- two chords respelled on lines 1 and 3 (`"[Bb]\nla la\n[Bb]"` → `"[B]\nla la\n[B]"`) with the caret mid-way in line 2
  stays at the same column of line 2 (this one fails with a single changed stretch, which sends it to the end of line 3);
- a caret at the end of line 3 in that example ends at the end of the new line 3;
- an empty `before` or `after`, and `before == after` (identity), and an offset past the end (clamped);
- a surrogate pair (`"😀"`) at the boundary of a change is not split: the mapped offset is never between its two halves.

## Manual check

Desktop, German notation: in a verse, type `[Bb]` on a line above the caret's line, put the caret mid-word below, press
Ctrl/Cmd+S: the field shows `[B]` and typing continues where the caret was. From the editor's menu add a tag with the
caret mid-verse; tap back into the field (or keep typing on desktop/web): the caret is still in that verse.
