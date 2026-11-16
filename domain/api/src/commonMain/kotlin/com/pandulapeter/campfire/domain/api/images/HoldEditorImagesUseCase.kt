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

interface HoldEditorImagesUseCase {

    /**
     * Tells the library's pruning which pictures the editor's open text names, so that none of them is deleted while the
     * editor may still save a text naming it; an empty set once no editor is open. Each call replaces the last. Returns
     * at once. See `ImageRepository.holdEditorImages`.
     */
    operator fun invoke(names: Set<String>)
}
