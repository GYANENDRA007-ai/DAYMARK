package com.example.reminderapp

/** Select a short notification line without repeating the last line when alternatives exist. */
internal fun chooseNotificationCopy(lines: List<String>, previousCopy: String?): String {
    require(lines.isNotEmpty()) { "Notification copy pool must not be empty" }
    val alternatives = lines.filter { it != previousCopy }
    return (alternatives.ifEmpty { lines }).random()
}
