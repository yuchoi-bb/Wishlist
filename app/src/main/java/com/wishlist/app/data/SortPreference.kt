package com.wishlist.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * The single app-wide table sort, persisted so it survives a restart. One row, always id 0 —
 * sorting is no longer chosen per category.
 */
@Entity(tableName = "sort_preference")
data class SortPreference(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val sortField: SortField = SortField.END_DATE,
    val ascending: Boolean = true,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}

@Dao
interface SortPreferenceDao {
    @Query("SELECT * FROM sort_preference WHERE id = ${SortPreference.SINGLETON_ID}")
    fun observe(): Flow<SortPreference?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preference: SortPreference)
}
