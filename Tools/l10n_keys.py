"""Lists every English source string passed to L(...) in the app.

Usage: python3 Tools/l10n_keys.py [--check]
With --check, exits non-zero when uz.json or ru.json is missing a key.
"""
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SOURCES = ROOT / "app" / "src" / "main" / "java"
RESOURCES = ROOT / "app" / "src" / "main" / "assets"

# L("text"), L("text", mapOf(...)) and L(one = "text", other = "texts", count = n)
PATTERN = re.compile(r'\bL\(\s*(?:one\s*=\s*)?"((?:[^"\\]|\\.)*)"(?:\s*,\s*other\s*=\s*"((?:[^"\\]|\\.)*)")?')
ESCAPES = {'"': '"', "\\": "\\", "$": "$", "n": "\n", "t": "\t", "'": "'"}


def unescape(text):
    return re.sub(r"\\(.)", lambda m: ESCAPES.get(m.group(1), m.group(0)), text)


def keys():
    found = set()
    for path in SOURCES.rglob("*.kt"):
        for match in PATTERN.finditer(path.read_text()):
            for group in match.groups():
                if group:
                    found.add(unescape(group))
    return sorted(found)


def main():
    all_keys = keys()
    if "--check" not in sys.argv:
        print(json.dumps(all_keys, ensure_ascii=False, indent=1))
        return 0
    failed = False
    for language in ("uz", "ru"):
        table = json.loads((RESOURCES / f"{language}.json").read_text())
        missing = [key for key in all_keys if key not in table]
        unused = [key for key in table if key not in all_keys]
        for key in missing:
            print(f"{language}: missing {key!r}")
        for key in unused:
            print(f"{language}: unused {key!r}")
        failed = failed or bool(missing)
    print(f"{len(all_keys)} keys checked")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
