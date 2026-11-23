/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.api.importing

interface CanLoadPagesUseCase {

    /**
     * Whether this platform can read a page by its address at all (Android and iOS can, the desktop and the web build
     * cannot), whatever the Pages from links switch says: where it cannot, the switch is no use either.
     */
    operator fun invoke(): Boolean
}
