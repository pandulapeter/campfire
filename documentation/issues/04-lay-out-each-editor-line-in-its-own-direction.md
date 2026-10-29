<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Move the editor's caret by each line's own direction, so arrow keys read right on a directive or tab line of a right-to-left song

**Kind:** bug · **Severity:** medium · **Platforms:** all (arrow keys: desktop, web, and hardware keyboards on Android/iOS)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/ChordProTextField.kt`
(or `LoadedSongEditor.kt`, where `fieldModifier` is built), a new `EditorArrowKeys.kt` beside them, a new public helper in
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/edit/` with its test in `chordpro/src/commonTest`,
`tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/EditorDirectionTest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/CLAUDE.md`
**Challenged:** amended — option A (per-line direction marks with `TextDirection.Content`) cannot work on Skia, which
resolves one direction for the whole text, and its marks would shift every offset the field's layout reports; B is now
the recommendation, specified fully; `ChordProLineScanner` is `internal` to `:chordpro`, so the line kinds come from a
new public helper there.

**Decision:** awaiting the user (README, Decisions D1); the recommended option is B.

## Problem

Since 7c645dffc the editor field forces one paragraph direction on every line:

```kotlin
textDirection = if (isRightToLeft) TextDirection.Rtl else TextDirection.Ltr,
```

Compose's text field decides what Left and Right mean from the paragraph direction at the caret (`moveCursorLeft`:
`isLtr()` from `TextLayoutResult.getParagraphDirection`, Left = previous character in a left-to-right paragraph, next in
a right-to-left one; Cmd + Left on Apple, line-left, the same way). In a Hebrew song every `{start_of_verse}`,
`{title: …}`, `X:1`, or tab line is a right-to-left paragraph, but its text is one left-to-right run, so Left moves the
caret one character *later*, which is visually rightwards. An English song with a Hebrew lyric line gets the mirror.
And a line with no letters (an unnamed tab row `|---3---5---|`, a count row) lays out right to left in a Hebrew song,
its numbers reversed (`|---5---3---|`), while the reader keeps music left to right (`SongSectionContent`'s
`LocalLayoutDirection provides LayoutDirection.Ltr`).

## Why per-line direction (the former option A) is out

Checked against Compose 1.12.2 (`ui-text-desktop`, the Skia text used by desktop, iOS, and web):

- `SkiaParagraphIntrinsics` resolves `TextDirection.Content` once, from the first strong character of the **whole**
  text (`resolveTextDirection` → `firstStrongDirectionType`), and `ParagraphBuilder` hands that single direction to the
  Skia `ParagraphStyle`. `SkiaParagraph.getParagraphDirection(offset)` ignores the offset and returns it. A field's
  newlines do not start new Compose paragraphs (only `ParagraphStyle` ranges do, which is what showed the empty lines
  the comment above `textDirection` describes). So on three of the four platforms a mark per line would either turn the
  whole song by its first line's mark or change nothing for the arrow keys. Only Android's `StaticLayout` resolves
  per paragraph.
- An `OutputTransformation` that inserts characters makes the field's layout one of the *transformed* text:
  `onTextLayout`'s `TextLayoutResult` (read by `revealOffset` with `textFieldState.selection.min` and search match
  offsets, and by `EditorDirectionTest`) would be off by one per line above the caret, and the search highlights and
  token styles added by `ChordProOutputTransformation` (offsets of the original text) would need mapping too. Its KDoc
  promises "no characters are inserted or removed … no offset mapping is needed", which the rest of the editor relies on.

(An LRM inserted before a tab row *would* fix its display even under the forced direction, since the digits then follow
a strong left-to-right character; it is left out for the second reason above, and the reversed letterless rows stay a
known limitation, written down in the package `CLAUDE.md`.)

## Fix — options

- **B (recommended): arrow keys by the line's own direction.** Keep the forced `textDirection` and the layout direction
  as they are (the field's look and `EditorDirectionTest` stay unchanged). Add `Modifier.editorArrowKeys(textFieldState,
  songDirection: () -> WritingDirection)` in `EditorArrowKeys.kt`, an `onPreviewKeyEvent` chained into `fieldModifier`
  next to `editorFormattingShortcuts` (so it sees keys before the field and only along the focus path). On a key-down of
  Left or Right, find the line holding `selection.end`; if that line's own direction equals the song's, return `false`
  and let the field handle it. Otherwise handle it and consume the key (and its key-up):
  - the line's own direction: left to right for a directive, a `#` line, and any line `ChordProLiteralLines` calls
    literal (tab, grid, an environment handed to another program); for a lyric line `writingDirectionOf(line)` with
    chords in brackets left out, and for a comment directive (`comment`, `comment_italic`, `comment_box`, `highlight`
    and their short forms) `writingDirectionOf` its value, since its value is lyric-like text the reader shows. Put the line classification in `:chordpro` as a new public function beside
    `ChordProLiteralLines` (e.g. `ChordProLineDirections.directionOfLineAt(text, offset): WritingDirection`), built on
    `ChordProLineScanner` (which is `internal` there; `:chordpro` is `explicitApi()`), so `:presentation` never
    re-implements the parser's line rules.
  - Left on a left-to-right line = one step back, Right = one step forward; the mirror on a right-to-left line. A step
    is a grapheme: skip a surrogate pair whole and any following `CharCategory.NON_SPACING_MARK` /
    `ENCLOSING_MARK` / `COMBINING_SPACING_MARK` (Hebrew points), common Kotlin only.
  - with a non-empty selection and no Shift, collapse to its visually left / right end by the same rule (as Compose
    does); with Shift, move only the `end` and keep `start` as the anchor (`TextRange(start, newEnd)` through
    `textFieldState.edit { selection = … }`).
  - word steps (Ctrl + Left/Right; Alt on Apple platforms) the same, to the previous / next boundary between a run of
    letters-or-digits and anything else; Cmd + Left/Right on Apple to the line's start or end by the line's direction.
    If this grows past a small file, ship character steps first and leave word and line steps to the platform, saying
    so in the package `CLAUDE.md`.
  - leave Up/Down, Home/End (logical already), and any key with Ctrl + Alt (AltGr) alone.
  The text is read once per key press (one scanner walk of at most `LONG_DOCUMENT_LENGTH`), never per frame.
- **C: keep today's behaviour**, and document the reversed keys and letterless rows in the package `CLAUDE.md`.

Either way, keep the comment above `textDirection` (it remains true), and add to `songEditor/CLAUDE.md`'s field section:
every line is laid out in the song's direction; Left/Right follow each line's own direction (B); letterless rows of a
right-to-left song show reversed in the editor only.

## Tests

- `:chordpro` `commonTest` for the new helper: in a Hebrew song `{start_of_verse}`, `{title: …}`, a `#` line, a tab row,
  a grid row, and an abc line are left to right, a Hebrew lyric with `[Am]` first is right to left; in an English song a
  Hebrew lyric line is right to left and everything else left to right; an offset past the end is on the last line.
- `EditorDirectionTest` (UI, `tools/screenshots`): open the Hebrew test song's editor, put the caret inside
  `{title: Shir Laderech}` (`textFieldState`'s selection via `performTextInputSelection`), `performKeyInput { pressKey(Key.DirectionLeft) }`,
  and assert the offset decreased by one; Shift + Left extends the selection backwards; on a Hebrew lyric line Left
  increases the offset (the field's own behaviour, untouched). The existing two tests must pass unchanged.
