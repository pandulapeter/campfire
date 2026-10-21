/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.share

/**
 * Whether a [Share]'s copy still follows its item. A share in any state but [COPY_MISSING] keeps serving the last copy
 * uploaded.
 */
enum class ShareState {

    /** The copy follows the item. */
    OK,

    /**
     * The item is no longer in the library, and nobody stopped sharing it: deleted outside Campfire, or renamed where
     * the rename could not be recorded. Its copy is still there. Never stopped automatically; it comes back to [OK] if
     * the item reappears.
     */
    ORPHANED,

    /** The item's latest version is too large to share, so the copy is the last version that fit. */
    TOO_LARGE,

    /**
     * The shared copy is gone from the cloud folder, deleted by hand or by a stop that could not finish, so the link
     * opens nothing. Sharing the item again makes a new link.
     */
    COPY_MISSING,
}
