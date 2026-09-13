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

import androidx.compose.ui.window.ComposeUIViewController
import com.pandulapeter.campfire.di.startCampfireDependencyGraph
import com.pandulapeter.campfire.presentation.ui.CampfireIosApp
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController

private val koinApplication by lazy { startCampfireDependencyGraph() }

/**
 * Entry point called from Swift. Returns the view controller hosting the shared Compose UI.
 */
@Suppress("unused", "FunctionName")
fun CampfireViewController(): UIViewController {
    koinApplication
    // The picker needs something to present itself from, which is the controller being created here.
    var controller: UIViewController? = null
    val syncNotifier = IosSyncNotifier()
    val filePicker = IosFilePicker { requireNotNull(controller) }
    return ComposeUIViewController {
        CampfireIosApp(
            urlOpener = ::openUrl,
            filePicker = filePicker,
            filesToImport = filesToImport,
            syncNotifier = syncNotifier,
        )
    }.also { controller = it }
}

private fun openUrl(url: String) {
    NSURL.URLWithString(url)?.let { nsUrl ->
        UIApplication.sharedApplication.openURL(nsUrl, options = emptyMap<Any?, Any>(), completionHandler = null)
    }
}
