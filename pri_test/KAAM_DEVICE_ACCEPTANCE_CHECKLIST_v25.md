# KAAM v25 — Android device acceptance checklist

Use this after the project builds and installs. These checks are **not yet run** in the current environment.

## Install and navigation
- [ ] Fresh install launches to Home without a crash; visible brand remains `YOUR DAY` / `Reminders`, not the internal codename.
- [ ] Open each destination: Tasks, Timers, Alarms, Alerts, Progress, Report, Plan, Sounds, Chat, Settings; return Home from each.
- [ ] On a small screen, scroll every page and open the keyboard in the intention/task editor; ensure fields and save controls remain reachable.

## Task storage and history
- [ ] Create a task, force-stop/relaunch, and confirm title/details/schedule persist.
- [ ] Edit only title/details while a snooze is pending; verify the snooze still arrives with the updated task title.
- [ ] Edit scheduling parameters and confirm the regular recurrence count resets while the intended pending snooze remains.
- [ ] Complete via the notification Done action; confirm it moves to Completed/History and no later regular notification fires.
- [ ] Delete a task with confirmation; confirm its reminder and persisted snooze are cancelled.
- [ ] With test data, remove/corrupt the primary task JSON key but leave a valid backup; verify the app restores the backup instead of showing an empty task list.

## Recurrence and snooze
- [ ] Create an interval reminder with limit 3; verify exactly three regular deliveries, then no fourth regular delivery.
- [ ] Repeat with limit 5 and 10; verify the first delivered reminder counts toward the limit.
- [ ] Snooze the final allowed regular delivery; verify the snooze still arrives and does not add another regular occurrence.
- [ ] After a snooze delivery, verify the regular recurrence continues only if the configured regular-delivery limit has not been reached.
- [ ] Create an unlimited interval reminder; verify it continues until Done/completion.
- [ ] Create a Daily reminder with a custom time; verify the next local clock occurrence is chosen and a time already passed rolls to tomorrow.
- [ ] Change the phone time zone or cross a daylight-saving transition where applicable; verify daily reminders still use local wall-clock time.

- [ ] Create an interval reminder with a 12:00 PM–3:00 PM window and every-1-hour repeat; confirm reminders occur only at 12, 1, 2, and 3 while the window is active.
- [ ] Create the same window after 3:00 PM; verify the next delivery is at noon the following day, not immediately.
- [ ] Set only one window endpoint, identical endpoints, and a reversed/overnight window; verify the editor rejects each invalid configuration.
- [ ] Edit the time window and confirm the occurrence count resets; edit title/details only and confirm the count remains.
- [ ] Verify a persisted snooze still delivers even if its snooze time falls outside the normal daily window.

## Timer and alarm permissions
- [ ] Create a duration timer, leave the app in background/screen off, and verify the deadline remains tied to the saved start timestamp.
- [ ] Verify expired timers show elapsed state rather than resetting on app resume.
- [ ] Deny notification permission; verify Settings offers the recovery path and the UI does not claim notifications are enabled.
- [ ] Revoke exact-alarm access, create a clock reminder, grant access again, and verify the deferred reminder is restored.
- [ ] Snooze without exact-alarm access; verify the inexact follow-up still arrives (Android may deliver slightly late).
- [ ] Reboot with an active reminder and with a persisted snooze; verify both schedules are restored appropriately.

## Sound and notification quality
- [ ] Preview each sound profile and verify the four bundled tones are audible and distinct on the target device.
- [ ] Change a per-kind sound profile and verify the new channel is selected; if Android preserves an old channel, update it in system notification settings.
- [ ] Trigger repeated notifications for one task; verify consecutive copy lines do not repeat when alternatives exist.
- [ ] Verify Task, Timer, Alarm, and Alert titles, priority, vibration, and Done/Snooze actions match their intended purpose.

## Data integrity and reporting
- [ ] Create tasks across multiple dates, complete some, and compare Today/Week/Month report totals with the known dataset.
- [ ] Confirm editing/deleting one item does not duplicate task IDs or resurrect intentionally deleted tasks after relaunch.
- [ ] Test TalkBack/focus order, touch targets, contrast, and font scaling before release.

## v34 accessibility follow-up
- [ ] With TalkBack enabled, confirm the secondary-screen back control is announced as “Back” and is focusable.
- [ ] Confirm each SectionAction is announced as a button and its title/subtitle are read in a useful order.
- [ ] Confirm ChoiceRow announces radio-button role and selected/not-selected state.
- [ ] Confirm task completion is announced with the task title and active/completed state, and toggling it does not unexpectedly open task details.
- [ ] Confirm delete controls are announced with the task title and remain independently actionable from the task-card click target.
- [ ] Test at large font/display scale and on the narrowest supported device width; ensure 48dp targets remain operable and text is not clipped.
