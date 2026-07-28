package com.wishlist.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CategorySortPrefDao {
    @Query("SELECT * FROM category_sort_prefs")
    fun observeAll(): Flow<List<CategorySortPref>>

    @Query("SELECT * FROM category_sort_prefs WHERE categoryKey = :categoryKey")
    suspend fun get(categoryKey: String): CategorySortPref?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(pref: CategorySortPref)
}
