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
import com.pandulapeter.campfire.domain.api.sharing.GetSharedLinksUseCase
import com.pandulapeter.campfire.domain.api.sharing.LoadSharedLinksUseCase
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
    private val createShareLink: CreateShareLinkUseCase,
    private val stopSharing: StopSharingUseCase,
    private val loadSharedLinks: LoadSharedLinksUseCase,
) {

    /** Everything shared from the connected account, as this device last saw it, most recently shared first. */
    val shares: StateFlow<List<Share>> = getSharedLinks().asState(scope, emptyList())

    private val _creation = MutableStateFlow<ShareCreation?>(null)

    /** The last share asked for, while it is made and after, until it is asked again or forgotten. */
    val creation = _creation.asStateFlow()

    private val _stoppingShareIds = MutableStateFlow(emptySet<String>())

    /** The shares being stopped, which are still listed until the account's list says otherwise. */
    val stoppingShareIds = _stoppingShareIds.asStateFlow()

    /** Shares [target] as a link, see [CreateShareLinkUseCase]; one at a time, a second request while one runs ignored. */
    fun create(target: ShareTarget, title: String) {
        if (_creation.value?.let { it.result == null } == true) return
        _creation.value = ShareCreation(target = target, result = null)
        scope.launch {
            val result = createShareLink(kind = target.kind, fileName = target.fileName, title = title)
            _creation.update { if (it?.target == target) it.copy(result = result) else it }
        }
    }

    /** Forgets how sharing [target] went, so that its screen offers to share it afresh; one still running is kept. */
    fun forgetCreation(target: ShareTarget) = _creation.update { if (it?.target == target && it.result != null) null else it }

    /** Stops sharing [share], saying so where it could not be done. */
    fun stop(share: Share) {
        if (share.shareId in _stoppingShareIds.value) return
        _stoppingShareIds.update { it + share.shareId }
        // A share made here a moment ago is otherwise still answered by its creation once the list has let it go.
        _creation.update { if ((it?.result as? CreateShareResult.Shared)?.share?.shareId == share.shareId) null else it }
        scope.launch {
            try {
                stopSharing(share.shareId)?.let { messageSink.sendMessage(Message.StopSharingFailed(it)) }
            } finally {
                _stoppingShareIds.update { it - share.shareId }
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
