# Hide the app under the export screen from screen readers while the screen is up

**Kind:** accessibility  ·  **Severity:** low  ·  **Platforms:** Android (TalkBack), iOS (VoiceOver), desktop and web screen readers
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireApp.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/PrintExportScreen.kt` (only for the transition's progress)

## Problem

The old export sheet was a window of its own, which screen readers treat as modal. The new `PrintExportHost` is
composed after `NavDisplay(` inside the same layout; its `Surface` swallows pointer input, but nothing removes the
`NavDisplay` subtree from the semantics tree. A TalkBack or VoiceOver swipe past the export screen's last control walks
into the song or list under it, and double-tapping one acts on the covered screen.

## Fix

In `CampfireApp.kt`, on the `NavDisplay` modifier chain, add
`Modifier.then(if (isPrintExportCovering) Modifier.clearAndSetSemantics { } else Modifier)`, where
`isPrintExportCovering` is `printExportTransition.progress.value >= 1f` — read it in a `derivedStateOf` so the app is
not recomposed on every frame of the slide. Only once fully covering, so that during a back gesture the screen being
revealed is not empty to an accessibility service mid-swipe. Do not use `invisibleToUser` on the whole tree if
`clearAndSetSemantics` is available on every target (it is common Compose).

## Tests

None: semantics are Compose UI.

## Manual check

TalkBack on Android and VoiceOver on iOS: open a song, Export to PDF, swipe right through every control: focus stays on
the export screen. Close it: the song's controls are reachable again.
