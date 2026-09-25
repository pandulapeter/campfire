/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.platform

import androidx.compose.ui.input.pointer.PointerEvent

/**
 * True on platforms driven by a pointer rather than touch, where holding a song row opens nothing: the long press
 * that opens the row's overflow menu is a touch gesture, and the button that opens the same menu is one click away.
 * It is also what the interface scale is decided by (see `interfaceScale`).
 */
internal expect val isDesktopPlatform: Boolean

/**
 * Whether the app's own launch screen is the whole of the startup the user watches, from the first frame the window
 * paints to the app: true only in the desktop application. Android and the web put a startup screen of their own over
 * it (the system splash, the page's loading screen) and iOS shows its storyboard first, so there the launch screen's
 * exit is never watched and is kept short. Not [isDesktopPlatform], which on the web says which input the page is
 * used with rather than what is on screen before it.
 */
internal expect val isLaunchScreenWholeStartup: Boolean

/**
 * Where the library's files can be found, or null where there is nothing the user could go and look at. It is a
 * value rather than a string so that the wording that needs translating stays in the string resources, while a path
 * (which does not) can be handed over as it is.
 */
internal expect val libraryLocation: LibraryLocation?

/** Which of the app's icons follows the theme color here (see [AppIconSurface]). */
internal expect val appIconSurface: AppIconSurface

/**
 * The app store of the platform the app is **running on**, which is the one official way to get Campfire there: every
 * platform has exactly one, and a build made by hand is still a build of that platform. The settings screen's rating
 * row opens its listing, and it decides [canAskForDonations]. Null where the platform has no store - Linux has none,
 * and the web build runs on all of them, so any one choice would be a guess.
 *
 * A build that goes through App Review only ever runs on an Apple platform, so this never names another company's
 * store to one (guideline 2.3.10) without a rule of its own having to say so.
 */
internal expect val platformStore: Distribution?

/**
 * Whether the settings screen may offer a link that asks for money: everywhere but on Apple's platforms.
 *
 * The App Store and the Mac App Store forbid pointing at any way of paying the developer other than an in-app
 * purchase (guideline 3.1.1), and a tip is such a payment. Their builds are the only official ones on iOS and macOS,
 * so the platform decides it rather than the build. Play's billing is only required for purchases of digital content,
 * which a donation that buys nothing is not; the Microsoft Store has no such rule; and the Linux package and the web
 * build answer to no store at all.
 */
internal val canAskForDonations get() = platformStore?.isApple != true

/**
 * How far a scroll wheel event turned the wheel vertically, in notches (positive towards the user), which is the unit a
 * pointer event reports it in everywhere but the web: a browser hands over the DOM event's own delta, which is in
 * pixels there - about a hundred for a notch - or in lines.
 */
internal expect fun PointerEvent.verticalWheelNotches(): Float

internal sealed interface LibraryLocation {

    /** An absolute path, shown as it is. */
    data class Folder(val path: String) : LibraryLocation

    /** Somewhere only a sentence can describe, which the settings screen translates. */
    data object FilesApp : LibraryLocation
}

/**
 * The app stores Campfire is published on.
 *
 * @property listingUrl The page a rating is left on, or null for as long as the app is not on that store: it is
 *   filled in on the day the listing goes live, which is the whole of what publishing costs the app. It is an https
 *   address rather than a store's own scheme (`market://`, `ms-windows-store://`) so that it also opens in a
 *   browser, on a desktop where the store app may not be installed at all.
 * @property isApple Whether the store is one of Apple's, which is what decides [canAskForDonations].
 */
internal enum class Distribution(
    val listingUrl: String?,
    val isApple: Boolean = false,
) {
    PLAY_STORE(listingUrl = "https://play.google.com/store/apps/details?id=com.pandulapeter.campfire"),
    APP_STORE(listingUrl = null, isApple = true),
    MAC_APP_STORE(listingUrl = null, isApple = true),
    MICROSOFT_STORE(listingUrl = null),
}
