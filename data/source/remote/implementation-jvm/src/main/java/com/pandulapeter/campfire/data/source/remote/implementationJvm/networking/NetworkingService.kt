package com.pandulapeter.campfire.data.source.remote.implementationJvm.networking

import com.github.theapache64.retrosheet.annotations.Read
import com.pandulapeter.campfire.data.source.remote.implementationJvm.model.CollectionResponse
import com.pandulapeter.campfire.data.source.remote.implementationJvm.model.SongResponse
import retrofit2.http.GET

internal interface NetworkingService {

    @Read
    @GET(CollectionResponse.SHEET_NAME)
    suspend fun getCollections(): List<CollectionResponse>

    @Read
    @GET(SongResponse.SHEET_NAME)
    suspend fun getSongs(): List<SongResponse>
}