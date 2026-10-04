# Dismiss only the "first setlist" sheet's own dialog when it closes, through dismissSheet

**Kind:** bug (latent)  ·  **Severity:** low  ·  **Platforms:** all
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/Dialogs.kt

**Challenged:** sound

## Problem
`SetlistPicker` (`Dialogs.kt` ~1593 at 800ebde0b) opens straight onto the New setlist sheet when the library has no
setlists yet, and closes it with:

```kotlin
val isCreatingFirstSetlist = rememberSaveable { setlists.isEmpty() }
var isNamingNewSetlist by rememberSaveable { mutableStateOf(isCreatingFirstSetlist) }
val closeNamingDialog = { if (isCreatingFirstSetlist) viewModel.dismissDialog() else isNamingNewSetlist = false }
...
SetlistDetailsDialog(..., onDismiss = closeNamingDialog, ...)
```

`SetlistDetailsDialog` hands `onDismiss` to `TextFieldBottomSheet` → `CampfireBottomSheet`, whose KDoc says
`onDismiss` "has to dismiss this sheet's own dialog and nothing else (`CampfireViewModel.dismissSheet`): it is called
from the end of a hide animation, by which time another dialog may have taken the sheet's place." Since c1000eff0 it
is called after the hide animation (and Material calls it for a replaced sheet's cancelled hide as well).
`viewModel.dismissDialog()` clears whatever `visibleDialog` is at that moment, so a dialog that replaced the
`SetlistPicker` during those ~300ms — the desktop close button's unsaved-changes question, an import's question, a
dialog from the Escape/Back path — would be closed unseen. Every other `visibleDialog` sheet already uses
`dismissSheet(dialog)`.

## Fix
```kotlin
val closeNamingDialog = { if (isCreatingFirstSetlist) viewModel.dismissSheet(dialog) else isNamingNewSetlist = false }
```

`dialog` is the `CampfireViewModel.DialogType.SetlistPicker` parameter of `SetlistPicker`. The non-first path (a local
flag) is unaffected.

## Tests
None: UI wiring only (`dismissSheet`'s own behaviour is a one-line equality check in the view model).

## Manual check
With no setlists: open a song's menu → Setlist assignments → the New setlist sheet opens; Create — it slides away and
the setlist exists with the song; repeat and close with ✕ — it slides away and nothing is created. On desktop, open it
and press the window's close button while it is sliding away after Create — the unsaved-changes / quit flow is not
swallowed.
