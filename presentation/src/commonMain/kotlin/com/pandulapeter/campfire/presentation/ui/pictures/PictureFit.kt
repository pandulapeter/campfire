/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.pictures

import com.pandulapeter.campfire.data.model.image.ImageRefusal
import com.pandulapeter.campfire.presentation.ui.messages.Message

/** What [PictureFitter] made of a picture the user adds. */
internal sealed interface PictureFit {

    /** A picture to keep, as [bytes]. */
    sealed interface Usable : PictureFit {
        val bytes: ByteArray

        /** The snackbar saying what became of the picture, null where it is kept as it came. */
        val notice: Message?
    }

    /** Within the limits as it came, and never touched. */
    class Kept(override val bytes: ByteArray) : Usable {
        override val notice: Message? get() = null
    }

    /** Too large (in bytes or pixels) as it came, and [bytes] is a smaller copy. */
    class Shrunk(override val bytes: ByteArray) : Usable {
        override val notice: Message get() = Message.ImageShrunk
    }

    /** Of a format some platform cannot show (HEIC, AVIF) as it came, and [bytes] is a copy in one every platform can. */
    class Converted(override val bytes: ByteArray) : Usable {
        override val notice: Message get() = Message.ImageConverted
    }

    /** Refused for [reason], what the picture as it came was refused for, whether or not a copy was tried. */
    data class Refused(val reason: ImageRefusal) : PictureFit
}
