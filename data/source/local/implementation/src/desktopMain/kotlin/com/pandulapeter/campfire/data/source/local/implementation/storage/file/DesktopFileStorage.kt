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

import com.pandulapeter.campfire.data.model.library.desktopDataDirectory
import org.koin.core.annotation.Single

/** The library and preferences in the folder [desktopDataDirectory] names, which the desktop app keeps its guard and log beside. */
@Single
internal class DesktopFileStorage : FileStorage by JvmFileStorage(desktopDataDirectory())
