/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.source

import com.pandulapeter.campfire.data.model.sync.SystemBrowser
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/** What `:presentation` provides the data modules, for [ImportApp]'s graph; no import reaches it. */
@Module
object PresentationStandIns {

    @Single
    fun systemBrowser(): SystemBrowser = SystemBrowser { false }
}
