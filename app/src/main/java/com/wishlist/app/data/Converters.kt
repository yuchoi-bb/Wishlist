package com.wishlist.app.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun sortFieldToString(value: SortField): String = value.name

    @TypeConverter
    fun stringToSortField(value: String): SortField =
        runCatching { SortField.valueOf(value) }.getOrDefault(SortField.END_DATE)
}
