/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.widget

import android.content.res.Resources
import android.view.View
import androidx.glance.text.TextAlign
import com.pandulapeter.campfire.presentation.ui.widget.WidgetSnapshot

/**
 * Whether a widget is drawn the other way round from its launcher: a launcher lays a widget out in the system's
 * direction, which Glance cannot change, so where the app's language is written the other way
 * ([WidgetSnapshot.Strings.isRightToLeft]) the widget aligns its lines to their end and puts its header's button first,
 * as the app does. The system's direction rather than the context's, which follows the app's own language.
 */
internal fun WidgetSnapshot?.isMirrored() =
    this != null && strings.isRightToLeft != (Resources.getSystem().configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL)

/** Where a widget's lines are aligned, see [isMirrored]. */
internal fun textAlignOf(isMirrored: Boolean) = if (isMirrored) TextAlign.End else TextAlign.Start
