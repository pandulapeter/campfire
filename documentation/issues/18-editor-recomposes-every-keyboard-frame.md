# Read the keyboard inset while laying out the editor, not while composing it

**Kind:** ui-performance  ·  **Severity:** low  ·  **Platforms:** android, ios
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/SongEditorScreen.kt

## Problem
The editor is handed the keyboard-aware padding (CampfireApp.kt:545-552 → `SongEditorScreen(contentPadding = …)`,
662) and takes its bottom apart while composing in four places, which is what `PaddingValuesSides`' KDoc
(components/PaddingValuesSides.kt:18-25) and presentation/CLAUDE.md ("a screen narrows it with `PaddingValues.only(…)`
rather than by taking it apart while composing, which would bring the recomposition back") say not to do:

- SongEditorScreen.kt:469-474 and 487-491 — the `editor` and `preview` lambdas build
  `PaddingValues(bottom = contentPadding.calculateBottomPadding())`;
- `ChordProTextField`, 662 — `keyboardPadding = contentPadding.calculateBottomPadding() - restingBottomInset`, used in
  the field's `Modifier.padding` at 699;
- `SongPreview`, 767 — `bottomPadding = contentPadding.calculateBottomPadding() + 32.dp`, used for the padding at 778
  **and** for `availableHeight` at 781.

On a phone or tablet the keyboard comes and goes constantly in the editor (tap into the text, dismiss it to read),
and on each of the ~15-20 frames of every slide:
1. `ChordProTextField` recomposes, i.e. the whole `BasicTextField` with its output transformation and modifier chain;
2. in Split (a tablet), `SongPreview` recomposes and, because `availableHeight` shrinks by the keyboard's height, the
   preview's `SectionGridKey` changes every frame, the grid is searched again, and the preview **reflows into more
   columns** while the keyboard is up (and back when it hides) even though its pane did not change size — the
   keyboard only covers its lower part, which the bottom padding inside its `verticalScroll` already lets the user
   scroll past.

## Fix
All in SongEditorScreen.kt:
1. `editor` / `preview` lambdas: replace the hand-built `PaddingValues(start = …, end = …, bottom = contentPadding.calculateBottomPadding())`
   with `contentPadding.only(start = true, end = !hasSideBySidePreview, bottom = true)` and
   `contentPadding.only(start = !hasSideBySidePreview, end = true, bottom = true)` (`components/PaddingValuesSides.kt`),
   which ask for the bottom only when laid out.
2. `ChordProTextField`: move the keyboard part of the bottom padding into layout. E.g. build one `PaddingValues` object
   (a small private class, or `remember`ed anonymous object) whose `calculateBottomPadding()` returns
   `(contentPadding.calculateBottomPadding() - restingBottomInset).coerceAtLeast(0.dp) + if (respectsBottomInset) endPadding else 0.dp`
   and whose start/end are the current values, and pass it to `Modifier.padding(paddingValues)`, which reads it during
   measure. `respectsBottomInset` is snapshot state, so reading it there keeps the existing loop-breaking logic
   (673-679) working, now at layout time.
3. `SongPreview`: compute `availableHeight` from the resting inset only
   (`WindowInsets.contentEdges.asPaddingValues().calculateBottomPadding() + 32.dp`, as `ChordProTextField` already
   does for `restingBottomInset`) so that the keyboard no longer changes the column count, and pass the scroll room as
   `contentPadding.only(start = …, end = true, bottom = true, extraBottom = 32.dp)` plus the 16dp sides and 8dp top
   (e.g. `Modifier.padding(horizontal/top …).padding(contentPadding.only(bottom = true, extraBottom = 32.dp))`), so it
   is read in layout.
4. Update the KDoc of `SongPreview` if the reflow decision is worth stating ("the keyboard only covers the preview, so
   it decides its scroll room and not its columns").

## Verification
Manual, Android tablet emulator in landscape (Split): open a song of about two columns in the editor, tap into the text
and dismiss the keyboard a few times. Before: the preview reflows with the keyboard; with the layout inspector's
recomposition counts on, `ChordProTextField` and `SongPreview` count up once per animation frame. After: the preview
keeps its columns and neither recomposes while the keyboard slides; the last line of the field still ends above the
keyboard and the caret stays visible while typing at the end of a long song (the behaviour 656-679 describes). Phone
portrait: the same check for `ChordProTextField` alone.

## Conflicts
plan 17 adds an effect to `LoadedSongEditor` in the same file (different function). plan 19 changes which
padding CampfireApp.kt hands the editor (renamed `songEditorContentPadding`) but not its type.
