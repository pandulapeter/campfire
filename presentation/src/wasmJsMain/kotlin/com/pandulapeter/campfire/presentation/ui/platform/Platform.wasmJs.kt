package com.pandulapeter.campfire.presentation.ui.platform

// The page can be open on a phone just as well as on a computer, so the input method decides: with a touchscreen the
// touch treatment is used (pull to refresh, an auto-hiding scrollbar), without one the desktop treatment is.
internal actual val isDesktopPlatform = !hasTouchScreen()

/**
 * True if the browser reports any touchscreen. `maxTouchPoints` covers every current browser, `ontouchstart` is the
 * fallback for older ones.
 */
private fun hasTouchScreen(): Boolean = js("navigator.maxTouchPoints > 0 || 'ontouchstart' in window")
