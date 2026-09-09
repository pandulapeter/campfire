package com.pandulapeter.campfire.presentation.ui.platform

/**
 * True on platforms driven by a pointer rather than touch (no pull to refresh, the scrollbar is always shown).
 */
internal expect val isDesktopPlatform: Boolean

/**
 * Where the library's files can be found, shown by the settings screen. Null where there is nothing the user could
 * go and look at, which is what the app-private storage of Android and the browser's private file system are.
 */
internal expect val libraryLocationHint: String?
