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

import android.app.Application
import com.pandulapeter.campfire.di.startCampfireDependencyGraph
import com.pandulapeter.campfire.sync.CampfireSyncService
import org.koin.android.ext.koin.androidContext

class CampfireAndroidApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startCampfireDependencyGraph { androidContext(this@CampfireAndroidApplication) }
        // A run whose process never came back left its notification behind, and this is the first moment anything
        // of Campfire's is running again to take it down. See CampfireSyncService.clearStaleNotification.
        CampfireSyncService.clearStaleNotification(this)
    }
}