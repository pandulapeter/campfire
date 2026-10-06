# Write only the setlist details the Edit setlist sheet changed, and say so when the setlist is gone

**Kind:** bug (data loss) + ux  ·  **Severity:** low  ·  **Platforms:** all
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt,
presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/Dialogs.kt,
presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/SetlistDetailsEdit.kt (new),
presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/SetlistDetailsEditTest.kt (new),
presentation/CLAUDE.md

This plan covers two findings, because one rewrite of `editSetlist`'s guard and arguments fixes both: "Edit setlist
writes all four fields from the snapshot taken when the sheet opened" and "Saving the setlist edit sheet (or duplicating)
after a sync run removed the setlist does nothing and says nothing".

## Problem

1. **Stale fields written back.** The Edit setlist sheet (`SetlistDetailsDialog` for `DialogType.EditSetlist`,
   `Dialogs.kt:301-321`) is seeded from `dialog.setlist`, the snapshot taken when it opened, and its Save writes all four
   values:
   ```kotlin
   onConfirm = { setlistTitle, description, date, isCountdownShown ->
       viewModel.editSetlist(setlistFileName = dialog.setlist.fileName, title = setlistTitle, description = description, date = date, isCountdownShown = isCountdownShown)
   ```
   `CampfireViewModel.editSetlist` (`:3487-3496`) passes them on to `EditSetlistUseCase`, and
   `SetlistRepositoryImpl.renameSetlist` writes `setlist.copy(description = description, date = date, isCountdownShown =
   isCountdownShown)` plus the title. Scenario: open Edit setlist and leave it open while a sync run brings in another
   device's new description or date; change only the title and Save. The other device's description/date are reverted
   and the revert is synced back out. `setSongMetadata` (`:1967-1975`) avoids exactly this for songs by writing only the
   fields that differ from what was offered.
2. **Silent no-op when the setlist is gone.**
   ```kotlin
   fun editSetlist(...) = launchLibraryChange {
       if (setlists.value.firstOrNull { it.fileName == setlistFileName }?.isArchived != false) return@launchLibraryChange
       editSetlist.invoke(...) ?: sendMessage(Message.OperationFailed)
   }
   fun duplicateSetlist(setlist: Setlist, ...) = launchLibraryChange {
       ...
       val current = setlists.value.firstOrNull { it.fileName == setlist.fileName } ?: return@launchLibraryChange
   ```
   A setlist deleted by a sync run while its Edit or Duplicate sheet is open makes `null?.isArchived != false` true, so
   Save returns before the use case and the `?: sendMessage(OperationFailed)` written for "gone by now" never runs; the
   sheet closes as if it had saved. Duplicate does the same. `setSetlistSongs` and the transposition writes report the
   same situation with `OperationFailed`. (The setlist sheets are deliberately not closed by the view model when their
   setlist leaves — presentation/CLAUDE.md: "the setlist dialogs are not, since the song picker opens on a setlist the
   library has not caught up with yet" — so a message is the right answer, not closing the sheet.)

## Fix

1. New pure helper `ui/SetlistDetailsEdit.kt`:
   ```kotlin
   /** What the setlist details sheet says about a setlist: the four things a user writes about it. */
   internal data class SetlistDetails(val title: String, val description: String, val date: LocalDate?, val isCountdownShown: Boolean)

   internal val Setlist.details get() = SetlistDetails(title = title, description = description, date = date, isCountdownShown = isCountdownShown)

   /**
    * [chosen] where the sheet changed a value from what it [offered], and [current] - the setlist as the library has it
    * now - everywhere else, so that a value another device changed while the sheet was open and the user left alone is
    * kept. Text is compared trimmed, as it is written trimmed.
    */
   internal fun mergedSetlistDetails(offered: SetlistDetails, chosen: SetlistDetails, current: SetlistDetails) = SetlistDetails(
       title = if (chosen.title.trim() != offered.title.trim()) chosen.title else current.title,
       description = if (chosen.description.trim() != offered.description.trim()) chosen.description else current.description,
       date = if (chosen.date != offered.date) chosen.date else current.date,
       isCountdownShown = if (chosen.isCountdownShown != offered.isCountdownShown) chosen.isCountdownShown else current.isCountdownShown,
   )
   ```
   `Setlist.date` is `LocalDate?` (undated setlists, the demo one), so `SetlistDetails.date` is `LocalDate?` too. The
   sheet always confirms a date, so for an undated setlist the offered `null` differs from the chosen day and the chosen
   day is written — exactly what Save does today; the repository call keeps its non-null `date` parameter
   (`details.date ?: date`, which can only be null-free here because `chosen.date` is non-null whenever `offered.date`
   is null).
2. `CampfireViewModel.editSetlist`: take what the sheet offered, and split the guard:
   ```kotlin
   fun editSetlist(offered: Setlist, title: String, description: String, date: LocalDate, isCountdownShown: Boolean) = launchLibraryChange {
       val current = setlists.value.firstOrNull { it.fileName == offered.fileName } ?: return@launchLibraryChange sendMessage(Message.OperationFailed)
       if (current.isArchived) return@launchLibraryChange
       val details = mergedSetlistDetails(offered = offered.details, chosen = SetlistDetails(title, description, date, isCountdownShown), current = current.details)
       if (details == current.details) return@launchLibraryChange
       editSetlist.invoke(fileName = offered.fileName, title = details.title, description = details.description, date = details.date ?: date, isCountdownShown = details.isCountdownShown)
           ?: sendMessage(Message.OperationFailed)
   }
   ```
   and update its KDoc ("only the fields the sheet changed are written; the rest are the library's as they are now").
   `Dialogs.kt`'s EditSetlist `onConfirm` passes `offered = dialog.setlist`.
3. `duplicateSetlist`: `?: return@launchLibraryChange sendMessage(Message.OperationFailed)` in place of the bare return.
   (The copy is not made from the sheet's snapshot, as its KDoc says: keep that.)

`setlists.value` can lag the repository by one read, the same window every other sheet here accepts; the repository
still applies the write to `latest(fileName)`. If a tighter merge is ever wanted it belongs in
`SetlistRepositoryImpl.renameSetlist` (nullable fields meaning "keep"), which is lane B's — not needed for this plan.

presentation/CLAUDE.md: where `editSetlist` is mentioned (the archived-setlist paragraph says the view model refuses
`editSetlist` for an archived setlist), add that the sheet writes only what it changed and that a setlist gone by Save
time is reported as a failed operation, not recreated.

## Tests

`SetlistDetailsEditTest`: offered `(T, D, day1, false)`, current `(T, D2, day2, false)` (another device changed the
description and the date):
- chosen `(T2, D, day1, false)` → `(T2, D2, day2, false)`;
- chosen `(T, D3, day1, false)` → `(T, D3, day2, false)`;
- chosen equal to offered → equal to current;
- a title changed only by surrounding whitespace counts as unchanged.

## Manual check

Two devices. Device 1: open Edit setlist on S. Device 2: change S's description and sync. Device 1: let a sync run
finish (Sync now from Settings on a wide window, or wait), change only S's title and Save: the description is device 2's.
Then on device 1 open Edit setlist on S, delete S on device 2 and sync it into device 1, tap Save: a "could not be
saved" message appears and S is not recreated. Same for Duplicate.
