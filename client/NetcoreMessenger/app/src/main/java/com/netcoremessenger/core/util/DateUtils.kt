package com.netcoremessenger.core.util

import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

object DateUtils {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val fullFormatter = DateTimeFormatter.ofPattern("dd MMMM yyyy, HH:mm")

    fun formatTime(instant: Instant): String {
        return instant.atZone(ZoneId.systemDefault()).format(timeFormatter)
    }

    fun formatDate(instant: Instant): String {
        val date = instant.atZone(ZoneId.systemDefault()).toLocalDate()
        val today = LocalDate.now()
        return when {
            date == today -> "Today"
            date == today.minusDays(1) -> "Yesterday"
            date.year == today.year -> date.format(DateTimeFormatter.ofPattern("d MMMM"))
            else -> date.format(dateFormatter)
        }
    }

    fun formatFull(instant: Instant): String {
        return instant.atZone(ZoneId.systemDefault()).format(fullFormatter)
    }

    fun formatDuration(durationMs: Long): String {
        val seconds = (durationMs / 1000) % 60
        val minutes = (durationMs / (1000 * 60)) % 60
        val hours = (durationMs / (1000 * 60 * 60))
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }

    fun formatCountdown(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format("%d:%02d", mins, secs)
    }
}
