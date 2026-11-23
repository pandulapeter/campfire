/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.editor

import com.pandulapeter.campfire.data.model.song.SongContent
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination.SongEditor.Companion.NEW_SONG_FILE_NAME
import com.pandulapeter.campfire.presentation.ui.state.asState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

/**
 * **Use the whole page**: a new song's editor opened on a page's text whose clutter the import cropped
 * (`ImportPlan.SongEntry.wholePageText`) offers the text the whole page converts to instead, since the crop may have
 * left out a line of the song. The offer stands only while the editor holds the text it opened with: it is gone for good
 * once the text changes (typed, used, or saved under a file name) or the editor is left, and when dismissed. Nothing of
 * it is written or kept past the process; the field takes the whole page through its own editing, one undo step.
 *
 * @param editorDraft What the open editor holds, see `EditorSession.editorDraft`.
 * @param emitEdit Hands an edit to the open editor, see `EditorSession.emitEditorTextEdit`.
 */
internal class WholePageOffer(
    scope: CoroutineScope,
    editorDraft: StateFlow<SongContent?>,
    private val emitEdit: (EditorTextEdit) -> Unit,
) {
    private val offered = MutableStateFlow<Offer?>(null)

    /** The whole page as the editor's text, while it is offered; null otherwise. */
    val wholePageDraft: StateFlow<String?> = combine(offered, editorDraft) { offer, draft ->
        if (offer != null && (draft?.fileName != NEW_SONG_FILE_NAME || draft.text != offer.draft)) {
            offered.compareAndSet(offer, null)
            null
        } else {
            offer?.wholePageDraft
        }
    }.asState(scope, null)

    /**
     * Offers [wholePageDraft] to the new song's editor that has just been given [draft], or withdraws any offer where it is
     * null. Called after the editor's draft is [draft], or the offer would be taken for one the text has moved away from.
     */
    fun offer(draft: String, wholePageDraft: String?) {
        offered.value = wholePageDraft?.takeIf { it != draft }?.let { Offer(draft = draft, wholePageDraft = it) }
    }

    /** Puts the whole page in the editor in place of the text it opened with, unless that has changed meanwhile. */
    fun use() {
        val offer = offered.value ?: return
        offered.value = null
        emitEdit(EditorTextEdit(fileName = NEW_SONG_FILE_NAME) { current -> if (current == offer.draft) offer.wholePageDraft else current })
    }

    fun dismiss() {
        offered.value = null
    }

    private class Offer(val draft: String, val wholePageDraft: String)
}
