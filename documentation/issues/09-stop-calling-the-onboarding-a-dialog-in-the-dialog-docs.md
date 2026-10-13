# Stop calling the onboarding a dialog where the documentation lists the dialogs put up against no dialog or left unsaved

**Kind:** docs  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CLAUDE.md`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/SavedDialog.kt`

## Problem

`ui/CLAUDE.md` says, about `setVisibleDialog` and the work parked behind a dialog:

> The three exceptions only ever replace no dialog, so there is nothing parked to drop: the song picker opened after a new setlist, the onboarding and the What's new dialog are each a compare-and-set against null.

The onboarding is no longer a dialog: it is a back stack destination, `CampfireDestination.Onboarding(isOpenedFromSettings)`, put there by `Navigator.openOnboarding` (`updateBackStack { add(CampfireDestination.Onboarding(...)) }`), and `DialogType` has no onboarding. The compare-and-set is `DialogHost.showIfNoneIsShown`:

```kotlin
fun showIfNoneIsShown(dialogType: DialogType) = _visibleDialog.compareAndSet(null, dialogType).also { isShown ->
```

and `grep -rn "showIfNoneIsShown" presentation/src/commonMain` finds exactly two callers: `FirstRunController` (`dialogHost.showIfNoneIsShown(DialogType.WhatsNew)`) and `SetlistsController` (`dialogHost.showIfNoneIsShown(DialogType.SongPicker(setlist))`).

The KDoc of `SavedDialog` (`ui/dialogs/SavedDialog.kt`) has the same leftover in its list of dialogs with no saved form: "…the tuner, which only ever listens from a tap, the onboarding and what's new, which the start decides on by itself, …". The onboarding is restored as part of the saved back stack (with its page, `ONBOARDING_STEP_KEY`), not left out of it, as `FirstRunController.openOnboardingOnFirstRun`'s KDoc says ("It is part of the saved back stack like any other screen").

## Fix

- `ui/CLAUDE.md`: "The two exceptions only ever replace no dialog, so there is nothing parked to drop: the song picker opened after a new setlist and the What's new dialog are each a compare-and-set against null (`DialogHost.showIfNoneIsShown`)."
- `SavedDialog.kt` KDoc: "…the tuner, which only ever listens from a tap, what's new, which the start decides on by itself, and the sheets that hold nothing typed (Song info, a chord's shapes, the song filters)." Optionally add a clause that the onboarding is a screen, saved with the back stack, rather than a dialog.

No behaviour changes.

## Tests

None: documentation only.

## Manual check

None.
