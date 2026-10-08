/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation

import com.pandulapeter.campfire.data.source.local.api.LibraryChanges
import com.pandulapeter.campfire.data.source.local.api.LibraryFileLock
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
@ComponentScan
object DataLocalSourceModule {

    /** The one lock every write to a library file is made under, by the repositories and by sync alike. */
    @Single
    internal fun libraryFileLock(): LibraryFileLock = LibraryFileLock()

    /** What tells sync that the app changed the library, announced by the repositories. */
    @Single
    internal fun libraryChanges(): LibraryChanges = LibraryChanges()
}
