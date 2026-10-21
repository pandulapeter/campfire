/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.share

/**
 * What a shared link says it holds. It only chooses the wording a recipient sees: what is imported is decided by the
 * downloaded archive's content, never by this.
 */
enum class SharedLinkKind(
    val id: String,
) {
    SONG("song"),
    SETLIST("setlist"),
    ;

    companion object {
        fun fromId(id: String) = entries.firstOrNull { it.id == id }
    }
}
