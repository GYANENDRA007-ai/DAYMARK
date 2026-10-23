package com.example.reminderapp

/** Standalone JVM smoke checks for notification-copy pool invariants. */
fun main() {
    val pool = listOf("Chalo boss", "Ab kaam pe", "Let's go", "Ek step le lo")
    var checks = 0

    repeat(1_000) {
        val selected = chooseNotificationCopy(pool, null)
        check(selected in pool) { "Selected copy was outside the configured pool" }
        checks++
    }

    repeat(1_000) {
        val selected = chooseNotificationCopy(pool, "Chalo boss")
        check(selected in pool) { "Selected copy was outside the configured pool" }
        check(selected != "Chalo boss") { "Previous copy repeated while alternatives existed" }
        checks += 2
    }

    check(chooseNotificationCopy(listOf("Only line"), "Only line") == "Only line")
    checks++

    var rejectedEmptyPool = false
    try {
        chooseNotificationCopy(emptyList(), null)
    } catch (_: IllegalArgumentException) {
        rejectedEmptyPool = true
    }
    check(rejectedEmptyPool) { "Empty copy pool should fail clearly" }
    checks++

    println("PASS: $checks notification-copy invariant assertions")
}
