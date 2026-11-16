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
 * The picture [bytes] hold, decoded once and encoded by each of [attempts] in turn until one comes out within
 * [maxBytes], which is answered; null where none does, or where the platform cannot decode the picture at all.
 *
 * The decoding is what this is for, so it is bounded by the size it is decoded to ([reencodedSizeOf] of the picture's
 * upright size), never the picture's own: a photo of a hundred megapixels must not be held whole. The picture comes out
 * upright (its EXIF orientation applied), in sRGB where the platform converts easily, and with none of its metadata, so a
 * photo's location is not kept. Runs away from the main thread, and never throws but for cancellation: a picture the
 * platform runs out of memory on is null too.
 */
internal expect suspend fun reencodePicture(bytes: ByteArray, attempts: List<PictureAttempt>, maxBytes: Long): ByteArray?
