package com.pandulapeter.campfire.data.source.local

import com.pandulapeter.campfire.data.model.domain.Sheet

interface SheetsLocalSource {

    suspend fun getSheets(): List<Sheet>

    suspend fun saveSheets(sheets: List<Sheet>)
}