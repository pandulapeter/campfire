package com.pandulapeter.campfire.presentation.ui.platform

internal actual val isDesktopPlatform = false

// Info.plist declares UIFileSharingEnabled and LSSupportsOpeningDocumentsInPlace, so the documents directory the
// library lives in shows up under "On My iPhone".
internal actual val libraryLocation: LibraryLocation? = LibraryLocation.FilesApp
