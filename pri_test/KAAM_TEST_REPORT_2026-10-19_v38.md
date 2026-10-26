# KAAM v38 QA report — 19 October 2026

## Result

**All available offline QA suites passed.** These checks do not replace Android compilation or device testing.

| Suite | Observed result |
|---|---:|
| Static structural + regression checks | **150/150 passed** |
| Bundled WAV assets | **4/4 valid** |
| Timer / scheduling / recurrence / window / snooze assertions | **11,588 passed** |
| Notification-copy invariant assertions | **3,002 passed** |
| Pure JVM test methods | **45 passed, 0 failed** |
| Offline QA orchestration | **Passed end to end** |
| ZIP integrity | To be recorded in the handoff after packaging |

## v38 focus: schedule recovery after system changes

- `KaamBootReceiver` now responds to reboot, package replacement, wall-clock changes, time-zone changes, and Android's exact-alarm-access grant broadcast.
- The exact-alarm grant path verifies that access is actually available before rebuilding schedules.
- Wall-clock and time-zone changes trigger recomputation of active clock-based schedules.
- Duration timers are excluded from that specific clock-change recovery path so the receiver does not deliberately reset their countdown schedule.
- Recovery continues to support persisted snoozes and deferred scheduling when exact-alarm access is unavailable.
- Individual schedule-restoration failures are logged per task, allowing later tasks in the same recovery pass to continue.
- Static regression checks explicitly verify the new manifest actions and receiver guards.

## Not verified

Android/Compose compilation, Gradle/JUnit execution, APK generation, emulator/physical-device behavior, TalkBack traversal, and real notification delivery remain unverified. This environment has no configured Android SDK and has previously failed DNS resolution for official Gradle/Google Maven download hosts. Offline checks are not a substitute for those tests.

## Reproduce offline QA

From the `ReminderApp` directory:

```powershell
python tools/verify_kaam_offline.py
```
