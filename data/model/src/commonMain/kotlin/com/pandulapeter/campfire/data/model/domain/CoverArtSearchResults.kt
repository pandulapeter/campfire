/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.domain

/**
 * Where one cover search is, with every catalogue it asks: what has been found so far and what each of the rest is
 * doing. The services answer at their own pace, so the same search is reported again every time one of them does.
 */
data class CoverArtSearchResults(
    /** What has been found so far, each service's candidates in its own order and the services in the order they answered. */
    val candidates: List<CoverArtCandidate>,
    /** The services that have not answered yet. */
    val pending: Set<CoverArtService>,
    /** Those of [pending] that were asked to wait by the service and are waiting before they ask again. */
    val busy: Set<CoverArtService>,
    /** The services that could not be reached, or kept refusing. */
    val failed: Set<CoverArtService>,
) {
    /** Whether every service has answered, one way or the other. */
    val isComplete get() = pending.isEmpty()
}
