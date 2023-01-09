package com.pandulapeter.campfire.presentation.debugMenu.sections

import com.pandulapeter.beagle.modules.HeaderModule
import com.pandulapeter.beagle.modules.PaddingModule

internal fun createHeaderSection(
    applicationTitle: String,
    versionName: String,
    versionCode: Int
) = listOf(
    HeaderModule(
        title = applicationTitle,
        subtitle = "v$versionName ($versionCode)"
    ),
    PaddingModule()
)