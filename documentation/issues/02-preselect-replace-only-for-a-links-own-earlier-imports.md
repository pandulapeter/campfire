# Preselect Replace in a shared link's review only for taken names that an earlier import of that same link wrote on this device, and Keep both for every other one

**Kind:** design decision (abuse of a default) · **Severity:** medium · **Platforms:** all
**Challenged:** amended — the record is keyed by the link's `fileId`, not its `shareId` (a crafted link can carry any
share id, e.g. one copied from a link posted publicly, but cannot make another link's file id serve its own bytes);
re-recording merges into the link's entry instead of replacing it (a song skipped as already there is not in the
result, so replacing dropped it and its next update started on Keep both); the read use case is `suspend`; and one
link's paths are capped too.
**Status:** NEEDS THE USER'S DECISION. Choose option (a), (b), (c), or (d) below before executing. The plan is written
for the recommended option, (a).
**Files:**
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/importing/ImportController.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/importing/ImportRequest.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/importReport/ImportReport.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/importReport/review/ImportReviewItems.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt` (wiring the new use case)
- `presentation/src/commonMain/composeResources/values/strings.xml`, `presentation/src/commonMain/composeResources/values-hu/strings.xml`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/importReport/CLAUDE.md`
- `data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/preferences/UserPreferences.kt`
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/model/UserPreferencesDocument.kt`
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/mapper/UserPreferencesMappers.kt`
- `domain/api/src/commonMain/kotlin/com/pandulapeter/campfire/domain/api/library/` (two new use case interfaces, beside `RememberDemoLibraryFilesUseCase.kt`)
- `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/library/` (their implementations)
- `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/state/importing/ImportControllerQueueTest.kt`
- `data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/mapper/UserPreferencesMappersTest.kt`

## Problem

`ImportController.kt` (in the `Preparation.Planned` branch of the preparation, at 5514fcf76) starts every taken name
of a shared link's review on Replace:

```kotlin
val review = ImportReport.Review(
    plan = plan,
    // Opening a shared link again is how its recipient updates, and Keep both would leave a numbered
    // copy of every changed song each time; Replace is still confirmed before anything is written.
    choices = if (sharedLink == null) ImportChoices() else ImportChoices(conflictResolution = ImportConflictResolution.REPLACE),
```

This was deliberate (6f3858277, "Open shared song and setlist links in the import screen, updating copies already
imported…"). The intended case is a recipient who imported a link earlier and opens it again to get the sharer's
newer version.

A file name is derived from the song's header (`artist-title.cho`), and the default does not check where the existing
file came from. A link from anyone that holds a song named like one the recipient already has (the demo songs, a
common standard) therefore opens with Replace selected for the recipient's own version. Identical files are skipped
as "already there", so only versions that differ reach this point.

Two screens stand between the default and the write, and neither tells a stranger's copy apart from the recipient's
own:
- Under the review's counts, `ImportReviewItems.kt` shows `import_review_shared_song_replaces`: "This replaces your
  copy of the song with the shared version. Choose Keep both to keep your own changes." (or the setlist variant). The
  wording assumes the recipient's file is a copy of the link's. Each row reads "Will replace the one you have: …".
- `confirmImport` → `DialogType.ConfirmImportReplace(count)`: "Replace N files? The files in your library are written
  over. This cannot be undone." This dialog names only a count.

Two taps (Import, then Replace) overwrite the user's own song or setlist, and the next sync run carries the
overwrite to every device. For the default to be safe, the recipient has to spot that the row names a file of their
own. The default does the opposite: it suggests the file is the link's.

## Options

- **(a) Recommended.** On this device, remember which library files each shared link's import wrote. Preselect
  Replace only for a taken name in that list for the same link, keyed by its Dropbox file id
  (`SharedLink.Readable.fileId`). Every other taken name starts on Keep both, as in any other import. A stranger's link
  has a different file id, so it can never preselect Replace. Not `shareId`: every part of a link is text the link
  carries, and a crafted link can copy a public link's share id while pointing `fileId` / `rlkey` at its own file; the
  file id decides which bytes `downloadUrl` serves, and it stays the same for as long as the link works (the sharer's
  runs update the copy in place, and a replaced share is a new link anyway). The intended update case keeps working with no extra tap on the device that imported the link.
  The cost is one device-local preferences field, following the `demoLibraryContentHashes` precedent, and two small
  use cases. A rename, another device, or a cleared preferences document falls back to Keep both, the safe default.
- **(b)** Option (a), plus the content hash each written file had (`localContentHash`, as `DemoLibraryRepositoryImpl`
  records the demo files). Preselect Replace only where the file still holds exactly those bytes. This also protects
  the recipient's own edits to an imported copy, but it needs a repository in `:data:sync:implementation` that reads
  and hashes files under `LibraryFileLock` at review time. The deliberate design already accepts overwriting edits
  after a warning, so the extra machinery buys little.
- **(c)** Every link starts on Keep both, like any other import, and the line under the counts says that Replace
  updates copies imported from the link before. This needs no new state: change one line, the strings, and the test.
  The cost is the update case: every update needs one change of the "For all of them" choice, and forgetting it leaves
  a numbered duplicate of every changed song.
- **(d)** Keep the current behaviour and accept the risk: it needs a social-engineering step and two explicit
  confirmations.

Recommendation: **(a)**. It is the smallest design that keeps the feature's intent, an update with no extra decision,
and closes the abuse completely. (c) is the fallback if the user prefers no new state. The rest of this plan
describes (a).

## Fix (option a)

1. **Record.** Add to `UserPreferences`, after `demoLibraryContentHashes` and with a KDoc in its style:

   ```kotlin
   /**
    * The library files the imports of each shared link on this device wrote under the names it asked for, by the
    * link's file id, as library paths (`songs/<file name>`, `setlists/<file name>`). A later import of the same link starts
    * those names on Replace. Never exported or synced.
    */
   val sharedLinkImports: Map<String, List<String>> = emptyMap(),
   ```

   Mirror the field in `UserPreferencesDocument`, with a default of `emptyMap()` so older documents read unchanged.
   Mirror it in both directions of `UserPreferencesMappers` the way `demoLibraryContentHashes` is mapped, dropping
   blank keys and paths. Do not add the field to `SyncedPreferencesDocument.OWNED_FIELDS`: it stays on this device. The
   record is bounded: keep at most `MAX_SHARED_LINK_IMPORTS = 100` links, and when a new one is added drop the oldest
   in insertion order. Re-recording a link moves it to the end. One link's paths are bounded too
   (`MAX_SHARED_LINK_IMPORT_PATHS = 1_000`, keeping the most recent), since one setlist link can hold any number of
   songs and `preferences.json` is rewritten on every preference change; a name past it falls back to Keep both.
   Both limits are single constants beside the use case implementation.

2. **Use cases**, following `RememberDemoLibraryFilesUseCase` / `RememberDemoLibraryFilesUseCaseImpl` (an `@Factory`
   with `operator fun invoke`, going through `UserPreferencesRepository`):
   - `RememberSharedLinkImportUseCase(linkFileId: String, songFileNames: Collection<String>, setlistFileNames: Collection<String>)`
     (`suspend`) **adds** these paths to that link's entry, a union and never a replacement, and never throws (wrap it
     as `DemoLibraryRepositoryImpl` does with `logger.recovering`, or catch and log). A replacement would forget every
     name the import did not write: an entry identical to the library is skipped as already there and is absent from
     `importedSongFileNames`, so reopening a link with one changed song would drop all the unchanged ones, and the
     sharer's next change to any of them would start on Keep both. A name the user unticked or kept both for stays
     recorded, which is right: the file under it is still the one this link wrote.
   - `GetSharedLinkImportUseCase(linkFileId: String): Set<String>` (`suspend`: it reads preferences, from the
     preparation coroutine) returns the recorded paths, or empty.

   Use the path format of `SyncKey.path` (`LibraryFileKind.id` + `/` + name) so that the record reads like
   `demoLibraryContentHashes`. Compare names with `LibraryFiles.isSameLibraryName`, never with `==` (root
   `CLAUDE.md`: case and Unicode form).

3. **Preselect.** In `ImportController`, read the link's file id where `sharedLink` is read today
   (`(request.source as? ImportSource.SharedLink)?.link?.fileId`). Build the choices as:

   ```kotlin
   ImportChoices(
       conflictResolutions = buildMap {
           plan.songs.forEachIndexed { index, entry ->
               if (entry.status == ImportPlan.Status.CONFLICTING && recorded.containsName("songs", entry.replacesFileName ?: entry.fileName)) {
                   put(ImportEntryKey(ImportEntryKey.Kind.SONG, index), ImportConflictResolution.REPLACE)
               }
           }
           // the same for plan.setlists with Kind.SETLIST
       },
   )
   ```

   `conflictResolution` stays at its `KEEP_BOTH` default, and `recorded` comes from `GetSharedLinkImportUseCase`.
   Factor the mapping into a small pure function next to the controller, such as `sharedLinkChoices(plan, recorded)`,
   so that it can be tested directly. Replace the code comment so that it says why only the link's own earlier
   imports start on Replace.

4. **Remember.** The file id must outlive `request.source = ImportSource.Files(emptyList())`. Add
   `sharedLinkFileId: String?` to `ImportRequest`, set where a `SharedLink` source is enqueued (in `importSharedLink`).
   This is cheaper than the bytes the source held. In `applyImportPlan`, beside the
   `if (request.isDemoLibrary) rememberDemoLibraryFiles(...)` block, call `RememberSharedLinkImportUseCase` when
   `request.sharedLinkFileId != null`. Pass the result's `importedSongFileNames` / `importedSetlistFileNames` that
   equal a plan entry's own name (`replacesFileName ?: fileName`). A numbered Keep-both copy is not the name a later
   import of the link asks for, so it is not recorded, the same rule as `DemoLibrary.songFileNamesWrittenBy`.

5. **Wording.** Show the line under the counts (`ImportReviewItems.kt`, today `if (isSharedLink && plan.hasConflicts)`)
   only when at least one row was preselected Replace, so pass that fact in place of, or beside, `isSharedLink`.
   Its text must no longer claim that every taken name is the user's copy of the link. Add new keys in both languages,
   for example `import_review_shared_updates` with `<plurals>` (`one` / `other`): "One song you imported from this
   link before is updated to the shared version. Choose Keep both to keep your own changes." /
   "%1$d files you imported from this link before are updated to the shared version. Choose Keep both to keep your own
   changes.", plus the Hungarian equivalents. **The `strings.xml` files may hold the user's uncommitted edits: add the
   new keys without touching, reordering, or reformatting any pending line.** Remove `import_review_shared_song_replaces`
   and `import_review_shared_setlist_replaces` only if they are not among the pending lines; otherwise leave them in
   place, unused, and say so in the commit. Load the `code-style` skill first, since it governs how strings are read.

6. **Docs.** In `ui/screens/importReport/CLAUDE.md`, rewrite the sentence "A shared link's review starts its taken
   names on **Replace** (opening a link again is how its recipient updates) with a line under the counts saying so"
   to describe the new rule: only names that this device's earlier import of the same link wrote, recorded in
   preferences and never synced. In `presentation/CLAUDE.md`, mention nothing more unless it already describes the
   default. The root `CLAUDE.md` library layout needs no change, because the record is inside `preferences.json`.

## Tests

`./gradlew :presentation:desktopTest :data:source:local:implementation:desktopTest`.

- `ImportControllerQueueTest`: split today's
  ``fun `a shared link downloads from the address it builds and is reviewed with taken names on Replace` ``:
  - ``fun `a shared link's taken name it wrote before starts on Replace` ``: the fake `GetSharedLinkImportUseCase`
    returns `songs/riverlight.cho` for `sharedLink.fileId`. Assert
    `review.choices.resolutionOf(ImportEntryKey(SONG, 0)) == REPLACE`, and that `confirmImport()` shows
    `DialogType.ConfirmImportReplace`. This test passes today too; it guards the update case.
  - ``fun `a shared link's taken name it never wrote keeps both` ``: the fake returns nothing, or the path under another
    link's file id (the same share id with another file id included: a crafted link). Assert `resolutionOf(...) == KEEP_BOTH` and that `confirmImport()` shows no dialog. **This fails at
    5514fcf76**, which yields `REPLACE` for every taken name of a link.
  - ``fun `a shared link's import remembers what it wrote under its own names` ``: resolve the review, then assert that
    the fake `RememberSharedLinkImportUseCase` received `sharedLink.fileId` and `riverlight.cho`, and no numbered name.
    It fails today because nothing is recorded.
  - Keep ``fun `a file imported any other way keeps both by default` `` as it is.
- If `sharedLinkChoices` is factored out, add a pure test of it covering a case-variant name (`Riverlight.cho`
  recorded, `riverlight.cho` planned) and a setlist entry.
- A new `RememberSharedLinkImportUseCaseImplTest` in `domain/implementation`'s `commonTest` (`library/`; the demo use
  case has none to copy, so use `RepositoryStubs.kt`'s `UserPreferencesRepository` stub): recording `songs/b.cho` for
  a link that already holds `songs/a.cho` keeps both. A replacing implementation fails it. Put the two limits' tests
  wherever the limits are applied (use case or mapper), not in both.
- `UserPreferencesMappersTest`: the new field round-trips through `toDocument()` / `toModel()`, an older document
  without it reads as empty, blank paths are dropped, and the 101st link drops the oldest. This test fails until the
  field exists.

For option (c) instead: change the `choices` line to `ImportChoices()`, reword the line under the counts, and change
the existing test's expected `REPLACE` to `KEEP_BOTH`. That updated test fails at 5514fcf76.
