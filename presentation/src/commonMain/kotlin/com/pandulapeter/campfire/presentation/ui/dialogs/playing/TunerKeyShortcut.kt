/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.playing

import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.ui.components.shortcuts.KeyShortcut

/**
 * U over one of song details' sheets (Playing, Tempo), opening the tuner over it as U does over the song, for the sheet's
 * `keyShortcut`: the app's root does not hear keys while a sheet is up. Null with the Tuner feature off. Neither sheet is
 * opened in an import preview, which is read only. Opening the tuner stops a playing click (`SoundLifetime`).
 */
internal fun tunerKeyShortcut(userPreferences: UserPreferences?, onTunerClicked: () -> Unit) =
    if (userPreferences?.isTunerEnabled == false) {
        null
    } else {
        KeyShortcut.TUNER to {
            println("Keyboard: tuner")
            onTunerClicked()
        }
    }
