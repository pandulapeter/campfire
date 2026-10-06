# Declare `adjustResize` on the activity so the keyboard never pans the window

**Kind:** bug (Android shell)  ·  **Severity:** medium  ·  **Platforms:** Android
**Files:** `app/android/src/main/AndroidManifest.xml`, `app/android/CLAUDE.md`

## Problem

`CampfireMainActivity` declares no `android:windowSoftInputMode` (`AndroidManifest.xml` at dac1d9d59):

```xml
<activity
    android:name=".CampfireMainActivity"
    android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|density|fontScale|keyboard|keyboardHidden|navigation"
    android:exported="true"
    android:launchMode="singleTask" />
```

and nothing sets one in code (`grep softInputMode`/`SOFT_INPUT` finds nothing in `app/` or `presentation/`). With the
mode unspecified Android picks *resize* only for a view hierarchy holding a scroll container, which a `ComposeView` is
not, so it picks **pan**: `dumpsys window` on the 360 × 640 dp emulator reports `sim={adjust=pan}`. Panning moves the
whole decor up until the focused caret clears the keyboard, on top of what the app already does itself.

The app is built to be laid out *under* the keyboard and to move its own content: `enableEdgeToEdge()` in
`CampfireMainActivity.onCreate`, and in `CampfireApp.kt`
`val ime = rememberUpdatedState(WindowInsets.ime)` feeding `KeyboardAwarePadding` for `shellContentPadding`,
`songEditorContentPadding` and `messagesPadding` ("the app is laid out under the keyboard rather than resized by it").
The editor's field ends above the keyboard through that padding, and `SongEditorScreen` derives
`isKeyboardVisible` / `isTypingInShortWindow` from `WindowInsets.ime`. Pan on top of that shifts the window a second
time: in the portrait editor with the keyboard up, the app bar is pushed under the status bar (title row level with the
clock; `android/30_editor_keyboard.png`, `android/35_editor_kb_low_line.png`), and in landscape the title row is cut off
at the top edge (`android/46_land_editor_kb3.png`). A side effect seen after closing the keyboard: the field's
horizontal scroller was left offset (`android/36_x.png`).

Compose's own guidance for `WindowInsets.ime` / `imePadding` is `adjustResize`: with an edge-to-edge window on API 30+
it does not resize anything and only lets the IME insets (and their animation) through untouched; on API 28–29 (minSdk
is 28) it is what makes the IME arrive as an inset at all, so on those versions `WindowInsets.ime` is likely 0 today and
the editor's keyboard-dependent behavior (folding the Shortcuts, compact title row) never triggers, the pan doing all
the work.

## Fix

1. Add `android:windowSoftInputMode="adjustResize"` to the `CampfireMainActivity` element, with a line in its XML
   comment block: the app follows the keyboard through `WindowInsets.ime` (the content padding in `CampfireApp`), so the
   system must neither pan the window nor resize it; on an edge-to-edge window `adjustResize` only passes the insets
   through.
2. `app/android/CLAUDE.md`, `CampfireMainActivity` paragraph: add one sentence — it declares `adjustResize`, because
   an unspecified mode is taken as `adjustPan` for a Compose window and the pan comes on top of the app's own keyboard
   padding.

Drop this plan if, on reading, an `adjustResize` turns out to be set anywhere else at runtime (theme
`android:windowSoftInputMode` in `res/values*/themes.xml`, or `window.setSoftInputMode` in the activity) — at
dac1d9d59 it is not.

Bottom sheets are unaffected: Material 3's `ModalBottomSheet` (the only modal window, `Dialogs.kt`'s
`CampfireBottomSheet`) runs in a dialog window of its own with its own soft input mode, so the manifest attribute does
not reach it. They are on the re-check list anyway in case something relied on the pan behind them.

## Tests

None: a manifest attribute and window behavior; nothing pure to test.

## Manual check

On the 360 × 640 dp emulator (API 36/37) and on an API 28 or 29 emulator:
1. `adb shell dumpsys window | grep -A3 CampfireMainActivity` shows `adjust=resize`.
2. Editor, portrait: tap a line near the bottom of the text with the keyboard down — the app bar stays below the status
   bar, the field ends above the keyboard and the caret line is scrolled into view by the field itself.
3. Editor, landscape (360dp tall): the title row is whole at the top (the status bar is hidden by
   `CompactKeyboardEffect`); with plan 50 the field shows three lines.
4. Close the keyboard: the field's horizontal scroll is where it was.
5. Re-check every place text is typed into in the activity window: the Songs and Setlists list searches (results end
   above the keyboard, the search field is not pushed off), the import report's search, and the snackbar of a failed
   save over the editor (it sits above the keyboard).
6. Re-check every text sheet (they are dialog windows, expected unchanged): New song, Edit song details, Manage tags /
   languages / links, the setlist details sheet (title, description, date), Choose songs / Choose setlists search, the
   cover art search sheet (both tabs), the Song defaults sheet, the library deletion sheet (typing `DELETE`).
7. On API 28/29: in the editor, opening the keyboard folds the Shortcuts and the field ends above the keyboard (it did
   not before if `WindowInsets.ime` was 0 there).
