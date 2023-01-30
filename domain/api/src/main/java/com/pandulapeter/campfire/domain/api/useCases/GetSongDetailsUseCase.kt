package com.pandulapeter.campfire.domain.api.useCases

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.SongDetails
import kotlinx.coroutines.flow.Flow

interface GetSongDetailsUseCase {

    operator fun invoke(): Flow<DataState<SongDetails>>
}