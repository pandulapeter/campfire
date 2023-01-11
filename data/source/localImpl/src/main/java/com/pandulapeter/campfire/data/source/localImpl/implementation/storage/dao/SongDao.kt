package com.pandulapeter.campfire.data.source.localImpl.implementation.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.pandulapeter.campfire.data.source.localImpl.implementation.model.SongEntity

@Dao
internal interface SongDao {

    @Query("SELECT * FROM ${SongEntity.TABLE_NAME} WHERE ${SongEntity.DATABASE_URL} = :databaseUrl")
    suspend fun getAll(databaseUrl: String): List<SongEntity>

    @Query("DELETE FROM ${SongEntity.TABLE_NAME} WHERE ${SongEntity.DATABASE_URL} = :databaseUrl")
    suspend fun deleteAll(databaseUrl: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(songs: List<SongEntity>)

    @Transaction
    suspend fun updateAll(databaseUrl: String, songs: List<SongEntity>) {
        deleteAll(databaseUrl)
        insertAll(songs)
    }
}