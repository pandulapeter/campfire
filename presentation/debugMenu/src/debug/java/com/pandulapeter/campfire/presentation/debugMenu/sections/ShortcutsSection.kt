package com.pandulapeter.campfire.presentation.debugMenu.sections

import com.pandulapeter.beagle.Beagle
import com.pandulapeter.beagle.modules.AppInfoButtonModule
import com.pandulapeter.beagle.modules.DeveloperOptionsButtonModule
import com.pandulapeter.beagle.modules.DividerModule
import com.pandulapeter.beagle.modules.PaddingModule
import com.pandulapeter.beagle.modules.TextModule

internal fun createShortcutsSection() = listOf(
    TextModule(
        text = "Shortcuts",
        type = TextModule.Type.SECTION_HEADER
    ),
    AppInfoButtonModule(type = TextModule.Type.NORMAL),
    DeveloperOptionsButtonModule(type = TextModule.Type.NORMAL),
    TextModule(
        text = "Clear app data",
        onItemSelected = {
            Beagle.currentActivity?.run {
                Runtime.getRuntime().exec("pm clear ${applicationContext.packageName}")
            }
        }
    ),
    PaddingModule(),
    DividerModule()
)