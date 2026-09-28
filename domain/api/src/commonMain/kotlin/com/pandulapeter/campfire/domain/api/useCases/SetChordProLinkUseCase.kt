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

interface SetChordProLinkUseCase {

    /**
     * Puts one link into a ChordPro document or takes it out of it, leaving the rest of the text exactly as it was: a
     * `{meta: link …}` line is written after the links the file already has, or into its header where it has none.
     * Adding a link the song already has, or an address that is not an `http` or `https` one, changes nothing.
     *
     * @param isAdded True to add the link, false to remove every line naming it.
     */
    operator fun invoke(text: String, url: String, isAdded: Boolean): String
}
