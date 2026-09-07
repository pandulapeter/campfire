package com.pandulapeter.campfire.data.source.local.implementation.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.pandulapeter.campfire.data.source.local.implementation.model.TranspositionEntity

@Dao
internal interface TranspositionDao {

    @Query("SELECT * FROM ${TranspositionEntity.TABLE_NAME}")
    suspend fun getAll(): List<TranspositionEntity>

    @Query("DELETE FROM ${TranspositionEntity.TABLE_NAME}")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transpositions: List<TranspositionEntity>)

    @Transaction
    suspend fun updateAll(transpositions: List<TranspositionEntity>) {
        deleteAll()
        insertAll(transpositions)
    }
}
