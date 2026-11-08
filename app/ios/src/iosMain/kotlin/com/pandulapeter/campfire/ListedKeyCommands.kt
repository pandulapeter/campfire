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

import com.pandulapeter.campfire.presentation.ui.IosKeyCommand
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIKeyCommand

/**
 * [commands] as the Command-hold overlay lists them, for the Swift controller around the Compose UI
 * (`ShortcutListingController` in `ContentView.swift`) to answer `keyCommands` with. Only listed, never performed:
 * Compose hears every key itself, on whichever screen it means something, and a key command UIKit performed would
 * never reach it. So nothing implements their action, `campfireListedShortcut:`, and the controller says it cannot
 * perform it, which leaves UIKit to deliver the press as it would without them.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun listedKeyCommands(commands: List<IosKeyCommand>): List<UIKeyCommand> = commands.map { command ->
    UIKeyCommand.commandWithTitle(
        title = command.title,
        image = null,
        action = NSSelectorFromString("campfireListedShortcut:"),
        input = command.input,
        modifierFlags = command.modifierFlags,
        propertyList = null,
    ).apply { discoverabilityTitle = command.title }
}
