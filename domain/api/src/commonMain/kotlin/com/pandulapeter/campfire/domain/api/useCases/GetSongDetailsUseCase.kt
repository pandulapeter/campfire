package com.pandulapeter.campfire.domain.api.useCases

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.RawSongDetails
import kotlinx.coroutines.flow.Flow

interface GetSongDetailsUseCase {

    operator fun invoke(): Flow<DataState<Map<String, RawSongDetails>>>
}