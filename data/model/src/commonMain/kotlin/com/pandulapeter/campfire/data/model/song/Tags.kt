/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.song

import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.data.model.library.normalizedToNfc

/**
 * The tags of a song as every part of the app lists them: each composed to NFC, and one spelling per tag whatever its
 * case, the first one kept, in the order they were written. The song list, the tag sheet and the editor read them
 * through this, or a sheet opened from the editor would show a different set from the list.
 */
fun normalizedTags(tags: Iterable<String>): List<String> = tags.map { it.normalizedToNfc() }.distinctBy { it.lowercase() }

/**
 * What a tag is told apart from every other by, which [normalizedTags] keeps one spelling per: its NFC form, whatever
 * its case. A setting kept for a tag ([UserPreferences.tagColors]) is filed under it, so that "Christmas" written on one
 * song and "christmas" on another, or one spelled decomposed by a Mac, share it.
 */
fun tagKey(tag: String) = tag.normalizedToNfc().lowercase()
