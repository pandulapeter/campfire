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
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.di.startCampfireDependencyGraph
import com.pandulapeter.campfire.domain.api.useCases.CancelSynchronizationUseCase
import com.pandulapeter.campfire.domain.api.useCases.GetSyncStateUseCase
import com.pandulapeter.campfire.presentation.ui.CampfireIosApp
import kotlinx.cinterop.BetaInteropApi
import platform.Foundation.NSCharacterSet
import platform.Foundation.NSMutableCharacterSet
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.URLQueryAllowedCharacterSet
import platform.Foundation.create
import platform.Foundation.stringByAddingPercentEncodingWithAllowedCharacters
import platform.UIKit.UIApplication
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.alternateIconName
import platform.UIKit.setAlternateIconName
import platform.UIKit.supportsAlternateIcons
import org.koin.mp.KoinPlatform

private val koinApplication by lazy { startCampfireDependencyGraph() }

/**
 * One per process, like the run it follows: a second one would watch the same state and hold a background task of its
 * own. Created after Koin, which the state comes from.
 */
private val syncNotifier by lazy {
    IosSyncNotifier(
        syncState = KoinPlatform.getKoin().get<GetSyncStateUseCase>().invoke(),
        onBackgroundTimeExpired = { KoinPlatform.getKoin().get<CancelSynchronizationUseCase>().invoke() },
    )
}

/**
 * Entry point called from Swift. Returns the view controller hosting the shared Compose UI.
 */
@Suppress("unused", "FunctionName")
fun CampfireViewController(): UIViewController {
    koinApplication
    // The picker needs something to present itself from, which is the controller being created here.
    var controller: UIViewController? = null
    val filePicker = IosFilePicker { requireNotNull(controller) }
    return ComposeUIViewController {
        CampfireIosApp(
            urlOpener = ::openUrl,
            filePicker = filePicker,
            filesToImport = filesToImport,
            syncNotifier = syncNotifier,
            onUiModeChanged = ::applyInterfaceStyle,
            onAppIconChanged = ::applyAppIcon,
        )
    }.also { controller = it }
}

/**
 * Hands the theme preference to UIKit, whose status bar, system sheets and keyboard follow the window's interface style
 * rather than Compose's colors. Every window of the app rather than the controller's own: the status bar is styled by
 * the root controller, which is SwiftUI's, and the hosted view is not in its window yet when the first preference can
 * arrive.
 */
private fun applyInterfaceStyle(uiMode: UserPreferences.UiMode?) {
    val style = when (uiMode) {
        UserPreferences.UiMode.LIGHT -> UIUserInterfaceStyle.UIUserInterfaceStyleLight
        UserPreferences.UiMode.DARK -> UIUserInterfaceStyle.UIUserInterfaceStyleDark
        UserPreferences.UiMode.SYSTEM_DEFAULT, null -> UIUserInterfaceStyle.UIUserInterfaceStyleUnspecified
    }
    UIApplication.sharedApplication.connectedScenes
        .filterIsInstance<UIWindowScene>()
        .flatMap { it.windows }
        .filterIsInstance<UIWindow>()
        .forEach { it.overrideUserInterfaceStyle = style }
}

/**
 * Switches the home screen icon to the alternate icon of [themeColor] (`AppIcon-Red`…, in the asset catalog and named
 * by `ASSETCATALOG_COMPILER_ALTERNATE_APPICON_NAMES`), or back to the primary one for the app's own color. Each icon
 * set has a dark and a tinted appearance as well, and which of the three is shown is the home screen's own setting:
 * the app decides the color only. Nothing is asked of iOS when the icon already matches, because every switch it
 * carries out is announced with an alert.
 */
private fun applyAppIcon(themeColor: UserPreferences.ThemeColor) {
    val application = UIApplication.sharedApplication
    val iconName = if (themeColor == UserPreferences.ThemeColor.CAMPFIRE) null else "AppIcon-" + themeColor.id.replaceFirstChar { it.uppercase() }
    if (application.supportsAlternateIcons && application.alternateIconName != iconName) {
        application.setAlternateIconName(iconName, completionHandler = null)
    }
}

/**
 * Opens [url] in the browser, percent-encoding it first where NSURL will not take it as written. A song's link is kept
 * as the user wrote it, which may hold non-ASCII letters or characters such as `|` that a URL may not; iOS 17 encodes
 * those itself, but before it `URLWithString` answers null for them, and the chip would do nothing at all. The set the
 * address is encoded with is a query's plus `#` and `%`, so its structure and any escape already in it survive and only
 * what is illegal is escaped.
 */
@OptIn(BetaInteropApi::class)
private fun openUrl(url: String) {
    val nsUrl = NSURL.URLWithString(url) ?: NSString.create(string = url)
        .stringByAddingPercentEncodingWithAllowedCharacters(urlAllowedCharacters)
        ?.let(NSURL::URLWithString)
    nsUrl?.let { UIApplication.sharedApplication.openURL(it, options = emptyMap<Any?, Any>(), completionHandler = null) }
}

private val urlAllowedCharacters by lazy {
    (NSCharacterSet.URLQueryAllowedCharacterSet.mutableCopy() as NSMutableCharacterSet).apply { addCharactersInString("#%") }
}
