# Release the picked files' bytes once the import has been planned

**Challenged:** amended — the clearing goes into the `finally` of the try around the preparation (after 27-let-the-user-cancel-an-import-while-it-is-being-prepared.md, next to `preparation = null`), so success, failure and a cancelled preparation all release it; verified that nothing reads `request.files` after planning (KEEP_BOTH/REPLACE, setlist replanning, snackbar Details and Open of a converted song all work from the plan and the `ImportResult`).

**Kind:** performance (memory)  ·  **Severity:** low  ·  **Platforms:** all, mostly web/iOS
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`

## Problem
`ImportRequest(val files: List<ImportedFile>, ...)` holds up to 24 MiB of picked bytes. After `prepareImport` the plan
holds what is needed (decoded texts), yet the request stays reachable: `PendingImport(plan, request)` for as long as the
Review question is open (indefinitely), and the consumer loop's `request` local across `awaitImportSettled()` until
the report screen is left. Afterwards `request.files` is read nowhere (`applyImportPlan` uses only
`shouldAnnounceResult` and `shouldOpenSong`; the only other read is the `request.files.isEmpty()` guard at the top of
`import()`).

## Fix
Make the field `var files: List<ImportedFile>` (private class, so no API change; touched on the main thread only) and set
`request.files = emptyList()` in the `finally` of the `try` around the preparation, so every branch — planned, failed,
cancelled — lets go of it. The `async` of 27-let-the-user-cancel-an-import-while-it-is-being-prepared.md receives the
list as an argument when it starts, so clearing the field afterwards does not affect it; without plan 27, the `finally`
is added to the existing `try` around `prepareImport(...)`. The `request.files.isEmpty()` guard at the top of
`import()` is unaffected, since each request is imported once. Comment why: the plan carries
everything; the request lives as long as a question does. Do this after plan 27-let-the-user-cancel-an-import-while-it-is-being-prepared.md
if both are done (same function), keeping the cleared assignment next to the `await()`.
Drop this plan if a profile shows nothing holds the list anyway (e.g. the platform picker keeps its own reference).

## Tests
None: it is a reference being dropped inside a view model.

## Manual check
Import a 20 MB PDF with a conflict, leave the Review question open, and watch the heap (Android Studio profiler) fall
after the question appears compared with before.
