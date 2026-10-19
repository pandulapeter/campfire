/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.platform

/**
 * The title and the artist a shared text's subject suggests (see [sharedTextTitle]), which a new song made of the text
 * is given in place of the placeholders for what the text itself does not declare.
 */
internal data class SharedTextTitle(
    val title: String,
    val artist: String?,
)
