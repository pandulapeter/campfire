package com.pandulapeter.campfire.presentation.ui.platform

/**
 * True on platforms driven by a pointer rather than touch (no pull to refresh, the scrollbar is always shown).
 */
internal expect val isDesktopPlatform: Boolean

/**
 * Where the library's files can be found, or null where there is nothing the user could go and look at. It is a
 * value rather than a string so that the wording that needs translating stays in the string resources, while a path
 * (which does not) can be handed over as it is.
 */
internal expect val libraryLocation: LibraryLocation?

internal sealed interface LibraryLocation {

    /** An absolute path, shown as it is. */
    data class Folder(val path: String) : LibraryLocation

    /** Somewhere only a sentence can describe, which the settings screen translates. */
    data object FilesApp : LibraryLocation
}
