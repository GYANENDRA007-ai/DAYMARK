# KAAM v26 device acceptance checklist

**Status: not executed — requires a successful Android build and physical device/emulator.**

## Install and navigation
- [ ] Build and install `app/build/outputs/apk/debug/app-debug.apk`.
- [ ] Launch app; navigate Home, Tasks, Timers, Alarms, Alerts, Sounds, Settings, Reports, and back.
- [ ] Create, edit, complete, reopen, and delete an item; verify confirmation and history.

## Recurrence and window
- [ ] All-day interval with no window retains previous behavior.
- [ ] Before a 12 PM–3 PM window, next regular occurrence is 12 PM.
- [ ] Within the window, interval slots remain anchored to its start.
- [ ] A slot exactly at the end is allowed; after end, next occurrence is next day's start.
- [ ] Partial, malformed, equal, reversed, and overnight windows are rejected.
- [ ] Change schedule and verify occurrence count resets; edit title/details and verify count remains.
- [ ] Finite limits stop regular occurrences; unlimited keeps scheduling.
- [ ] Snoozing the final allowed regular occurrence still delivers the snooze without consuming a new occurrence.

## Notification and persistence
- [ ] Notification permission denied then restored; deferred reminders retry correctly.
- [ ] Exact-alarm permission unavailable then restored; deferred IDs clear and reminders schedule.
- [ ] Done action completes item and cancels later regular occurrences.
- [ ] Snooze 10m survives app process death and reboot.
- [ ] Regular schedules restore after reboot/package replacement.
- [ ] Deleted/completed items do not deliver stale notifications.
- [ ] Each kind uses its own configured sound/vibration and sound previews stop cleanly.
- [ ] Notification tap opens the matching item; already-open MainActivity handles the tap.

## UI and data safety
- [ ] Small-screen layout, keyboard resize, AM/PM selector, and long task names remain usable.
- [ ] Active / Completed / History and report counts match a small manually known task set.
- [ ] Corrupt primary task JSON attempts backup recovery without discarding recoverable tasks.
- [ ] Confirm app label remains `Reminders`; Home wordmark remains `YOUR DAY`.
