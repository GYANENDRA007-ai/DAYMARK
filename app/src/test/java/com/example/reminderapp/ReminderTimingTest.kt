package com.example.reminderapp

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderTimingTest {
    @Test
    fun timerDeadlineUsesSavedStartTime() {
        assertEquals(1_120_000L, timerDeadlineAt(1_000_000L, 2))
    }

    @Test
    fun invalidTimerDurationDoesNotCreateDeadline() {
        assertNull(timerDeadlineAt(1_000_000L, 0))
        assertNull(timerDeadlineAt(1_000_000L, -3))
    }

    @Test
    fun overflowingOrNegativeTimerTimestampsAreRejected() {
        assertNull(timerDeadlineAt(Long.MAX_VALUE - 59_999L, 1))
        assertNull(timerDeadlineAt(-1L, 1))
        assertEquals(Long.MAX_VALUE, timerDeadlineAt(Long.MAX_VALUE - 60_000L, 1))
    }

    @Test
    fun intervalSchedulesFromCurrentOccurrence() {
        assertEquals(7_200_000L, intervalTriggerAt(0L, 2))
    }

    @Test
    fun intervalNeverSchedulesInThePast() {
        assertEquals(3_600_000L, intervalTriggerAt(0L, 0))
    }

    @Test
    fun legacyHourIntervalsMigrateSafelyToCanonicalMinutes() {
        assertEquals(120, canonicalIntervalMinutes(0, 2))
        assertEquals(15, canonicalIntervalMinutes(15, 2))
        assertEquals(1_440, canonicalIntervalMinutes(99_999, 2))
        assertEquals(1_440, canonicalIntervalMinutes(0, Int.MAX_VALUE))
    }

    @Test
    fun minuteIntervalsSupportQuarterHourAndHalfHourCadence() {
        assertEquals(900_000L, intervalTriggerAtMinutes(0L, 15))
        assertEquals(1_800_000L, intervalTriggerAtMinutes(0L, 30))
        assertEquals(60_000L, intervalTriggerAtMinutes(0L, 0))
        assertEquals(60_000L, intervalTriggerAtMinutes(0L, -4))
    }

    @Test
    fun minutePrecisionRecurrenceSummaryIsHumanReadable() {
        assertEquals("Every 15 minutes", repeatSummary("INTERVAL", 0, intervalMinutes = 15))
        assertEquals("Every 30 minutes", repeatSummary("INTERVAL", 0, intervalMinutes = 30))
        assertEquals("Every 2 hours", repeatSummary("INTERVAL", 2, intervalMinutes = 120))
        assertEquals("Every 1h 30m", repeatSummary("INTERVAL", 0, intervalMinutes = 90))
    }

    @Test
    fun defaultSnoozeIsTenMinutes() {
        assertEquals(600_000L, snoozeTriggerAt(0L))
    }

    @Test
    fun snoozeDurationIsClampedToAtLeastOneMinute() {
        assertEquals(60_000L, snoozeTriggerAt(0L, 0))
    }
    @Test
    fun dateOnlyDeadlineIsStoredAtEndOfLocalDay() {
        val dueAt = localDeadlineEndOfDay(2026, java.util.Calendar.OCTOBER, 19)!!
        val calendar = Calendar.getInstance().apply { timeInMillis = dueAt }
        assertEquals(2026, calendar.get(Calendar.YEAR))
        assertEquals(Calendar.OCTOBER, calendar.get(Calendar.MONTH))
        assertEquals(19, calendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(23, calendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, calendar.get(Calendar.MINUTE))
        assertEquals(59, calendar.get(Calendar.SECOND))
        assertEquals(999, calendar.get(Calendar.MILLISECOND))
    }

    @Test
    fun invalidCalendarDateIsRejectedAndOverdueRequiresIncompleteTask() {
        assertNull(localDeadlineEndOfDay(2026, Calendar.FEBRUARY, 31))
        assertEquals(true, isTaskOverdue(100L, false, 101L))
        assertEquals(false, isTaskOverdue(100L, false, 100L))
        assertEquals(false, isTaskOverdue(100L, true, 101L))
        assertEquals(false, isTaskOverdue(null, false, 101L))
    }

    @Test
    fun editingTimerTextPreservesOriginalCountdownStart() {
        assertEquals(100L, timerStartForEdit("TIMER", "TIMER", "30", "30", 100L, 999L))
    }

    @Test
    fun editingTimerDurationRestartsCountdown() {
        assertEquals(999L, timerStartForEdit("TIMER", "TIMER", "30", "45", 100L, 999L))
    }

    @Test
    fun switchingAwayFromTimerClearsCountdownStart() {
        assertNull(timerStartForEdit("TIMER", "ANYTIME", "30", "30", 100L, 999L))
    }

    @Test
    fun remainingTimerMinutesRoundsPartialMinuteUp() {
        assertEquals(2, timerRemainingMinutes(0L, 2, 1L))
        assertEquals(1, timerRemainingMinutes(0L, 2, 60_001L))
    }

    @Test
    fun remainingTimerMinutesIsZeroAtOrAfterDeadline() {
        assertEquals(0, timerRemainingMinutes(0L, 2, 120_000L))
        assertEquals(0, timerRemainingMinutes(0L, 2, 180_000L))
    }

    @Test
    fun remainingTimerMinutesRoundsUpWithoutLongOverflow() {
        assertEquals(Int.MAX_VALUE, timerRemainingMinutes(Long.MAX_VALUE - 60_000L, 1, 0L))
    }

    @Test
    fun remainingTimerMinutesRejectsInvalidDuration() {
        assertNull(timerRemainingMinutes(0L, 0, 0L))
    }

    @Test
    fun legacyTimerWithoutSavedStartUsesCreationTimestamp() {
        assertEquals(123_456L, timerStartTimestamp(null, 123_456L))
    }

    @Test
    fun invalidSavedTimerStartFallsBackToCreationTimestamp() {
        assertEquals(123_456L, timerStartTimestamp(0L, 123_456L))
    }

    @Test
    fun validSavedTimerStartTakesPrecedenceOverCreationTimestamp() {
        assertEquals(456_789L, timerStartTimestamp(456_789L, 123_456L))
    }

    @Test
    fun dailyRepeatRequiresClockBasedSchedule() {
        assertEquals(
            "Choose a time for daily reminders, or use an interval repeat.",
            repeatScheduleValidationMessage("ANYTIME", "DAILY")
        )
        assertNull(repeatScheduleValidationMessage("CUSTOM", "DAILY"))
    }

    @Test
    fun intervalRepeatCanRemainFlexible() {
        assertNull(repeatScheduleValidationMessage("ANYTIME", "INTERVAL"))
        assertNull(repeatScheduleValidationMessage("ANYTIME", "NONE"))
    }

    @Test
    fun repeatSummaryExplainsIntervalDailyAndOneTimeSchedules() {
        assertEquals("Every 2 hours", repeatSummary("INTERVAL", 2))
        assertEquals("Every 1 hour", repeatSummary("INTERVAL", 0))
        assertEquals("Daily", repeatSummary("DAILY", 0))
        assertEquals("Once", repeatSummary("NONE", 0))
    }

    @Test
    fun parsesAmPmBoundariesAndTwentyFourHourTimes() {
        assertEquals(0 to 0, parseKaamClockTime("12:00 AM"))
        assertEquals(11 to 59, parseKaamClockTime("11:59 am"))
        assertEquals(12 to 0, parseKaamClockTime("12:00 PM"))
        assertEquals(23 to 45, parseKaamClockTime("11:45 PM"))
        assertEquals(18 to 30, parseKaamClockTime("18:30"))
    }

    @Test
    fun rejectsMalformedOrOutOfRangeClockTimes() {
        assertNull(parseKaamClockTime("13:00 PM"))
        assertNull(parseKaamClockTime("12:60 AM"))
        assertNull(parseKaamClockTime("noon"))
        assertNull(parseKaamClockTime(""))
        assertNull(parseKaamClockTime("12:00AMPM"))
        assertNull(parseKaamClockTime("1 2:00 PM"))
        assertNull(parseKaamClockTime("12:00 XM"))
        assertNull(parseKaamClockTime("12:000 PM"))
        assertEquals(9 to 5, parseKaamClockTime("9:05 AM"))
        assertEquals(21 to 5, parseKaamClockTime("9:05PM"))
    }

    @Test
    fun fixedTimeUsesTodayWhenStillAhead() {
        val now = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.OCTOBER, 17, 8, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val expected = java.util.Calendar.getInstance().apply {
            timeInMillis = now
            set(java.util.Calendar.HOUR_OF_DAY, 9)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        assertEquals(expected, nextFixedTimeTriggerAt(now, 9, 0))
    }

    @Test
    fun fixedTimeMovesToTomorrowWhenTimeHasPassedOrIsExactlyNow() {
        val now = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.OCTOBER, 17, 9, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val expected = java.util.Calendar.getInstance().apply {
            timeInMillis = now
            add(java.util.Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        assertEquals(expected, nextFixedTimeTriggerAt(now, 9, 0))
        assertEquals(expected, nextFixedTimeTriggerAt(now + 60_000L, 9, 0))
    }

    @Test
    fun finiteRecurrenceStopsAtLimitAndUnlimitedKeepsGoing() {
        assertEquals(true, shouldScheduleNextOccurrence("INTERVAL", 3, 0))
        assertEquals(true, shouldScheduleNextOccurrence("INTERVAL", 3, 2))
        assertEquals(false, shouldScheduleNextOccurrence("INTERVAL", 3, 3))
        assertEquals(true, shouldScheduleNextOccurrence("DAILY", 0, 100))
        assertEquals(false, shouldScheduleNextOccurrence("NONE", 0, 0))
        assertEquals(true, shouldScheduleNextOccurrence("INTERVAL", -1, 50))
        assertEquals(true, shouldScheduleNextOccurrence("INTERVAL", 3, -1))
    }

    @Test
    fun recurrenceSummaryShowsFiniteLimitWithoutOverloadingIntervalHours() {
        assertEquals("Every 2 hours · max 5", repeatSummary("INTERVAL", 2, 5))
        assertEquals("Daily · max 3", repeatSummary("DAILY", 0, 3))
        assertEquals("Every 2 hours", repeatSummary("INTERVAL", 2, 0))
    }

    @Test
    fun recurrenceCountIncrementClampsCorruptNegativeValues() {
        assertEquals(1, incrementRepeatCount(-20))
        assertEquals(1, incrementRepeatCount(0))
        assertEquals(8, incrementRepeatCount(7))
    }

    @Test
    fun recurrenceCountIncrementDoesNotOverflowAtIntMaximum() {
        assertEquals(Int.MAX_VALUE, incrementRepeatCount(Int.MAX_VALUE))
    }

    @Test
    fun snoozedDeliveryDoesNotConsumeARegularOccurrence() {
        assertEquals(2, repeatCountAfterDelivery(2, true))
        assertEquals(3, repeatCountAfterDelivery(2, false))
        assertEquals(0, repeatCountAfterDelivery(-4, true))
    }

    @Test
    fun savedSnoozeSurvivesWhenTaskHasNoRegularReminderSchedule() {
        assertEquals(true, shouldSkipNoReminderSchedule("NONE", "ANYTIME", 0L))
        assertEquals(false, shouldSkipNoReminderSchedule("NONE", "ANYTIME", 1_800_000L))
        assertEquals(false, shouldSkipNoReminderSchedule("INTERVAL", "ANYTIME", 0L))
        assertEquals(false, shouldSkipNoReminderSchedule("NONE", "CUSTOM", 0L))
        assertEquals(true, shouldSkipNoReminderSchedule("NONE", "ANYTIME", -1L))
    }

    @Test
    fun fixedTimeRejectsInvalidClockComponents() {
        assertNull(nextFixedTimeTriggerAt(0L, 24, 0))
        assertNull(nextFixedTimeTriggerAt(0L, 12, 60))
    }

    @Test
    fun optionalRepeatWindowRequiresBothValidSameDayTimes() {
        assertNull(repeatWindowValidationMessage("", ""))
        assertEquals("Choose both a window start and end time.", repeatWindowValidationMessage("12:00 PM", ""))
        assertEquals("The window start and end must be different.", repeatWindowValidationMessage("12:00 PM", "12:00 PM"))
        assertEquals("The end time must be later than the start time on the same day.", repeatWindowValidationMessage("8:00 PM", "7:00 PM"))
        assertNull(repeatWindowValidationMessage("12:00 PM", "3:00 PM"))
    }

    @Test
    fun windowedMinuteIntervalUsesMinuteCadenceAnchoredToWindowStart() {
        val now = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.OCTOBER, 17, 12, 7, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val expected = java.util.Calendar.getInstance().apply {
            timeInMillis = now
            set(java.util.Calendar.MINUTE, 15)
            set(java.util.Calendar.SECOND, 0)
        }.timeInMillis
        assertEquals(expected, nextWindowedIntervalTriggerAtMinutes(now, 15, 12, 0, 13, 0))
    }

    @Test
    fun windowedIntervalStartsAtWindowStartWhenStillBeforeIt() {
        val now = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.OCTOBER, 17, 10, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val expected = java.util.Calendar.getInstance().apply {
            timeInMillis = now
            set(java.util.Calendar.HOUR_OF_DAY, 12)
            set(java.util.Calendar.MINUTE, 0)
        }.timeInMillis
        assertEquals(expected, nextWindowedIntervalTriggerAt(now, 1, 12, 0, 15, 0))
    }

    @Test
    fun windowedIntervalUsesSlotsAnchoredToWindowStart() {
        val now = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.OCTOBER, 17, 12, 30, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val expected = java.util.Calendar.getInstance().apply {
            timeInMillis = now
            set(java.util.Calendar.HOUR_OF_DAY, 13)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        assertEquals(expected, nextWindowedIntervalTriggerAt(now, 1, 12, 0, 15, 0))
    }

    @Test
    fun windowedIntervalResumesAtNextDaysStartAfterWindowEnds() {
        val now = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.OCTOBER, 17, 15, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val expected = java.util.Calendar.getInstance().apply {
            timeInMillis = now
            add(java.util.Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        assertEquals(expected, nextWindowedIntervalTriggerAt(now, 1, 12, 0, 15, 0))
    }

    @Test
    fun windowedIntervalAllowsEndSlotAndRejectsInvalidWindows() {
        val now = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.OCTOBER, 17, 14, 30, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val expected = java.util.Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 15)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        assertEquals(expected, nextWindowedIntervalTriggerAt(now, 1, 12, 0, 15, 0))
        assertNull(nextWindowedIntervalTriggerAt(now, 1, 20, 0, 19, 0))
        assertEquals("Every 1 hour · max 5 · 12:00 PM–3:00 PM", repeatSummary("INTERVAL", 1, 5, "12:00 PM", "3:00 PM"))
    }

}
