package com.pandulapeter.campfire.presentation.debugMenu.sections

import com.pandulapeter.beagle.modules.LifecycleLogListModule
import com.pandulapeter.beagle.modules.LogListModule
import com.pandulapeter.beagle.modules.TextModule

internal fun createLogsSection() = listOf(
    TextModule(
        text = "Logs",
        type = TextModule.Type.SECTION_HEADER
    ),
    LogListModule(),
    LifecycleLogListModule()
)