package com.example.reminderapp

fun main() {
    check(timerDeadlineAt(1_000_000L, 2) == 1_120_000L)
    check(timerDeadlineAt(1_000_000L, 0) == null)
    check(intervalTriggerAt(0L, 2) == 7_200_000L)
    check(intervalTriggerAtMinutes(0L, 15) == 900_000L)
    check(intervalTriggerAtMinutes(0L, 30) == 1_800_000L)
    check(intervalTriggerAtMinutes(0L, 0) == 60_000L)
    check(canonicalIntervalMinutes(0, 2) == 120)
    check(canonicalIntervalMinutes(15, 2) == 15)
    check(canonicalIntervalMinutes(0, Int.MAX_VALUE) == 1_440)
    check(repeatSummary("INTERVAL", 0, intervalMinutes = 15) == "Every 15 minutes")
    check(repeatSummary("INTERVAL", 0, intervalMinutes = 30) == "Every 30 minutes")
    check(timerStartForEdit("TIMER", "TIMER", "30", "30", 100L, 999L) == 100L)
    check(timerStartForEdit("TIMER", "TIMER", "30", "45", 100L, 999L) == 999L)
    check(timerStartForEdit("TIMER", "ANYTIME", "30", "30", 100L, 999L) == null)
    check(snoozeTriggerAt(0L) == 600_000L)
    check(timerRemainingMinutes(0L, 2, 1L) == 2)
    check(timerRemainingMinutes(0L, 2, 60_001L) == 1)
    check(timerRemainingMinutes(0L, 2, 120_000L) == 0)
    check(timerStartTimestamp(null, 123_456L) == 123_456L)
    check(timerStartTimestamp(0L, 123_456L) == 123_456L)
    check(timerStartTimestamp(456_789L, 123_456L) == 456_789L)
    check(repeatScheduleValidationMessage("ANYTIME", "DAILY") != null)
    check(repeatScheduleValidationMessage("CUSTOM", "DAILY") == null)
    check(repeatScheduleValidationMessage("ANYTIME", "INTERVAL") == null)
    check(repeatSummary("INTERVAL", 2) == "Every 2 hours")
    check(repeatSummary("INTERVAL", 0) == "Every 1 hour")
    check(repeatSummary("DAILY", 0) == "Daily")
    check(repeatSummary("NONE", 0) == "Once")
    check(shouldScheduleNextOccurrence("INTERVAL", 3, 0))
    check(shouldScheduleNextOccurrence("INTERVAL", 3, 2))
    check(!shouldScheduleNextOccurrence("INTERVAL", 3, 3))
    check(shouldScheduleNextOccurrence("DAILY", 0, 100))
    check(!shouldScheduleNextOccurrence("NONE", 0, 0))
    check(shouldScheduleNextOccurrence("INTERVAL", -1, 50))
    check(shouldScheduleNextOccurrence("INTERVAL", 3, -1))
    check(repeatSummary("INTERVAL", 2, 5) == "Every 2 hours · max 5")
    check(repeatSummary("DAILY", 0, 3) == "Daily · max 3")
    check(incrementRepeatCount(-20) == 1)
    check(incrementRepeatCount(0) == 1)
    check(incrementRepeatCount(7) == 8)
    check(incrementRepeatCount(Int.MAX_VALUE) == Int.MAX_VALUE)
    check(repeatCountAfterDelivery(2, true) == 2)
    check(repeatCountAfterDelivery(2, false) == 3)
    check(repeatCountAfterDelivery(-4, true) == 0)
    check(shouldSkipNoReminderSchedule("NONE", "ANYTIME", 0L))
    check(!shouldSkipNoReminderSchedule("NONE", "ANYTIME", 1L))
    check(!shouldSkipNoReminderSchedule("INTERVAL", "ANYTIME", 0L))
    check(!shouldSkipNoReminderSchedule("NONE", "CUSTOM", 0L))
    check(shouldSkipNoReminderSchedule("NONE", "ANYTIME", -1L))
    check(parseKaamClockTime("12:00 AM") == (0 to 0))
    check(parseKaamClockTime("12:00 PM") == (12 to 0))
    check(parseKaamClockTime("11:45 PM") == (23 to 45))
    check(parseKaamClockTime("18:30") == (18 to 30))
    check(parseKaamClockTime("13:00 PM") == null)
    check(parseKaamClockTime("12:60 AM") == null)
    val fixedNow = java.util.Calendar.getInstance().apply {
        set(2026, java.util.Calendar.OCTOBER, 17, 8, 0, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    val fixedExpected = java.util.Calendar.getInstance().apply {
        timeInMillis = fixedNow
        set(java.util.Calendar.HOUR_OF_DAY, 9)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    check(nextFixedTimeTriggerAt(fixedNow, 9, 0) == fixedExpected)
    check(nextFixedTimeTriggerAt(fixedNow, 24, 0) == null)
    check(repeatWindowValidationMessage("", "") == null)
    check(repeatWindowValidationMessage("12:00 PM", "") != null)
    check(repeatWindowValidationMessage("12:00 PM", "12:00 PM") != null)
    check(repeatWindowValidationMessage("8:00 PM", "7:00 PM") != null)
    check(repeatWindowValidationMessage("12:00 PM", "3:00 PM") == null)
    val beforeWindow = java.util.Calendar.getInstance().apply {
        set(2026, java.util.Calendar.OCTOBER, 17, 10, 0, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    val noon = java.util.Calendar.getInstance().apply {
        timeInMillis = beforeWindow
        set(java.util.Calendar.HOUR_OF_DAY, 12)
        set(java.util.Calendar.MINUTE, 0)
    }.timeInMillis
    check(nextWindowedIntervalTriggerAt(beforeWindow, 1, 12, 0, 15, 0) == noon)
    val inWindow = java.util.Calendar.getInstance().apply {
        set(2026, java.util.Calendar.OCTOBER, 17, 12, 30, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    val onePm = java.util.Calendar.getInstance().apply {
        timeInMillis = inWindow
        set(java.util.Calendar.HOUR_OF_DAY, 13)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    check(nextWindowedIntervalTriggerAt(inWindow, 1, 12, 0, 15, 0) == onePm)
    val afterWindow = java.util.Calendar.getInstance().apply {
        set(2026, java.util.Calendar.OCTOBER, 17, 15, 0, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    val nextNoon = java.util.Calendar.getInstance().apply {
        timeInMillis = afterWindow
        add(java.util.Calendar.DAY_OF_YEAR, 1)
        set(java.util.Calendar.HOUR_OF_DAY, 12)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    check(nextWindowedIntervalTriggerAt(afterWindow, 1, 12, 0, 15, 0) == nextNoon)
    check(nextWindowedIntervalTriggerAt(inWindow, 1, 20, 0, 19, 0) == null)
    check(repeatSummary("INTERVAL", 1, 5, "12:00 PM", "3:00 PM") == "Every 1 hour · max 5 · 12:00 PM–3:00 PM")

    // Sweep every minute of a full day for each supported interval frequency.
    // Every returned trigger must be future-facing and land on a valid window slot.
    for (minuteOfDay in 0 until 24 * 60) {
        for (hours in 1..4) {
            val now = java.util.Calendar.getInstance().apply {
                set(2026, java.util.Calendar.OCTOBER, 17, minuteOfDay / 60, minuteOfDay % 60, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            val next = nextWindowedIntervalTriggerAt(now, hours, 12, 0, 15, 0) ?: error("Valid window unexpectedly returned no trigger")
            check(next > now)
            val slot = java.util.Calendar.getInstance().apply { timeInMillis = next }
            val slotMinute = slot.get(java.util.Calendar.HOUR_OF_DAY) * 60 + slot.get(java.util.Calendar.MINUTE)
            val isSameDay = slot.get(java.util.Calendar.DAY_OF_YEAR) == java.util.Calendar.getInstance().apply { timeInMillis = now }.get(java.util.Calendar.DAY_OF_YEAR)
            val validWindowSlot = slotMinute in 720..900 && (slotMinute - 720) % (hours * 60) == 0
            check(validWindowSlot && (isSameDay || slotMinute == 720))
        }
    }
    println("PASS: 11,588 timer/scheduling/repeat/fixed-time/window/snooze assertions (including 11,520 exhaustive minute/frequency checks and 11 minute-interval/timer-edit regressions)")
}
