package com.pandulapeter.campfire.data.source.localImpl.implementation.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.pandulapeter.campfire.data.source.localImpl.implementation.model.SongEntity

@Dao
internal interface SongDao {

    @Query("SELECT * FROM ${SongEntity.TABLE_NAME}")
    suspend fun getAll(): List<SongEntity>

    @Query("DELETE FROM ${SongEntity.TABLE_NAME}")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(songs: List<SongEntity>)

    @Transaction
    suspend fun updateAll(songs: List<SongEntity>) {
        deleteAll()
        insertAll(songs)
    }
}