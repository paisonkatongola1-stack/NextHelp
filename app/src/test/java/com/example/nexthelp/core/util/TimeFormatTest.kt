package com.example.nexthelp.core.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.concurrent.TimeUnit

class TimeFormatTest {

    private fun calendarAt(hour: Int, minute: Int = 0): Long =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test
    fun `relativeTime returns Just now under one minute`() {
        val now = System.currentTimeMillis()
        assertEquals("Just now", TimeFormat.relativeTime(now - 30_000, now))
        assertEquals("Just now", TimeFormat.relativeTime(now, now))
    }

    @Test
    fun `relativeTime formats minutes and hours`() {
        val now = System.currentTimeMillis()
        assertEquals("5m ago", TimeFormat.relativeTime(now - TimeUnit.MINUTES.toMillis(5), now))
        assertEquals("3h ago", TimeFormat.relativeTime(now - TimeUnit.HOURS.toMillis(3), now))
    }

    @Test
    fun `relativeTime handles yesterday boundary`() {
        val now = calendarAt(12)
        val twentyFiveHoursAgo = now - TimeUnit.HOURS.toMillis(25)
        assertEquals("Yesterday", TimeFormat.relativeTime(twentyFiveHoursAgo, now))
        assertEquals("23h ago", TimeFormat.relativeTime(now - TimeUnit.HOURS.toMillis(23), now))
    }

    @Test
    fun `relativeTime falls back to formatted date beyond a week`() {
        val now = calendarAt(12)
        val eightDaysAgo = now - TimeUnit.DAYS.toMillis(8)
        assertEquals(TimeFormat.formatDate(eightDaysAgo), TimeFormat.relativeTime(eightDaysAgo, now))
    }

    @Test
    fun `dayGroupLabel buckets correctly`() {
        val now = calendarAt(15)
        assertEquals("Today", TimeFormat.dayGroupLabel(calendarAt(9), now))
        assertEquals("Yesterday", TimeFormat.dayGroupLabel(now - TimeUnit.DAYS.toMillis(1), now))
        assertEquals(
            "This week",
            TimeFormat.dayGroupLabel(now - TimeUnit.DAYS.toMillis(4), now)
        )
        assertEquals(
            "Earlier",
            TimeFormat.dayGroupLabel(now - TimeUnit.DAYS.toMillis(10), now)
        )
    }

    @Test
    fun `greeting matches day parts`() {
        assertEquals("Good morning", TimeFormat.greeting(calendarAt(6)))
        assertEquals("Good morning", TimeFormat.greeting(calendarAt(11)))
        assertEquals("Good afternoon", TimeFormat.greeting(calendarAt(12)))
        assertEquals("Good afternoon", TimeFormat.greeting(calendarAt(17)))
        assertEquals("Good evening", TimeFormat.greeting(calendarAt(18)))
        assertEquals("Good evening", TimeFormat.greeting(calendarAt(4)))
    }
}
