package com.pandulapeter.campfire.presentation.debugMenu

import android.app.Application
import android.util.Log
import androidx.annotation.StyleRes
import com.pandulapeter.beagle.Beagle
import com.pandulapeter.beagle.common.configuration.Appearance
import com.pandulapeter.beagle.common.configuration.Behavior
import com.pandulapeter.beagle.logCrash.BeagleCrashLogger
import com.pandulapeter.campfire.presentation.debugMenu.sections.createGeneralSection
import com.pandulapeter.campfire.presentation.debugMenu.sections.createHeaderSection
import com.pandulapeter.campfire.presentation.debugMenu.sections.createLogsSection
import com.pandulapeter.campfire.presentation.debugMenu.sections.createShortcutsSection
import com.pandulapeter.campfire.presentation.debugMenu.sections.createTestingSection

object DebugMenu : DebugMenuContract {

    override fun initialize(
        application: Application,
        applicationTitle: String,
        versionName: String,
        versionCode: Int,
        @StyleRes themeResourceId: Int
    ) {
        Beagle.initialize(
            application = application,
            appearance = Appearance(
                themeResourceId = themeResourceId,
                applyInsets = insetHandler
            ),
            behavior = Behavior(
                bugReportingBehavior = Behavior.BugReportingBehavior(
                    crashLoggers = listOf(BeagleCrashLogger)
                )
            )
        )
        Beagle.set(
            modules = (createHeaderSection(
                applicationTitle = applicationTitle,
                versionName = versionName,
                versionCode = versionCode
            ) + createGeneralSection(
            ) + createShortcutsSection(
            ) + createTestingSection(
            ) + createLogsSection(
            )).toTypedArray()
        )
        log("Debug menu initialized")
    }

    override fun log(text: String) {
        Log.d("CampfireLogs", text)
        Beagle.log(text)
    }
}