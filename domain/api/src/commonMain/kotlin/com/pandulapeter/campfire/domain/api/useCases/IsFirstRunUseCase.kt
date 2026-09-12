/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.api.useCases

/**
 * Whether the app has never been used on this device, which is what the demo library is offered on and nothing else.
 *
 * What it actually asks is whether Campfire has ever written its preferences, since those are the first thing it
 * writes about itself. That leaves one case it cannot tell apart: somebody who installed the app, changed no setting
 * and imported nothing looks exactly like a fresh installation — which is why the caller asks whether the library is
 * empty as well, and why an answer of true is a reason to *offer* something rather than to change anything.
 *
 * It has to be asked before anything is saved, and it is therefore read once as the app starts rather than observed.
 */
interface IsFirstRunUseCase {

    suspend operator fun invoke(): Boolean
}
