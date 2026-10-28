/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.screenshots

/**
 * The generic device a listing image draws its screenshot in, after the promotional video's (`tools/promo-video`'s
 * `kit.tsx`): no brand's shape, no camera notch or island, so the frame never dates and never claims a device.
 */
internal enum class FrameKind {
    PHONE,
    TABLET,

    /** A phone that unfolds into a small tablet, drawn opened flat and on its side, with the phone's thin bezel. */
    FOLDABLE,

    /** A laptop's lid and the edge of its base, for every desktop platform. */
    LAPTOP,
}
