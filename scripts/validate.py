"""Run the existing engineering checks without installing, flashing, or publishing."""
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
CHECKS = ("android", "simulator", "firmware", "rtc")


def git(*args):
    return subprocess.check_output(["git", "-C", str(ROOT), *args], text=True).strip()


def commands(check, output):
    firmware = ROOT / "Firmware/RykerConnect-REV05"
    if check == "android":
        wrapper = [str(ROOT / "Android/gradlew.bat")] if os.name == "nt" else ["bash", "./gradlew"]
        return [(wrapper + ["--no-daemon", "--console=plain", "--rerun-tasks", "assembleDebug", "testDebugUnitTest", "lintDebug"], ROOT / "Android")]
    if check == "simulator":
        return [([os.environ.get("SIMULATOR_PYTHON", sys.executable), "-m", "unittest", "discover", "-v"], ROOT / "Simulator")]
    if check == "firmware":
        python = os.environ.get("PLATFORMIO_PYTHON", sys.executable)
        return [([python, "-m", "platformio", "run", "-d", str(firmware), "-e", env], ROOT)
                for env in ("RykerConnect_REV05", "RykerConnect_REV05_SensorTest")]
    compiler = os.environ.get("CXX") or shutil.which("g++") or shutil.which("clang++") or shutil.which("cl")
    if not compiler:
        raise RuntimeError("RTC check needs g++, clang++, or an MSVC Developer shell (cl). CXX may name a compiler executable.")
    binary = output / ("rtc_native.exe" if os.name == "nt" else "rtc_native")
    source = firmware / "test/rtc_native.cpp"
    include = firmware / "include"
    if Path(compiler).stem.lower() == "cl":
        compile_args = [compiler, "/nologo", "/EHsc", "/std:c++17", f"/I{include}", str(source), f"/Fe:{binary}", f"/Fo:{output / 'rtc_native.obj'}"]
    else:
        compile_args = [compiler, "-std=c++17", "-I", str(include), str(source), "-o", str(binary)]
    return [(compile_args, ROOT), ([str(binary)], ROOT)]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--only", choices=CHECKS, nargs="+", default=list(CHECKS))
    args = parser.parse_args()
    # Each run has its own directory so results cannot be mistaken for a previous run.
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S.%fZ")
    output = ROOT / ".validation" / stamp
    output.mkdir(parents=True)
    report = {"started_utc": stamp, "commit": git("rev-parse", "HEAD"),
              "dirty": bool(git("status", "--porcelain")), "python": sys.version.split()[0],
              "selected": args.only, "results": [], "physical_device_testing": "pending"}
    for check in dict.fromkeys(args.only):
        started = time.monotonic()
        code = 0
        print(f"Running {check}...", flush=True)
        with (output / f"{check}.log").open("w", encoding="utf-8") as log:
            try:
                for command, cwd in commands(check, output):
                    log.write(f"Command: {command!r}\n")
                    log.flush()
                    code = subprocess.run(command, cwd=cwd, stdout=log, stderr=subprocess.STDOUT).returncode
                    if code:
                        break
            except (OSError, RuntimeError) as exc:
                log.write(f"Unable to run check: {exc}\n")
                code = 1
        result = {"check": check, "status": "passed" if code == 0 else "failed",
                  "exit_code": code, "seconds": round(time.monotonic() - started, 1)}
        report["results"].append(result)
        (output / "results.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        print(f"{check}: {result['status']} ({result['seconds']}s)", flush=True)
        if code:
            print((output / f"{check}.log").read_text(encoding="utf-8", errors="replace")[-10000:], flush=True)
    print(f"Reports: {output.relative_to(ROOT)}", flush=True)
    return int(any(item["exit_code"] != 0 for item in report["results"]))


if __name__ == "__main__":
    raise SystemExit(main())
