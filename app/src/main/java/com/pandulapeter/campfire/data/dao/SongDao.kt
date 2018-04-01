package com.pandulapeter.campfire.data.dao

import android.arch.persistence.room.Dao
import android.arch.persistence.room.Insert
import android.arch.persistence.room.Query
import android.arch.persistence.room.Transaction
import com.pandulapeter.campfire.data.model.Song

@Dao
interface SongDao {

    @Query("SELECT * from ${Song.TABLE_NAME}")
    fun getAll(): List<Song>

    @Transaction
    fun updateData(songs: List<Song>) {
        deleteAll()
        insertAll(songs)
    }

    @Insert
    abstract fun insertAll(songs: List<Song>)

    @Query("DELETE from ${Song.TABLE_NAME}")
    fun deleteAll()
}