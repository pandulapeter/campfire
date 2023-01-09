package com.pandulapeter.campfire.data.source.localImpl.implementation.database.dao

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

    @Query("DELETE FROM ${CollectionEntity.TABLE_NAME}")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(collections: List<CollectionEntity>)

    @Transaction
    suspend fun updateAll(collections: List<CollectionEntity>) {
        deleteAll()
        insertAll(collections)
    }
}