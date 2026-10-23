package com.example.reminderapp

import java.util.Calendar

/** Pure time arithmetic kept separate so deadline behavior can be JVM-tested. */
internal fun timerDeadlineAt(startedAtMillis: Long, durationMinutes: Int): Long? {
    if (durationMinutes <= 0 || startedAtMillis < 0L) return null
    val durationMillis = durationMinutes.toLong() * 60_000L
    // Imported/corrupt timestamps can be close to Long.MAX_VALUE. Reject an
    // overflowing deadline instead of wrapping it into the distant past.
    if (startedAtMillis > Long.MAX_VALUE - durationMillis) return null
    return startedAtMillis + durationMillis
}

internal fun intervalTriggerAt(nowMillis: Long, intervalHours: Int): Long =
    intervalTriggerAtMinutes(nowMillis, intervalHours.coerceAtLeast(1) * 60)

/** Canonical minute interval with safe migration from legacy hour-based records. */
internal fun canonicalIntervalMinutes(savedMinutes: Int, legacyHours: Int): Int =
    savedMinutes.takeIf { it > 0 }?.coerceIn(1, 1_440)
        ?: legacyHours.coerceIn(1, 24) * 60

/** Minute-precision recurrence; malformed/non-positive values clamp to one minute. */
internal fun intervalTriggerAtMinutes(nowMillis: Long, intervalMinutes: Int): Long {
    val safeMinutes = intervalMinutes.coerceAtLeast(1)
    return nowMillis + safeMinutes.toLong() * 60_000L
}

internal fun snoozeTriggerAt(nowMillis: Long, snoozeMinutes: Int = 10): Long {
    val safeMinutes = snoozeMinutes.coerceAtLeast(1)
    return nowMillis + safeMinutes.toLong() * 60_000L
}

/**
 * Uses a persisted timer start when present, and falls back to the item's creation
 * timestamp for older saved timers. Scheduling and UI must share this fallback so
 * opening an older timer cannot display one deadline while scheduling another.
 */
internal fun timerStartTimestamp(savedStartAtMillis: Long?, createdAtMillis: Long): Long =
    savedStartAtMillis?.takeIf { it > 0L } ?: createdAtMillis

/** Store a date-only deadline at the end of that local calendar day. */
internal fun localDeadlineEndOfDay(year: Int, monthZeroBased: Int, dayOfMonth: Int): Long? {
    if (monthZeroBased !in 0..11 || dayOfMonth !in 1..31) return null
    val calendar = Calendar.getInstance().apply {
        clear()
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, monthZeroBased)
        set(Calendar.DAY_OF_MONTH, dayOfMonth)
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }
    return calendar.timeInMillis.takeIf {
        val check = Calendar.getInstance().apply { timeInMillis = it }
        check.get(Calendar.YEAR) == year && check.get(Calendar.MONTH) == monthZeroBased && check.get(Calendar.DAY_OF_MONTH) == dayOfMonth
    }
}

/** A deadline is overdue only for a valid, incomplete item after its saved due instant. */
internal fun isTaskOverdue(dueAtMillis: Long?, completed: Boolean, nowMillis: Long): Boolean =
    dueAtMillis != null && dueAtMillis > 0L && !completed && nowMillis > dueAtMillis

/** Preserve a running countdown when editing only its text; restart only when its duration changes. */
internal fun timerStartForEdit(
    oldWhenType: String?, newWhenType: String, oldDuration: String?, newDuration: String,
    existingStartedAt: Long?, nowMillis: Long
): Long? = when {
    newWhenType != "TIMER" -> null
    oldWhenType == "TIMER" && oldDuration == newDuration && existingStartedAt != null && existingStartedAt > 0L -> existingStartedAt
    else -> nowMillis
}

/** Remaining whole minutes, rounded up so a partial minute is never shown as finished. */
internal fun timerRemainingMinutes(startedAtMillis: Long, durationMinutes: Int, nowMillis: Long): Int? {
    val deadline = timerDeadlineAt(startedAtMillis, durationMinutes) ?: return null
    val remainingMillis = deadline - nowMillis
    if (remainingMillis <= 0L) return 0
    // Quotient + remainder rounds up without overflowing near Long.MAX_VALUE.
    val wholeMinutes = remainingMillis / 60_000L
    val roundedMinutes = wholeMinutes + if (remainingMillis % 60_000L == 0L) 0L else 1L
    return roundedMinutes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}


/** Daily repetition needs a clock-based schedule; ANYTIME has no clock trigger. */
internal fun repeatScheduleValidationMessage(whenType: String, repeatType: String): String? =
    if (repeatType == "DAILY" && whenType == "ANYTIME") {
        "Choose a time for daily reminders, or use an interval repeat."
    } else {
        null
    }




/** Validate an optional same-day clock window for interval reminders. */
internal fun repeatWindowValidationMessage(start: String, end: String): String? {
    if (start.isBlank() && end.isBlank()) return null
    if (start.isBlank() || end.isBlank()) return "Choose both a window start and end time."
    val startTime = parseKaamClockTime(start) ?: return "Choose a valid window start time."
    val endTime = parseKaamClockTime(end) ?: return "Choose a valid window end time."
    val startMinutes = startTime.first * 60 + startTime.second
    val endMinutes = endTime.first * 60 + endTime.second
    return when {
        startMinutes == endMinutes -> "The window start and end must be different."
        endMinutes < startMinutes -> "The end time must be later than the start time on the same day."
        else -> null
    }
}

/**
 * Find the next interval slot inside a daily same-day window. Before the window,
 * the first slot is its start; inside it, slots are anchored to the start; after
 * it, scheduling resumes at the next day's start. A null result means invalid input.
 */
internal fun nextWindowedIntervalTriggerAt(
    nowMillis: Long,
    intervalHours: Int,
    startHour: Int,
    startMinute: Int,
    endHour: Int,
    endMinute: Int
): Long? {
    if (startHour !in 0..23 || endHour !in 0..23 || startMinute !in 0..59 || endMinute !in 0..59) return null
    val startMinutes = startHour * 60 + startMinute
    val endMinutes = endHour * 60 + endMinute
    if (endMinutes <= startMinutes) return null

    return nextWindowedIntervalTriggerAtMinutes(
        nowMillis, intervalHours.coerceAtLeast(1) * 60,
        startHour, startMinute, endHour, endMinute
    )
}

/** Same-day window scheduler with minute-precision recurrence. */
internal fun nextWindowedIntervalTriggerAtMinutes(
    nowMillis: Long,
    intervalMinutes: Int,
    startHour: Int,
    startMinute: Int,
    endHour: Int,
    endMinute: Int
): Long? {
    if (startHour !in 0..23 || endHour !in 0..23 || startMinute !in 0..59 || endMinute !in 0..59) return null
    val startMinutes = startHour * 60 + startMinute
    val endMinutes = endHour * 60 + endMinute
    if (endMinutes <= startMinutes) return null

    val intervalMillis = intervalMinutes.coerceAtLeast(1).toLong() * 60_000L
    val start = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, startHour)
        set(Calendar.MINUTE, startMinute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val end = Calendar.getInstance().apply {
        timeInMillis = start.timeInMillis
        set(Calendar.HOUR_OF_DAY, endHour)
        set(Calendar.MINUTE, endMinute)
    }
    if (nowMillis < start.timeInMillis) return start.timeInMillis
    if (nowMillis >= end.timeInMillis) {
        start.add(Calendar.DAY_OF_YEAR, 1)
        return start.timeInMillis
    }

    val elapsed = nowMillis - start.timeInMillis
    val nextSlotIndex = elapsed / intervalMillis + 1L
    val candidate = start.timeInMillis + nextSlotIndex * intervalMillis
    return if (candidate <= end.timeInMillis) candidate else {
        start.add(Calendar.DAY_OF_YEAR, 1)
        start.timeInMillis
    }
}


/** Human-readable recurrence summary used consistently across mode dashboards. */
internal fun shouldScheduleNextOccurrence(repeatType: String, repeatLimit: Int, deliveredCount: Int): Boolean {
    if (repeatType == "NONE") return false
    val safeLimit = repeatLimit.coerceAtLeast(0)
    val safeCount = deliveredCount.coerceAtLeast(0)
    return safeLimit == 0 || safeCount < safeLimit
}

/** Increment a persisted regular-delivery count without allowing corrupt negative values or Int overflow. */
internal fun incrementRepeatCount(currentCount: Int): Int {
    val safeCount = currentCount.coerceAtLeast(0)
    return if (safeCount == Int.MAX_VALUE) Int.MAX_VALUE else safeCount + 1
}

/** Snoozes are follow-ups, so only regular deliveries advance the recurrence counter. */
internal fun repeatCountAfterDelivery(currentCount: Int, wasSnoozedDelivery: Boolean): Int {
    val safeCount = currentCount.coerceAtLeast(0)
    return if (wasSnoozedDelivery) safeCount else incrementRepeatCount(safeCount)
}

/**
 * An ANYTIME item with no repeat has no regular alarm, but a saved snooze is a
 * deliberate one-off deadline and must survive edit/reboot rescheduling.
 */
internal fun shouldSkipNoReminderSchedule(
    repeatType: String,
    whenType: String,
    savedSnoozeUntilMillis: Long
): Boolean = repeatType == "NONE" && whenType == "ANYTIME" && savedSnoozeUntilMillis <= 0L

/** A zero limit means unlimited; finite limits count regular deliveries, not snoozes. */
internal fun repeatSummary(
    repeatType: String, repeatValue: Int, repeatLimit: Int = 0,
    windowStart: String = "", windowEnd: String = "",
    intervalMinutes: Int = 0
): String {
    val base = when (repeatType) {
        "INTERVAL" -> {
            val minutes = canonicalIntervalMinutes(intervalMinutes, repeatValue)
            when {
                minutes < 60 -> "Every $minutes minute${if (minutes == 1) "" else "s"}"
                minutes % 60 == 0 -> {
                    val hours = minutes / 60
                    "Every $hours hour${if (hours == 1) "" else "s"}"
                }
                else -> "Every ${minutes / 60}h ${minutes % 60}m"
            }
        }
        "DAILY" -> "Daily"
        else -> "Once"
    }
    val window = if (repeatType == "INTERVAL" && windowStart.isNotBlank() && windowEnd.isNotBlank()) {
        " · $windowStart–$windowEnd"
    } else ""
    val limited = if (repeatType != "NONE" && repeatLimit > 0) "$base · max $repeatLimit" else base
    return limited + window
}


/** Parse a user-entered 12-hour or 24-hour clock time into 24-hour hour/minute. */
internal fun parseKaamClockTime(value: String): Pair<Int, Int>? {
    // Accept only a clock value with an optional, single AM/PM suffix. Do not
    // silently normalize malformed inputs such as "12:00AMPM" into a valid time.
    val cleaned = value.trim().uppercase()
    if (!Regex("^\\d{1,2}:\\d{2}(?:\\s?(?:AM|PM))?$").matches(cleaned)) return null

    val isPm = cleaned.endsWith("PM")
    val isAm = cleaned.endsWith("AM")
    val timeOnly = cleaned.removeSuffix("AM").removeSuffix("PM").trim()
    val parts = timeOnly.split(":")
    if (parts.size != 2) return null

    val inputHour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (minute !in 0..59) return null

    val hour = when {
        isAm && inputHour in 1..12 -> if (inputHour == 12) 0 else inputHour
        isPm && inputHour in 1..12 -> if (inputHour == 12) 12 else inputHour + 12
        !isAm && !isPm && inputHour in 0..23 -> inputHour
        else -> return null
    }
    return hour to minute
}

/** Return the next local occurrence of a clock time; a time equal to now rolls to tomorrow. */
internal fun nextFixedTimeTriggerAt(nowMillis: Long, hourOfDay: Int, minute: Int): Long? {
    if (hourOfDay !in 0..23 || minute !in 0..59) return null
    val calendar = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, hourOfDay)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    if (calendar.timeInMillis <= nowMillis) calendar.add(Calendar.DAY_OF_YEAR, 1)
    return calendar.timeInMillis
}
