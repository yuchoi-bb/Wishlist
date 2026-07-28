package com.wishlist.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [CategorySortPref::class],
    version = 3,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class WishlistDatabase : RoomDatabase() {
    abstract fun categorySortPrefDao(): CategorySortPrefDao

    companion object {
        @Volatile
        private var instance: WishlistDatabase? = null

        fun getInstance(context: Context): WishlistDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WishlistDatabase::class.java,
                    "wishlist.db",
                )
                    // Items moved from this local table to Firestore; older installs can just drop it.
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
