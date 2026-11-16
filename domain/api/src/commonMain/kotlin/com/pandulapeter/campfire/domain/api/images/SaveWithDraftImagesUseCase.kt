/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.api.images

interface SaveWithDraftImagesUseCase {

    /**
     * Runs [save], the write of the editor's [text] as a song, with the draft pictures [text] names moved into the library
     * first, both under one hold of the library's pruning, so that no picture is deleted before the song naming it is
     * written. A picture that had to take another name in the library (a different one took its name meanwhile) is
     * renamed in the text [save] is handed, which is then the text to keep. A text naming no picture is handed to [save]
     * as it is, with nothing else done. Not cancellable once started; what [save] throws is thrown, the moved pictures
     * then left to the next pruning, the draft's still where they were.
     */
    suspend operator fun <T> invoke(text: String, save: suspend (text: String) -> T): T
}
