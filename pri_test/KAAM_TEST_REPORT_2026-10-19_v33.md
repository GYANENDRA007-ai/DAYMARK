# KAAM v33 — QA report (19 October 2026)

## Implemented in this checkpoint
- Added optional date-only deadlines to task records. Dates are stored at the end of the selected local calendar day, persisted in the existing JSON storage, and old records without a deadline continue to load.
- Added deadline selection, clearing, deadline display in task cards/details and mode dashboards, overdue indication for incomplete tasks, and overdue/upcoming-first ordering in the Active list. A deadline is a planning marker and does not silently change the reminder schedule.
- Added 15- and 30-minute recurrence intervals in addition to 1–4 hours. Canonical `repeatIntervalMinutes` is persisted, old hour-based records migrate safely, and same-day recurrence windows support minute cadence.
- Fixed timer editing: changing title/details while keeping the same timer duration preserves the original countdown start; changing duration restarts it; changing away from Timer clears timer state.
- Hardened the offline QA runner to set a sane default Kotlin compiler heap and terminate the entire compiler process tree on timeout.

## Offline verification
- Static structural checks: **142/142 passed**.
- Bundled sound assets: **4/4 valid**.
- Timing/scheduling/recurrence/window/snooze smoke assertions: **11,588 passed**, including 11,520 exhaustive minute/frequency checks.
- Notification-copy invariants: **3,002 passed**.
- Pure JVM unit tests executed through the temporary JUnit-compatible runner: **45 passed, 0 failed**.
- End-to-end offline QA runner: **passed**.
- ZIP integrity: to be checked after packaging.

## Explicit limitations
The Android/Compose Gradle build, APK generation, emulator launch, and real-device acceptance remain **unverified**. This environment has no configured Android SDK (`ANDROID_HOME`/`ANDROID_SDK_ROOT` are absent and no `android.jar` was found), and Gradle distribution DNS access has failed in prior attempts. These offline checks do not prove that the Android app compiles or that notification behavior works on a physical device. No APK or device-test success is claimed.
