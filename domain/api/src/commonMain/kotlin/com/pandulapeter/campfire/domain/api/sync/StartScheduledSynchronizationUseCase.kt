/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.api.sync

import com.pandulapeter.campfire.data.model.sync.SyncProgress

interface StartScheduledSynchronizationUseCase {

    /**
     * Starts the automatic run that is waiting for the library to settle (see `SyncRepository.scheduleSynchronization`)
     * straight away, and does nothing when none is waiting. For the moment the app leaves the front, the last moment a
     * phone lets it start a run that carries on in the background.
     *
     * Answers the progress of the run going once it has returned - started now or already going - or null when none
     * is, so that the caller can hand it to the platform's keep-alive without waiting for the state to arrive.
     */
    operator fun invoke(): SyncProgress?
}
