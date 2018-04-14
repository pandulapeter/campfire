package com.pandulapeter.campfire.data.persistence.access

import android.arch.persistence.room.*
import com.pandulapeter.campfire.data.model.remote.Song

@Dao
interface SongDao {

    @Query("SELECT * FROM ${Song.TABLE_NAME}")
    fun getAll(): List<Song>

    @Transaction
    fun updateAll(songs: List<Song>) {
        deleteAll()
        insertAll(songs)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(songs: List<Song>)

    @Query("DELETE FROM ${Song.TABLE_NAME}")
    fun deleteAll()
}