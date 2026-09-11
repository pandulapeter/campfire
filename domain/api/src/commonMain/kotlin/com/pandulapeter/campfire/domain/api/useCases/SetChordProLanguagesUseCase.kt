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

interface SetChordProLanguagesUseCase {

    /**
     * Declares exactly [codes] as the languages of a ChordPro document, leaving the rest of the text exactly as it
     * was: what comes back is written straight to the user's own file, whatever they wrote in it and however they
     * formatted it. A language the document already declares keeps the line and the spelling it is written on.
     *
     * The codes are normalized on the way in — folded to lower case and cut down to their primary subtag — so
     * `en-US` and `EN` name the same language as `en` does, and a code that names no language (`und`, `zxx`, an
     * empty string) is dropped rather than written.
     */
    operator fun invoke(text: String, codes: List<String>): String
}
