# Correct the browser history's onboarding comment and name the import download in the root module map

**Kind:** docs  ·  **Severity:** low  ·  **Platforms:** all (the first item the web)
**Challenged:** amended — an imported song does not go over the onboarding either: `ImportController.applyImportPlan` waits for `backStack.none { it is CampfireDestination.Onboarding }` before `openImportedSong`. What can still put a song over an open onboarding is the converted-song snackbar's **Open** (`Messages.kt` → `CampfireViewModel.openImportedSong`, no wait), tapped while an onboarding opened from Settings is up; the comment now names that.
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/navigation/BrowserRoutes.kt` (comments only),
`CLAUDE.md` (root; the module map only)

## Problem

1. `BrowserRoutes.validate` (at 72d48deb7) explains why an open onboarding entry is kept:

   ```kotlin
   // The onboarding is only ever opened by a first run or from Settings: an entry left by finishing, skipping
   // or closing it is not one to go forward into again, as an address names nothing for it either. One that is
   // still open stays, since a dropped file can put the import screen or a song over it, and a Forward back into
   // those must not close it.
   is CampfireDestination.Onboarding -> destination.takeIf { it == openOnboarding }
   ```

   Two parts are out of date. The import screen no longer goes over the onboarding: `ImportController.showImportReport`
   waits until `stack.none { it is CampfireDestination.Onboarding }` and the screen is pushed as the onboarding leaves.
   And the onboarding is opened by every start that still owes it, not only a first run
   (`FirstRunController.plantDemoLibraryOnFirstRun`: `else if (… isOnboardingPending == true) { … openOnboardingOnStartUp() }`).
   An imported song waits for it as well (`applyImportPlan` waits for `backStack.none { it is CampfireDestination.Onboarding }`
   before `openImportedSong`). A song can still be pushed over it by the snackbar's **Open** for a converted document
   (`Messages.kt` calls `CampfireViewModel.openImportedSong` → `Navigator.openImportedSong` at once), when that snackbar
   is still up as an onboarding is opened from Settings. The KDoc of `validate` repeats the first-run claim ("which only
   a first run or Settings opens").
2. The root `CLAUDE.md` module map lists what `:data:source:remote` does without the import download, although
   `data/source/remote/implementation/.../importDownload/ImportDownloadRemoteSourceImpl.kt` is a network call of that module:

   ```
   data:source:remote:api -> :implementation   the sync contracts and the Dropbox provider, the cover download
                                               and the MusicBrainz and iTunes searches (covers and song details);
   ```

## Fix

1. In `BrowserRoutes.kt` replace the comment with:

   ```kotlin
   // The onboarding is opened by a start up that owes it or from Settings, never by an address: an entry left by
   // finishing, skipping or closing it is not one to go forward into again. One that is still open stays, since a
   // song opened from an import's snackbar can be put over it, gone back from and gone forward into again, and that
   // Forward must not close it.
   ```

   and in the KDoc of `validate` replace "which only a first run or Settings opens" with "which only a start up that
   owes it or Settings opens".
2. In the root `CLAUDE.md` module map change the `:data:source:remote` lines to
   "the sync contracts and the Dropbox provider, the cover download, the MusicBrainz and iTunes searches (covers and
   song details), and the download of an import from an address (reached by nothing yet);" keeping the rest of the entry
   and the column alignment. Do not touch the opening paragraph's list of what reaches the network: the address import
   is reached by nothing, which `ui/screens/importReport/CLAUDE.md` already says.

## Tests

None: comments and documentation.

## Manual check

None.
