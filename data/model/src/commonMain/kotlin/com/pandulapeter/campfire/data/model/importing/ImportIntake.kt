/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.importing

/** How an import takes in the files that arrive, before anything in them is read. */
object ImportIntake {

    /**
     * Whether an archive that arrives is received into the staging folder (`imports/`) and unpacked from there rather
     * than read whole into memory. The one switch between the two paths: off while the streaming one is proven equal to
     * the in-memory one, which stays what runs until then.
     */
    const val STAGES_ARCHIVES = false
}
