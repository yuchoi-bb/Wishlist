package com.wishlist.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WishlistDao {
    @Query("SELECT * FROM wishlist_items ORDER BY id DESC")
    fun observeAll(): Flow<List<WishlistItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: WishlistItem): Long

    @Update
    suspend fun update(item: WishlistItem)

    @Delete
    suspend fun delete(item: WishlistItem)

    @Query("SELECT DISTINCT majorCategory FROM wishlist_items WHERE majorCategory IS NOT NULL AND majorCategory != '' ORDER BY majorCategory")
    fun observeMajorCategories(): Flow<List<String>>

    @Query("SELECT DISTINCT minorCategory FROM wishlist_items WHERE majorCategory = :major AND minorCategory IS NOT NULL AND minorCategory != '' ORDER BY minorCategory")
    fun observeMinorCategories(major: String): Flow<List<String>>
}
