# KAAM v41 QA report — 20 October 2026

## Result

**All available offline QA suites passed.** Android compilation and device tests are still outstanding.

| Suite | Result |
|---|---:|
| Static source/resource/regression checks | **153/153 passed** |
| Bundled WAV assets | **4/4 valid** |
| Timer/scheduling/recurrence/window/snooze assertions | **11,588 passed** |
| Notification-copy invariant assertions | **3,002 passed** |
| Pure JVM unit-test methods | **45 passed, 0 failed** |
| Offline QA orchestration | **Passed end-to-end** |

## v41 focus: strict clock-time input validation

The shared clock parser previously removed spaces and stripped AM/PM suffixes sequentially, which could accidentally normalize malformed input such as `12:00AMPM` into a valid time. It now validates the full input format before parsing. Supported forms remain `H:mm`, `HH:mm`, and those forms with a single optional AM/PM suffix, with an optional space before the suffix.

Regression coverage includes repeated suffixes, embedded whitespace, invalid suffixes, extra minute digits, and valid AM/PM formats. The existing v40 recovery fault-isolation and receiver-hardening changes remain in the source.

## Build limitation

Attempted `./gradlew :app:assembleDebug --stacktrace --no-daemon --console=plain`. It fails before Gradle starts because `downloads.gradle.org` cannot resolve (`java.net.UnknownHostException`). No Android SDK is configured in this environment. No APK, Android/Compose compile, or real-device success is claimed.

## Reproduce offline QA

From the `ReminderApp` directory:

```powershell
python tools/verify_kaam_offline.py
```
