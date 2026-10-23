#!/usr/bin/env python3
"""Run KAAM's network-free source, asset, and pure-Kotlin regression checks.

This deliberately compiles the pure Kotlin helpers, smoke runners, and unit-test
sources in one kotlinc invocation. Starting the compiler repeatedly can stall
or be very slow on constrained Windows/JDK setups. This is not an Android build
and does not replace Gradle/JUnit or device acceptance testing.
"""
from __future__ import annotations

import os
import shutil
import signal
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def run(label: str, command: list[str], cwd: Path = ROOT, timeout: int = 180) -> None:
    print(f"\n=== {label} ===", flush=True)
    env = os.environ.copy()
    # The Kotlin compiler can otherwise inherit a very small default heap on
    # constrained hosts. Respect an explicit user value, otherwise use 2 GiB.
    env.setdefault("JAVA_OPTS", "-Xms256m -Xmx2g")
    creationflags = subprocess.CREATE_NEW_PROCESS_GROUP if os.name == "nt" else 0
    process = subprocess.Popen(
        command, cwd=cwd, text=True, env=env,
        start_new_session=(os.name != "nt"), creationflags=creationflags
    )
    try:
        return_code = process.wait(timeout=timeout)
    except subprocess.TimeoutExpired:
        # Kill the entire compiler process tree, not only kotlinc's launcher;
        # its Java child can otherwise survive and make a timeout look frozen.
        if os.name == "nt":
            subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"],
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False)
        else:
            try:
                os.killpg(process.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
        process.wait()
        raise SystemExit(f"FAILED: {label} timed out after {timeout}s; process tree terminated; command: {command[0]}")
    if return_code != 0:
        raise SystemExit(f"FAILED: {label} (exit {return_code})")


def main() -> int:
    run("Static source checks", [sys.executable, "tools/verify_kaam_static.py"])
    run("Hosted Android build workflow checks", [sys.executable, "tools/verify_cloud_workflow.py"])
    run("Bundled sound asset checks", [sys.executable, "tools/verify_kaam_assets.py"])

    kotlinc = shutil.which("kotlinc")
    java = shutil.which("java")
    javac = shutil.which("javac")
    if not kotlinc or not java or not javac:
        missing = [name for name, path in (("kotlinc", kotlinc), ("java", java), ("javac", javac)) if not path]
        print(f"SKIP: pure Kotlin checks need these tools on PATH: {', '.join(missing)}")
        print("Static and sound-asset checks completed; Android build/device checks are separate.")
        return 0

    source = ROOT / "app/src/main/java/com/example/reminderapp"
    tests = ROOT / "app/src/test/java/com/example/reminderapp"
    with tempfile.TemporaryDirectory(prefix="kaam-offline-qa-") as temp:
        out = Path(temp)
        shim_src = out / "junit-shim" / "src" / "org" / "junit"
        shim_classes = out / "junit-shim" / "classes"
        shim_src.mkdir(parents=True)
        shim_classes.mkdir(parents=True)
        (shim_src / "Test.java").write_text(
            "package org.junit;\nimport java.lang.annotation.*;\n"
            "@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)\n"
            "public @interface Test { Class<? extends Throwable> expected() default None.class; "
            "public static class None extends Throwable { private None() {} } }\n",
            encoding="utf-8")
        (shim_src / "Assert.java").write_text(
            "package org.junit;\npublic class Assert {\n"
            " public static void assertEquals(Object e,Object a) { if(e==null?a!=null:!e.equals(a)) "
            "throw new AssertionError(\"expected <\"+e+\"> but was <\"+a+\">\"); }\n"
            " public static void assertNull(Object v) { if(v!=null) throw new AssertionError(\"expected null\"); }\n"
            " public static void assertTrue(boolean v) { if(!v) throw new AssertionError(\"expected true\"); }\n"
            " public static void assertFalse(boolean v) { if(v) throw new AssertionError(\"expected false\"); }\n}\n",
            encoding="utf-8")
        run("Compile temporary JUnit-compatible shim", [javac, "-d", str(shim_classes),
            str(shim_src / "Test.java"), str(shim_src / "Assert.java")])

        runner = out / "PureJvmTestRunner.kt"
        runner.write_text("\n".join([
            "package com.example.reminderapp", "import org.junit.Test", "fun main() {",
            " val classes = listOf(ReminderTimingTest::class.java, NotificationCopyTest::class.java, ExampleUnitTest::class.java)",
            " var passed = 0; var failed = 0",
            " for (clazz in classes) for (method in clazz.declaredMethods) {",
            "  val annotation = method.getAnnotation(Test::class.java) ?: continue", "  try {",
            "   method.invoke(clazz.getDeclaredConstructor().newInstance())",
            "   if (annotation.expected != Test.None::class) throw AssertionError(\"Expected exception but nothing was thrown\")",
            "   passed++", "  } catch (e: java.lang.reflect.InvocationTargetException) {",
            "   val cause = e.cause ?: e",
            "   if (annotation.expected.java.isInstance(cause)) passed++ else { failed++; System.err.println(\"FAIL ${clazz.simpleName}.${method.name}: $cause\") }",
            "  } catch (e: Throwable) { failed++; System.err.println(\"FAIL ${clazz.simpleName}.${method.name}: $e\") }",
            " }", " println(\"Pure JVM unit tests: $passed passed, $failed failed\")",
            " if (failed > 0) error(\"Pure JVM tests failed\")", "}", ""
        ]), encoding="utf-8")

        qa_jar = out / "kaam-offline-qa.jar"
        test_sources = [tests / "ReminderTimingTest.kt", tests / "NotificationCopyTest.kt", tests / "ExampleUnitTest.kt"]
        run("Compile all pure Kotlin helpers, smoke runners, and unit tests once", [
            kotlinc, "-cp", str(shim_classes),
            str(source / "ReminderTiming.kt"), str(source / "NotificationCopy.kt"),
            str(ROOT / "tools/TimerTimingSmoke.kt"), str(ROOT / "tools/NotificationCopySmoke.kt"),
            *map(str, test_sources), str(runner), "-include-runtime", "-d", str(qa_jar)
        ], timeout=180)
        classpath = str(qa_jar) + (";" if sys.platform == "win32" else ":") + str(shim_classes)
        run("Timer / scheduling / recurrence / window / snooze smoke tests", [java, "-cp", classpath,
            "com.example.reminderapp.TimerTimingSmokeKt"])
        run("Notification-copy invariant smoke tests", [java, "-cp", classpath,
            "com.example.reminderapp.NotificationCopySmokeKt"])
        run("Execute actual pure JVM test methods with temporary compatibility shim", [java, "-cp", classpath,
            "com.example.reminderapp.PureJvmTestRunnerKt"])

    print("\nPASS: All available offline QA suites passed.")
    print("LIMITATION: This does not compile the Android/Compose app, produce an APK, or replace device testing.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
