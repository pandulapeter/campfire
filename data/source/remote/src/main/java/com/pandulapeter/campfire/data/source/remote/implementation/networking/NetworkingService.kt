package com.pandulapeter.campfire.data.source.remote.implementation.networking

import com.github.theapache64.retrosheet.annotations.Read
import com.pandulapeter.campfire.data.source.remote.implementation.model.CollectionResponse
import com.pandulapeter.campfire.data.source.remote.implementation.model.LanguageResponse
import com.pandulapeter.campfire.data.source.remote.implementation.model.SongResponse
import retrofit2.http.GET

internal interface NetworkingService {

    @Read
    @GET(CollectionResponse.SHEET_NAME)
    suspend fun getCollections(): List<CollectionResponse>

    @Read
    @GET(LanguageResponse.SHEET_NAME)
    suspend fun getLanguages(): List<LanguageResponse>

    @Read
    @GET(SongResponse.SHEET_NAME)
    suspend fun getSongs(): List<SongResponse>
}