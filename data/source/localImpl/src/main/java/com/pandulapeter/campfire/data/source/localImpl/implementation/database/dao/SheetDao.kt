package com.pandulapeter.campfire.data.source.localImpl.implementation.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.pandulapeter.campfire.data.source.localImpl.implementation.model.SheetEntity

@Dao
internal interface SheetDao {

    @Query("SELECT * FROM ${SheetEntity.TABLE_NAME}")
    suspend fun getAll(): List<SheetEntity>

    @Query("DELETE FROM ${SheetEntity.TABLE_NAME}")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sheets: List<SheetEntity>)

    @Transaction
    suspend fun updateAll(sheets: List<SheetEntity>) {
        deleteAll()
        insertAll(sheets)
    }
}