package com.pandulapeter.campfire.data.source.localImpl.implementation.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.pandulapeter.campfire.data.source.localImpl.implementation.model.CollectionEntity

@Dao
internal interface CollectionDao {

    @Query("SELECT * FROM ${CollectionEntity.TABLE_NAME}")
    suspend fun getAll(): List<CollectionEntity>

    @Query("DELETE FROM ${CollectionEntity.TABLE_NAME} WHERE ${CollectionEntity.SHEET_URL} = :sheetUrl")
    suspend fun deleteAll(sheetUrl: String,)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(collections: List<CollectionEntity>)

    @Transaction
    suspend fun updateAll(sheetUrl: String, collections: List<CollectionEntity>) {
        deleteAll(sheetUrl)
        insertAll(collections)
    }
}