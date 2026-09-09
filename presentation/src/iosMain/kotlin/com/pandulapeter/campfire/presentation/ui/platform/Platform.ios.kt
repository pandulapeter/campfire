package com.pandulapeter.campfire.presentation.ui.platform

internal actual val isDesktopPlatform = false

// TODO(step 10): once Info.plist exposes the documents directory, this becomes "Visible in the Files app under
//  Campfire" - there is nothing to point at before that.
internal actual val libraryLocationHint: String? = null
