package com.pandulapeter.campfire.data.repository.implementation

import com.pandulapeter.campfire.data.model.domain.Database
import com.pandulapeter.campfire.data.repository.api.DatabaseRepository
import com.pandulapeter.campfire.data.source.local.DatabaseLocalSource

internal class DatabaseRepositoryImpl(
    private val sheetLocalSource: DatabaseLocalSource
) : DatabaseRepository {

    private var cache: List<Database>? = null

    override suspend fun getDatabases() = ((cache ?: sheetLocalSource.getDatabases().also {
        cache = it
    }) + hardcodedDatabases).distinctBy { it.url }.sortedBy { it.priority }

    override suspend fun updateDatabases(databases: List<Database>) {
        cache = databases
        sheetLocalSource.saveDatabases(databases)
    }

    companion object {
        private val hardcodedDatabases = listOf(
            Database(
                url = "https://docs.google.com/spreadsheets/d/1dS-Dz7XnXepl4_RYw44J0CGtjNMNTLiBD9fHL7IifJs/",
                name = "Campfire - Main",
                isActive = true,
                priority = 0,
                isAddedByUser = false
            ),
            Database(
                url = "https://docs.google.com/spreadsheets/d/1iFXYxBJAHEwELAtJM-hRYohKgQDRR34V2gM0hN6zr0s/",
                name = "Campfire - Hungarian songs",
                isActive = true,
                priority = 1,
                isAddedByUser = false
            ),
            Database(
                url = "https://docs.google.com/spreadsheets/d/19-L5khxfdNMq1V4uRzo6StHxbnHyDlFgzeT7t_Fe1YA/",
                name = "Campfire - Romanian songs",
                isActive = true,
                priority = 2,
                isAddedByUser = false
            ),
        )
    }
}