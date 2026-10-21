#!/usr/bin/env python3
"""Tries the shrunk (R8) app at full strength, by hand, against the mock.

`make ui-test-qa` has to keep a few libraries whole so the test APK can run. This builds the
QA app without those keep rules (-PnoQaKeeps), signs in through the mock and opens every tab
and a few screens with plain adb taps, then fails on any crash, serialization error or error
banner in the log. Needs a running emulator; the mock is started here if it is not running.
"""
import os
import re
import subprocess
import sys
import time
import urllib.request
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ADB = os.environ.get("ADB", os.path.expanduser("~/Library/Android/sdk/platform-tools/adb"))
APP = "ai.sotuvchi.app"
MOCK = "http://localhost:8787"
EMAIL = "owner@zebo.uz"


def adb(*args: str) -> str:
    return subprocess.run([ADB, *args], capture_output=True, text=True, check=False).stdout


def screen() -> list[ET.Element]:
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    xml = adb("exec-out", "cat", "/sdcard/ui.xml")
    return list(ET.fromstring(xml).iter("node")) if xml.strip().startswith("<") else []


def label(node: ET.Element) -> str:
    return node.get("text") or node.get("content-desc") or ""


def find(text: str) -> ET.Element | None:
    for node in screen():
        if text.lower() in label(node).lower():
            return node
    return None


def wait_for(text: str, timeout: float = 15) -> ET.Element:
    deadline = time.time() + timeout
    while time.time() < deadline:
        node = find(text)
        if node is not None:
            return node
        time.sleep(0.7)
    sys.exit(f"FAIL  never saw {text!r} on screen")


def tap(node: ET.Element) -> None:
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))


def open_tab(text: str) -> None:
    tap(wait_for(text))
    time.sleep(1.5)


def step(text: str) -> None:
    print("ok   ", text)


print("Building the QA app without test keep rules...")
subprocess.run(["./gradlew", "--console=plain", "-PnoQaKeeps", ":app:assembleQa"], cwd=ROOT, check=True, stdout=subprocess.DEVNULL)
adb("install", "-r", os.path.join(ROOT, "app/build/outputs/apk/qa/app-qa.apk"))

mock = None
try:
    urllib.request.urlopen(MOCK + "/", timeout=2)
except Exception:
    os.makedirs(os.path.join(ROOT, "Tools/out"), exist_ok=True)
    mock = subprocess.Popen(
        [sys.executable, "-u", os.path.join(ROOT, "Tools/mock-server/mock_server.py")],
        stdout=open(os.path.join(ROOT, "Tools/out/mock-server.log"), "w"),
        stderr=subprocess.STDOUT,
    )
    time.sleep(1.5)
adb("reverse", "tcp:8787", "tcp:8787")
urllib.request.urlopen(urllib.request.Request(MOCK + "/mock/reset", data=b"{}", headers={"Content-Type": "application/json"}))

try:
    adb("logcat", "-c")
    adb("shell", "am", "force-stop", APP)
    adb("shell", "pm", "clear", APP)
    adb("shell", "am", "start", "-n", f"{APP}/.MainActivity", "--ez", "uiTesting", "true", "--es", "apiURL", MOCK + "/api", "--es", "language", "en")

    wait_for("Send code", 30)
    field = next(n for n in screen() if n.get("class") == "android.widget.EditText")
    tap(field)
    adb("shell", "input", "text", EMAIL)
    adb("shell", "input", "keyevent", "KEYCODE_BACK")  # close the keyboard
    tap(wait_for("Send code"))
    step("code requested")
    code = None
    for _ in range(20):
        try:
            body = urllib.request.urlopen(f"{MOCK}/mock/code?email={EMAIL}", timeout=2).read().decode()
            code = re.search(r'"code":\s*"(\d{6})"', body).group(1)
            break
        except Exception:
            time.sleep(0.5)
    assert code, "the mock sent no code"
    time.sleep(1)
    tap(next(n for n in screen() if n.get("class") == "android.widget.EditText"))
    adb("shell", "input", "text", code)
    wait_for("Home", 20)
    step("signed in")

    wait_for("Approved sales", 20)
    step("home: dashboard decoded")
    open_tab("Orders")
    wait_for("Aziza Karimova")
    step("orders: list decoded")
    tap(wait_for("Aziza Karimova"))
    wait_for("Approve")
    step("order detail with receipt")
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    open_tab("Chats")
    wait_for("Jasur Toshmatov")
    step("chats: list decoded")
    tap(wait_for("Aziza Karimova"))
    wait_for("Chekingiz qabul qilindi")
    step("chat thread")
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    open_tab("Products")
    wait_for("Adidas Samba OG")
    step("products: list decoded")
    open_tab("More")
    wait_for("Zebo Karimova")
    step("more: account and stores")

    log = adb("logcat", "-d")
    bad = [l for l in log.splitlines() if (APP in l or "AndroidRuntime" in l) and re.search(r"FATAL|SerializationException|ClassNotFound|NoSuchMethod|MissingFieldException", l)]
    if bad:
        print("\n".join(bad[:10]))
        sys.exit("FAIL  errors in the log")
    step("no crashes or serialization errors in the log")
finally:
    if mock:
        mock.terminate()
