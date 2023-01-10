package com.pandulapeter.campfire.data.repository.implementation

import com.pandulapeter.campfire.data.model.domain.Sheet
import com.pandulapeter.campfire.data.repository.api.SheetRepository
import com.pandulapeter.campfire.data.source.local.SheetsLocalSource

internal class SheetRepositoryImpl(
    private val sheetLocalSource: SheetsLocalSource
) : SheetRepository {

    private var cache: List<Sheet>? = null

    override suspend fun getSheets() = ((cache ?: sheetLocalSource.getSheets().also {
        cache = it
    }) + hardcodedSheets).distinctBy { it.url }.sortedBy { it.priority }

    override suspend fun updateSheets(sheets: List<Sheet>) {
        cache = sheets
        sheetLocalSource.saveSheets(sheets)
    }

    companion object {
        private val hardcodedSheets = listOf(
            Sheet(
                url = "https://docs.google.com/spreadsheets/d/1dS-Dz7XnXepl4_RYw44J0CGtjNMNTLiBD9fHL7IifJs/",
                name = "Campfire - Main",
                isActive = true,
                priority = 0,
            ),
            Sheet(
                url = "https://docs.google.com/spreadsheets/d/1iFXYxBJAHEwELAtJM-hRYohKgQDRR34V2gM0hN6zr0s/",
                name = "Campfire - Hungarian songs",
                isActive = true,
                priority = 1,
            ),
            Sheet(
                url = "https://docs.google.com/spreadsheets/d/19-L5khxfdNMq1V4uRzo6StHxbnHyDlFgzeT7t_Fe1YA/",
                name = "Campfire - Romanian songs",
                isActive = true,
                priority = 2,
            ),
        )
    }
}