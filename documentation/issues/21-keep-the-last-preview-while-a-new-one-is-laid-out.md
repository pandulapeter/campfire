# Keep the last preview on screen while a new one is laid out, and fade between them

**Kind:** UI  ·  **Severity:** high  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/PrintExportSheet.kt`
**Challenged:** amended — a held document must never be exportable once the options have moved on (Save is enabled only for a document laid out from the current inputs); crossfade the page's canvas, not the pager plan 25 adds; no debounce for the first layout; edit the `produceState` block minimally because lane A plan 12 edits its `layoutPrintDocument` call.

## Problem

```kotlin
val document by produceState<PrintDocument?>(null, chosenSource, settings, labels, renderer) {
    value = null
    layoutFailed = false
```

Every option change empties the document, and `PrintPreview` hard-swaps the page for a `CircularProgressIndicator`
(`if (document == null || document.pages.isEmpty())`). Stepping the text size flashes page → spinner → page once per
step; ticking a checkbox or a song does the same. The Save button swaps its label for a spinner the same way, changing
width. This breaks both house rules: user-caused changes are animated, and nothing flickers through a loading state.

## Fix

- Do not reset `value`; hold the previous document until the new one is ready. Reset it (to null) when `chosenSource`
  is null or has no songs, since there is then nothing for the old page to stand in for.
- **The held document is a stand-in for looking at, never for exporting.** Keep it together with the inputs it was laid
  out from (`PrintSource`, `PrintSettings`, `PrintLabels`) and treat it as *current* only when those equal the sheet's
  present ones. Save (and Share, plan 28) are enabled only for a current document. Do not derive this from an
  `isLayingOut` flag set inside the producer: the producer starts a frame after the option changed, and in that frame
  a tap on Save would export the old page with the old options. `isLayingOut` for the indicator is `!current`.
- `DelayedLoadingIndicator` (as other screens do) over the stale page while `!current`; it already waits 300 ms.
- `AnimatedContent` with `fadeIn() togetherWith fadeOut()` (as `CoverArtSearchSheet` does) between the loaded /
  empty-selection / failed states (keyed by the kind of state, not by the document), and a `Crossfade` around the
  page's **canvas only** between one document and the next, keyed by a generation counter rather than by the
  `PrintDocument` (a structural `==` over tens of thousands of `PrintText`s is not a key). The pager and the zoom of
  plan 25 must not sit inside a crossfade that remounts them on every option change.
- The Save button's label/spinner is crossfaded inside a fixed-size box. Plan 23 moves the button to the bottom row;
  it carries this box with it.
- Debounce layouts by 120 ms (`delay` at the top of the producer, cancelled by the next change) **only when there is
  already a document to show**; the first layout starts at once.
- Keep the edit to the producer small: remove `value = null`, add the delay and the bookkeeping above/around the
  existing `try`, and leave the `withContext(Dispatchers.Default) { layoutPrintDocument(...) { ... } }` lines as they
  are, since lane A plan 12 changes exactly those.

## Tests

None (UI).

## Manual check

On desktop and a phone: change every option quickly; the page never blanks, changes fade, the Save button keeps its
size while exporting. Change the columns and tap Save within a frame: the saved file has the new columns (or Save
was still disabled).
