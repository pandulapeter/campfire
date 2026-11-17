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
 *
 * @param fileSizes The size of every file the library or the editor's draft shelf holds, by name (the library's where
 *   both hold one): what tells a song's attachment that is on this device from one that is not (synced from another
 *   device and not downloaded yet, or named by a song copied in by hand). The two totals are the library's alone.
 */
data class LibraryFileSizes(
    val picturesSize: Long,
    val attachmentsSize: Long,
    val fileSizes: Map<String, Long> = emptyMap(),
)
