# KAAM v40 QA report — 19 October 2026

## Result

**All available offline QA suites passed.** These checks do not replace Android compilation or device testing.

| Suite | Observed result |
|---|---:|
| Static structural + regression checks | **152/152 passed** |
| Bundled WAV assets | **4/4 valid** |
| Timer / scheduling / recurrence / window / snooze assertions | **11,588 passed** |
| Notification-copy invariant assertions | **3,002 passed** |
| Pure JVM test methods | **45 passed, 0 failed** |
| Offline QA orchestration | **Passed end to end** |

## v40 focus: recovery fault isolation and receiver hardening

When the user returns from Android's exact-alarm settings, the app retries reminders that were deferred while access was unavailable. Previously, a single exception in this foreground retry loop could stop processing the remaining IDs after the pending-ID set had already been cleared.

The retry loop now isolates each reminder. If scheduling one item throws, KAAM logs the failure, requeues that task ID, and continues with the rest. This mirrors the per-task fault isolation already used by the boot/system-change recovery receiver. A static regression check covers the requeue and logging behavior.

The system recovery receiver is now `exported=false`. Its manifest actions are system-originated recovery broadcasts, so other apps should not be able to spoof a clock-change or package-replacement broadcast and force active reminder schedules to be reset. A dedicated static check verifies the receiver is not externally exported.

## Not verified

Android/Compose compilation, Gradle/JUnit execution, APK generation, emulator/physical-device behavior, TalkBack traversal, and real notification delivery remain unverified. This environment has no configured Android SDK and has previously failed DNS resolution for official Gradle/Google Maven download hosts. Offline checks are not a substitute for those tests.

## Reproduce offline QA

From the `ReminderApp` directory:

```powershell
python tools/verify_kaam_offline.py
```
