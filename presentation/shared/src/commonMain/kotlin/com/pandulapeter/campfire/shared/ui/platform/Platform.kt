package com.pandulapeter.campfire.shared.ui.platform

/**
 * True on platforms driven by a pointer rather than touch (no pull to refresh, the scrollbar is always shown).
 */
internal expect val isDesktopPlatform: Boolean
