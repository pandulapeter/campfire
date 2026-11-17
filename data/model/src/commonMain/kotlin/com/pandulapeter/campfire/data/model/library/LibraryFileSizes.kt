/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.library

/**
 * The bytes the files of `library/files` take up on disk, split by what their names say they are: a name of
 * [LibraryFiles.IMAGE_EXTENSIONS] counts as a picture even where a song only attaches it, since the name cannot tell.
 */
data class LibraryFileSizes(
    val picturesSize: Long,
    val attachmentsSize: Long,
)
