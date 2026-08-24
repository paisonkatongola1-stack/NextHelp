package com.example.nexthelp.core.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object TimeFormat {

    fun formatDate(timestamp: Long): String =
        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(timestamp))

    fun formatDateTime(timestamp: Long): String =
        SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(timestamp))

    fun relativeTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        val diff = now - timestamp
        return when {
            diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
            diff < TimeUnit.HOURS.toMillis(1) -> {
                val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
                "${minutes}m ago"
            }
            diff < TimeUnit.DAYS.toMillis(1) -> {
                val hours = TimeUnit.MILLISECONDS.toHours(diff)
                "${hours}h ago"
            }
            diff < TimeUnit.DAYS.toMillis(7) -> {
                val days = TimeUnit.MILLISECONDS.toDays(diff)
                if (days == 1L) "Yesterday" else "${days}d ago"
            }
            else -> formatDate(timestamp)
        }
    }

    private fun startOfDay(now: Long): Calendar =
        Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

    fun dayGroupLabel(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        val todayStart = startOfDay(now).timeInMillis
        val yesterdayStart = todayStart - TimeUnit.DAYS.toMillis(1)
        val earlierWeekStart = todayStart - TimeUnit.DAYS.toMillis(7)

        return when {
            timestamp >= todayStart -> "Today"
            timestamp >= yesterdayStart -> "Yesterday"
            timestamp >= earlierWeekStart -> "This week"
            else -> "Earlier"
        }
    }

    fun greeting(now: Long = System.currentTimeMillis()): String {
        val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
    }
}
