# Refuse to select a tab whose feature is switched off, even from its item while it shrinks away

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt,
presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireApp.kt
**Challenged:** amended — step 2 now keeps a leaving item's colours (a disabled item is drawn dimmed, and the wide
rail's `WideNavigationRailItem` swaps colours in one frame with no animation); the race with a switch turned back on
is shown not to arise.

## Problem

`components/NavigationItemPresence.kt:33-45` keeps a navigation item composed while it leaves:
```kotlin
if (state.currentState || state.targetState) {
    val presence = rememberTransition(state).animateFloat(...)
    content { presence.value }
}
```
and `collapsingNavigationItem` only fades and shrinks it; nothing disables input. The three chromes in `CampfireApp.kt`
(`WideNavigationRailItem` ~:1119, `NavigationRailItem` ~:1141, `NavigationBarItem` ~:1155) all keep
`onClick = { onDestinationSelected(destination) }`, wired to `viewModel::selectTopLevelDestination` (`:518`, `:631`),
which does not check that the destination is still offered (`CampfireViewModel.kt:1653-1673`):
```kotlin
fun selectTopLevelDestination(destination: CampfireDestination.TopLevel) { ... updateBackStack { clear(); add(Songs); add(destination) } }
```
Scenario: Settings → Features, switch Metronome (or Setlists) off and tap that item while it is still shrinking (the
effects spring, a few hundred ms). The app opens the Metronome (or Setlists) tab of a feature that is off: fully usable,
with no selected item in the chrome, and on the web the address becomes `/metronome`. Root CLAUDE.md promises a
switched-off tab is "gone everywhere", and the back stack/address paths are already cut at the first disabled screen
(`NavigationState.withoutDisabledFeatures`); this one path is not. Monkey-tapping is fixed with state guards, which is
what this is.

## Fix

1. `selectTopLevelDestination`: first line `if (destination !in topLevelDestinations.value) return`.
   `topLevelDestinations` (`:605`) is the list the chrome draws from, derived from the same preferences. Its two other
   callers pass `Settings`, which is always offered.
2. Belt and braces in `CampfireApp.kt`, recommended, since an item that can be focused and announced while it
   disappears is the same defect for a screen reader: pass `enabled = destination in destinations` to the three item
   composables (all three Material 3 items take `enabled`), so a leaving item also drops its ripple and semantics.
   Give each its default colours with the disabled ones set to the unselected ones, so that nothing but the fade and
   the shrink changes on screen — a disabled item is drawn at 38% opacity, `NavigationBarItem` and `NavigationRailItem`
   animating into it but `WideNavigationRailItem` (material3 `NavigationItem.kt`) switching in one frame, and the
   project wants every visible change animated:
   ```kotlin
   colors = NavigationBarItemDefaults.colors().let { it.copy(disabledIconColor = it.unselectedIconColor, disabledTextColor = it.unselectedTextColor) },
   ```
   and the same with `NavigationRailItemDefaults.colors()` and `WideNavigationRailItemDefaults.colors()` (all three
   colour classes have `copy` with those four parameters). A leaving item is never the selected one: the switches are
   only in Settings, which is then the selected destination.

Why the refusal cannot swallow a legitimate tap: the chrome draws from the very `topLevelDestinations` state the guard
reads (`CampfireApp.kt:488`/`:555` collect it; `asState` is `SharingStarted.Eagerly`), and the composition can only lag
the view model's value, never lead it. A switch turned back on reaches `topLevelDestinations` before the chrome shows
the item growing back (or re-enables a leaving one), so any tap the user can aim at a re-offered item finds it offered.

## Tests

None: `topLevelDestinations` already has `FeatureDestinationsTest`; the guard is one comparison in the view model.

## Manual check

On a phone (bottom bar) and a tablet/desktop (rail): Settings → Features, switch Metronome off and immediately tap the
Metronome item as it shrinks. The app stays on Settings. Same for Setlists.
