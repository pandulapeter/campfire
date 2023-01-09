package com.pandulapeter.campfire.data.source.localImpl.implementation.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.pandulapeter.campfire.data.source.localImpl.implementation.model.LanguageEntity

@Dao
internal interface LanguageDao {

    @Query("SELECT * FROM ${LanguageEntity.TABLE_NAME}")
    suspend fun getAll(): List<LanguageEntity>

    @Query("DELETE FROM ${LanguageEntity.TABLE_NAME}")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(languages: List<LanguageEntity>)

    @Transaction
    suspend fun updateAll(languages: List<LanguageEntity>) {
        deleteAll()
        insertAll(languages)
    }
}