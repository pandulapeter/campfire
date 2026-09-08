package com.pandulapeter.campfire.data.source.local.implementation.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pandulapeter.campfire.data.source.local.implementation.model.RawSongDetailsEntity

@Dao
internal interface RawSongDetailsDao {

    @Query("SELECT url FROM ${RawSongDetailsEntity.TABLE_NAME}")
    suspend fun getAllUrls(): List<String>

    @Query("SELECT * FROM ${RawSongDetailsEntity.TABLE_NAME} WHERE url = :url")
    suspend fun get(url: String): RawSongDetailsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rawSongDetails: RawSongDetailsEntity)
}
