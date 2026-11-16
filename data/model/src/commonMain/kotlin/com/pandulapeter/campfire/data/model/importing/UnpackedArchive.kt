/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.importing

/**
 * What an archive unpacked to: its [files], and how much of the import's picture budget the pictures its songs name
 * took ([imageSize]), which the caller carries on to the archives after it.
 */
data class UnpackedArchive(
    val files: List<ImportedFile>,
    val imageSize: Long,
)
