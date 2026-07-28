package com.wishlist.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [SortPreference::class],
    version = 4,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class WishlistDatabase : RoomDatabase() {
    abstract fun sortPreferenceDao(): SortPreferenceDao

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
                    // Only holds UI preferences — items live in Firestore — so dropping it on a
                    // schema change costs nothing but the current sort choice.
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
