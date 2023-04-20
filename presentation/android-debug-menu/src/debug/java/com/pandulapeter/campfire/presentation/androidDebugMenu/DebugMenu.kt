package com.pandulapeter.campfire.presentation.androidDebugMenu

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.StyleRes
import com.pandulapeter.beagle.Beagle
import com.pandulapeter.beagle.common.configuration.Appearance
import com.pandulapeter.beagle.common.configuration.Behavior
import com.pandulapeter.beagle.logCrash.BeagleCrashLogger
import com.pandulapeter.campfire.presentation.androidDebugMenu.sections.createGeneralSection
import com.pandulapeter.campfire.presentation.androidDebugMenu.sections.createHeaderSection
import com.pandulapeter.campfire.presentation.androidDebugMenu.sections.createLogsSection
import com.pandulapeter.campfire.presentation.androidDebugMenu.sections.createShortcutsSection
import com.pandulapeter.campfire.presentation.androidDebugMenu.sections.createTestingSection

object DebugMenu : DebugMenuContract {

    override fun initialize(
        application: Application,
        applicationTitle: String,
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
        val packageInfo = with(application) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getPackageInfo(packageName, 0)
                }
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        }
        Beagle.set(
            modules = (createHeaderSection(
                applicationTitle = applicationTitle,
                packageName = application.packageName,
                versionName = packageInfo?.versionName.orEmpty(),
                versionCode = packageInfo?.versionCode ?: 0
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