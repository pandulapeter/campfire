package com.pandulapeter.campfire.data.repository.api

import com.pandulapeter.campfire.data.model.domain.Sheet

interface SheetRepository {

    suspend fun getSheets(): List<Sheet>

    suspend fun updateSheets(sheets: List<Sheet>)
}