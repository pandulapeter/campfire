package com.pandulapeter.campfire.data.source.local.implementationAndroid.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pandulapeter.campfire.data.source.local.implementationAndroid.model.RawSongDetailsEntity

@Dao
internal interface RawSongDetailsDao {

    @Query("SELECT * FROM ${RawSongDetailsEntity.TABLE_NAME}")
    suspend fun getAll(): List<RawSongDetailsEntity>

    @Query("DELETE FROM ${RawSongDetailsEntity.TABLE_NAME}")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rawSongDetails: RawSongDetailsEntity)
}