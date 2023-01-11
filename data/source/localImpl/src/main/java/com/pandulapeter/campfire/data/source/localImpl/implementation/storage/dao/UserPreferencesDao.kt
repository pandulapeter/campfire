package com.pandulapeter.campfire.data.source.localImpl.implementation.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.pandulapeter.campfire.data.source.localImpl.implementation.model.UserPreferencesEntity

@Dao
internal interface UserPreferencesDao {

    @Query("SELECT * FROM ${UserPreferencesEntity.TABLE_NAME}")
    suspend fun getAll(): List<UserPreferencesEntity>

    @Query("DELETE FROM ${UserPreferencesEntity.TABLE_NAME}")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(userPreferences: List<UserPreferencesEntity>)

    @Transaction
    suspend fun updateAll(userPreferences: List<UserPreferencesEntity>) {
        deleteAll()
        insertAll(userPreferences)
    }
}