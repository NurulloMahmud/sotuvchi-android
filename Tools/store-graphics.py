#!/usr/bin/env python3
"""Draws the Play Store icon (512 x 512) and the feature graphics (1024 x 500, en/ru/uz) into
store/graphics/. Cream canvas, white card with a hairline border, brand blue only for the AI
moment, as in the app. Texts come from the app's own translation files; the order is sample data.
Needs Pillow and the Arial fonts of macOS. Run: python3 Tools/store-graphics.py
"""
import json
import os

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "store/graphics")
FONTS = "/System/Library/Fonts/Supplemental/"
CANVAS, INK, SUBTLE, BORDER, BLUE = "#F5F1EC", "#111111", "#6B6560", "#DDD7CF", "#2563EB"
TAGLINES = {"en": "AI seller for Instagram shops", "ru": "ИИ-продавец для Instagram", "uz": "Instagram uchun AI sotuvchi"}
S = 2  # draw at 2x, then scale down for smooth edges


def font(bold: bool, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(FONTS + ("Arial Bold.ttf" if bold else "Arial.ttf"), size * S)


def strings(lang: str) -> dict[str, str]:
    if lang == "en":
        return {"Approve": "Approve", "Receipt sent": "Receipt sent", "Read by AI": "Read by AI", "Matches the order total": "Matches the order total", "Order": "Order"}
    with open(os.path.join(ROOT, f"app/src/main/assets/{lang}.json"), encoding="utf-8") as f:
        data = json.load(f)
    return {k: data[k] for k in ("Approve", "Receipt sent", "Read by AI", "Matches the order total", "Order")}


def box(d: ImageDraw.ImageDraw, xy, radius, fill, outline=None):
    x0, y0, x1, y1 = [v * S for v in xy]
    d.rounded_rectangle((x0, y0, x1, y1), radius=radius * S, fill=fill, outline=outline, width=S if outline else 0)


def text(d, xy, value, f, fill, anchor="la"):
    d.text((xy[0] * S, xy[1] * S), value, font=f, fill=fill, anchor=anchor)


def feature(lang: str) -> None:
    t = strings(lang)
    img = Image.new("RGB", (1024 * S, 500 * S), CANVAS)
    d = ImageDraw.Draw(img)

    # Logo tile, wordmark and the one line.
    logo = Image.open(os.path.join(OUT, "icon-512.png")).convert("RGB").resize((116 * S, 116 * S), Image.LANCZOS)
    mask = Image.new("L", logo.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, logo.size[0], logo.size[1]), radius=26 * S, fill=255)
    img.paste(logo, (64 * S, 120 * S), mask)
    text(d, (64, 262), "Sotuvchi", font(True, 64), INK)
    text(d, (66, 346), TAGLINES[lang], font(False, 30), SUBTLE)

    # An order card, as it looks in the app.
    x0, y0, x1, y1 = 560, 70, 960, 430
    box(d, (x0, y0, x1, y1), 22, "#FFFFFF", BORDER)
    px = x0 + 28
    pill = font(True, 15)
    w = d.textlength(t["Receipt sent"], font=pill) / S + 28
    box(d, (px, y0 + 28, px + w, y0 + 58), 15, "#FFE9E0")
    text(d, (px + 14, y0 + 43), t["Receipt sent"], pill, "#B83B00", "lm")
    text(d, (px, y0 + 76), f'{t["Order"]} #1048', font(False, 20), SUBTLE)
    text(d, (px, y0 + 104), "620 000 UZS", font(True, 42), INK)
    # The AI moment is the only blue.
    box(d, (px, y0 + 176, x1 - 28, y0 + 244), 14, "#F2F0EC")
    text(d, (px + 16, y0 + 196), t["Read by AI"], font(True, 16), BLUE)
    text(d, (px + 16, y0 + 222), t["Matches the order total"], font(False, 16), "#1E7F4F")
    box(d, (px, y0 + 270, x1 - 28, y0 + 326), 14, INK)
    text(d, ((px + x1 - 28) / 2, y0 + 298), t["Approve"], font(True, 22), "#FFFFFF", "mm")

    img.resize((1024, 500), Image.LANCZOS).save(os.path.join(OUT, f"feature-graphic-{lang}.png"), optimize=True)


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    src = Image.open(os.path.join(ROOT, "../sotuvchi-icon-512.png")).convert("RGBA")
    bg = Image.new("RGBA", src.size, (37, 99, 235, 255))
    bg.alpha_composite(src)
    bg.convert("RGB").save(os.path.join(OUT, "icon-512.png"), optimize=True)
    for lang in TAGLINES:
        feature(lang)
    print("wrote", sorted(os.listdir(OUT)))
