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
 */
internal expect val isDesktopPlatform: Boolean

/**
 * Where the library's files can be found, or null where there is nothing the user could go and look at. It is a
 * value rather than a string so that the wording that needs translating stays in the string resources, while a path
 * (which does not) can be handed over as it is.
 */
internal expect val libraryLocation: LibraryLocation?

/**
 * Which of the places Campfire is handed out from this build is for, or null where it is for none of them - the
 * desktop installers the GitHub release carries, which answer to no store. The settings screen lists the others next to
 * it, so that the app can be found for every device its user has; see [visibleDistributions] for what each store lets
 * a build say about the rest.
 */
internal expect val currentDistribution: Distribution?

/**
 * Whether the settings screen may offer a link that asks for money, which is decided by the store the build is
 * published on.
 *
 * The App Store and the Mac App Store forbid pointing at any way of paying the developer other than an in-app
 * purchase (guideline 3.1.1), and a tip is such a payment. Play's billing is only required for purchases of digital
 * content, which a donation that buys nothing is not; the Microsoft Store has no such rule; and the direct
 * installers, the Linux package and the web build answer to no store at all.
 */
internal val canAskForDonations get() = currentDistribution?.isApple != true

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
 * Everywhere Campfire can be had from, in the order the settings screen lists them.
 *
 * @property url The listing, or null for as long as there is none. Such an entry is a placeholder: it is drawn
 *   disabled, saying that the build is on its way, and gets its address here on the day it is published.
 * @property isApple Whether the store is one of Apple's, which is what [visibleDistributions] decides by.
 */
internal enum class Distribution(
    val url: String?,
    val isApple: Boolean = false,
) {
    PLAY_STORE(url = "https://play.google.com/store/apps/details?id=com.pandulapeter.campfire"),
    APP_STORE(url = null, isApple = true),
    MAC_APP_STORE(url = null, isApple = true),
    MICROSOFT_STORE(url = null),

    /** No store at all: the package the release workflow attaches to every GitHub release. */
    LINUX(url = "https://github.com/pandulapeter/campfire/releases/latest"),
    WEB(url = "https://pandulapeter.com/campfire"),
}

/**
 * The entries of [Distribution] a build for [current] may show, its own included, since that is the listing to rate
 * the app on or to send to somebody else.
 *
 * A build that goes through App Review is the exception twice over. Guideline 2.3.10 has an app name no other
 * platform's store, so an Apple build leaves out Google's and Microsoft's; the web build stays, being a page rather
 * than a platform with a store of its own. Guideline 2.1 wants no placeholder content either, so there a listing
 * that does not exist yet is left out rather than announced. Neither Play nor the Microsoft Store has a rule against
 * naming the others, and the web and Linux builds answer to no store at all.
 *
 * Where this leaves something out, the settings screen adds a row that names no platform and leads to the project's
 * own page, which lists every build: an app may link to its home page, and what that page says is not the app's
 * metadata.
 */
internal fun visibleDistributions(current: Distribution? = currentDistribution) = Distribution.entries.filter { distribution ->
    current?.isApple != true || distribution == Distribution.WEB || (distribution.isApple && distribution.url != null)
}
