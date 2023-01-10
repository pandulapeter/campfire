package com.pandulapeter.campfire.data.source.localImpl.implementation.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.pandulapeter.campfire.data.source.localImpl.implementation.model.DatabaseEntity

@Dao
internal interface DatabaseDao {

    @Query("SELECT * FROM ${DatabaseEntity.TABLE_NAME}")
    suspend fun getAll(): List<DatabaseEntity>

    @Query("DELETE FROM ${DatabaseEntity.TABLE_NAME}")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(databases: List<DatabaseEntity>)

    @Transaction
    suspend fun updateAll(databases: List<DatabaseEntity>) {
        deleteAll()
        insertAll(databases)
    }
}