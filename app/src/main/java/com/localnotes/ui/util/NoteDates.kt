package com.localnotes.ui.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object NoteDates {
    private val time: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    private val weekday: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())
    private val monthDay: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    private val monthDayYear: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
    private val editor: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy 'at' H:mm", Locale.getDefault())

    fun listLabel(epochMillis: Long, now: LocalDate = LocalDate.now()): String {
        val dateTime = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
        val date = dateTime.toLocalDate()
        return when {
            date == now -> dateTime.format(time)
            date == now.minusDays(1) -> "Yesterday"
            date.isAfter(now.minusDays(6)) -> date.format(weekday)
            date.year == now.year -> date.format(monthDay)
            else -> date.format(monthDayYear)
        }
    }

    fun editorLabel(epochMillis: Long): String {
        return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(editor)
    }
}
