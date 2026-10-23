# KAAM v49 Verification Report — 21 October 2026

## Change
Added a hosted build workflow at `.github/workflows/android-build.yml` so the app can be compiled on a clean GitHub Actions Linux runner with JDK 21 and Android SDK Platform 37. A successful run publishes `app-debug.apk`; any available build diagnostics are published even if the build fails.

## Local checks
- Static/source/XML checks: 159/159 passed.
- Bundled audio assets: 4/4 valid.
- Timer/scheduling/repeat/fixed-time/window/snooze smoke assertions: 11,588 passed.
- Notification-copy smoke assertions: 3,002 passed.
- Pure JVM tests: 47 passed, 0 failed.
- Hosted workflow YAML and required steps: 9/9 checks passed.
- ZIP integrity: checked after packaging.

## Android build result
Attempted `./gradlew :app:assembleDebug --stacktrace --no-daemon --console=plain`. It fails before Gradle starts because `downloads.gradle.org` cannot be resolved (`java.net.UnknownHostException`). This container has no Android SDK Platform 37. No APK was generated, and the complete Android/Compose source has not been compiler-verified.

## Limitation / next gate
The new workflow is prepared and structurally validated, but not executed: this session has no outbound network/DNS and no connected GitHub repository/runner credentials. A hosted workflow run must complete before we can report an APK. Real-device tests remain pending even after compilation.
