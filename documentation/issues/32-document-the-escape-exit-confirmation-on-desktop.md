# Say in app/desktop's CLAUDE.md and CampfireDesktopApp's KDoc that Escape on the root screen asks before closing

**Challenged:** sound

**Kind:** docs  ·  **Severity:** low  ·  **Platforms:** desktop
**Files:** `app/desktop/CLAUDE.md`,
`presentation/src/desktopMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireDesktopApp.kt` (KDoc only)

## Problem

Since `1b26dfb94 Add close confirmation on desktop.`, the last branch of `handleKeyEvent`'s Escape handling in
`CampfireDesktopApp.kt` is:

```kotlin
when {
    isSetlistReordering -> navigateBack()
    search?.isOpen?.value == true -> search.close()
    backStack.size > 1 -> navigateBack()
    else -> confirmExit(onExit)
}
```

and `confirmExit` only shows `DialogType.ConfirmExit`; `CampfireViewModel.exitConfirmed` (its Close) is what calls
`requestExit`. `presentation/CLAUDE.md` was updated in that commit; two other descriptions were not:

1. `app/desktop/CLAUDE.md`, first paragraph: "… whose `onKeyEvent` is wired to `CampfireViewModel.handleKeyEvent`
   (Escape dismisses the visible modal, pops the back stack, or exits the application on the root screen) …", and
   second paragraph: "`onCloseRequest`, the Escape that would exit, and the macOS quit … all go through
   `CampfireViewModel.requestExit` … The window and Escape end it with `exitApplication`." Escape on the root screen
   no longer exits; it asks, and reaches `requestExit` only through the question's Close.
2. The KDoc of `CampfireDesktopApp` says Escape "dismisses whatever is open on top of the app - a dialog, a bottom
   sheet or an overflow menu - pops the back stack when there is none, clears the Songs search query on the root
   screen if it's not already empty, and otherwise asks whether to close the application". The code closes the open
   search of whichever list screen is on top (`currentSearch`, Songs or Setlists) — it does not clear a query — and
   does so *before* popping the back stack; the setlist reorder mode is left first of all.

## Fix

- `app/desktop/CLAUDE.md`, first paragraph: replace the parenthesis with "(Escape dismisses the visible modal, closes
  an open search, pops the back stack, or on the root screen asks whether to close the app — `DialogType.ConfirmExit`,
  see `presentation`)".
- Second paragraph: "`onCloseRequest`, the Close of the question Escape asks on the root screen, and the macOS quit
  … all go through `CampfireViewModel.requestExit` …", and "The window and that Close end it with
  `exitApplication`."
- `CampfireDesktopApp` KDoc: "… dismisses whatever is open on top of the app - a dialog, a bottom sheet or an
  overflow menu - and otherwise leaves the setlist reorder mode, closes the search of the list screen on top, or
  pops the back stack, in that order; with nothing left to go back from it asks whether to close the application
  ([CampfireViewModel.confirmExit])."

Wrap at the files' existing widths; no behavior changes.

## Tests

None: documentation only.

## Manual check

None beyond reading the result against `handleKeyEvent`.
