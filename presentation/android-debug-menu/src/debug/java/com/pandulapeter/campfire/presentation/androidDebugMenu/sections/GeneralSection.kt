package com.pandulapeter.campfire.presentation.androidDebugMenu.sections

import com.pandulapeter.beagle.modules.BugReportButtonModule
import com.pandulapeter.beagle.modules.DeviceInfoModule
import com.pandulapeter.beagle.modules.DividerModule
import com.pandulapeter.beagle.modules.PaddingModule
import com.pandulapeter.beagle.modules.ScreenCaptureToolboxModule
import com.pandulapeter.beagle.modules.TextModule

internal fun createGeneralSection() = listOf(
    TextModule(
        text = "General",
        type = TextModule.Type.SECTION_HEADER
    ),
    BugReportButtonModule(),
    ScreenCaptureToolboxModule(),
    DeviceInfoModule(),
    PaddingModule(),
    DividerModule()
)