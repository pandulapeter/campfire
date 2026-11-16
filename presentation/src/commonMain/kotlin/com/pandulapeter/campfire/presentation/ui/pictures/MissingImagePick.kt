/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.pictures

import kotlinx.serialization.Serializable

/** What a missing picture's Add image picks for: the picture the song [fileName] names [missingName] but the library lacks. */
@Serializable
internal data class MissingImagePick(
    val fileName: String,
    val missingName: String,
)
