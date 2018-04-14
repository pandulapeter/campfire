package com.pandulapeter.campfire.data.persistence.access

import android.arch.persistence.room.Dao
import android.arch.persistence.room.Insert
import android.arch.persistence.room.OnConflictStrategy
import android.arch.persistence.room.Query
import com.pandulapeter.campfire.data.model.local.Playlist

@Dao
interface PlaylistDao {

    @Query("SELECT * FROM ${Playlist.TABLE_NAME}")
    fun getAll(): List<Playlist>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(playlist: Playlist)

    @Query("DELETE FROM ${Playlist.TABLE_NAME} WHERE ${Playlist.ID} IN(:playlistId)")
    fun delete(playlistId: String)

    @Query("DELETE FROM ${Playlist.TABLE_NAME}")
    fun deleteAll()
}