package com.pandulapeter.campfire.data.dao

import android.arch.persistence.room.Dao
import android.arch.persistence.room.Insert
import android.arch.persistence.room.OnConflictStrategy.REPLACE
import android.arch.persistence.room.Query
import com.pandulapeter.campfire.data.model.Song

@Dao
interface SongDao {

    @Query("SELECT * from ${Song.TABLE_NAME}")
    fun getAll(): List<Song>

    @Insert(onConflict = REPLACE)
    fun insert(song: Song)

    @Query("DELETE from ${Song.TABLE_NAME}")
    fun deleteAll()
}