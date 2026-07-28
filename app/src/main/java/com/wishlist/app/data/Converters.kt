package com.wishlist.app.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun sortFieldToString(value: SortField): String = value.name

    @TypeConverter
    fun stringToSortField(value: String): SortField = SortField.fromStoredName(value)
}
