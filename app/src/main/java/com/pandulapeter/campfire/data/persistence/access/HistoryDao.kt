package com.pandulapeter.campfire.data.persistence.access

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pandulapeter.campfire.data.model.local.HistoryItem
import com.pandulapeter.campfire.data.model.remote.Song

@Dao
interface HistoryDao {

    @Query("SELECT * FROM ${HistoryItem.TABLE_NAME}")
    fun getAll(): List<HistoryItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(historyItem: HistoryItem)

    @Query("DELETE FROM ${HistoryItem.TABLE_NAME} WHERE ${Song.ID} IN(:songId)")
    fun delete(songId: String)

    @Query("DELETE FROM ${HistoryItem.TABLE_NAME}")
    fun deleteAll()
}