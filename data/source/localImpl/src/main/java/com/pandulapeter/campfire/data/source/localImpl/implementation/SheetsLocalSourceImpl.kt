package com.pandulapeter.campfire.data.source.localImpl.implementation

import com.pandulapeter.campfire.data.model.domain.Sheet
import com.pandulapeter.campfire.data.source.local.SheetsLocalSource
import com.pandulapeter.campfire.data.source.localImpl.implementation.database.dao.SheetDao
import com.pandulapeter.campfire.data.source.localImpl.implementation.mapper.toEntity
import com.pandulapeter.campfire.data.source.localImpl.implementation.mapper.toModel

internal class SheetsLocalSourceImpl(
    private val sheetDao: SheetDao
) : SheetsLocalSource {

    override suspend fun getSheets() = sheetDao.getAll().map { it.toModel() }

    override suspend fun saveSheets(sheets: List<Sheet>) = sheetDao.updateAll(sheets.map { it.toEntity() })
}