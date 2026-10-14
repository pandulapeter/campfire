<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :presentation — ui/fontScale

The song text size: state, its saving, and the pinch, wheel, and keyboard gestures that change it.

- `fontScale` is snapshot state on the view model, not a flow, so a pinch recomposes only its readers. Saved to
  `UserPreferences.fontScale` debounced; a pending value is written on `onCleared` and before the desktop exits. The
  stored value wins whenever nothing local is pending, so Settings, a restore, or sync are followed.
- Pages beside the current one read `settledFontScale` (held still 500 ms; a stepper or shortcut sets it at once; a
  swipe settles it, so the song being left does not jump back).
- Kept in whole percent, so sub-percent pinch frames lay nothing out again; `FontScaleAccumulator` adds the wheel's
  unrounded deltas, since each fraction would otherwise round away.
- `fontScaleGestures`: pinch damped by an exponent; Ctrl / Cmd + wheel counted in notches (`verticalWheelNotches`, the
  web reporting pixel or line deltas); both consumed in the initial pass. A touchpad pinch the platform reports (macOS
  desktop, browsers' Ctrl-less wheel) goes to `magnifyByTouchpad` with the same `PINCH_SENSITIVITY`. Ctrl / Cmd + plus,
  minus, and zero are answered by the desktop window and the web page (`zoomSongText`), since the browser acts first.
