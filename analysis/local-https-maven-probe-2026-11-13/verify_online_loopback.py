"""Independent native Maven HTTPS publishing probe with loopback-only egress.

This supplements verify.py's strict --offline negative path. Gradle's native
Maven uploader refuses *all* HTTPS PUTs in --offline, including 127.0.0.1.
Real publishing here intentionally omits --offline, but directs both Java and
process HTTP(S) proxy traffic to a reject-only localhost proxy. Java's
nonProxyHosts allows only the private 127.0.0.1 Maven fixture. Every configured
repository artifact is already cached by prior offline consumer builds.

The probe makes no Modrinth/CurseForge calls and uses no user credentials.
Evidence is non-overwriting, all inside /vbc/Fabric Amper/analysis/.
"""

from __future__ import annotations

import json
import os
from pathlib import Path
import re
import secrets
import ssl
import subprocess
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import verify as fixture


ROOT = fixture.ROOT
HERE = fixture.HERE
RUN = HERE / "online-loopback-2026-11-14"
PUBLISH_TASK = fixture.PUBLISH_TASK


def check(condition: bool, explanation: str) -> None:
    if not condition:
        raise AssertionError(explanation)


class DenyProxy(ThreadingHTTPServer):
    daemon_threads = True
    allow_reuse_address = False

    def __init__(self) -> None:
        super().__init__(("127.0.0.1", 0), ProxyHandler)
        self.denied: list[str] = []
        self.lock = threading.Lock()


class ProxyHandler(BaseHTTPRequestHandler):
    server: DenyProxy

    def log_message(self, format: str, *args: object) -> None:
        return

    def deny(self) -> None:
        # Do not forward, resolve or dial the requested endpoint.
        with self.server.lock:
            self.server.denied.append(self.command)
        self.send_response(403)
        self.send_header("Content-Length", "0")
        self.send_header("Connection", "close")
        self.end_headers()
        self.close_connection = True

    def do_CONNECT(self) -> None:
        self.deny()

    def do_GET(self) -> None:
        self.deny()

    def do_HEAD(self) -> None:
        self.deny()

    def do_POST(self) -> None:
        self.deny()

    def do_PUT(self) -> None:
        self.deny()


def run_gradle(stage: str, consumer: Path, env: dict[str, str],
               args: list[str], secrets_to_hide: tuple[str, str]) -> str:
    check(not (stage.startswith("publish") and "--offline" in args),
          "Real native publish may not use Gradle --offline")
    cmd = fixture.gradle_command(fixture.DEFAULT_GRADLE) + args + [
        "--console=plain", "--no-daemon", "--no-build-cache"
    ]
    outcome = subprocess.run(
        cmd, cwd=consumer, env=env, stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT, encoding="utf-8", errors="replace",
        timeout=900, check=False
    )
    output = outcome.stdout
    leaked = any(value in output for value in secrets_to_hide)
    for value in secrets_to_hide:
        output = output.replace(value, "[REDACTED]")
    (RUN / f"{stage}.log").write_text(output, encoding="utf-8")
    check(not leaked, f"{stage}: dummy credentials occurred in Gradle output")
    check(outcome.returncode == 0 and "BUILD SUCCESSFUL" in output,
          f"{stage}: Gradle failed with exit {outcome.returncode}; see the saved log")
    return output


def main() -> None:
    check(HERE.parent == ROOT / "analysis" and ROOT.name == "Fabric Amper",
          "Fixture must remain inside the approved Fabric Amper project")
    check(not RUN.exists(), "Non-overwriting online-loopback evidence already exists")
    fixture.require_local_marker_repo()
    java, keytool = fixture.resolve_java()
    check(fixture.DEFAULT_GRADLE.is_file(), "Gradle 9.7.1 installation missing")
    home = ROOT / ".h"
    check((home / "caches" / "modules-2").is_dir() and
          (home / "caches" / "fabric-loom" / "26.3").is_dir(),
          "Prior isolated offline Gradle dependency cache is unavailable")

    RUN.mkdir()
    certificate, private_key = fixture.create_local_certificate(RUN)
    store = fixture.import_test_truststore(RUN, keytool, certificate)
    username = "local-probe-" + secrets.token_hex(8)
    password = secrets.token_urlsafe(32)
    hide = (username, password)
    target = fixture.LocalMavenServer(username + ":" + password)
    proxy = DenyProxy()
    tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    tls.minimum_version = ssl.TLSVersion.TLSv1_2
    tls.load_cert_chain(str(certificate), str(private_key))
    target.socket = tls.wrap_socket(target.socket, server_side=True)
    target_thread = threading.Thread(target=target.serve_forever, daemon=True)
    proxy_thread = threading.Thread(target=proxy.serve_forever, daemon=True)
    target_thread.start()
    proxy_thread.start()

    try:
        fixture.verify_tls_challenge(target.server_port, certificate)
        consumer = RUN / "settings-only-consumer"
        fixture.create_consumer(
            consumer, f"https://127.0.0.1:{target.server_port}/maven"
        )
        env = os.environ.copy()
        for key in list(env):
            if any(part in key.upper() for part in
                   ("TOKEN", "SECRET", "PASSWORD", "CREDENTIAL")):
                env.pop(key, None)
        for key in ("GRADLE_OPTS", "JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS",
                    "JDK_JAVA_OPTIONS", "GRADLE_RO_DEP_CACHE"):
            env.pop(key, None)
        env["GRADLE_USER_HOME"] = str(home)
        env["JAVA_HOME"] = str(java.parent.parent)
        env["PATH"] = str(java.parent) + os.pathsep + env.get("PATH", "")
        env[fixture.USERNAME_VAR] = username
        env[fixture.PASSWORD_VAR] = password

        # Neither the JVM nor subprocess clients can reach an external HTTP
        # endpoint through the configured proxies. The Maven endpoint itself
        # is the literal loopback IP and uses genuine JSSE trust validation.
        env["JAVA_TOOL_OPTIONS"] = (
            f'-Djavax.net.ssl.trustStore="{store.as_posix()}" '
            "-Djavax.net.ssl.trustStoreType=PKCS12 "
            f"-Djavax.net.ssl.trustStorePassword={fixture.TRUSTSTORE_PASSWORD} "
            "-Dhttp.proxyHost=127.0.0.1 "
            f"-Dhttp.proxyPort={proxy.server_port} "
            "-Dhttps.proxyHost=127.0.0.1 "
            f"-Dhttps.proxyPort={proxy.server_port} "
            '-Dhttp.nonProxyHosts="localhost|127.0.0.1"'
        )
        for key in ("HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY",
                    "http_proxy", "https_proxy", "all_proxy"):
            env[key] = f"http://127.0.0.1:{proxy.server_port}"
        for key in ("NO_PROXY", "no_proxy"):
            env[key] = "127.0.0.1,localhost"

        print("Compiling in offline mode from isolated project-local caches", flush=True)
        prewarm = run_gradle("00-compile-offline", consumer, env,
                             ["compileJava", "--offline"], hide)
        check(re.search(r"(?m)^> Task :compileJava(?:\s|$)", prewarm) is not None,
              "Offline prewarm must run the real compiler task")

        # Attempt real native uploads with --configuration-cache: the Gradle
        # version is known to reject explicit PasswordCredentials for CC.
        # Report actual behavior, and never report a false CC HIT.
        entries = []
        hashes = None
        for index in (1, 2):
            with target.lock:
                start = len(target.events)
            print(f"Native localhost HTTPS Maven publish, pass {index}", flush=True)
            output = run_gradle(
                f"publish-0{index}-with-cc", consumer, env,
                [PUBLISH_TASK, "--configuration-cache"], hide
            )
            observed = fixture.verify_received(
                RUN, target, consumer, start, hide, f"publish pass {index}"
            )
            if hashes is not None:
                check(hashes == observed, "Artifact SHA changed across real uploads")
            hashes = observed
            with target.lock:
                new_events = target.events[start:]
            entries.append({
                "pass": index,
                "authenticatedSuccessfulPutCount": sum(
                    event["method"] == "PUT" and event["status"] == 201 and
                    event["authenticated"] for event in new_events
                ),
                "configurationCacheStored": "configuration cache entry stored" in output.lower(),
                "configurationCacheReused": "reusing configuration cache" in output.lower(),
                "configurationCacheDisabled": "configuration cache disabled" in output.lower(),
            })
        with proxy.lock:
            denied = list(proxy.denied)
        with target.lock:
            events = list(target.events)
        check(not denied, "Gradle attempted an unrelated external HTTP(S) endpoint")
        check(all(entry["authenticatedSuccessfulPutCount"] >= 4 for entry in entries),
              "Expected JAR/POM/module/sources authenticated uploads from each run")
        check(all(e["authenticated"] for e in events if e["status"] == 201),
              "Server accepted unauthenticated PUT")
        check(not (consumer / "build.gradle").exists() and
              not (consumer / "build.gradle.kts").exists(),
              "Settings-only authoring contract changed")

        manifest = {
            "status": "PASS_FOR_NATIVE_HTTPS_UPLOAD_ONLY",
            "date": "2026-11-14",
            "target": "127.0.0.1 TLS SAN IP + Basic authentication",
            "egress": "JVM and HTTP(S) environment proxies reject all non-loopback requests",
            "proxyRejectedRequests": len(denied),
            "gradle": "9.7.1",
            "java": "25",
            "consumer": "settings.gradle.kts + mod.yaml + Java; fabric/api",
            "gav": fixture.GAV,
            "uploads": entries,
            "sha256": hashes,
            "allHttpEvents": events,
            "externalModrinthCurseForgeUploads": 0,
            "ccCaveat": "Native explicit PasswordCredentials may disable Gradle configuration cache; inspect upload flags.",
        }
        (RUN / "manifest.json").write_text(
            json.dumps(manifest, indent=2) + "\n", encoding="utf-8"
        )
        print("PASS authenticated native Maven uploads to loopback HTTPS", flush=True)
        print("Evidence:", RUN / "manifest.json", flush=True)
    finally:
        with target.lock:
            events = list(target.events)
        with proxy.lock:
            denied = list(proxy.denied)
        (RUN / "events-on-exit.json").write_text(
            json.dumps({"targetEvents": events, "denyProxyRequestMethods": denied},
                       indent=2) + "\n", encoding="utf-8"
        )
        target.shutdown()
        proxy.shutdown()
        target.server_close()
        proxy.server_close()
        target_thread.join(timeout=10)
        proxy_thread.join(timeout=10)


if __name__ == "__main__":
    main()
