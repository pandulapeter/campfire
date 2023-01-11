package com.pandulapeter.campfire.domain.api.useCases

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.domain.api.models.ScreenData
import kotlinx.coroutines.flow.Flow

interface GetScreenDataUseCase {

    operator fun invoke(): Flow<DataState<ScreenData>>
}