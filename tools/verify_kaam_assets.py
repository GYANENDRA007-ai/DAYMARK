"""Validate bundled notification WAVs with only the Python standard library."""
from array import array
from pathlib import Path
import wave
import sys

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/res/raw"
EXPECTED = {
    "kaam_focus_chime.wav": (0.20, 1.50),
    "kaam_timer_chime.wav": (0.20, 1.50),
    "kaam_alarm_pulse.wav": (0.20, 2.00),
    "kaam_alert_double.wav": (0.15, 1.00),
}
failures = []

for filename, (min_seconds, max_seconds) in EXPECTED.items():
    path = ASSETS / filename
    try:
        with wave.open(str(path), "rb") as audio:
            channels = audio.getnchannels()
            sample_rate = audio.getframerate()
            sample_width = audio.getsampwidth()
            frame_count = audio.getnframes()
            compression = audio.getcomptype()
            raw = audio.readframes(frame_count)
        duration = frame_count / sample_rate if sample_rate else 0
        samples = array("h")
        samples.frombytes(raw)
        if sys.byteorder != "little":
            samples.byteswap()
        peak = max((abs(sample) for sample in samples), default=0)
        rms = int((sum(sample * sample for sample in samples) / max(1, len(samples))) ** 0.5)
        valid = (
            channels == 1
            and sample_rate >= 16000
            and sample_width == 2
            and compression == "NONE"
            and min_seconds <= duration <= max_seconds
            and peak > 1000
            and rms > 100
        )
        if valid:
            print(f"PASS | {filename} | {sample_rate} Hz | {duration:.2f}s | peak={peak} rms={rms}")
        else:
            failures.append(filename)
            print(f"FAIL | {filename} | channels={channels} rate={sample_rate} width={sample_width} compression={compression} duration={duration:.2f}s peak={peak} rms={rms}")
    except (OSError, EOFError, wave.Error, ValueError) as error:
        failures.append(filename)
        print(f"FAIL | {filename} | {error}")

print(f"RESULT: {len(EXPECTED) - len(failures)}/{len(EXPECTED)} bundled audio assets valid")
if failures:
    raise SystemExit(1)
