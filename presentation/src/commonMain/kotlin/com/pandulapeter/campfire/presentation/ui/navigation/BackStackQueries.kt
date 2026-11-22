/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.navigation

internal fun List<CampfireDestination>.songDetailsIds() = mapNotNullTo(mutableSetOf()) { (it as? CampfireDestination.SongDetails)?.id }

internal fun List<CampfireDestination>.lastTool() = (lastOrNull() as? CampfireDestination.TopLevel)?.takeIf { it in CampfireDestination.TopLevel.tools }

/**
 * Whether an editor holding unsaved text is anywhere on [stack]: what neither a main screen's item, an imported song, nor
 * the import's screen may clear or cover, since the unsaved changes question is only ever asked of an editor on top.
 */
internal fun isUnsavedEditorOpen(stack: List<CampfireDestination>, hasUnsavedEditorText: () -> Boolean) =
    hasUnsavedEditorText() && stack.any { it is CampfireDestination.SongEditor }
