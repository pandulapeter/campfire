/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.api.useCases

interface SetChordProTagUseCase {

    /**
     * Puts one tag into a ChordPro document or takes it out of it, leaving the rest of the text exactly as it was:
     * what comes back is written straight to the user's own file, whatever they wrote in it and however they
     * formatted it.
     *
     * Tags are matched without regard to case, so adding one the song already carries in another spelling changes
     * nothing, and removing one takes every spelling of it with it.
     *
     * @param isSelected True to add the tag, false to remove it.
     */
    operator fun invoke(text: String, tag: String, isSelected: Boolean): String
}
