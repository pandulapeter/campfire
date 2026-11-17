/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.pictures

import com.pandulapeter.campfire.presentation.ui.platform.PictureSource

/**
 * What a missing picture's Add image and Download do ([LocalSongImageAdder]).
 *
 * @param canPickPhotos Whether the button asks which [PictureSource] to pick from first (`FilePicker.canPickPhotos`);
 *   where it does not, it picks from [PictureSource.FILES].
 * @param add Picks a picture from the source handed for the missing picture the song names by the name handed, and
 *   stores it under that song.
 * @param download Downloads the picture a missing one names by its address (`PictureLinks.isLink`), and stores it under
 *   that song as [add] would.
 */
internal class MissingImageAdder(
    val canPickPhotos: Boolean,
    val add: (name: String, source: PictureSource) -> Unit,
    val download: (name: String) -> Unit,
)
