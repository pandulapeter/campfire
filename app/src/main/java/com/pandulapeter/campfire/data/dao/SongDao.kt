package com.pandulapeter.campfire.data.dao

import android.arch.persistence.room.*
import com.pandulapeter.campfire.data.model.Song

@Dao
interface SongDao {

    @Query("SELECT * FROM ${Song.TABLE_NAME}")
    fun getAll(): List<Song>

    @Transaction
    fun updateData(songs: List<Song>) {
        deleteAll()
        insertAll(songs)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(songs: List<Song>)

    @Query("DELETE FROM ${Song.TABLE_NAME}")
    fun deleteAll()
}