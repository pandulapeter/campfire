/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
@file:OptIn(ExperimentalWasmJsInterop::class)

package com.pandulapeter.campfire.data.model.web

import kotlin.js.ExperimentalWasmJsInterop

/**
 * A `File` the user picked or dropped, as the web's intake hands it to the staging (`ReceivedArchive.take`): a Kotlin
 * type around it, so that the storage, which takes a platform's file as `Any`, can tell it by a checked cast.
 */
class PickedFile(val file: JsAny)
