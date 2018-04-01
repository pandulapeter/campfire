package com.pandulapeter.campfire.data.dao

import android.arch.persistence.room.*
import com.pandulapeter.campfire.data.model.Song
import com.pandulapeter.campfire.data.model.SongDetail
import com.pandulapeter.campfire.data.model.SongDetailMetadata

@Dao
interface SongDetailDao {

    @Query("SELECT ${Song.ID}, ${SongDetail.VERSION} FROM ${SongDetail.TABLE_NAME}")
    fun getAllMetadata(): List<SongDetailMetadata>

    @Query("SELECT * FROM ${SongDetail.TABLE_NAME} WHERE ${Song.ID} IN(:songId) LIMIT 1")
    fun get(songId: String): SongDetail?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(songs: SongDetail)

    @Query("DELETE FROM ${SongDetail.TABLE_NAME} WHERE ${Song.ID} IN(:songId)")
    fun delete(songId: String)

    @Query("DELETE FROM ${SongDetail.TABLE_NAME}")
    fun deleteAll()
}