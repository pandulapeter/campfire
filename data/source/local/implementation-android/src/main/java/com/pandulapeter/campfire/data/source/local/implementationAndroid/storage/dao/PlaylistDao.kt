package com.pandulapeter.campfire.data.source.local.implementationAndroid.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.pandulapeter.campfire.data.source.local.implementationAndroid.model.PlaylistEntity

@Dao
internal interface PlaylistDao {

    @Query("SELECT * FROM ${PlaylistEntity.TABLE_NAME}")
    suspend fun getAll(): List<PlaylistEntity>

    @Query("DELETE FROM ${PlaylistEntity.TABLE_NAME}")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(playlists: List<PlaylistEntity>)

    @Transaction
    suspend fun updateAll(playlists: List<PlaylistEntity>) {
        deleteAll()
        insertAll(playlists)
    }
}