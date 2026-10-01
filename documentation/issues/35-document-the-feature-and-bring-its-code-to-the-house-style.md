# Document the feature in the module notes and bring its code to the house style

**Kind:** docs / code quality  ·  **Severity:** medium  ·  **Platforms:** —
**Files:** `presentation/CLAUDE.md`, `data/source/local/implementation/CLAUDE.md` (if it lists the preference fields), root `CLAUDE.md` (Printing), `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/PrintExportSheet.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayout.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintRenderer.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintPdfWriter.kt`, the print tests
**Challenged:** amended — the state holder must keep plan 26's saveable state saveable (a `remember`ed holder would silently undo it); the root Printing paragraph is updated for plans 21–34 as well; counts are taken at the time, and "unmodified" means unmodified by this plan.

Runs after every other lane has been merged (it touches all of their files); no behaviour change.

## Problem

`presentation/CLAUDE.md` says nothing about `ui/print`, the sheet or `DialogType.PrintExport`, which the code-style
skill requires of a module whose behaviour changed. The new files are the only ones in the module with wildcard
imports (`material3.*`, `runtime.*`, `resources.*`, `chordpro.model.*`) and unordered ones; no composable or model has
KDoc; no trailing commas; about 40 lines run past 140 columns at HEAD (recount after the other plans); `layoutPrintDocument`
is one ~200-line function of nested local functions and one-liners (`fun page() { pages.add(mutableListOf()); column = 0; y = margin }`);
the sheet's state is eight loose `remember`s.

## Fix

- Load the `code-style` skill and apply it to the four files and their tests: explicit sorted imports (only in these
  files, which the feature added; the skill's "don't reorder imports of files you touch" is about the older ones), KDoc
  on every declaration saying why, trailing commas, named arguments, lines within the width of the surrounding module.
- Split `layoutPrintDocument` into a private `PrintLayouter` class (cursor state + `place`) and one function per block
  kind (`lyricsRows`, `tabRows`, `gridRows`, `commentRows`, `recallRows`), no logic change — whatever tests exist when
  this runs (lane A's included) must pass **without being edited by this plan**. The suspension points and the memo
  that lane A plan 12 adds stay where they are in effect: a yield after each song and every 50 placed blocks.
- Gather the sheet's state into a `PrintExportState` holder built by one `rememberPrintExportState(dialog)` function.
  It must keep every field exactly as saveable or not as the plans before it made it (`selected` and the page index
  are `rememberSaveable`; `settings`, `source` and `attempt` are `remember(dialog)`): the function calls `remember` /
  `rememberSaveable` inside, field by field, rather than wrapping the whole holder in a `remember`, which would lose the
  selection on a rotation again.
- `presentation/CLAUDE.md`: a "PDF export" section — the pipeline (snapshot source → pure layout in points →
  renderer shared by preview and export → writer), what is deliberately not done (no selectable text, no platform print
  service), the styles and the chorus rule, the compression chosen, how options are saved (settled, flushed when the
  sheet goes), the progress and cancellation contract (Cancel only while rendering, the saved event, Share where
  `canShare`), how the file is named, and what the tests pin. Update the root Printing paragraph for what plans
  13–34 changed: font, compression, title, the share action on Android and iOS, the progress and cancellation, the
  debounced saving of the options, the file names.

## Tests

All print tests unchanged by this plan, and green.

## Manual check

None.
