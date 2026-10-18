# Let the web's file picker offer every file, as the other platforms' pickers do

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** web
**Files:** `presentation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/FilePicker.wasmJs.kt`,
`presentation/src/wasmJsMain/CLAUDE.md` (if it mentions the picker's filter),
`data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/library/LibraryFiles.kt` (the KDoc of
`LIBRARY_BACKUP_EXTENSIONS`), `data/source/local/implementation/CLAUDE.md` (the "Other apps' libraries" sentence
naming the web picker)

**Challenged:** amended — the trade-off the decision needs is stated (without `accept`, mobile browsers add camera and
photo choices to the picker), the `js` block's parameter goes too, and the import left unused once plan 10 also drops
`listenForDrops`' extensions is named (ktlint fails on it). No other effect: `accept` is read nowhere else.

**Decision awaiting the user** (README): keep the filter, or drop it. The recommendation is to drop it.

## Problem

Only the web filters what the picker offers:

```kotlin
/** Extensions rather than MIME types: `.cho` and friends have none, so a type filter would hide them all. */
private val ACCEPTED_TYPES = LibraryFiles.IMPORTABLE_EXTENSIONS.joinToString(separator = ",")
```

Android opens the picker with `*/*`, iOS with `UTTypeData`, and the desktop's dialog has no filter, so on those a
user can pick another app's backup under any name (`.backup`, `.onsong`, no extension) and the import reads it by
its bytes. On the web such a file is greyed out or hidden behind a "Custom files" switch, depending on the browser
and OS (Android Chrome maps extensions to MIME types and filters strictly), and can only be dropped. The import's
rule everywhere else is "no filter, report afterwards".

## Fix

Remove `ACCEPTED_TYPES`, the `accept` parameter of the private `pickFiles(accept)` and its `input.accept = accept`
line; the unknown files picked go through `ImportBudget.read` as drops already do. Reword the
`LIBRARY_BACKUP_EXTENSIONS` KDoc ("Named so that a picker offers them" is then only about a nested archive being
unpacked by name) and the one sentence in `data/source/local/implementation/CLAUDE.md` that says the web's picker
offers them.

Once plan 10 has also removed `listenForDrops`' `importableExtensions`, `FilePicker.wasmJs.kt` no longer uses
`LibraryFiles`: remove the import (whichever plan lands second does it), or `spotlessCheck` fails.

The cost, for the decision: with no `accept`, Android Chrome's chooser and iOS Safari's menu typically offer the camera
and the photo library beside the files (check on one phone when executing) (an `accept` naming no image or video type is what leaves them out today), one more
step before Files on a phone. Desktop browsers only lose the greyed-out entries.

If the decision is to keep the filter, drop this plan and instead add the names of the formats later plans teach
the import (`.onsong`, `.osb`, `.backup`) to `IMPORTABLE_EXTENSIONS` only where they are read by name.

## Tests

None can be written: the picker's filter is browser UI. Manual check only if executed: on the web build, Import
files offers a `.backup` file.
