package com.example.reminderapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationCopyTest {
    @Test
    fun selectedCopyAlwaysComesFromTheProvidedPool() {
        val lines = listOf("Chalo boss", "Ab kaam pe", "Let's go")
        repeat(100) {
            assertTrue(chooseNotificationCopy(lines, null) in lines)
        }
    }

    @Test
    fun previousCopyIsAvoidedWhenAnotherLineExists() {
        val lines = listOf("Chalo boss", "Ab kaam pe", "Let's go")
        repeat(100) {
            assertFalse(chooseNotificationCopy(lines, "Chalo boss") == "Chalo boss")
        }
    }

    @Test
    fun singleLinePoolFallsBackWithoutCrashing() {
        assertEquals("Bas shuru karo", chooseNotificationCopy(listOf("Bas shuru karo"), "Bas shuru karo"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyPoolIsRejectedClearly() {
        chooseNotificationCopy(emptyList(), null)
    }
}
