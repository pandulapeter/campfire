package com.pandulapeter.campfire.data.source.remote.implementationJvm.networking

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Url

internal interface SongDetailsService {

    @Streaming
    @GET
    suspend fun downloadFile(@Url fileUrl: String): ResponseBody
}