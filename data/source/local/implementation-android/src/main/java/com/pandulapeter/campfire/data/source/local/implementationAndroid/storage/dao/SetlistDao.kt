package com.pandulapeter.campfire.data.source.local.implementationAndroid.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.pandulapeter.campfire.data.source.local.implementationAndroid.model.SetlistEntity

@Dao
internal interface SetlistDao {

    @Query("SELECT * FROM ${SetlistEntity.TABLE_NAME}")
    suspend fun getAll(): List<SetlistEntity>

    @Query("DELETE FROM ${SetlistEntity.TABLE_NAME}")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(setlists: List<SetlistEntity>)

    @Transaction
    suspend fun updateAll(setlists: List<SetlistEntity>) {
        deleteAll()
        insertAll(setlists)
    }
}