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

/** The app-private `files` directory, which is backed up with the app and removed when it is uninstalled. */
@Single
internal class AndroidFileStorage(
    // Provided rather than declared: the context is what the Android app shell hands to Koin as it starts, which no
    // shared module can see.
    @Provided context: Context,
) : FileStorage by JvmFileStorage(context.applicationContext.filesDir)
