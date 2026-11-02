# Move applying an import plan, with its bookkeeping, into `:domain`, and the shared-link trust rule below `:presentation`

**Kind:** structure · **Severity:** medium · **Priority:** P2 · **Platforms:** all · **Lane:** P (after 14, before 16) · **Effort:** M
**Files:** `domain/api/src/commonMain/kotlin/com/pandulapeter/campfire/domain/api/importing/ApplyImportUseCase.kt` (new),
`…/domain/api/library/RememberDemoLibraryFilesUseCase.kt`, `…/domain/api/library/RememberSharedLinkImportUseCase.kt`
(both removed), `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/importing/ApplyImportUseCaseImpl.kt`
(new), `…/domain/implementation/library/RememberDemoLibraryFilesUseCaseImpl.kt`, `RememberSharedLinkImportUseCaseImpl.kt`,
`data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/importing/ImportBookkeeping.kt` (new, with
`SharedLinkPart`), `…/data/model/importing/SharedLinkChoices.kt` (moved from `:presentation`),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/importing/ImportController.kt`,
`SharedLinkChoices.kt`, `SharedLinkPart.kt` (both moved out), `ui/firstRun/DemoLibrary.kt`, `ui/CampfireViewModel.kt`,
tests: `presentation/…/ui/state/importing/SharedLinkChoicesTest.kt` (moves to `data/model/src/commonTest/…/importing/`),
`ImportControllerQueueTest.kt`, `domain/implementation/src/commonTest/…/library/RememberSharedLinkImportUseCaseImplTest.kt`,
`domain/implementation/importing/CLAUDE.md`, `ui/state/importing/CLAUDE.md` (if it names these), `presentation/CLAUDE.md`

**Challenged:** amended — no `GetSharedLinkChoicesUseCase`: adding a link to an open review reads the record *before*
its last read of the review ("read after the last suspension, so that a tick made while the batch was read is kept"),
and the choices need the merged plan computed after that read, so a suspending use case taking the plan would either
lose ticks or need a second read. The trust rule (`sharedLinkChoices` and the two `…WrittenBy`) moves verbatim to
`:data:model` beside `ImportChoices` instead, pure and below `:presentation`; `GetSharedLinkImportUseCase` stays.

## Problem

`ui/state/importing/ImportController.kt` (920 lines, 19 constructor parameters, 8 of them lambdas) is the import's
queue, its prepare → review → apply state machine, and its navigation, and it also composes three domain writes into
one, deciding in `:presentation` that they must not be cut off:

```kotlin
val result = withContext(NonCancellable) {
    importFiles.invoke(plan = plan, choices = choices) { if (request.shouldAnnounceResult) _importProgress.value = it }
        .also { result ->
            if (request.isDemoLibrary) {
                rememberDemoLibraryFiles(
                    songFileNames = DemoLibrary.songFileNamesWrittenBy(result),
                    setlistFileNames = DemoLibrary.setlistFileNamesWrittenBy(result),
                )
            }
            sharedLinkParts.forEach { part ->
                val linkPlan = part.of(plan)
                rememberSharedLinkImport(
                    linkFileId = part.fileId,
                    songFileNames = sharedLinkSongFileNamesWrittenBy(linkPlan, result),
                    setlistFileNames = sharedLinkSetlistFileNamesWrittenBy(linkPlan, result),
                )
            }
        }
}
```

The shared-link trust rule — a stranger's link starts on Replace only for names that link wrote on this device, never
for the recipient's own songs (`ui/state/importing/SharedLinkChoices.kt`, `sharedLinkChoices(plan, recorded)`) — is a
safety rule about the library, yet lives in the UI layer, and the domain hands `:presentation` the raw recorded paths
(`GetSharedLinkImportUseCase`) to apply it.

## Fix

1. **`ApplyImportUseCase`** (`domain/api/importing`):
   `suspend operator fun invoke(plan: ImportPlan, choices: ImportChoices, bookkeeping: ImportBookkeeping, onProgress: (ImportProgress) -> Unit = {}): ImportResult`.
   Its impl runs, under `withContext(NonCancellable)` with the comment that justifies it moved from the controller,
   exactly today's sequence: `ImportFilesUseCase`, then the demo record, then one shared-link record per part.
   KDoc: once started, the import and its records are written whole; an exception from storage (a
   `CancellationException` included, as `ImportFilesUseCaseImplTest` checks) still propagates.
2. **`ImportBookkeeping`** (`data/model/importing`):
   `data class ImportBookkeeping(val demoSongFileNames: Set<String>? = null, val demoSetlistFileNames: Set<String>? = null, val sharedLinkParts: List<SharedLinkPart> = emptyList())`,
   with `SharedLinkPart` (today `ui/state/importing/SharedLinkPart.kt`, its `of(plan)` included) moved beside it. The
   demo names stay `DemoLibrary`'s, passed in by the controller, so the filter
   (`result.importedSongFileNames.filter { it in songFileNames }`, `… == SETLIST_FILE_NAME`) is the same; it moves into
   the impl verbatim.
3. **The trust rule leaves `:presentation`.** `SharedLinkChoices.kt` (`sharedLinkChoices`,
   `sharedLinkSongFileNamesWrittenBy`, `sharedLinkSetlistFileNamesWrittenBy`, `containsName`) moves verbatim into
   `data/model/importing/`, beside `ImportChoices` and `ImportPlan` (pure, like `LibraryFiles`), `internal` widened to
   public only where `:presentation` or `:domain:implementation` calls it; its test moves with it. The controller keeps
   calling `sharedLinkChoices(plan, getSharedLinkImport(it))` at the same places, so the read of the record stays before
   the review is read; `ApplyImportUseCaseImpl` calls the two `…WrittenBy`.
4. **`ImportController`** loses `importFiles`, `rememberDemoLibraryFiles`, and `rememberSharedLinkImport` (19 → 17
   parameters) and gains `applyImport`. `RememberDemoLibraryFilesUseCase` and `RememberSharedLinkImportUseCase` leave
   `domain/api`; their impls stay as `internal` classes the new impl takes (Koin `@Factory`, still annotated), so their
   tests stay as they are. `CampfireViewModel`'s constructor loses three use cases and gains one.
5. Docs: the importing `CLAUDE.md` of `domain/implementation` gains the trust rule (one line: "a link's review starts on
   Replace only for names that link wrote here", pointing at `SharedLinkChoices.kt` in `:data:model`); `presentation/CLAUDE.md`'s import section points at the use case.

Order with plan 16: this plan first; it removes the `NonCancellable` from `ImportController` that plan 16 would
otherwise have to move.

## Tests

- `SharedLinkChoicesTest` moves to `data/model/src/commonTest/…/importing/` unchanged.
- `ApplyImportUseCaseImplTest` (domain, with the module's shared repository fakes):
  `` `a demo import records the demo files it wrote under their own names` ``,
  `` `each shared link records only the names it wrote, not a numbered copy` ``,
  `` `cancelling the caller does not stop the import or its records` `` (cancel the calling job after the first
  progress callback; the result and both records are still written).
- `ImportControllerQueueTest`: replace the three stubs with `applyImport`; its assertions stay.
