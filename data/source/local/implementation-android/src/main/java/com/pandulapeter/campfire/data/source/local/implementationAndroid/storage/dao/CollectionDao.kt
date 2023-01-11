package com.pandulapeter.campfire.data.source.local.implementationAndroid.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.pandulapeter.campfire.data.source.local.implementationAndroid.model.CollectionEntity

@Dao
internal interface CollectionDao {

    @Query("SELECT * FROM ${CollectionEntity.TABLE_NAME} WHERE ${CollectionEntity.DATABASE_URL} = :databaseUrl")
    suspend fun getAll(databaseUrl: String): List<CollectionEntity>

    @Query("DELETE FROM ${CollectionEntity.TABLE_NAME} WHERE ${CollectionEntity.DATABASE_URL} = :databaseUrl")
    suspend fun deleteAll(databaseUrl: String,)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(collections: List<CollectionEntity>)

    @Transaction
    suspend fun updateAll(databaseUrl: String, collections: List<CollectionEntity>) {
        deleteAll(databaseUrl)
        insertAll(collections)
    }
}