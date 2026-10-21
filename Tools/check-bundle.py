#!/usr/bin/env python3
"""Checks the release bundle after `make bundle`: signed with the upload key,
no debug-only code, version, permissions and 16 KB native library alignment.

Uses jarsigner (Android Studio's JDK) and the SDK's bundletool-free zip checks,
so it needs nothing beyond the Android Studio install.
"""
import os
import re
import struct
import subprocess
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
AAB = os.path.join(ROOT, "app/build/outputs/bundle/release/app-release.aab")
JAVA_HOME = os.environ.get("JAVA_HOME", "/Applications/Android Studio.app/Contents/jbr/Contents/Home")
failures: list[str] = []


def check(ok: bool, message: str) -> None:
    print(("ok    " if ok else "FAIL  ") + message)
    if not ok:
        failures.append(message)


if not os.path.exists(AAB):
    sys.exit(f"No bundle at {AAB}. Run `make bundle`.")

out = subprocess.run(
    [os.path.join(JAVA_HOME, "bin/jarsigner"), "-verify", "-verbose:summary", "-certs", AAB],
    capture_output=True,
    text=True,
).stdout
check("jar verified" in out, "jarsigner: bundle signature verifies")
check("CN=Android Debug" not in out, "signed with the upload key, not the debug key")
check("CN=Sotuvchi" in out, "signer is the Sotuvchi upload certificate")

with zipfile.ZipFile(AAB) as z:
    names = z.namelist()
    manifest = z.read("base/manifest/AndroidManifest.xml")
    dex = [n for n in names if n.startswith("base/dex/") and n.endswith(".dex")]
    text = b"".join(z.read(n) for n in dex)
    check(b"androidx/compose/ui/tooling" not in text, "no Compose tooling classes in the dex")
    # DEBUG_OVERRIDES is a compile-time false in release, so R8 drops the override branch.
    check(b"apiURL" not in text, "the apiURL override code is gone from the release dex")
    check(b"hideBanner" not in text, "the banner switch is gone from the release dex")
    check(b"TEST BUILD" not in text, "the server banner is gone from the release dex")
    check(b"debuggable" not in manifest, "the manifest is not debuggable")
    check(b"1.0.0" in manifest, "versionName 1.0.0")
    merged = os.path.join(ROOT, "app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml")
    if os.path.exists(merged):
        requested = set(re.findall(r'<uses-permission[^>]*android:name="([^"]+)"', open(merged).read()))
        # The second one is the app's own signature permission that androidx.core adds for receivers.
        expected = {"android.permission.INTERNET", "ai.sotuvchi.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"}
        check(requested == expected, f"only the INTERNET permission is requested: {sorted(requested)}")
    so = [n for n in names if n.endswith(".so")]
    print(f"      native libraries: {len(so)}")
    for n in so:
        data = z.read(n)
        # Play needs every LOAD segment aligned to 16 KB (0x4000) or more.
        if data[4] == 2:
            phoff = struct.unpack_from("<Q", data, 0x20)[0]
            entsize, count = struct.unpack_from("<HH", data, 0x36)
            align_at = 48
            fmt = "<Q"
        else:
            phoff = struct.unpack_from("<I", data, 0x1C)[0]
            entsize, count = struct.unpack_from("<HH", data, 0x2A)
            align_at = 28
            fmt = "<I"
        aligns = [
            struct.unpack_from(fmt, data, phoff + k * entsize + align_at)[0]
            for k in range(count)
            if struct.unpack_from("<I", data, phoff + k * entsize)[0] == 1
        ]
        check(all(a >= 16384 for a in aligns), f"{n} is 16 KB aligned")

sys.exit(1 if failures else 0)
