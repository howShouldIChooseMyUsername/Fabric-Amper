"""One-shot, loopback-only HTTPS Maven publish and Gradle cache acceptance probe.

Run explicitly from the project's Windows host with Python + cryptography,
JDK 25 (including keytool), the already installed Gradle 9.7.1 and warmed
Gradle dependency caches. This script has no external HTTP client or platform
upload; every Gradle task uses --offline and every HTTP server socket binds
only to 127.0.0.1. Run artifacts stay below this script's directory; an
isolated, deliberately short Gradle cache home lives in the project root .h
to avoid Windows MAX_PATH failures when copying warmed dependency caches.

Optional flags: --check-missing-credentials, --check-http-rejection.
No production files, shared Gradle cache, EULA, game installation, ~/.m2,
or existing probe output is modified. Running it twice deliberately fails.
"""

from __future__ import annotations

import argparse
import base64
import datetime as dt
import hashlib
import hmac
import http.client
import ipaddress
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import ssl
import subprocess
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import unquote, urlsplit
import xml.etree.ElementTree as ET
import zipfile


HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
# Publish the *current* production sources to this project-local test repository
# before executing the probe; do not accidentally re-test the archived old JAR.
SOURCE_REPO = ROOT / "build" / "g29-probe" / "maven-repo"
DEFAULT_GRADLE = Path(
    r"C:\Users\user\.gradle\wrapper\dists\gradle-9.7.1-bin\1w1c7tv4s851m17nbqdsro2tv"
    r"\gradle-9.7.1\bin\gradle.bat"
)
GROUP = "dev.fabricamper.localhttps"
ARTIFACT = "probe-api"
VERSION = "0.1.0+26.3"
GAV = f"{GROUP}:{ARTIFACT}:{VERSION}"
RELATIVE_ARTIFACT_DIR = f"{GROUP.replace('.', '/')}/{ARTIFACT}/{VERSION}"
RELATIVE_NAME = f"{ARTIFACT}-{VERSION}"
PUBLISH_TASK = "publishMavenJavaPublicationToFabricAmperRemoteRepository"
USERNAME_VAR = "FABRIC_AMPER_PROBE_LOCAL_MAVEN_USERNAME"
PASSWORD_VAR = "FABRIC_AMPER_PROBE_LOCAL_MAVEN_PASSWORD"
MAX_ARTIFACT_BYTES = 64 * 1024 * 1024
CHECKSUM_EXTENSIONS = ("", ".md5", ".sha1", ".sha256", ".sha512")
TRUSTSTORE_PASSWORD = "changeit"  # Local disposable test truststore; not a service credential.


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest().upper()


def check(condition: bool, reason: str) -> None:
    if not condition:
        raise AssertionError(reason)


def is_allowed_path(relative: str) -> bool:
    """Only this one Maven GAV (and its Maven metadata/checksums)."""
    if "\\" in relative or "//" in relative or ".." in relative or not relative:
        return False
    module_base = f"{GROUP.replace('.', '/')}/{ARTIFACT}"
    metadata = f"{module_base}/maven-metadata.xml"
    if any(relative == metadata + ext for ext in CHECKSUM_EXTENSIONS):
        return True
    prefix = RELATIVE_ARTIFACT_DIR + "/" + RELATIVE_NAME
    permitted = (".jar", "-sources.jar", ".pom", ".module")
    return any(relative == prefix + suffix + ext for suffix in permitted for ext in CHECKSUM_EXTENSIONS)


class LocalMavenServer(ThreadingHTTPServer):
    daemon_threads = True
    allow_reuse_address = False

    def __init__(self, credentials: str):
        super().__init__(("127.0.0.1", 0), MavenHandler)
        self.expected_authorization = "Basic " + base64.b64encode(credentials.encode("utf-8")).decode("ascii")
        self.blobs: dict[str, bytes] = {}
        self.events: list[dict[str, object]] = []
        self.lock = threading.Lock()

    def record(self, method: str, path: str, status: int, authenticated: bool, size: int = 0) -> None:
        # No HTTP headers, authorization values, passwords or request bodies in logs.
        with self.lock:
            self.events.append({
                "method": method,
                "path": path,
                "status": status,
                "authenticated": authenticated,
                "bytes": size,
            })


class MavenHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server: LocalMavenServer

    def log_message(self, format: str, *args: object) -> None:
        # BaseHTTPRequestHandler's default stderr logger could echo sensitive URLs.
        pass

    def _send(self, status: int, payload: bytes = b"", *, challenge: bool = False) -> None:
        self.send_response(status)
        self.send_header("Content-Length", str(len(payload)))
        self.send_header("Content-Type", "application/octet-stream")
        if challenge:
            self.send_header("WWW-Authenticate", 'Basic realm="FabricAmperLocalProbe"')
            self.send_header("Connection", "close")
            self.close_connection = True
        self.end_headers()
        if self.command != "HEAD" and payload:
            self.wfile.write(payload)

    def _request_path(self) -> str | None:
        parsed = urlsplit(self.path)
        if parsed.scheme or parsed.netloc or parsed.query or parsed.fragment or not parsed.path.startswith("/maven/"):
            return None
        relative = unquote(parsed.path[len("/maven/"):])
        return relative if is_allowed_path(relative) else None

    def _authorize(self, relative: str) -> bool:
        header = self.headers.get("Authorization", "")
        authorized = hmac.compare_digest(header, self.server.expected_authorization)
        if not authorized:
            self.server.record(self.command, relative, 401, False)
            self._send(401, challenge=True)
        return authorized

    def _read_upload(self) -> bytes:
        transfer = self.headers.get("Transfer-Encoding", "").lower().strip()
        length_header = self.headers.get("Content-Length")
        if transfer == "chunked":
            data = bytearray()
            while True:
                line = self.rfile.readline(100)
                size = int(line.split(b";", 1)[0].strip(), 16)
                check(len(data) + size <= MAX_ARTIFACT_BYTES, "Maven upload exceeds probe size limit")
                if size == 0:
                    while self.rfile.readline(4096) not in (b"\r\n", b"\n", b""):
                        pass
                    return bytes(data)
                data.extend(self.rfile.read(size))
                check(self.rfile.read(2) == b"\r\n", "Invalid HTTP chunk terminator")
        check(transfer == "", "Unsupported upload transfer encoding")
        check(length_header is not None and length_header.isdecimal(), "Missing Content-Length")
        length = int(length_header)
        check(0 < length <= MAX_ARTIFACT_BYTES, "Invalid Maven upload size")
        data = self.rfile.read(length)
        check(len(data) == length, "Truncated Maven upload")
        return data

    def _handle_read(self) -> None:
        relative = self._request_path()
        if relative is None:
            self.server.record(self.command, "rejected", 404, False)
            self._send(404)
            return
        if not self._authorize(relative):
            return
        with self.server.lock:
            payload = self.server.blobs.get(relative)
        code = 200 if payload is not None else 404
        self.server.record(self.command, relative, code, True)
        self._send(code, payload or b"")

    def do_GET(self) -> None:
        self._handle_read()

    def do_HEAD(self) -> None:
        self._handle_read()

    def do_PUT(self) -> None:
        relative = self._request_path()
        if relative is None:
            self.server.record("PUT", "rejected", 404, False)
            self._send(404, challenge=True)
            return
        if not self._authorize(relative):
            return
        try:
            payload = self._read_upload()
        except (AssertionError, ValueError, OSError):
            self.server.record("PUT", relative, 400, True)
            self._send(400, challenge=True)
            return
        with self.server.lock:
            self.server.blobs[relative] = payload
        self.server.record("PUT", relative, 201, True, len(payload))
        self._send(201)

    def do_POST(self) -> None:
        self._send(405, challenge=True)

    def do_DELETE(self) -> None:
        self._send(405, challenge=True)


def create_local_certificate(run: Path) -> tuple[Path, Path]:
    try:
        from cryptography import x509
        from cryptography.hazmat.primitives import hashes, serialization
        from cryptography.hazmat.primitives.asymmetric import rsa
        from cryptography.x509.oid import ExtendedKeyUsageOID, NameOID
    except ImportError as exc:
        raise RuntimeError("Python cryptography is required for local SAN TLS certificate generation") from exc

    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    now = dt.datetime.now(dt.timezone.utc)
    identity = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "127.0.0.1")])
    cert = (
        x509.CertificateBuilder()
        .subject_name(identity)
        .issuer_name(identity)
        .public_key(key.public_key())
        .serial_number(x509.random_serial_number())
        .not_valid_before(now - dt.timedelta(hours=1))
        .not_valid_after(now + dt.timedelta(days=2))
        .add_extension(x509.SubjectAlternativeName([x509.IPAddress(ipaddress.IPv4Address("127.0.0.1"))]), critical=False)
        .add_extension(x509.BasicConstraints(ca=True, path_length=0), critical=True)
        .add_extension(x509.KeyUsage(digital_signature=True, content_commitment=False, key_encipherment=True,
                                      data_encipherment=False, key_agreement=False, key_cert_sign=True,
                                      crl_sign=False, encipher_only=False, decipher_only=False), critical=True)
        .add_extension(x509.ExtendedKeyUsage([ExtendedKeyUsageOID.SERVER_AUTH]), critical=False)
        .sign(private_key=key, algorithm=hashes.SHA256())
    )
    cert_file = run / "localhost-cert.pem"
    key_file = run / "localhost-key.pem"
    cert_file.write_bytes(cert.public_bytes(serialization.Encoding.PEM))
    key_file.write_bytes(key.private_bytes(serialization.Encoding.PEM,
                                            serialization.PrivateFormat.PKCS8,
                                            serialization.NoEncryption()))
    if os.name != "nt":
        key_file.chmod(0o600)
    return cert_file, key_file


def resolve_java() -> tuple[Path, Path]:
    java_home_value = os.environ.get("FABRIC_AMPER_PROBE_JAVA_HOME") or os.environ.get("JAVA_HOME")
    java_home = Path(java_home_value) if java_home_value else None
    java = (java_home / "bin" / "java.exe") if java_home else Path(shutil.which("java") or "")
    keytool = (java_home / "bin" / "keytool.exe") if java_home else Path(shutil.which("keytool") or "")
    check(java.is_file() and keytool.is_file(), "JDK 25 java.exe and keytool.exe are required; set FABRIC_AMPER_PROBE_JAVA_HOME")
    version = subprocess.run([str(java), "-version"], capture_output=True,
                             text=True, errors="replace", check=False)
    check(version.returncode == 0 and re.search(r'version "25(?:\.|\")', version.stderr + version.stdout) is not None,
          "The local HTTPS Maven probe requires JDK 25")
    return java, keytool


def import_test_truststore(run: Path, keytool: Path, cert_file: Path) -> Path:
    store = run / "local-truststore.p12"
    result = subprocess.run([
        str(keytool), "-importcert", "-noprompt", "-alias", "fabric-amper-localhost",
        "-storetype", "PKCS12", "-keystore", str(store),
        "-storepass", TRUSTSTORE_PASSWORD, "-file", str(cert_file),
    ], capture_output=True, text=True, errors="replace", check=False)
    check(result.returncode == 0 and store.is_file(), "keytool could not import the local SAN certificate")
    return store


def create_isolated_gradle_home(run: Path) -> Path:
    # A deep analysis/.../run/gradle-home path triggers Windows WinError 206
    # for otherwise valid Gradle cache artifact paths. Keep the independent
    # writable home at the shortest practical path *inside the project*.
    destination = ROOT / ".h"
    source = Path(os.environ.get("FABRIC_AMPER_PROBE_SEED_GRADLE_HOME", str(Path.home() / ".gradle")))
    modules_cache = source / "caches" / "modules-2"
    check(modules_cache.is_dir(),
          "Previously warmed dependency cache required for strict --offline execution")
    reuse_isolated_home = destination.exists()
    if reuse_isolated_home:
        check((destination / "caches" / "modules-2").is_dir(),
              "Existing short Gradle home is not the isolated cache created by this probe")
        print("Reusing only this probe's project-local isolated Gradle dependency cache", flush=True)
    else:
        destination.mkdir()
    # Gradle documents portable copies of caches/modules-2, including its
    # resolution metadata. Ignore stale locks and gc.properties. Copying ONLY
    # modules-2 avoids the multi-GiB transforms/build-cache trees and never
    # reads a live writable installation through GRADLE_RO_DEP_CACHE.
    # On Windows some unrelated old plugin-marker filenames exceed MAX_PATH
    # even when copied under ROOT/.h. Skip only such uncopyable entries; an
    # artifact actually needed by the probe will make --offline fail closed.
    writable_cache = destination / "caches"
    writable_cache.mkdir(exist_ok=True)
    skipped_paths: list[str] = []

    def ignore_long_paths(src: str, names: list[str]) -> set[str]:
        relative = Path(src).relative_to(modules_cache)
        excluded = set(shutil.ignore_patterns("*.lock", "*.lck", "gc.properties")(src, names))
        for name in names:
            target = writable_cache / "modules-2" / relative / name
            if len(str(target)) >= 240:
                excluded.add(name)
                skipped_paths.append(str(relative / name).replace("\\", "/"))
        return excluded

    if not reuse_isolated_home:
        shutil.copytree(modules_cache, writable_cache / "modules-2",
                        ignore=ignore_long_paths)
    # Minecraft JARs and version manifests live outside modules-2 in Loom's
    # shared cache. Seed only local metadata and the 26.3 game data needed by
    # this fixture, not other versions or the user's assets/worlds/profiles.
    loom_source = source / "caches" / "fabric-loom"
    loom_target = writable_cache / "fabric-loom"
    check((loom_source / "26.3" / "mojang_minecraft_info.json").is_file(),
          "Warmed Loom 26.3 cache is required for --offline")
    if not loom_target.exists():
        loom_target.mkdir()
        shutil.copytree(loom_source / "26.3", loom_target / "26.3",
                        ignore=shutil.ignore_patterns("*.lock", "*.lck"))
        if (loom_source / "minecraftMaven").is_dir():
            shutil.copytree(loom_source / "minecraftMaven", loom_target / "minecraftMaven",
                            ignore=shutil.ignore_patterns("*.lock", "*.lck"))
        for filename in ("mojang_versions_manifest.json", "fabric_experimental_versions_manifest.json"):
            if (loom_source / filename).is_file():
                shutil.copy2(loom_source / filename, loom_target / filename)
    (run / "cache-path-omissions.json").write_text(
        json.dumps({"excludedOverlongEntries": skipped_paths}, indent=2), encoding="utf-8")
    print(f"Isolated cache copy done; {len(skipped_paths)} unrelated overlong entries excluded", flush=True)
    # A missing Loom game cache will make the strictly offline prewarm fail
    # closed; it is never repaired by unexpected internet downloads.
    return destination


def consumer_settings() -> str:
    return (
        "pluginManagement {\n"
        "    repositories {\n"
        f'        maven {{ url = uri("{SOURCE_REPO.resolve().as_uri()}") }}\n'
        '        maven("https://maven.fabricmc.net/")\n'
        "        gradlePluginPortal()\n"
        "        mavenCentral()\n"
        "    }\n"
        "}\n"
        'plugins { id("dev.fabric-amper.settings") version "0.1.0" }\n'
    )


def consumer_yaml(url: str) -> str:
    return (
        "schema: 1\n"
        "product: fabric/api\n"
        "mod:\n"
        "  id: amper_local_https_probe\n"
        f'  version: "{VERSION}"\n'
        f"  group: {GROUP}\n"
        f"  archivesName: {ARTIFACT}\n"
        "  name: Local HTTPS Maven Probe\n"
        '  environment: "*"\n'
        "  entrypoints:\n"
        "    main:\n"
        "      - dev.fabricamper.localhttps.ProbeEntry\n"
        "minecraft:\n"
        '  version: "26.3"\n'
        "  mappings: mojang\n"
        '  loader: "0.20.1"\n'
        "settings:\n"
        "  java:\n"
        "    release: 25\n"
        "    sourcesJar: true\n"
        "  kotlin:\n"
        "    enabled: false\n"
        "  loom:\n"
        "    splitEnvironmentSourceSets: false\n"
        "repositories:\n"
        "  - fabric\n"
        "  - mavenCentral\n"
        "publish:\n"
        "  maven:\n"
        f"    url: {url}\n"
        f"    credentialsFromEnv: [{USERNAME_VAR}, {PASSWORD_VAR}]\n"
    )


def create_consumer(path: Path, url: str) -> None:
    path.mkdir(parents=True, exist_ok=False)
    (path / "settings.gradle.kts").write_text(consumer_settings(), encoding="utf-8")
    (path / "mod.yaml").write_text(consumer_yaml(url), encoding="utf-8")
    source = path / "src" / "main" / "java" / "dev" / "fabricamper" / "localhttps"
    source.mkdir(parents=True)
    (source / "ProbeEntry.java").write_text(
        "package dev.fabricamper.localhttps;\n"
        "public final class ProbeEntry implements net.fabricmc.api.ModInitializer {\n"
        "    @Override public void onInitialize() {}\n"
        "}\n", encoding="utf-8",
    )
    check(not (path / "build.gradle.kts").exists() and not (path / "build.gradle").exists(),
          "Consumer must be settings-only")
    check(not (path / "src" / "main" / "resources" / "fabric.mod.json").exists(),
          "Consumer must not author fabric.mod.json")


def require_local_marker_repo() -> None:
    base = SOURCE_REPO
    marker = base / "dev" / "fabric-amper" / "settings" / "dev.fabric-amper.settings.gradle.plugin" / "0.1.0"
    plugin = base / "dev" / "fabricamper" / "fabric-amper" / "0.1.0"
    check((marker / "dev.fabric-amper.settings.gradle.plugin-0.1.0.pom").is_file(), "Local Settings plugin marker is missing")
    for suffix in (".pom", ".module", ".jar"):
        check((plugin / ("fabric-amper-0.1.0" + suffix)).is_file(), "Local plugin publication is incomplete")


def gradle_command(gradle: Path, version_only: bool = False) -> list[str]:
    return [str(gradle), "--version"] if version_only else [str(gradle)]


def run_gradle(
    stage: str, path: Path, gradle: Path, env: dict[str, str], args: list[str],
    secrets_to_hide: tuple[str, str], *, expected_success: bool = True,
) -> str:
    check("--offline" in args, "All consumer Gradle actions must be offline")
    command = gradle_command(gradle) + args + ["--console=plain", "--no-daemon"]
    result = subprocess.run(command, cwd=path, env=env,
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                            encoding="utf-8", errors="replace", check=False, timeout=900)
    output = result.stdout
    had_leak = any(value and value in output for value in secrets_to_hide)
    for value in secrets_to_hide:
        if value:
            output = output.replace(value, "[REDACTED]")
    (HERE / "run" / f"{stage}.log").write_text(output, encoding="utf-8")
    check(not had_leak, f"{stage}: Gradle output contained a dummy credential value")
    if expected_success:
        check(result.returncode == 0 and "BUILD SUCCESSFUL" in output,
              f"{stage}: Gradle failed (exit={result.returncode}); see run/{stage}.log")
    else:
        check(result.returncode != 0 and "BUILD FAILED" in output,
              f"{stage}: expected isolated failure; see run/{stage}.log")
    return output


def verify_received(run: Path, server: LocalMavenServer, consumer: Path, events_from: int,
                    secrets_to_hide: tuple[str, str], stage: str) -> dict[str, str]:
    names = {
        "jar": RELATIVE_ARTIFACT_DIR + "/" + RELATIVE_NAME + ".jar",
        "pom": RELATIVE_ARTIFACT_DIR + "/" + RELATIVE_NAME + ".pom",
        "module": RELATIVE_ARTIFACT_DIR + "/" + RELATIVE_NAME + ".module",
        "sources": RELATIVE_ARTIFACT_DIR + "/" + RELATIVE_NAME + "-sources.jar",
    }
    with server.lock:
        seen = list(server.events[events_from:])
        saved = dict(server.blobs)
    uploaded = {str(event["path"]) for event in seen if event["method"] == "PUT"
                and event["status"] == 201 and event["authenticated"] is True}
    check(all(path in uploaded for path in names.values()),
          f"{stage}: authenticated PUT missing JAR/POM/module/sources from this invocation")
    for name, relative in names.items():
        body = saved[relative]
        check(body and not any(token.encode("utf-8") in body for token in secrets_to_hide),
              f"{stage}: empty artifact or credential leaked into {name}")

    local_paths = {
        "jar": consumer / "build" / "libs" / f"{RELATIVE_NAME}.jar",
        "pom": consumer / "build" / "publications" / "mavenJava" / "pom-default.xml",
        "module": consumer / "build" / "publications" / "mavenJava" / "module.json",
        "sources": consumer / "build" / "libs" / f"{ARTIFACT}-{VERSION}-sources.jar",
    }
    hashes: dict[str, str] = {}
    for name, relative in names.items():
        expected_file = local_paths[name]
        check(expected_file.is_file(), f"{stage}: expected local publication output absent: {name}")
        check(saved[relative] == expected_file.read_bytes(),
              f"{stage}: local {name} differs from authenticated HTTPS upload")
        hashes[name] = digest(saved[relative])

    pom = ET.fromstring(saved[names["pom"]])
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    for tag, expected in (("groupId", GROUP), ("artifactId", ARTIFACT), ("version", VERSION)):
        check(pom.findtext("m:" + tag, namespaces=ns) == expected, f"{stage}: published POM {tag} mismatch")
    module = json.loads(saved[names["module"]])
    component = module.get("component", {})
    check(all(component.get(key) == expected for key, expected in
              (("group", GROUP), ("module", ARTIFACT), ("version", VERSION))),
          f"{stage}: Gradle module metadata coordinates differ from {GAV}")
    with zipfile.ZipFile(local_paths["jar"]) as archive:
        check(archive.namelist().count("fabric.mod.json") == 1,
              f"{stage}: uploaded main JAR does not contain exactly one fabric.mod.json")
        mod_json = json.loads(archive.read("fabric.mod.json"))
        check(mod_json.get("id") == "amper_local_https_probe", f"{stage}: incorrect Fabric mod ID")
    return hashes


def verify_tls_challenge(port: int, certificate: Path) -> None:
    context = ssl.create_default_context(cafile=str(certificate))
    context.check_hostname = True
    conn = http.client.HTTPSConnection("127.0.0.1", port, context=context, timeout=10)
    try:
        conn.request("GET", "/maven/" + RELATIVE_ARTIFACT_DIR + "/" + RELATIVE_NAME + ".pom")
        response = conn.getresponse()
        check(response.status == 401 and response.getheader("WWW-Authenticate", "").startswith("Basic "),
              "Local HTTPS server must challenge missing Basic auth with 401")
        response.read()
    finally:
        conn.close()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check-missing-credentials", action="store_true")
    parser.add_argument("--check-http-rejection", action="store_true")
    args = parser.parse_args()
    check(HERE.parent == ROOT / "analysis", "Probe script must live inside the designated analysis folder")
    check(is_allowed_path(RELATIVE_ARTIFACT_DIR + "/" + RELATIVE_NAME + ".jar"),
          "Maven path allowlist rejected the fixed GAV")
    check(is_allowed_path(unquote((RELATIVE_ARTIFACT_DIR + "/" + RELATIVE_NAME + ".pom")
                                  .replace("+", "%2B"))),
          "Percent-encoded + in the Maven version must resolve to the same GAV")
    require_local_marker_repo()
    java, keytool = resolve_java()
    gradle = Path(os.environ.get("FABRIC_AMPER_PROBE_GRADLE", str(DEFAULT_GRADLE))).resolve()
    check(gradle.is_file() and gradle.name.lower() == "gradle.bat"
          and (gradle.parent.parent / "lib" / "gradle-launcher-9.7.1.jar").is_file(),
          "An installed, unmodified Gradle 9.7.1 distribution is required")

    run = HERE / "run"
    check(not run.exists(), "Run directory already exists; this is a one-shot non-overwriting probe")
    run.mkdir()
    certificate, private_key = create_local_certificate(run)
    store = import_test_truststore(run, keytool, certificate)
    print("Local certificate generated with SAN IP 127.0.0.1; JDK truststore imported", flush=True)
    user, password = "local-probe-" + secrets.token_hex(8), secrets.token_urlsafe(32)
    secrets_to_hide = (user, password)

    print("Copying only warmed modules-2 metadata/artifacts into an isolated Gradle home", flush=True)
    home = create_isolated_gradle_home(run)
    env = os.environ.copy()
    for name in list(env):
        if any(part in name.upper() for part in ("TOKEN", "SECRET", "PASSWORD", "CREDENTIAL")):
            env.pop(name)
    for name in ("GRADLE_OPTS", "JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS"):
        env.pop(name, None)
    env["GRADLE_USER_HOME"] = str(home)
    env.pop("GRADLE_RO_DEP_CACHE", None)
    env["JAVA_HOME"] = str(java.parent.parent)
    env["PATH"] = str(java.parent) + os.pathsep + env.get("PATH", "")
    # JVM-wide, per-subprocess truststore. Normal JSSE hostname/SAN checking
    # and CA validation stay enabled; no Gradle/product TLS bypass is set.
    env["JAVA_TOOL_OPTIONS"] = (
        f'-Djavax.net.ssl.trustStore="{store.as_posix()}" '
        "-Djavax.net.ssl.trustStoreType=PKCS12 "
        f"-Djavax.net.ssl.trustStorePassword={TRUSTSTORE_PASSWORD}"
    )

    version = subprocess.run(gradle_command(gradle, version_only=True), cwd=run, env=env,
                             capture_output=True, encoding="utf-8", errors="replace", check=False, timeout=120)
    check(version.returncode == 0 and re.search(r"(?m)^Gradle 9\.7\.1\s*$", version.stdout) is not None,
          "Requested distribution must report exactly Gradle 9.7.1")

    server = LocalMavenServer(f"{user}:{password}")
    check(server.server_address[0] == "127.0.0.1", "Server must bind to IPv4 loopback only")
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.minimum_version = ssl.TLSVersion.TLSv1_2
    context.load_cert_chain(str(certificate), str(private_key))
    server.socket = context.wrap_socket(server.socket, server_side=True)
    thread = threading.Thread(target=server.serve_forever, name="fabric-amper-local-maven", daemon=True)
    thread.start()
    try:
        port = server.server_address[1]
        verify_tls_challenge(port, certificate)
        print(f"HTTPS Basic-auth loopback probe started on 127.0.0.1:{port}", flush=True)
        project = run / "settings-only-consumer"
        create_consumer(project, f"https://127.0.0.1:{port}/maven")
        env[USERNAME_VAR] = user
        env[PASSWORD_VAR] = password

        warm = run_gradle("00-compile-prewarm", project, gradle, env,
                          ["compileJava", "--offline"], secrets_to_hide)
        check(re.search(r"(?m)^> Task :compileJava(?:\s|$)", warm) is not None,
              "Prewarm must schedule the real Java entrypoint compile")
        with server.lock:
            check(not any(event["method"] == "PUT" for event in server.events),
                  "Prewarm compileJava unexpectedly published an artifact")

        command = [PUBLISH_TASK, "--configuration-cache", "--offline"]
        with server.lock:
            first_offset = len(server.events)
        first = run_gradle("01-publish-cc-store", project, gradle, env, command, secrets_to_hide)
        check("Reusing configuration cache" not in first and
              re.search(r"(?i)configuration cache entry stored", first) is not None,
              "First real Maven publish must STORE a configuration cache entry")
        hashes_first = verify_received(run, server, project, first_offset, secrets_to_hide, "first STORE")

        with server.lock:
            second_offset = len(server.events)
        second = run_gradle("02-publish-cc-hit", project, gradle, env, command, secrets_to_hide)
        check("Reusing configuration cache" in second,
              "Second real Maven publish must HIT the existing configuration cache")
        hashes_second = verify_received(run, server, project, second_offset, secrets_to_hide, "second HIT")
        check(hashes_first == hashes_second, "Published file hashes changed across STORE/HIT")
        check(not (project / "build.gradle").exists() and not (project / "build.gradle.kts").exists(),
              "Consumer unexpectedly acquired a build script")

        if args.check_missing_credentials:
            no_creds = env.copy()
            no_creds.pop(USERNAME_VAR, None)
            no_creds.pop(PASSWORD_VAR, None)
            with server.lock:
                before = len(server.events)
            failure = run_gradle("03-missing-credentials", project, gradle, no_creds,
                                 command, secrets_to_hide, expected_success=False)
            check("FabricAmper FA003 publish.maven" in failure,
                  "Missing credentials must fail with the plugin's FA003 diagnostic")
            with server.lock:
                check(not any(event["method"] == "PUT" and event["status"] == 201
                              for event in server.events[before:]),
                      "Missing credentials unexpectedly performed an authenticated PUT")

        if args.check_http_rejection:
            invalid = run / "http-url-rejection-consumer"
            create_consumer(invalid, f"http://127.0.0.1:{port}/maven")
            failure = run_gradle("04-http-url-rejection", invalid, gradle, env,
                                 ["help", "--offline"], secrets_to_hide, expected_success=False)
            check("FA003" in failure and "publish.maven.url" in failure,
                  "Insecure HTTP Maven URLs must be rejected during configuration")

        with server.lock:
            events = list(server.events)
            blobs = dict(server.blobs)
        received = run / "received"
        received.mkdir()
        for relative, content in blobs.items():
            check(is_allowed_path(relative), "Received artifact escaped the fixed GAV allowlist")
            output = received / relative
            output.parent.mkdir(parents=True, exist_ok=True)
            output.write_bytes(content)
        check(not any(event["method"] == "PUT" and not event["authenticated"]
                      and event["status"] == 201 for event in events),
              "A Maven upload was accepted without Basic authentication")
        result = {
            "status": "PASS",
            "localOnly": "127.0.0.1",
            "gradle": "9.7.1",
            "java": "25",
            "mode": "offline; real publish; configuration-cache STORE then HIT",
            "consumer": "settings.gradle.kts + mod.yaml + Java entrypoint; fabric/api",
            "gav": GAV,
            "successfulAuthenticatedPutCounts": [
                sum(e["method"] == "PUT" and e["status"] == 201 and e["authenticated"]
                    for e in events[first_offset:second_offset]),
                sum(e["method"] == "PUT" and e["status"] == 201 and e["authenticated"]
                    for e in events[second_offset:]),
            ],
            "sha256": hashes_first,
            "serverEvents": events,
            "missingCredentialNegative": args.check_missing_credentials,
            "httpUrlNegative": args.check_http_rejection,
            "remotePlatformUploads": False,
        }
        (run / "manifest.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
        print("PASS: real local HTTPS Basic-auth Maven PUTs, offline STORE/HIT, matching JAR/POM/module hashes", flush=True)
        print("Evidence: analysis/local-https-maven-probe-2026-11-13/run/manifest.json", flush=True)
    finally:
        # Preserve exactly what reached the local server even when Gradle
        # refuses the native publication before the HTTP action can start.
        # Never persist authorization headers or test credential values.
        with server.lock:
            observed_events = list(server.events)
        (run / "server-events-on-exit.json").write_text(
            json.dumps({
                "loopbackOnly": True,
                "events": observed_events,
                "authenticatedSuccessfulPutCount": sum(
                    e["method"] == "PUT" and e["status"] == 201 and e["authenticated"]
                    for e in observed_events
                ),
            }, indent=2) + "\n", encoding="utf-8")
        server.shutdown()
        server.server_close()
        thread.join(timeout=10)


if __name__ == "__main__":
    main()
