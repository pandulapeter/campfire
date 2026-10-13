# Say so in a snackbar when a restored app finds an import review it could not keep (awaiting the user's decision)

**Kind:** UX  ·  **Severity:** low  ·  **Platforms:** Android (process death); the web's reload at the import address is left out, see (a)
**Status:** awaiting the user's decision between the options below; the recommended default is option (a).
**Challenged:** amended — the restored back stack alone cannot tell an unfinished import from a finished one: the screen is on it while it shows a **Finished** result too (one that left something out, or a clean one reopened from **Details**), and a process ended behind that would have said "interrupted" about an import that was written. Option (a) now saves whether the report is unfinished next to the back stack and reads that instead; the web is left out, since a reload at `/import` cannot tell the two apart either.
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/SavedStateStore.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/importReport/ImportReport.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/messages/Message.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/messages/Messages.kt`,
`presentation/src/commonMain/composeResources/values/strings.xml`,
`presentation/src/commonMain/composeResources/values-hu/strings.xml`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/importReport/CLAUDE.md`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/navigation/CLAUDE.md`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/importReport/` (a new test, see Tests)

## Problem

The import screen is deliberately never restored (`Navigator.kt`, at 72d48deb7):

```kotlin
// The import screen shows what an import running in this process is doing or did, so a new process has
// nothing to put on it and comes back on the screen under it.
addAll(
    savedStateStore.restore<List<CampfireDestination>>(BACK_STACK_KEY)
        ?.filterNot { it == CampfireDestination.ImportReport }
        ?.takeIf { it.isNotEmpty() }
        ?: listOf(CampfireDestination.Songs),
)
```

and `ui/navigation/CLAUDE.md` says so ("`ImportReport` … is never restored (the view model's report is gone with the
process)"). What is not designed is the silence: an Android user who switched to the Files app mid-review (or took a
call) and whose process was ended comes back on the Songs screen with no review, no ticks, and no Replace choices, and
nothing says what happened (live run: `98-review-after-death.png`). The "open with" intent that started the import has
already been acted on, so the file is not offered again. On the web a reload at `…/import` goes the same way: the address
is cut off in `BrowserRoutes.validate` (`CampfireDestination.ImportReport -> destination.takeIf { hasImportReport }`).

The finding also names Back mid-review dropping the ticks without a question (`89-back-mid.png`). That part is by
design: the import screen's `CLAUDE.md` says "Leaving it … in `Failed` or `Review` drops the import, which needs no
confirmation since nothing has been written".

## Fix

Options:

- **(a) Recommended.** A snackbar on the first frame after a restore that lost an unfinished import: "The import was
  interrupted. Open the files again to import them." / "Az importálás megszakadt. Nyisd meg újra a fájlokat az
  importáláshoz." The back stack alone cannot say so: `ImportReport` is on it while the screen shows a **Finished**
  result too (an import that left something out, or a clean one reopened from the snackbar's **Details**), and a
  process ended behind that result lost nothing. So one flag is saved beside the back stack, in the same
  `SavedStateHandle` (empty on every real start, and on every platform but Android, see `SavedStateStore`'s KDoc).
  1. `ImportReport.kt`: `internal val ImportReport?.isUnfinished get() = this != null && this !is ImportReport.Finished`
     with a one-line KDoc (`Loading`, `Failed`, `Review`, and `Importing` all lose the import with the process; a
     report waiting behind the onboarding or a dialog counts too, since its import is lost the same way).
  2. `SavedStateStore`: `const val IMPORT_UNFINISHED_KEY = "importUnfinished"`.
  3. `CampfireViewModel` `init`, before any collector of `importReport` is launched: read
     `val wasImportInterrupted = savedStateStore.restore<Boolean>(IMPORT_UNFINISHED_KEY) == true`, then launch
     `importController.importReport.collect { savedStateStore.persist(IMPORT_UNFINISHED_KEY, it.isUnfinished) }`
     (read first, or the collector's first emission writes `false` over the restored value), then
     `if (wasImportInterrupted) messageSink.sendMessage(Message.ImportInterrupted)`. `Navigator` is unchanged: it
     still filters the import screen out of the restored stack as now.
  4. `Message.kt`: add `data object ImportInterrupted : Message` with a one-line KDoc; `Messages.kt`: map it to
     `stringResource(Res.string.import_interrupted)`.
  5. Web: left out. A reload at `…/import` is cut in `BrowserRoutes.validate` (`hasImportReport` false) whatever the
     screen showed, and the address cannot tell a review from a finished result, so a message there would be wrong
     after every finished import that was reloaded; Android is what loses a review to the system rather than to the user.
  6. Strings, both files, next to `import_failed`:
     `<string name="import_interrupted">The import was interrupted. Open the files again to import them.</string>` and
     `<string name="import_interrupted">Az importálás megszakadt. Nyisd meg újra a fájlokat az importáláshoz.</string>`.
  7. Docs: in `ui/navigation/CLAUDE.md` after "is never restored (the view model's report is gone with the process)" add
     ", which a snackbar says where the import had not been written yet (`Message.ImportInterrupted`, from a flag saved
     beside the back stack)"; in the import screen's `CLAUDE.md` add the same to the "Search and leaving" paragraph.
- **(b)** (a), and also confirm Back / Close mid-review when the user has changed any tick or conflict choice
  (`ImportChoices() != review.choices`), through a `DialogType.ConfirmDiscardImportReview`. This reverses the documented
  "needs no confirmation" rule, adds a dialog to every deliberate cancel, and the choices are quick to redo; not
  recommended.
- **(c)** Leave the behaviour as it is and only document the silence in `ui/navigation/CLAUDE.md`.

Keeping the review across process death (saving the plan) is not an option: the plan holds up to 24 MB of song text,
far past the saved state's Binder limit (see the 100,000-character cap in `ui/navigation/CLAUDE.md`).

## Tests

Option (a): add `ImportReportTest` (or extend the package's existing test of `ImportReport.kt` helpers, if one exists)
asserting `isUnfinished` is true for `Loading`, `Failed`, a `Review`, and `Importing`, and false for `Finished` (with a
result and with `null`) and for no report. The view model's read-before-persist order is checked by hand (below).

## Manual check

Android: start an import of a zip from Settings → Library → Import, untick a song on the review, press Home, run
`adb shell am kill com.pandulapeter.campfire.debug`, and open the app from Recents. It opens on the screen under the
import screen with the "The import was interrupted" snackbar. Open the zip again: the review starts fresh. A normal
start (not restored) shows no snackbar. Then import an archive that leaves something out, so that the **Finished**
result stays up, press Home, kill the process the same way, and reopen: no snackbar, since that import was written.
