/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.screenshots

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import com.pandulapeter.campfire.presentation.ui.platform.ImpersonatedPlatform
import java.io.File

/**
 * The fonts a platform's system sets an app in where it asks for none. A Mac has the Apple ones itself, so they are
 * left to Compose's own defaults (SF Pro and SF Mono); the others come from the folder the build downloads them into.
 * Windows' monospace, Consolas, has no open stand-in with its metrics, so tablature there is set in the Mac's own.
 */
internal class Fonts(directory: File) {
    private val roboto = File(directory, "Roboto.ttf")
    private val droidSansMono = File(directory, "DroidSansMono.ttf")
    private val openSans = File(directory, "OpenSans.ttf")

    val android: FontFamily by lazy { variableFamily(roboto) }

    val androidMonospace: FontFamily by lazy { FontFamily(Font(file = droidSansMono)) }

    val windows: FontFamily by lazy { variableFamily(openSans) }

    /** What the interface is set in on [platform], or null where it is the desktop's own. */
    fun interfaceFamily(platform: ImpersonatedPlatform): FontFamily? = when (platform) {
        ImpersonatedPlatform.ANDROID -> android
        ImpersonatedPlatform.WINDOWS -> windows
        ImpersonatedPlatform.IOS, ImpersonatedPlatform.MACOS -> null
    }

    /** What tablature and the editor are set in on [platform], or null where it is the desktop's own. */
    fun monospaceFamily(platform: ImpersonatedPlatform): FontFamily? = when (platform) {
        ImpersonatedPlatform.ANDROID -> androidMonospace
        ImpersonatedPlatform.IOS, ImpersonatedPlatform.MACOS, ImpersonatedPlatform.WINDOWS -> null
    }

    /** What the system chrome around the app is set in, which is the interface's font, the Apple ones included. */
    fun chromeFamily(platform: ImpersonatedPlatform) = interfaceFamily(platform) ?: FontFamily.Default

    /** One file of a variable font, as the nine weights Compose asks a family for, each set on its weight axis. */
    @OptIn(ExperimentalTextApi::class)
    private fun variableFamily(file: File) = FontFamily(
        (100..900 step 100).map { weight ->
            Font(
                file = file,
                weight = FontWeight(weight),
                variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
            )
        },
    )
}
