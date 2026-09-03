package com.pandulapeter.campfire.data.source.remote.implementation.networking

import com.pandulapeter.campfire.data.source.remote.implementation.model.SongResponse
import de.jensklingenberg.ktorfit.http.GET
import io.github.theapache64.retrosheet.annotations.Read

internal interface SongService {

    @Read
    @GET(SongResponse.SHEET_NAME)
    suspend fun getSongs(): List<SongResponse>
}
