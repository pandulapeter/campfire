package com.pandulapeter.campfire.data.persistence.access

import android.arch.persistence.room.Dao
import android.arch.persistence.room.Insert
import android.arch.persistence.room.OnConflictStrategy
import android.arch.persistence.room.Query
import com.pandulapeter.campfire.data.model.local.HistoryItem
import com.pandulapeter.campfire.data.model.remote.Song

@Dao
interface HistoryDao {

    @Query("SELECT * FROM ${HistoryItem.TABLE_NAME}")
    fun getAllHistory(): List<HistoryItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(historyItem: HistoryItem)

    @Query("DELETE FROM ${HistoryItem.TABLE_NAME} WHERE ${Song.ID} IN(:songId)")
    fun delete(songId: String)

    @Query("DELETE FROM ${HistoryItem.TABLE_NAME}")
    fun deleteAll()
}