/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.ui.theme.appIconColor
import java.util.concurrent.Executors

/**
 * Makes the launcher icon the one of a theme color, by enabling the launcher entry of that color - one of the
 * `CampfireActivity…` aliases of the manifest - and disabling the rest. Android has no other way for an app to change
 * its icon, and a launcher sees it as the app's entry going and another one coming: Pixel Launcher moves a home screen
 * icon over to the new entry, while a launcher that does not may take it off the home screen. So the switch is only
 * made when the color has changed.
 *
 * Every color's entry, the one of the app's own color included, is disabled in the manifest and switched between that and enabled
 * alone. The one entry that is enabled in the manifest, `CampfireActivity`, is what an installation that never
 * switched opens from, and it is disabled - explicitly, since nothing else takes it out of the launcher - at the
 * first switch and never enabled again. The difference matters because Android closes every task that was started from
 * a component it is told is disabled, and only those: one set back to a manifest default of disabled leaves its task
 * alone. So the user's place in the app is lost once, at the first switch, and never after - which is why that one
 * waits for the user to leave the app, while every later one is made as the color is picked.
 *
 * The System color is the one [appIconColor] has no file for everywhere else, and has a launcher icon of its own here:
 * its background is the wallpaper's accent color (`values-v31`), from the same Android version that offers the option.
 *
 * The switch is asked for on every composition and every stop, so the color this process last applied in full is
 * remembered and asks the package manager nothing; the first call of a process still reads the real state once, on the
 * switcher's own thread.
 */
internal object AppIconSwitcher {

    /**
     * The color whose launcher entry this process has already made the only enabled one, so that the switch asked for
     * on every composition and every stop costs nothing when the icon is already right. Only ever written after the whole
     * switch has been carried out (or found to be unnecessary for the app's own icon), never on the early return that
     * leaves the first switch for the user's way out.
     */
    private var appliedTarget: UserPreferences.ThemeColor? = null

    /**
     * Every switch runs here, one after another: the package manager answers each call over binder, and the first call
     * of a process asks about every launcher entry - thirteen round trips the main thread would make around the first
     * frame. One thread, so that a switch asked for as the color is picked and the one asked for as the user leaves
     * never interleave, and [appliedTarget] is only ever touched from it.
     */
    private val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "AppIconSwitcher").apply { isDaemon = true } }

    /**
     * Queues the switch and returns at once. The application context is what is kept, since the activity that asked
     * may be gone by the time the switch runs; a process killed before then is a switch that was not made, which the
     * next stop tries again.
     *
     * @param isLeaving Whether the user is on the way out of the app. Only the first switch waits for that, since it
     *   is the one that closes the task the user is in.
     */
    fun apply(context: Context, themeColor: UserPreferences.ThemeColor, isLeaving: Boolean) {
        val applicationContext = context.applicationContext
        executor.execute { applyNow(context = applicationContext, themeColor = themeColor, isLeaving = isLeaving) }
    }

    private fun applyNow(context: Context, themeColor: UserPreferences.ThemeColor, isLeaving: Boolean) {
        val target = if (themeColor == UserPreferences.ThemeColor.SYSTEM && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            themeColor
        } else {
            themeColor.appIconColor
        }
        if (target == appliedTarget) return
        try {
            val packageManager = context.packageManager
            val initialEntry = component(context, INITIAL_ENTRY)
            val hasNeverSwitched = packageManager.getComponentEnabledSetting(initialEntry) == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            if (hasNeverSwitched && target == UserPreferences.ThemeColor.CAMPFIRE) {
                // The manifest's own entry is already the one of the app's own color.
                appliedTarget = target
                return
            }
            if (hasNeverSwitched && !isLeaving) return
            // The new entry is enabled before anything is disabled, so that there is never a moment in which the app has
            // no launcher entry at all - a launcher that looks then would take the app for one that cannot be opened.
            ENTRIES.entries.sortedByDescending { (color, _) -> color == target }.forEach { (color, name) ->
                packageManager.setState(
                    component = component(context, name),
                    state = if (color == target) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                )
            }
            packageManager.setState(component = initialEntry, state = PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
            appliedTarget = target
        } catch (exception: Exception) {
            // A device policy (a managed or work profile, some OEM launchers) may refuse a component switch, and this
            // is asked for on every stop, where a crash would come back every time the app is left. The icon stays as it
            // was, and with the target not recorded as applied, the next color change or the next stop tries again.
            println("Could not change the launcher icon: ${exception.message}")
        }
    }

    private fun PackageManager.setState(component: ComponentName, state: Int) {
        if (getComponentEnabledSetting(component) != state) {
            setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
        }
    }

    /**
     * The manifest's relative names resolve against the namespace, while the package the component is looked up in is
     * the application id, which a debug build adds a suffix to.
     */
    private fun component(context: Context, name: String) = ComponentName(context.packageName, "$NAMESPACE.$name")

    /**
     * Spelled out rather than read from a class: R8 moves the classes of a release build into packages of its own
     * naming, so no class's package is this one there, and none is anything at all once it has been moved to the top.
     */
    private const val NAMESPACE = "com.pandulapeter.campfire"

    private const val INITIAL_ENTRY = "CampfireActivity"

    private val ENTRIES = mapOf(
        UserPreferences.ThemeColor.CAMPFIRE to "CampfireActivityCampfire",
        UserPreferences.ThemeColor.SYSTEM to "CampfireActivitySystem",
        UserPreferences.ThemeColor.GRAY to "CampfireActivityGray",
        UserPreferences.ThemeColor.RED to "CampfireActivityRed",
        UserPreferences.ThemeColor.ORANGE to "CampfireActivityOrange",
        UserPreferences.ThemeColor.YELLOW to "CampfireActivityYellow",
        UserPreferences.ThemeColor.GREEN to "CampfireActivityGreen",
        UserPreferences.ThemeColor.TEAL to "CampfireActivityTeal",
        UserPreferences.ThemeColor.BLUE to "CampfireActivityBlue",
        UserPreferences.ThemeColor.PURPLE to "CampfireActivityPurple",
        UserPreferences.ThemeColor.PINK to "CampfireActivityPink",
    )
}
