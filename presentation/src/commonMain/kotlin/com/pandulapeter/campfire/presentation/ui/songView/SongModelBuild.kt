/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView

import com.pandulapeter.campfire.presentation.ui.songView.model.SongLyricsModelCache

/**
 * How the song pages of one pager build their models, beyond each page's own inputs: where [rememberSongLyricsModel]
 * takes a model built before and keeps the ones it builds, [cache], null for pages whose models nobody should share
 * (an import preview's, whose texts are not the library's and would only push library songs out); and [isQuiet], a
 * snapshot read of whether the screen's transition and the pager are at rest, which a model built in the background
 * waits for before it is applied.
 */
internal class SongModelBuild(val cache: SongLyricsModelCache?, val isQuiet: () -> Boolean)
