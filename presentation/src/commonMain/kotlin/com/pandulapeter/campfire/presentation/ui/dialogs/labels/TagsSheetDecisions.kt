/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.labels

import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.data.model.song.Tag
import com.pandulapeter.campfire.presentation.ui.components.labels.orderedBy
import com.pandulapeter.campfire.presentation.ui.components.labels.sortedAlphabeticallyBy

/**
 * The tags [TagsSheet] offers, one per tag whatever its case: the library's in its sorting order, then the songs' own
 * and the ones created in the sheet ([ownTags]) the library does not have yet. A tag is spelled as the songs spell it
 * where that differs from the library's only by case.
 */
internal fun offeredTagsOf(libraryTags: List<Tag>, sortingMode: UserPreferences.LabelSortingMode, ownTags: List<String>): List<String> {
    val tags = (libraryTags.orderedBy(sortingMode).map { tag ->
        ownTags.firstOrNull { it.equals(tag.name, ignoreCase = true) } ?: tag.name
    } + ownTags).distinctBy { it.lowercase() }
    return if (sortingMode == UserPreferences.LabelSortingMode.ALPHABETICAL) tags.sortedAlphabeticallyBy { it } else tags
}

/** The offered tag [typedTag] spells whatever its case, null where it would be a new one. */
internal fun spelledTagOf(typedTag: String, offeredTags: List<String>) = offeredTags.firstOrNull { it.equals(typedTag, ignoreCase = true) }

/**
 * What [TagsSheet]'s Save writes: the [draft] with a tag still typed into the field ticked, since Save commits it too,
 * and the spelling each folded tag is written in - the one it was offered under, or a new tag's as it was typed.
 */
internal fun tagsToSave(draft: BulkLabelDraft, offeredTags: List<String>, typedTag: String): Pair<BulkLabelDraft, Map<String, String>> {
    val draftToSave = if (typedTag.isEmpty()) draft else draft.checked(typedTag.lowercase())
    val spellings = offeredTags.associateBy { it.lowercase() }.let { offered ->
        if (typedTag.isEmpty() || spelledTagOf(typedTag, offeredTags) != null) offered else offered + (typedTag.lowercase() to typedTag)
    }
    return draftToSave to spellings
}
