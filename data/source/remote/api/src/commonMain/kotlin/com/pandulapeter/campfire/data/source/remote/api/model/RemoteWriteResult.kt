package com.pandulapeter.campfire.data.source.remote.api.model

/** What an upload ended in. A [Conflict] is not an error: it means the remote file moved under the write. */
sealed interface RemoteWriteResult {

    data class Written(val revision: String) : RemoteWriteResult

    /** The remote file did not have the revision the caller expected any more, so nothing was overwritten. */
    data object Conflict : RemoteWriteResult
}
