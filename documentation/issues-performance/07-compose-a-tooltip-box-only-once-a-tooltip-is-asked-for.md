<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Compose Material's tooltip box under an icon button only once its tooltip is first asked for, so a screen full of buttons is composed without one per button

**Challenged:** amended — settled the `show` question against Material 1.12.0-alpha03's source (no frame wait needed); the manual check is already a test.

**Kind:** performance  ·  **Severity:** high  ·  **Platforms:** all  ·  **Run on:** any machine
**Files:**
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/CampfireTooltip.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/CLAUDE.md` (the tooltip rule)
- `tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/presentation/ui/components/TooltipTest.kt`

## Problem

Every control drawn as an icon alone is wrapped in `CampfireTooltip` (`components/CLAUDE.md`: a `CampfireIconButton`,
or the wrapper itself), and the wrapper composes a whole Material `TooltipBox` for each, always, whether or not a
tooltip is ever shown (`CampfireTooltip.kt:78-94`):

```kotlin
val state = rememberTooltipState()
val hint = shortcut?.hint()
Box(
    modifier = when { … isShownOnLongPress -> modifier.showsOnLongPress(state).showsOnHover(state) … },
) {
    TooltipBox(
        positionProvider = rememberTooltipPositionProvider(),
        tooltip = { TooltipBubble(label = label, hint = hint) },
        state = state,
        enableUserInput = false,
        content = content,
    )
}
```

`TooltipBox` brings `BasicTooltipBox`, its own anchor box tracking the anchor's position, a transition, and the position
provider. The Songs screen has two per card (star and overflow), the app bar and the navigation chrome more: the 59th
review's composition trace of a pop to Songs counted `CampfireTooltip`/`TooltipBox` 55 times in the one long frame,
`BasicTooltipBox` alone 3.7 ms self, and the stack samples put `CampfireTooltip` at 10–11 % of the push's and pop's
compose phase. A pop and a tab switch rebuild every card (Navigation 3 drops the covered entry), so all of it is paid on
every return to a list. The app turns Material's own input off (`enableUserInput = false`) and shows the tooltip from
its own hover and long-press handlers, so nothing but those handlers ever opens it.

## Fix

Compose nothing of Material's tooltip until the wrapper's own handler first asks for one, then keep it composed (a
button hovered once is likely hovered again; only those pay).

- Keep `content` exactly where it is composed now (`Box { … content() }`), so arming the tooltip never composes the
  control afresh (a ripple mid-press, a focused button, or a stepper's repeat would be lost otherwise).
- Hold `var isArmed by remember { mutableStateOf(false) }`. `showsOnHover` and `showsOnLongPress` set it before
  calling `state.show(...)`. No frame wait is needed (checked in Material 1.12.0-alpha03's `Tooltip.kt`):
  `TooltipStateImpl.show` only sets `transition.targetState = true` on the state's own `MutableTransitionState` and
  suspends until dismissed, and `BasicTooltipBox` composes the popup while `state.isVisible`
  (`currentState || targetState`). A box composed a frame after the show therefore finds the state already visible and
  `updateTransition` animates the bubble in from `false`, exactly as today. `state` must stay remembered by the wrapper
  (not inside the armed branch), which is what carries the show over.
- Once armed, compose the Material box as a sibling over the content, anchored to the same bounds, with nothing in it:

  ```kotlin
  Box(modifier = …) {
      content()
      if (isArmed) {
          Box(modifier = Modifier.matchParentSize()) {
              TooltipBox(
                  positionProvider = rememberTooltipPositionProvider(),
                  tooltip = { TooltipBubble(label = label, hint = hint) },
                  state = state,
                  enableUserInput = false,
              ) { Spacer(Modifier.fillMaxSize()) }
          }
      }
  }
  ```

  The anchor's bounds are then the content's, so the bubble sits where it does today, with Material's positioning, popup
  and animation unchanged. The spacer takes no input and has no semantics, so presses reach the control and nothing
  new is read out.
- `rememberTooltipState()` stays unconditional (a small object holding the transition state the handlers write);
  `rememberTooltipPositionProvider()` moves into the armed branch.
- Accessibility does not change: with `enableUserInput = false` Material adds no anchor semantics today, and the label a
  screen reader hears is the control's own content description, which the wrapper never touched.

Alternative considered: draw the bubble in a `Popup` of the app's own instead of Material's `TooltipBox`. Smaller still,
but it re-implements the anchor tracking and the enter/exit animation, and the wrapper's docs promise Material's
position provider (`TooltipPosition.kt`, kept off every inset). Recommended: the armed sibling above.

Update the tooltip bullet of `components/CLAUDE.md` with one clause: Material's box is composed only once the
wrapper's hover or long press first asks for a tooltip.

## Tests

`TooltipTest.kt` already covers what a user sees (hover after a delay, the warm window, long press instead of a click,
switched off, keys named or not); all six must pass unchanged. Add one:
`` `an icon button never hovered or held shows no tooltip and presses as before` `` — tap a card's star, see the
setlist assignments sheet, and no tooltip node in the tree.

**Measure:** plan 01's `pop:plain`, `tab:songs>setlists`, `tab:setlists>songs` (the most buttons rebuilt at once) and
`push:long` (the song's header buttons). Expect the warm trigger frame's compose part to drop by the 3–4 ms the trace
gave `TooltipBox`, and allocated MB with it; nothing should grow.

## Manual check

None: the first long press and the first hover on a button never hovered are what `TooltipTest`'s
`` `a long press on an icon button shows its name instead of pressing it` `` and
`` `resting the mouse on an icon button shows its name after a moment` `` already do (each on a fresh button).
