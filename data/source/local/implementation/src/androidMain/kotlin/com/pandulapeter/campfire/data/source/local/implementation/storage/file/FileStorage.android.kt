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
import org.koin.core.scope.Scope

/** The app-private `files` directory, which is backed up with the app and removed when it is uninstalled. */
internal actual fun Scope.createFileStorage(): FileStorage = JvmFileStorage(get<Context>().applicationContext.filesDir)
