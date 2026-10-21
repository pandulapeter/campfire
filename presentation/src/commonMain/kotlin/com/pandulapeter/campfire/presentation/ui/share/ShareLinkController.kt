/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.share

import com.pandulapeter.campfire.data.model.share.CreateShareResult
import com.pandulapeter.campfire.data.model.share.Share
import com.pandulapeter.campfire.data.model.share.SharingFailure
import com.pandulapeter.campfire.domain.api.sharing.CreateShareLinkUseCase
import com.pandulapeter.campfire.domain.api.sharing.GetPendingShareStopsUseCase
import com.pandulapeter.campfire.domain.api.sharing.GetSharedLinksUseCase
import com.pandulapeter.campfire.domain.api.sharing.LoadSharedLinksUseCase
import com.pandulapeter.campfire.domain.api.sharing.StopSharingEverythingUseCase
import com.pandulapeter.campfire.domain.api.sharing.StopSharingUseCase
import com.pandulapeter.campfire.presentation.ui.messages.Message
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import com.pandulapeter.campfire.presentation.ui.state.asState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The songs and setlists shared as links from the connected account, and the creating and stopping of shares, for the
 * export screen and Settings alike. Both run in the view model's scope rather than a screen's: a share half made when
 * its screen went would leave an uploaded copy behind, and one stopped must stop however the confirmation was left.
 */
internal class ShareLinkController(
    private val scope: CoroutineScope,
    private val messageSink: MessageSink,
    getSharedLinks: GetSharedLinksUseCase,
    getPendingShareStops: GetPendingShareStopsUseCase,
    private val createShareLink: CreateShareLinkUseCase,
    private val stopSharing: StopSharingUseCase,
    private val stopSharingEverything: StopSharingEverythingUseCase,
    private val loadSharedLinks: LoadSharedLinksUseCase,
) {

    /** Everything shared from the connected account, as this device last saw it, most recently shared first. */
    val shares: StateFlow<List<Share>> = getSharedLinks().asState(scope, emptyList())

    /** Whether disconnecting now would leave a deleted item's link working, see [GetPendingShareStopsUseCase]. */
    val hasPendingStops: StateFlow<Boolean> = getPendingShareStops().asState(scope, false)

    private val _creations = MutableStateFlow<Map<ShareTarget, ShareCreation>>(emptyMap())

    /**
     * The last share asked for of each song and setlist, while it is made and after, until it is asked again or forgotten.
     * One per item rather than one in all, so that an item's Create link is not refused while another's copy is still
     * being uploaded after its screen went: two shares made at once never clobber each other in the account's list.
     */
    val creations = _creations.asStateFlow()

    private val _stoppingShareIds = MutableStateFlow(emptySet<String>())

    /** The shares being stopped, which are still listed until the account's list says otherwise. */
    val stoppingShareIds = _stoppingShareIds.asStateFlow()

    /**
     * Shares [target] as a link, see [CreateShareLinkUseCase]; one at a time per item, a second request for the same item
     * while it runs ignored.
     */
    fun create(target: ShareTarget, title: String) {
        if (_creations.value[target]?.let { it.result == null } == true) return
        _creations.update { it + (target to ShareCreation(target = target, result = null)) }
        scope.launch {
            val result = createShareLink(kind = target.kind, fileName = target.fileName, title = title)
            // Only into an entry still there, so that one cleared meanwhile by a stop is not put back.
            _creations.update { map -> map[target]?.let { map + (target to it.copy(result = result)) } ?: map }
        }
    }

    /** Forgets how sharing [target] went, so that its screen offers to share it afresh; one still running is kept. */
    fun forgetCreation(target: ShareTarget) = _creations.update { map -> if (map[target]?.result != null) map - target else map }

    /** Stops sharing [share], saying so where it could not be done. */
    fun stop(share: Share) {
        if (share.shareId in _stoppingShareIds.value) return
        _stoppingShareIds.update { it + share.shareId }
        // A share made here a moment ago is otherwise still answered by its creation once the list has let it go.
        _creations.update { map -> map.filterValues { (it.result as? CreateShareResult.Shared)?.share?.shareId != share.shareId } }
        scope.launch {
            try {
                stopSharing(share.shareId)?.let { messageSink.sendMessage(Message.StopSharingFailed(it)) }
            } finally {
                _stoppingShareIds.update { it - share.shareId }
            }
        }
    }

    private val _isStoppingEverything = MutableStateFlow(false)

    /** Whether every share is being stopped, see [stopEverything]. */
    val isStoppingEverything = _isStoppingEverything.asStateFlow()

    /** Stops every share of the account, saying so where some could not be stopped; one request at a time. */
    fun stopEverything() {
        if (_isStoppingEverything.value) return
        _isStoppingEverything.value = true
        _creations.update { map -> map.filterValues { it.result !is CreateShareResult.Shared } }
        scope.launch {
            try {
                stopSharingEverything()?.let { messageSink.sendMessage(Message.StopSharingEverythingFailed(it)) }
            } finally {
                _isStoppingEverything.value = false
            }
        }
    }

    /** Reads the account's list of shares again, see [LoadSharedLinksUseCase]. Null when it worked. */
    suspend fun refresh(): SharingFailure? = loadSharedLinks()
}

/** A request to share [target], and its [result] once it is in. */
internal data class ShareCreation(
    val target: ShareTarget,
    val result: CreateShareResult?,
)
