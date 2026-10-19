/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.catalogue

/**
 * What the app could find at a cover's address: the image, or why there is none to show. [Missing] will not change,
 * while [Unreachable] may a minute later, or once the user searches again.
 */
sealed interface CoverArtResult {

    class Image(val bytes: ByteArray) : CoverArtResult

    /** Nothing there, or something that is not an image. */
    data object Missing : CoverArtResult

    /** No answer: no network, a timeout, or the host's own trouble. */
    data object Unreachable : CoverArtResult
}
