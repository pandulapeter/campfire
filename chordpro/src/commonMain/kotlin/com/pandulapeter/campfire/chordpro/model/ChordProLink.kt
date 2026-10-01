/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.model

/**
 * A page about a song, carried as `{meta: link https://… Optional name}`. [name] is null for an unnamed link, whose
 * display label belongs to the caller; [url] alone identifies a link, so two names for one address are one link.
 */
data class ChordProLink(
    val url: String,
    val name: String? = null,
)
