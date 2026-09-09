package com.pandulapeter.campfire.presentation.ui.platform

internal actual val isDesktopPlatform = false

// The files live in the app's private storage, which no file manager will show; step 10 makes them shareable.
internal actual val libraryLocationHint: String? = null
