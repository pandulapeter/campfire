package com.pandulapeter.campfire.data.source.local.implementationDesktop.source

import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.source.local.api.SongLocalSource

internal class SongLocalSourceImpl : SongLocalSource {

    override suspend fun loadSongs(databaseUrl: String) = emptyList<Song>() // TODO

    override suspend fun saveSongs(databaseUrl: String, songs: List<Song>) = Unit // TODO
}