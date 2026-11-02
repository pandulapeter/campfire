/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.menu

import androidx.compose.runtime.compositionLocalOf

/**
 * True while the "update required" screen covers the app. That screen is drawn over the app's content, but a dialog,
 * a bottom sheet and a dropdown menu are windows of their own on Android, above the activity's content and so above
 * the screen, where they could still be used. Whatever opens one reads this and composes nothing while it is true;
 * what it was showing stays in its state, so an update that does not install leaves it where it was.
 */
internal val LocalIsCoveredByRequiredUpdate = compositionLocalOf { false }
