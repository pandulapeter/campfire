/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor

import com.pandulapeter.campfire.presentation.ui.CampfireViewModel

/**
 * One change of [CampfireViewModel.editorTextEdits]: [edit] applied to the text the editor of [fileName] holds when it
 * arrives.
 */
class EditorTextEdit(val fileName: String, val edit: (String) -> String)
