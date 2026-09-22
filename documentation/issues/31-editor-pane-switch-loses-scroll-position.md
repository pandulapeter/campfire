# 31 · Switching the editor between Edit and Preview (or rotating a phone, or resizing across the split width) throws the text back to its first line

**Severity:** wrong behaviour (all platforms; likely on phones — Edit ↔ Preview is how a phone user checks their
work, and rotating a phone whose landscape width reaches the expanded class turns Edit into Split; on desktop and the
web every resize across 840dp; in a long song the user has to scroll back to where they were typing every time) ·
**Area:** `:presentation` (`SongEditorScreen.kt`: `LoadedSongEditor`, `ChordProTextField`, `SongPreview`)

## Symptom
1. On a phone (or a desktop window narrower than the expanded class), open a long song in the editor and scroll the
   text to line 80; tap there so the caret is on it.
2. Tap **Preview**, then **Edit**.

The text is back at its first line, unfocused, with the caret still on line 80 off screen; the preview, too, starts
from the top every time it is shown. The same happens without touching the choice:
- rotating a phone whose landscape window is ≥ 840dp wide (the editor's default choice is Split, so the narrow
  window shows Edit and the wide one Split): after the rotation the field is at the top;
- dragging a desktop / browser window across 840dp: every crossing rebuilds the field (scroll lost, focus lost —
  keystrokes stop reaching the text until it is clicked again) and the preview (the whole song is laid out again).

## Cause
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/SongEditorScreen.kt`

The two panes are called from two different places depending on the layout (`:441-455`):

```kotlin
if (hasSideBySidePreview) {
    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
        editor(Modifier.weight(1f).fillMaxHeight())
        VerticalDivider()
        preview(Modifier.weight(1f).fillMaxHeight())
    }
} else {
    AnimatedContent(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        targetState = panes == EditorPanes.PREVIEW,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
    ) { showPreview ->
        if (showPreview) preview(Modifier.fillMaxSize()) else editor(Modifier.fillMaxSize())
    }
}
```

and each pane keeps its scroll position inside itself: `ChordProTextField` at `:541`
(`val scrollState = rememberScrollState()`) and `SongPreview` at `:617` (`val scrollState = rememberScrollState()`).
A different call site is a different composition group, and `AnimatedContent` disposes the content of the state it
leaves, so each switch starts a pane with a fresh `ScrollState(0)` (`rememberScrollState` is `rememberSaveable`, but
saved state is only handed back to the same position in the composition). The field does not scroll itself back to
the caret either: `TextFieldCoreModifierNode.updateScrollState` (foundation 1.12.0) returns early unless the cursor is
showing, and the cursor only shows in a focused field — a new one is not focused.

The text and the undo history survive (the `TextFieldState` is hoisted), which is why nothing else looks wrong.

## Fix
Hoist both scroll states into `LoadedSongEditor`, where the `TextFieldState` already lives, and pass them down:

1. In `LoadedSongEditor`, next to `textFieldState` (after `:250`):

   ```kotlin
   // Owned here rather than by the panes, because a pane is composed again from scratch whenever the layout
   // changes - Edit and Preview are one AnimatedContent, Split is a Row - and a scroll position kept inside it
   // would go back to the first line on every switch, rotation and resize across the split width.
   val fieldScrollState = rememberScrollState()
   val previewScrollState = rememberScrollState()
   ```

2. `ChordProTextField` takes `scrollState: ScrollState` as a parameter instead of creating one at `:541`; everything
   below it (`respectsBottomInset`'s effect, `BasicTextField(scrollState = scrollState)`) uses the parameter. The
   `editor` lambda at `:409` passes `fieldScrollState`.
3. `SongPreview` takes `scrollState: ScrollState` instead of creating one at `:617`; the `preview` lambda at `:422`
   passes `previewScrollState`.

Only one field and one preview exist at a time in every branch (the `AnimatedContent` keys on a Boolean, so it never
holds two editors at once, and the Row branch replaces it without a transition), so a state is never shared by two
scrollables at once. A restored value larger than the new layout's range is coerced by `ScrollState` itself when the
field or the lyrics report their new `maxValue`.

Do **not**:
- wrap the panes in `movableContentOf` to keep them alive across layouts: moving a node detaches it, which clears its
  focus anyway, and the preview's `LookaheadScope`/`animateBounds` would animate every section in from the old pane's
  position — an animation that narrates nothing;
- request focus for the field whenever it comes back: on Android that raises the keyboard over a user who only wanted
  to look at the text; the position is what was lost, the caret is still in the state;
- key anything on `panes` to "reset" it.

## Tests
None (UI is untested).

## Verify
1. `./gradlew :app:desktop:run` with a window under 840dp: open a long song in the editor, scroll to its middle, tap
   Preview, scroll the preview somewhere, tap Edit — the text is where it was; tap Preview — so is the preview.
2. Widen the window past 840dp (Split) and narrow it again: both panes keep their positions.
3. Android (`./gradlew :app:android:assembleDebug`) on a phone whose landscape width is ≥ 840dp (e.g. a Pixel 7 Pro
   emulator): scroll the field, rotate both ways — the field keeps its position.
4. The bottom inset behaviour of the field (`respectsBottomInset`) is unchanged: scroll the field to its end with the
   keyboard up and down.
5. Compile checks: `./gradlew :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution`.

## Docs
`presentation/CLAUDE.md`, the editor bullet that begins "The screen itself is a `BasicTextField` over a
`TextFieldState`…": after "(its own `scrollState`, never wrapped in a `verticalScroll` — that swallows the press that
places the caret)" add "; that scroll state and the preview's are held by the screen next to the `TextFieldState`
rather than by the panes, since a pane is composed from scratch whenever Edit, Preview and Split change places and
would otherwise go back to the first line every time".

## Touches
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/SongEditorScreen.kt`
- `presentation/CLAUDE.md`

## Depends on
Nothing. 30, 20 and 11 edit other lines of the same file; schedule them one after another.
