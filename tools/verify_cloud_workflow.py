#!/usr/bin/env python3
"""Validate the hosted Android build workflow structure without network access."""
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]
workflow = root / ".github/workflows/android-build.yml"
try:
    import yaml
except ImportError:
    print("FAIL: PyYAML is required to parse the workflow")
    sys.exit(1)

raw = workflow.read_text(encoding="utf-8")
versions = (root / "gradle/libs.versions.toml").read_text(encoding="utf-8")
wrapper = (root / "gradle/wrapper/gradle-wrapper.properties").read_text(encoding="utf-8")
try:
    data = yaml.load(raw, Loader=yaml.BaseLoader)
except Exception as exc:
    print(f"FAIL: workflow YAML parse error: {exc}")
    sys.exit(1)

checks = {
    "Workflow has manual dispatch": "workflow_dispatch" in data.get("on", {}),
    "Workflow supports push and pull requests": "push" in data.get("on", {}) and "pull_request" in data.get("on", {}),
    "Hosted Linux runner configured": data.get("jobs", {}).get("build-debug-apk", {}).get("runs-on") == "ubuntu-latest",
    "JDK 21 setup configured": "actions/setup-java@v4" in raw and "java-version: '21'" in raw,
    "Android SDK Platform 37 installed": "platforms;android-37" in raw,
    "Build Tools 36 installed": "build-tools;36.0.0" in raw,
    "Offline QA runs before Android build": raw.index("tools/verify_kaam_offline.py") < raw.index("./gradlew :app:assembleDebug"),
    "APK artifact uploaded from expected output": "app/build/outputs/apk/debug/app-debug.apk" in raw and "kaam-debug-apk" in raw,
    "Diagnostics upload runs even on failure": "if: always()" in raw and "kaam-build-diagnostics" in raw,
    "AGP 9.4 / Gradle 9.6 / API 37 toolchain pins are aligned": 'agp = "9.4.0"' in versions and "gradle-9.6.0-bin.zip" in wrapper and "version = release(37)" in (root / "app/build.gradle.kts").read_text(encoding="utf-8"),
    "Compose compiler plugin follows the pinned Kotlin version": 'kotlin = "2.4.10"' in versions and 'composeBom = "2026.08.00"' in versions and "kotlin-compose = { id = \"org.jetbrains.kotlin.plugin.compose\", version.ref = \"kotlin\" }" in versions,
}
for label, ok in checks.items():
    print(f"{'PASS' if ok else 'FAIL'} | {label}")
print(f"RESULT: {sum(checks.values())}/{len(checks)} cloud workflow checks passed")
sys.exit(0 if all(checks.values()) else 1)
