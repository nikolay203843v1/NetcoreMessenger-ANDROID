package com.netcoremessenger.core.data.local.converter

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate

class Converters {

    @TypeConverter
    fun fromInstant(instant: Instant?): Long? {
        return instant?.toEpochMilli()
    }

    @TypeConverter
    fun toInstant(millis: Long?): Instant? {
        return millis?.let { Instant.ofEpochMilli(it) }
    }

    @TypeConverter
    fun fromLocalDate(date: LocalDate?): String? {
        return date?.toString()
    }

    @TypeConverter
    fun toLocalDate(dateString: String?): LocalDate? {
        return dateString?.let { LocalDate.parse(it) }
    }
}
