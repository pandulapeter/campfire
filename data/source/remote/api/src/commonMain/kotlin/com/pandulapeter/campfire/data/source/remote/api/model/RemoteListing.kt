package com.pandulapeter.campfire.data.source.remote.api.model

/**
 * Everything in the remote library folder.
 *
 * A wrapper rather than a bare list because this is where a "what changed since" token would go if live updates are
 * ever wanted - Dropbox calls it a `list_folder` cursor, Drive a `startPageToken`. Sync does not need one: it lists
 * the whole folder every run, which for a library of songs is a single request and is right even after a run that
 * was interrupted half way.
 */
data class RemoteListing(
    val files: List<RemoteFile>
)
