package com.pandulapeter.campfire.data.dao

import android.arch.persistence.room.*
import com.pandulapeter.campfire.data.model.Song
import com.pandulapeter.campfire.data.model.SongDetail

@Dao
interface SongDetailDao {

    @Query("SELECT * FROM ${SongDetail.TABLE_NAME} WHERE ${Song.ID} IN(:songId) LIMIT 1")
    fun get(songId: String): SongDetail?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(songs: SongDetail)

    @Delete()
    fun delete(songDetail: SongDetail)
}