package com.pandulapeter.campfire.data.repository.implementation

import com.pandulapeter.campfire.data.model.domain.Database
import com.pandulapeter.campfire.data.repository.api.DatabaseRepository
import com.pandulapeter.campfire.data.repository.implementation.base.BaseLocalDataRepository
import com.pandulapeter.campfire.data.source.local.api.DatabaseLocalSource

internal class DatabaseRepositoryImpl(
    databaseLocalSource: DatabaseLocalSource
) : BaseLocalDataRepository<List<Database>>(
    loadDataFromLocalSource = { databaseLocalSource.loadDatabases().process() },
    saveDataToLocalSource = { databaseLocalSource.saveDatabases(it.process()) }
), DatabaseRepository {

    override val databases = dataState

    override suspend fun loadDatabasesIfNeeded() = loadDataIfNeeded()

    override suspend fun saveDatabases(databases: List<Database>) = saveData(databases)

    companion object {

        private val hardcodedDatabases = listOf(
            Database(
                url = "https://docs.google.com/spreadsheets/d/1dS-Dz7XnXepl4_RYw44J0CGtjNMNTLiBD9fHL7IifJs/",
                name = "Campfire - Main",
                isEnabled = true,
                priority = 0,
                isAddedByUser = false
            ),
            Database(
                url = "https://docs.google.com/spreadsheets/d/1iFXYxBJAHEwELAtJM-hRYohKgQDRR34V2gM0hN6zr0s/",
                name = "Campfire - Hungarian songs",
                isEnabled = false,
                priority = 1,
                isAddedByUser = false
            ),
            Database(
                url = "https://docs.google.com/spreadsheets/d/19-L5khxfdNMq1V4uRzo6StHxbnHyDlFgzeT7t_Fe1YA/",
                name = "Campfire - Romanian songs",
                isEnabled = false,
                priority = 2,
                isAddedByUser = false
            ),
        )

        private fun List<Database>.process() = (this + hardcodedDatabases).distinctBy { it.url }
    }
}