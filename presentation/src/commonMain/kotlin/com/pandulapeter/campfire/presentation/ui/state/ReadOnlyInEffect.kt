/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state

import com.pandulapeter.campfire.data.model.preferences.UserPreferences

/**
 * Whether read only mode is in effect: off while the songs are switched off, since its switch goes with them, and the
 * library actions of Settings would otherwise stay disabled with nothing on screen saying why. The preference is kept.
 */
internal val UserPreferences?.isReadOnlyInEffect get() = this?.isPerformanceModeEnabled == true && areSongsEnabled
