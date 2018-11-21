package com.pandulapeter.campfire.data.persistence.access

import androidx.room.*
import com.pandulapeter.campfire.data.model.remote.Collection

@Dao
interface CollectionDao {

    @Query("SELECT * FROM ${Collection.TABLE_NAME}")
    fun getAll(): List<Collection>

    @Transaction
    fun updateAll(songs: List<Collection>) {
        deleteAll()
        insertAll(songs)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(collections: List<Collection>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(collection: Collection)

    @Query("DELETE FROM ${Collection.TABLE_NAME}")
    fun deleteAll()
}