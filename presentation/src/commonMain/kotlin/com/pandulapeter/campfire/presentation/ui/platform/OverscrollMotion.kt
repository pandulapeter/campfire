/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.platform

/**
 * Whether the platform's overscroll effect moves the content it is applied to: Android's stretch from 12 on and iOS's
 * bounce do, while Android's glow before 12 only tints the edge and leaves the content in place (and the desktop and the
 * web have no effect at all). What follows the content's pull, a fade under a header above all, has nothing to follow
 * where this is false.
 */
internal expect val isContentCarriedByOverscroll: Boolean
