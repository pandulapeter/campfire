/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView.model

/**
 * The models song pages built lately, by what they were built of, so that a song opened again takes its model from
 * memory on its push frame rather than parsing it, walking its scales, and building its sections in composition again.
 *
 * The key is the whole [SongLyricsInputs], which is what makes it safe: the text (an edit, a sync run), the transposition
 * and the capo and tempo overrides, the spelling and notation, lyrics-only mode, the section labels (language,
 * numbering), the shape names, the inferred keys, the instrument and tuning (`chordTuning`, `tabs`), scale suggestions
 * and degrees, and the picture sizes. A changed preference makes a different key and the old entry simply ages out;
 * nothing is ever served for inputs it was not built of. A field added to [SongLyricsInputs] joins the key by itself,
 * while a value the page's build reads from anywhere but its inputs would break this.
 *
 * It keeps [capacity] models, the least recently read leaving first, and never one cut short by [LayoutBudget] (a
 * songbook), the only kind large enough to matter; an ordinary song's model is a few times its text. Read and written
 * only on the main thread (from composition, and from the effect once its background build has returned), so it has
 * no lock.
 */
internal class SongLyricsModelCache(private val capacity: Int = MAX_KEPT_MODELS) {

    /** In the order they were last read or kept, the least recent first: one read is taken out and put back last. */
    private val models = LinkedHashMap<SongLyricsInputs, SongLyricsModel>()

    /** The model kept for [inputs], null where none is. */
    operator fun get(inputs: SongLyricsInputs): SongLyricsModel? = models.remove(inputs)?.also { models[inputs] = it }

    /** Keeps [model] as built of [inputs], replacing the one kept for them before (the in-place one, without its searched shapes). */
    fun keep(inputs: SongLyricsInputs, model: SongLyricsModel) {
        if (model.isCut) return
        models.remove(inputs)
        models[inputs] = model
        if (models.size > capacity) models.remove(models.keys.first())
    }
}

/** A setlist's current page and its two neighbours, plus a few songs opened again. */
internal const val MAX_KEPT_MODELS = 8
