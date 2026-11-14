"""Isolated no-consent Fabric server smoke probe.

Never accepts Mojang EULA, touches another Minecraft profile, or kills a
process by executable name. This is diagnostic evidence, NOT a server PASS.
"""
from __future__ import annotations

import os
import pathlib
import subprocess
import sys
import time

import psutil

ROOT = pathlib.Path(__file__).resolve().parent
GRADLE = pathlib.Path(
    r"C:\Users\user\.gradle\wrapper\dists\gradle-9.7.1-bin"
    r"\1w1c7tv4s851m17nbqdsro2tv\gradle-9.7.1\bin\gradle.bat"
)
LOG = ROOT / "server-noeula.log"
TIMEOUT_SECONDS = 240


def tracked_descendants(root_pid: int, first_seen: dict[int, float]) -> None:
    try:
        proc = psutil.Process(root_pid)
        for child in proc.children(recursive=True):
            first_seen.setdefault(child.pid, child.create_time())
    except (psutil.NoSuchProcess, psutil.AccessDenied):
        pass


def stop_owned(first_seen: dict[int, float], root_pid: int) -> None:
    # Exact PID + observed process start identity protects unrelated/reused PIDs.
    tracked_descendants(root_pid, first_seen)
    for pid, created in reversed(list(first_seen.items())):
        try:
            process = psutil.Process(pid)
            if abs(process.create_time() - created) < 0.001:
                process.terminate()
        except (psutil.NoSuchProcess, psutil.AccessDenied):
            pass
    time.sleep(2)
    for pid, created in reversed(list(first_seen.items())):
        try:
            process = psutil.Process(pid)
            if abs(process.create_time() - created) < 0.001 and process.is_running():
                process.kill()
        except (psutil.NoSuchProcess, psutil.AccessDenied):
            pass


def main() -> int:
    if not GRADLE.is_file():
        raise RuntimeError("The explicitly pinned Gradle executable is absent")
    if (ROOT / "build.gradle.kts").exists() or (ROOT / "build.gradle").exists():
        raise RuntimeError("The consumer must remain settings-only")
    # A previous consent must never be inherited.
    for file in ROOT.rglob("eula.txt"):
        if file.read_text(encoding="utf-8", errors="replace").lower().find("eula=true") >= 0:
            raise RuntimeError("Refusing to reuse a directory with EULA accepted")

    home = ROOT / "sandbox-home"
    home.mkdir(exist_ok=True)
    gradle_home = ROOT / "sandbox-gradle-home"
    gradle_home.mkdir(exist_ok=True)
    env = dict(os.environ)
    env["GRADLE_USER_HOME"] = str(gradle_home)
    env["HOME"] = str(home)
    env["USERPROFILE"] = str(home)
    env["APPDATA"] = str(home / "AppData")
    env["LOCALAPPDATA"] = str(home / "LocalAppData")
    env["JAVA_TOOL_OPTIONS"] = f'-Duser.home="{home}"'
    command = [
        os.environ.get("ComSpec", "cmd.exe"), "/d", "/c",
        str(GRADLE), "--no-daemon", "--console=plain", "runServer",
    ]
    first_seen: dict[int, float] = {}
    with LOG.open("w", encoding="utf-8") as output:
        process = subprocess.Popen(
            command,
            cwd=ROOT,
            env=env,
            stdin=subprocess.DEVNULL,
            stdout=output,
            stderr=subprocess.STDOUT,
        )
        first_seen[process.pid] = psutil.Process(process.pid).create_time()
        deadline = time.monotonic() + TIMEOUT_SECONDS
        try:
            while time.monotonic() < deadline and process.poll() is None:
                tracked_descendants(process.pid, first_seen)
                time.sleep(0.2)
            if process.poll() is None:
                print("RESULT: TIMEOUT (server process tree terminated)")
                return 2
            print("GRADLE_EXIT:", process.returncode)
        finally:
            stop_owned(first_seen, process.pid)

    log = LOG.read_text(encoding="utf-8", errors="replace")
    loader_seen = "Fabric Loader" in log or "FabricLoader" in log
    mod_seen = "fabric_amper_server_probe" in log
    eula_lines = [s for s in log.splitlines() if "eula" in s.lower()]
    print("LOADER_SEEN:", loader_seen)
    print("MOD_ID_SEEN:", mod_seen)
    print("EULA_MESSAGES:", len(eula_lines))
    for line in eula_lines[-6:]:
        print(line[:350])
    for file in ROOT.rglob("eula.txt"):
        if "eula=true" in file.read_text(encoding="utf-8", errors="replace").lower():
            raise RuntimeError("EULA must never be accepted by this probe")
    print("EULA_ACCEPTED: false")
    if loader_seen and mod_seen and eula_lines:
        print("RESULT: PARTIAL - Loader discovered mod, server refused EULA")
        return 0
    print("RESULT: INCONCLUSIVE - inspect isolated log; never count as full server PASS")
    return 1


if __name__ == "__main__":
    sys.exit(main())
