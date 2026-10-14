/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.instrument

import com.pandulapeter.campfire.presentation.ui.components.RadioListItem
import com.pandulapeter.campfire.presentation.ui.components.only
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.ui.instrument.instrumentDescription
import com.pandulapeter.campfire.presentation.ui.instrument.instrumentLabel

/**
 * Every instrument the app knows, as a list rather than a segmented control: how many strings each has is under its
 * name, and an instrument the diagrams cannot be drawn for says so before it is picked ([instrumentDescription]). How it
 * is tuned is not, since that is a choice of its own with a chip of its own. Other is always offered: it only takes the
 * diagrams away, and the chip that opened the list stays to bring them back.
 */
@Composable
internal fun InstrumentList(
    modifier: Modifier = Modifier,
    selected: UserPreferences.Instrument,
    onSelected: (UserPreferences.Instrument) -> Unit,
) = Column(modifier = modifier.selectableGroup()) {
    UserPreferences.Instrument.entries.forEach { instrument ->
        RadioListItem(
            title = instrumentLabel(instrument),
            description = instrumentDescription(instrument),
            isSelected = selected == instrument,
            onSelected = { onSelected(instrument) },
        )
    }
}
