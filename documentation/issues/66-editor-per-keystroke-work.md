# 66 · The editor copies and scans the whole text several times per keystroke

**Severity:** low-medium (typing lag on a very long song) · **Area:** `:presentation` (`SongEditorScreen.kt`, `EditorToolbar.kt`)

`SongEditorScreen.kt:190–192` (`summarize(textFieldState.text.toString())`), :474 (`previewedText`), :539–541
(`ReportDraft`'s `snapshotFlow { textFieldState.text.toString() }`), `EditorToolbar.kt:101–103`
(`declaredMetadata(textFieldState.text.toString())`) and `ChordProOutputTransformation.transformOutput` each call
`toString()` on the `TextFieldState` and scan it. Correctness is fine (the editor keeps a local `TextFieldState`).

## Fix

1. One `derivedStateOf { textFieldState.text.toString() }` at the screen level, passed down as a `State<String>` /
   lambda, so the copy happens once per change; `summarize` and `declaredMetadata` derive from that.
2. `ReportDraft`: `snapshotFlow` over the same shared string.
3. If `ChordProParser.summarize` and `ChordProHeader.declaredMetadata` can take a `CharSequence`, add those
   overloads in `:chordpro` and skip the copy entirely; both only iterate lines.
4. The 300 ms preview debounce (`PREVIEW_DELAY_MILLIS`) already protects the render; leave it.
