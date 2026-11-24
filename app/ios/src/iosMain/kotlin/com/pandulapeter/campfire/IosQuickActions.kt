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

import com.pandulapeter.campfire.presentation.ui.shortcuts.LauncherShortcut
import com.pandulapeter.campfire.presentation.ui.shortcuts.LauncherShortcuts
import kotlinx.cinterop.BetaInteropApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import platform.Foundation.NSDictionary
import platform.Foundation.NSString
import platform.Foundation.create
import platform.Foundation.dictionaryWithObject
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationShortcutIcon
import platform.UIKit.UIApplicationShortcutItem
import platform.UIKit.shortcutItems

/** The key of a quick action's `userInfo` its app link is under, read by `QuickActions.swift`. */
const val QUICK_ACTION_LINK_KEY = "link"

/**
 * The app's own `campfire://` links, a quick action's among them, kept outside the composition like the files to import:
 * one that launches the app arrives before the view controller exists.
 */
private val pendingAppLinks = Channel<String>(Channel.BUFFERED)

internal val appLinksToOpen = pendingAppLinks.receiveAsFlow()

/** Queues an app link [openUrl] was handed. */
internal fun openAppLink(link: String) {
    pendingAppLinks.trySend(link)
}

/**
 * Hands iOS the launcher shortcuts as the app's Home Screen quick actions, which are dynamic for the reason Android's
 * are: their labels and their set follow the language, the switches, and read only mode. iOS shows the first four,
 * each with an SF Symbol as near the app's own icon as there is.
 */
@OptIn(BetaInteropApi::class)
internal fun applyQuickActions(shortcuts: LauncherShortcuts) {
    UIApplication.sharedApplication.shortcutItems = shortcuts.offered.map { item ->
        UIApplicationShortcutItem(
            type = item.shortcut.name.lowercase(),
            localizedTitle = item.label,
            localizedSubtitle = null,
            icon = UIApplicationShortcutIcon.iconWithSystemImageName(item.shortcut.systemImageName),
            // A Foundation dictionary, not a Kotlin map: iOS archives the items, and drops the map Kotlin bridges as one of
            // its own classes, leaving every item without its link.
            userInfo = NSDictionary.dictionaryWithObject(item.shortcut.link, forKey = NSString.create(string = QUICK_ACTION_LINK_KEY)),
        )
    }
    println("Quick actions published: ${shortcuts.offered.size}")
}

private val LauncherShortcut.systemImageName
    get() = when (this) {
        LauncherShortcut.NEW_SONG -> "plus"
        LauncherShortcut.PASTE_SONG -> "doc.on.clipboard"
        LauncherShortcut.SETLISTS -> "list.bullet"
        LauncherShortcut.METRONOME -> "metronome"
        LauncherShortcut.TUNER -> "tuningfork"
    }
