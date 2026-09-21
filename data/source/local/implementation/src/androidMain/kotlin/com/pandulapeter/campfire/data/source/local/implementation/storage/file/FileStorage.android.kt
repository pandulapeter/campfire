/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.storage.file

import android.content.Context
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

/**
 * The app-private `files` directory, which is removed when the app is uninstalled. The system's backup and its
 * transfer to a new device carry `library/` and `preferences/preferences.json` out of it and nothing else, by the
 * rules in `:app:android`'s `res/xml` - which name those paths literally, so they follow [StorageDirectory] by hand.
 */
@Single
internal class AndroidFileStorage(
    // Provided rather than declared: the context is what the Android app shell hands to Koin as it starts, which no
    // shared module can see.
    @Provided context: Context,
) : FileStorage by JvmFileStorage(context.applicationContext.filesDir)
