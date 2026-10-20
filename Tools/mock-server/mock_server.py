"""Local stand-in for the Sotuvchi API, for developing and testing the app
without a real backend or inbox.

It follows the shapes in mobile-api.md and keeps state in memory. Sign-in uses
the mobile email-code endpoints: requesting a code prints it here instead of
emailing it. Type that code into the app. owner@zebo.uz is an existing
account; any other email signs in as a new one. Deleting the account ends the
session and forgets every email, so the next sign-in is a new account.

Usage:
    python3 Tools/mock-server/mock_server.py [--port 8787] [--fail-delete]

--fail-delete makes DELETE /api/account answer 500, to see the retry path.

UI tests drive the mock through /mock/* (no session needed):
    POST /mock/reset        {"failDelete": bool, "soloOwner": bool}
                            ends the session and forgets codes and new accounts
    POST /mock/fail-delete  {"fail": bool}
    GET  /mock/code?email=  the last code sent to that email
    GET  /mock/handoffs     web sign-in links made, and whether a browser used them

Point a debug build on the emulator at it:
    adb reverse tcp:8787 tcp:8787
    adb shell am start -n ai.sotuvchi.app/.MainActivity --es apiURL http://localhost:8787/api

Standard library only.
"""

import argparse
import json
import random
import re
import struct
import time
import uuid
import zlib
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from html import escape
from urllib.parse import parse_qs, quote, urlparse

SESSION = "mock-session-token-0000000000000000"
CODES = {}
# One-time "open on the web" links: code -> {"path", "expires", "redeemed"}.
HANDOFFS = {}
KNOWN_EMAILS = {"owner@zebo.uz"}
# The single mock session ends when the account is deleted.
SESSION_STATE = {"revoked": False}
# failDelete: DELETE /api/account answers 500. soloOwner: the owner is the
# only member, so deleting the account deletes the workspace.
SETTINGS = {"failDelete": False, "soloOwner": False}
NOW = datetime.now(timezone.utc)


def iso(delta_minutes=0):
    return (NOW + timedelta(minutes=delta_minutes)).strftime("%Y-%m-%dT%H:%M:%S.000Z")


def new_id(prefix):
    return f"{prefix}{uuid.uuid4().hex[:10]}"


# ---------------------------------------------------------------- images

def png(width, height, painter):
    """Encodes an RGB image. painter(x, y) returns (r, g, b)."""
    rows = bytearray()
    for y in range(height):
        rows.append(0)
        for x in range(width):
            rows.extend(painter(x, y))
    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
    header = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(bytes(rows), 9)) + chunk(b"IEND", b"")


def product_png(color):
    r, g, b = color
    def paint(x, y):
        cx, cy = x - 160, y - 170
        if (cx * cx) / 1.8 + cy * cy < 90 * 90:
            return (max(r - 40, 0), max(g - 40, 0), max(b - 40, 0))
        return (r, g, b)
    return png(320, 320, paint)


def receipt_png():
    def paint(x, y):
        if 20 < x < 380 and 20 < y < 580:
            for top, width in ((60, 220), (110, 300), (150, 260), (230, 320), (270, 180), (420, 300), (460, 240)):
                if top < y < top + 14 and 50 < x < 50 + width:
                    return (60, 60, 60)
            if 330 < y < 380 and 50 < x < 330:
                return (37, 99, 235)
            return (255, 255, 255)
        return (225, 228, 232)
    return png(400, 600, paint)


IMAGES = {
    "receipt-1": receipt_png(),
    "receipt-2": receipt_png(),
    "img-airmax": product_png((214, 222, 235)),
    "img-airmax-2": product_png((235, 214, 214)),
    "img-samba": product_png((232, 226, 214)),
    "img-bag": product_png((210, 196, 180)),
    "img-dress": product_png((214, 235, 222)),
}

# ---------------------------------------------------------------- data

BASE = "http://localhost:8787"
STORE = {
    "id": "store1", "workspaceId": "ws1", "instagramAccountId": "ig1", "name": "Zebo Shoes",
    "description": "Original sneakers and bags in Tashkent. Delivery in one day.",
    "cardNumber": "8600123412341234", "cardHolder": "ZEBO KARIMOVA", "cardType": "UZCARD",
    "pickupAddress": "Tashkent, Chilonzor 9, Zebo Shoes", "holdMinutes": 30, "aiEnabled": True,
    "aiInstructions": "Be warm and short. Delivery in Tashkent is free from 500 000 so'm.",
    "isActive": True, "notifyEmail": True, "notifyLocale": "uz", "catalogSource": "MANUAL",
    "telegramChatId": None, "createdAt": iso(-60 * 24 * 40), "updatedAt": iso(-60), "telegramConfigured": False,
    "integration": {"connected": False, "warehouseName": None, "organizationName": None},
    "instagramAccount": {"id": "ig1", "username": "zebo.shoes"}, "cardMasked": False,
}

CATEGORIES = [
    {"id": "cat-shoes", "name": "Sneakers"},
    {"id": "cat-bags", "name": "Bags"},
    {"id": "cat-dress", "name": "Clothes"},
]


def image(img_id, position):
    return {"id": img_id, "position": position, "url": f"{BASE}/mock-images/{img_id}"}


PRODUCTS = [
    {"id": "p1", "storeId": "store1", "name": "Nike Air Max 90", "description": "Original, leather upper.", "price": 650000,
     "minPrice": 590000, "stock": 0, "isActive": True, "position": 0, "categoryId": "cat-shoes",
     "variants": [
         {"id": "v1", "productId": "p1", "label": "White / 41", "size": "41", "color": "White", "colorHex": "#f2f2f2", "imageId": "img-airmax", "stock": 3, "priceOverride": None, "position": 0},
         {"id": "v2", "productId": "p1", "label": "White / 42", "size": "42", "color": "White", "colorHex": "#f2f2f2", "imageId": "img-airmax", "stock": 1, "priceOverride": None, "position": 1},
         {"id": "v3", "productId": "p1", "label": "Red / 42", "size": "42", "color": "Red", "colorHex": "#d33a3a", "imageId": "img-airmax-2", "stock": 2, "priceOverride": 690000, "position": 2},
     ],
     "images": [image("img-airmax", 0), image("img-airmax-2", 1)]},
    {"id": "p2", "storeId": "store1", "name": "Adidas Samba OG", "description": "Classic suede.", "price": 720000,
     "minPrice": None, "stock": 6, "isActive": True, "position": 1, "categoryId": "cat-shoes", "variants": [],
     "images": [image("img-samba", 0)]},
    {"id": "p3", "storeId": "store1", "name": "Leather crossbody bag", "description": "Genuine leather, three colors.", "price": 380000,
     "minPrice": 340000, "stock": 2, "isActive": True, "position": 2, "categoryId": "cat-bags", "variants": [],
     "images": [image("img-bag", 0)]},
    {"id": "p4", "storeId": "store1", "name": "Summer linen dress", "description": None, "price": 290000,
     "minPrice": None, "stock": 0, "isActive": False, "position": 3, "categoryId": "cat-dress", "variants": [],
     "images": [image("img-dress", 0)]},
]


def order(number, status, customer, phone, items, minutes_ago, proofs=(), delivery="DELIVERY", sync_error=None, reason=None):
    total = sum(i["unitPrice"] * i["quantity"] for i in items)
    oid = f"o{number}"
    return {
        "id": oid, "number": number, "storeId": "store1", "conversationId": f"c-{customer.split()[0].lower()}",
        "status": status, "customerName": customer, "customerPhone": phone, "deliveryType": delivery,
        "address": "Tashkent, Yunusobod 4, house 12, apt 30" if delivery == "DELIVERY" else None,
        "total": total, "holdExpiresAt": iso(-minutes_ago + 30), "decidedAt": iso(-minutes_ago + 20) if status in ("APPROVED", "REJECTED") else None,
        "rejectReason": reason, "externalOrderId": None, "externalDemandId": None, "externalSyncedAt": None,
        "externalSyncError": sync_error, "createdAt": iso(-minutes_ago), "updatedAt": iso(-minutes_ago + 5),
        "items": [dict(i, id=new_id("i")) for i in items],
        "proofs": [{"id": new_id("pr"), "orderId": oid, "contentType": "image/png", "extractedAmount": amount,
                    "extractedNote": "Uzcard transfer", "createdAt": iso(-minutes_ago + 4), "url": f"{BASE}/mock-images/{img}"}
                   for img, amount in proofs],
        "store": {"id": "store1", "name": "Zebo Shoes"},
        "conversation": {"id": f"c-{customer.split()[0].lower()}", "customerName": customer},
    }


def item(name, variant, price, qty, list_price=None):
    return {"productId": "p1", "variantId": None, "productName": name, "variantLabel": variant,
            "unitPrice": price, "listPrice": list_price, "quantity": qty}


ORDERS = [
    order(1048, "PROOF_SUBMITTED", "Aziza Karimova", "+998901234567", [item("Nike Air Max 90", "White / 42", 620000, 1, 650000)], 12, [("receipt-1", 620000)]),
    order(1047, "PROOF_SUBMITTED", "Jasur Toshmatov", "+998935551122", [item("Adidas Samba OG", None, 720000, 1), item("Leather crossbody bag", None, 380000, 1)], 45, [("receipt-2", 1000000)]),
    order(1046, "AWAITING_PAYMENT", "Malika Yusupova", "+998977001020", [item("Leather crossbody bag", None, 380000, 1)], 8, delivery="PICKUP"),
    order(1045, "APPROVED", "Dilnoza Rahimova", "+998909998877", [item("Nike Air Max 90", "Red / 42", 690000, 1)], 60 * 5, [("receipt-1", 690000)]),
    order(1044, "APPROVED", "Sardor Aliyev", "+998946667788", [item("Adidas Samba OG", None, 700000, 2, 720000)], 60 * 26, [("receipt-2", 1400000)], sync_error=None),
    order(1043, "REJECTED", "Kamola Ergasheva", "+998933332211", [item("Summer linen dress", None, 290000, 1)], 60 * 30, [("receipt-1", 150000)], reason="The amount does not match the order"),
    order(1042, "EXPIRED", "Bekzod Nurmatov", "+998901112233", [item("Leather crossbody bag", None, 380000, 1)], 60 * 50),
]


def msg(role, text, minutes_ago, attachments=()):
    return {"id": new_id("m"), "conversationId": "", "role": role, "text": text, "igMessageId": None,
            "createdAt": iso(-minutes_ago), "attachments": list(attachments)}


CONVERSATIONS = {
    "c-aziza": {"customerName": "Aziza Karimova", "aiPaused": False, "needsAttention": False, "attentionReason": None, "last": 14,
                "messages": [msg("CUSTOMER", "Assalomu alaykum! Air Max 90 oq rangli 42 razmer bormi?", 40),
                             msg("AI", "Va alaykum assalom, Aziza! Ha, oq 42 razmer bor. Narxi 650 000 so'm. Buyurtma beraymi?", 39),
                             msg("CUSTOMER", "Biroz arzonroq bo'lmaydimi? 600 ga olsam", 30),
                             msg("AI", "Siz uchun 620 000 so'm qila olaman. Yetkazib berish bepul. Rozimisiz?", 29),
                             msg("CUSTOMER", "Mayli, rozi. Kartaga tashlayman", 20),
                             msg("AI", "Rahmat! Buyurtma #1048. To'lovni 8600 1234 1234 1234 (ZEBO KARIMOVA) kartasiga o'tkazib, chekni shu yerga yuboring.", 19),
                             msg("CUSTOMER", "", 14, [{"id": "att1", "messageId": "", "position": 0, "kind": "IMAGE", "contentType": "image/png", "sizeBytes": 2000, "createdAt": iso(-14), "url": f"{BASE}/mock-images/receipt-1"}]),
                             msg("AI", "Chekingiz qabul qilindi. Do'kon egasi tasdiqlagach, buyurtmangiz yo'lga chiqadi.", 13)]},
    "c-jasur": {"customerName": "Jasur Toshmatov", "aiPaused": False, "needsAttention": True, "attentionReason": "Customer asked for a human", "last": 6,
                "messages": [msg("CUSTOMER", "Samba va sumkani birga olsam chegirma bormi?", 70),
                             msg("AI", "Ikkalasi 1 100 000 so'm. Sizga 1 000 000 so'm qilib bera olaman.", 69),
                             msg("CUSTOMER", "To'ladim, chek yubordim. Operator bilan gaplashsam bo'ladimi?", 6)]},
    "c-malika": {"customerName": "Malika Yusupova", "aiPaused": True, "needsAttention": False, "attentionReason": None, "last": 9,
                 "messages": [msg("CUSTOMER", "Sumkani o'zim olib ketsam bo'ladimi?", 12),
                              msg("OWNER", "Albatta! Chilonzor 9 dagi do'konimizga kelishingiz mumkin, 10:00 dan 20:00 gacha.", 10),
                              msg("CUSTOMER", "Rahmat, bugun kechqurun boraman", 9)]},
    "c-dilnoza": {"customerName": "Dilnoza Rahimova", "aiPaused": False, "needsAttention": False, "attentionReason": None, "last": 60 * 5,
                  "messages": [msg("CUSTOMER", "Qizil 42 razmer qachon yetib keladi?", 60 * 5),
                               msg("AI", "Buyurtmangiz tasdiqlandi, ertaga kechgacha yetkazamiz.", 60 * 5 - 1)]},
    "c-sardor": {"customerName": "Sardor Aliyev", "aiPaused": False, "needsAttention": False, "attentionReason": None, "last": 60 * 30,
                 "messages": [msg("CUSTOMER", "Rahmat, krossovkalar zo'r ekan!", 60 * 30)]},
}

NOTIFICATIONS = [
    {"id": "n1", "type": "PAYMENT_PROOF", "data": {"orderId": "o1048", "number": 1048, "customer": "Aziza Karimova", "total": 620000, "receiptAmount": 620000}, "storeName": "Zebo Shoes", "read": False, "createdAt": iso(-12)},
    {"id": "n2", "type": "HANDOFF", "data": {"conversationId": "c-jasur", "customer": "Jasur Toshmatov", "reason": "Customer asked for a human"}, "storeName": "Zebo Shoes", "read": False, "createdAt": iso(-6)},
    {"id": "n3", "type": "PAYMENT_PROOF", "data": {"orderId": "o1047", "number": 1047, "customer": "Jasur Toshmatov", "total": 1100000, "receiptAmount": 1000000}, "storeName": "Zebo Shoes", "read": False, "createdAt": iso(-44)},
    {"id": "n4", "type": "NEW_ORDER", "data": {"orderId": "o1046", "number": 1046, "customer": "Malika Yusupova", "total": 380000}, "storeName": "Zebo Shoes", "read": True, "createdAt": iso(-8)},
    {"id": "n5", "type": "CUSTOMER_MESSAGE", "data": {"conversationId": "c-malika", "customer": "Malika Yusupova", "text": "Rahmat, bugun kechqurun boraman"}, "storeName": "Zebo Shoes", "read": True, "createdAt": iso(-9)},
]

TEAM = {
    "currentUserRole": "OWNER",
    "members": [
        {"id": "mem1", "role": "OWNER", "createdAt": iso(-60 * 24 * 40), "user": {"id": "u1", "email": "owner@zebo.uz", "name": "Zebo Karimova"}},
        {"id": "mem2", "role": "ADMIN", "createdAt": iso(-60 * 24 * 20), "user": {"id": "u2", "email": "bahrom@zebo.uz", "name": "Bahrom"}},
        {"id": "mem3", "role": "MEMBER", "createdAt": iso(-60 * 24 * 3), "user": {"id": "u3", "email": "operator@zebo.uz", "name": None}},
    ],
    "invitations": [
        {"id": "inv1", "email": "kassa@zebo.uz", "role": "MEMBER", "token": "tok1", "expiresAt": iso(60 * 24 * 6), "createdAt": iso(-60), "inviteUrl": f"{BASE}/invite/tok1"},
    ],
}

CAMPAIGNS = [
    {"id": "a1", "workspaceId": "ws1", "instagramAccountId": "ig1", "name": "Air Max price DM", "goal": None, "postId": "post1", "postUrl": "https://instagram.com/p/x",
     "pendingNextReel": False, "matchAnyPost": False, "keywords": ["narx", "price", "цена"], "matchAnyWord": False, "wholeWordMatch": False,
     "dmTriggerEnabled": True, "dmMessage": "Salom {username}! Air Max 90 narxi 650 000 so'm. Buyurtma uchun pastdagi tugmani bosing.",
     "isActive": True, "createdAt": iso(-60 * 24 * 10), "updatedAt": iso(-60), "instagramAccount": {"username": "zebo.shoes", "instagramId": "1"},
     "_count": {"dmLogs": 84}, "trackedLinks": [], "reportUrl": None,
     "analytics": {"sent": 84, "skipped": 6, "failed": 1, "clicks": 31, "topKeywords": [{"keyword": "narx", "count": 61}], "ctr": 36.9}},
    {"id": "a2", "workspaceId": "ws1", "instagramAccountId": "ig1", "name": "Any post: catalog link", "goal": None, "postId": None, "postUrl": None,
     "pendingNextReel": False, "matchAnyPost": True, "keywords": [], "matchAnyWord": True, "wholeWordMatch": False,
     "dmTriggerEnabled": False, "dmMessage": "Katalogimiz: zebo.uz", "isActive": False, "createdAt": iso(-60 * 24 * 30), "updatedAt": iso(-60 * 24),
     "instagramAccount": {"username": "zebo.shoes", "instagramId": "1"}, "_count": {"dmLogs": 12}, "trackedLinks": [], "reportUrl": None,
     "analytics": {"sent": 12, "skipped": 2, "failed": 0, "clicks": 3, "topKeywords": [], "ctr": 25.0}},
]

LOGS = [
    {"id": f"log{i}", "workspaceId": "ws1", "automationId": "a1", "instagramAccountId": "ig1", "commenterId": str(i),
     "commenterName": name, "commentText": text, "commentId": str(i), "matchedKeyword": "narx", "status": status,
     "attempts": 1, "dmSentAt": iso(-i * 17), "errorMessage": "User blocked messages" if status == "FAILED" else None,
     "dmDeliveryUnconfirmed": False, "publicReplyDeliveryUnconfirmed": False, "publicReplySentAt": None, "publicReplyError": None,
     "createdAt": iso(-i * 17), "updatedAt": iso(-i * 17), "automation": {"name": "Air Max price DM", "keywords": ["narx"]},
     "instagramAccount": {"username": "zebo.shoes"}}
    for i, (name, text, status) in enumerate([
        ("aziza_k", "narx qancha?", "SENT"), ("jasur.t", "narxi?", "SENT"), ("malika_y", "narx", "SKIPPED_DEDUP"),
        ("sardor", "price pls", "SENT"), ("kamola", "narx", "FAILED"), ("bek", "narxi qancha", "SENT"),
    ], start=1)
]

# ---------------------------------------------------------------- helpers

OPEN = {"AWAITING_PAYMENT", "PROOF_SUBMITTED"}


def summary(orders):
    approved = [o for o in orders if o["status"] == "APPROVED"]
    return {"orders": len(orders), "approvedCount": len(approved), "approvedSum": sum(o["total"] for o in approved),
            "openCount": sum(1 for o in orders if o["status"] in OPEN),
            "rejectedCount": sum(1 for o in orders if o["status"] == "REJECTED")}


def conversation_row(cid, convo):
    last = convo["messages"][-1] if convo["messages"] else None
    last_customer = next((m for m in reversed(convo["messages"]) if m["role"] == "CUSTOMER"), None)
    return {"id": cid, "storeId": "store1", "customerId": cid, "customerName": convo["customerName"],
            "aiPaused": convo["aiPaused"], "needsAttention": convo["needsAttention"], "attentionReason": convo["attentionReason"],
            "lastCustomerMessageAt": last_customer["createdAt"] if last_customer else None,
            "createdAt": iso(-60 * 24 * 3), "updatedAt": last["createdAt"] if last else iso(-60),
            "store": {"id": "store1", "name": "Zebo Shoes"},
            "lastMessage": {"role": last["role"], "text": last["text"], "createdAt": last["createdAt"]} if last else None}


def product_view(p):
    category = next((c for c in CATEGORIES if c["id"] == p.get("categoryId")), None)
    return dict(p, createdAt=iso(-60 * 24 * 5), updatedAt=iso(-60), category={"id": category["id"], "name": category["name"]} if category else None)


def badges():
    return {"ordersToReview": sum(1 for o in ORDERS if o["status"] == "PROOF_SUBMITTED"),
            "chatsNeedingYou": sum(1 for c in CONVERSATIONS.values() if c["needsAttention"])}


def dashboard(range_preset):
    s = summary(ORDERS)
    days = []
    for back in range(16, -1, -1):
        day = (NOW - timedelta(days=back)).strftime("%Y-%m-%d")
        revenue = [0, 650000, 0, 1340000, 720000, 0, 380000, 1100000, 0, 690000, 1440000, 0, 650000, 1020000, 0, 690000, 1400000][16 - back]
        days.append({"date": day, "orders": 1 if revenue else 0, "approved": 1 if revenue else 0, "revenue": revenue})
    return {
        "userName": "Zebo Karimova", "contactsCount": 320,
        "workspace": {"name": "Zebo Shoes", "dmsSentThisPeriod": 96, "planExpiresAt": iso(60 * 24 * 21)},
        "billingEnabled": True,
        "instagramAccounts": [{"id": "ig1", "username": "zebo.shoes", "instagramId": "1784", "name": "Zebo Shoes", "provider": "META", "tokenExpiresAt": iso(60 * 24 * 50), "webhookSubscribed": True}],
        "totalAutomations": len(CAMPAIGNS), "activeAutomations": sum(1 for c in CAMPAIGNS if c["isActive"]),
        "range": {"preset": range_preset, "from": None, "to": None},
        "period": {"dmsSent": 96, "dmsSkipped": 8, "dmsFailed": 1, "clicks": 34, "ctr": 35.4},
        "previous": {"dmsSent": 71, "dmsSkipped": 5, "dmsFailed": 2, "clicks": 22, "ctr": 31.0},
        "sales": {
            "byStatus": [{"status": st, "count": sum(1 for o in ORDERS if o["status"] == st), "sum": sum(o["total"] for o in ORDERS if o["status"] == st)} for st in ("APPROVED", "PROOF_SUBMITTED", "AWAITING_PAYMENT", "REJECTED", "EXPIRED")],
            "orders": 38, "approvedCount": 27, "approvedSum": 12080000 + s["approvedSum"], "openCount": s["openCount"],
            "previous": None if range_preset == "all" else {"orders": 31, "approvedCount": 22, "approvedSum": 9870000, "openCount": 2},
            "daily": days,
            "topProducts": [{"name": "Nike Air Max 90", "quantity": 14, "revenue": 8930000}, {"name": "Adidas Samba OG", "quantity": 6, "revenue": 4260000}, {"name": "Leather crossbody bag", "quantity": 4, "revenue": 1480000}],
        },
        "recentLogs": [],
    }


# ---------------------------------------------------------------- server

class Handler(BaseHTTPRequestHandler):
    server_version = "SotuvchiMock/1.0"

    def log_message(self, fmt, *args):
        print(f"{self.command} {self.path} -> {args[1] if len(args) > 1 else ''}")

    # plumbing

    def body(self):
        length = int(self.headers.get("Content-Length") or 0)
        return self.rfile.read(length) if length else b""

    def json_body(self):
        raw = self.body()
        try:
            return json.loads(raw) if raw else {}
        except ValueError:
            return {}

    def send_json(self, payload, status=200, headers=None):
        data = json.dumps(payload).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        for key, value in (headers or {}).items():
            self.send_header(key, value)
        self.end_headers()
        self.wfile.write(data)

    def send_html(self, title, text, status=200, headers=None):
        data = (f"<!doctype html><meta name=viewport content='width=device-width'>"
                f"<title>{escape(title)}</title><h1>{escape(title)}</h1><p>{escape(text)}</p>").encode()
        self.send_response(status)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        for key, value in (headers or {}).items():
            self.send_header(key, value)
        self.end_headers()
        self.wfile.write(data)

    def ok(self, data=None, **siblings):
        payload = {"success": True}
        if data is not None:
            payload["data"] = data
        payload.update(siblings)
        self.send_json(payload)

    def fail(self, status, message):
        self.send_json({"success": False, "error": message}, status)

    def authed(self):
        if SESSION_STATE["revoked"]:
            return False
        return (self.headers.get("Authorization") or "") == f"Bearer {SESSION}"

    def route(self, method):
        url = urlparse(self.path)
        path, query = url.path, {k: v[0] for k, v in parse_qs(url.query).items()}

        if path.startswith("/mock-images/"):
            data = IMAGES.get(path.rsplit("/", 1)[-1])
            if not data:
                return self.fail(404, "Not found")
            self.send_response(200)
            self.send_header("Content-Type", "image/png")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            return self.wfile.write(data)

        if path.startswith("/mock/"):
            return self.control(method, path, query)
        if path.startswith("/api/mobile/auth/"):
            return self.auth(method, path, query)
        if not path.startswith("/api/"):
            return self.fail(404, "Not found")
        if not self.authed():
            return self.fail(401, "Unauthorized")

        for pattern, handler in ROUTES:
            match = re.fullmatch(pattern, path)
            if match and method in handler:
                return handler[method](self, query, *match.groups())
        return self.fail(404, "Not found")

    def control(self, method, path, query):
        """Test hooks, so UI tests can set the scene and read sign-in codes."""
        if method == "GET" and path == "/mock/handoffs":
            return self.ok([{"path": h["path"], "redeemed": h["redeemed"]} for h in HANDOFFS.values()])
        if method == "GET" and path == "/mock/code":
            entry = CODES.get((query.get("email") or "").strip().lower())
            return self.ok({"code": entry["code"]}) if entry else self.fail(404, "No code sent")
        body = self.json_body()
        if method == "POST" and path == "/mock/reset":
            SESSION_STATE["revoked"] = True
            CODES.clear()
            HANDOFFS.clear()
            KNOWN_EMAILS.clear()
            KNOWN_EMAILS.add("owner@zebo.uz")
            SETTINGS["failDelete"] = body.get("failDelete") is True
            SETTINGS["soloOwner"] = body.get("soloOwner") is True
            return self.ok(dict(SETTINGS))
        if method == "POST" and path == "/mock/fail-delete":
            SETTINGS["failDelete"] = body.get("fail") is True
            return self.ok(dict(SETTINGS))
        return self.fail(404, "Not found")

    def auth(self, method, path, query):
        """Mobile email-code sign-in, as in mobile-api.md section 3.0."""
        if path == "/api/mobile/auth/web" and method == "GET":
            return self.open_handoff(query)
        if method != "POST":
            return self.fail(405, "Method not allowed")
        body = self.json_body()
        email = (body.get("email") or "").strip().lower() if isinstance(body.get("email"), str) else ""
        valid_email = re.fullmatch(r"[^\s@]+@[^\s@]+\.[^\s@]+", email) is not None

        if path == "/api/mobile/auth/request-code":
            if not valid_email:
                return self.fail(400, "Invalid email")
            now = time.time()
            sent = CODES.get(email)
            if sent and now - sent["sentAt"] < 60:
                wait = int(60 - (now - sent["sentAt"])) + 1
                return self.send_json({"success": False, "error": "Wait before requesting another code", "retryAfterSeconds": wait},
                                      429, headers={"Retry-After": str(wait)})
            code = f"{random.randint(0, 999999):06d}"
            CODES[email] = {"code": code, "sentAt": now, "expires": now + 600, "fails": 0}
            print(f"\n  Sign-in code for {email}: {code}\n", flush=True)
            return self.ok({"expiresInSeconds": 600, "resendAfterSeconds": 60})

        if path == "/api/mobile/auth/verify-code":
            code = body.get("code")
            if not valid_email or not (isinstance(code, str) and re.fullmatch(r"\d{6}", code.strip())):
                return self.fail(400, "Email and a 6-digit code are required")
            entry = CODES.get(email)
            if entry and entry["fails"] >= 5:
                return self.fail(429, "Too many wrong codes, request a new code")
            if not entry:
                return self.fail(401, "Wrong code")
            if time.time() > entry["expires"]:
                CODES.pop(email, None)
                return self.fail(401, "Code expired, request a new one")
            if code.strip() != entry["code"]:
                entry["fails"] += 1
                return self.fail(401, "Wrong code")
            CODES.pop(email, None)
            SESSION_STATE["revoked"] = False
            is_new = email not in KNOWN_EMAILS
            KNOWN_EMAILS.add(email)
            return self.ok({"token": SESSION, "expiresAt": iso(60 * 24 * 30), "isNewUser": is_new,
                            "user": {"id": "u-" + email.split("@")[0], "email": email}})

        if path == "/api/mobile/auth/logout":
            return self.ok()
        if path == "/api/mobile/auth/web-handoff":
            if not self.authed():
                return self.fail(401, "Unauthorized")
            page = body.get("path") if isinstance(body.get("path"), str) else ""
            if not page.startswith("/") or page.startswith("//") or "\\" in page:
                page = "/dashboard"
            code = uuid.uuid4().hex + uuid.uuid4().hex
            HANDOFFS[code] = {"path": page, "expires": time.time() + 60, "redeemed": False}
            return self.ok({"url": f"{BASE}/api/mobile/auth/web?code={code}&next={quote(page, safe='')}",
                            "expiresInSeconds": 60})
        return self.fail(404, "Not found")

    def open_handoff(self, query):
        """Stands in for the website: a fresh link signs the browser in once."""
        entry = HANDOFFS.get(query.get("code") or "")
        if not entry or entry["redeemed"] or time.time() > entry["expires"]:
            return self.send_html("Sign in", "This link was already used or has expired. Sign in on the web.")
        entry["redeemed"] = True
        print(f"\n  Browser signed in through the app, page {entry['path']}\n", flush=True)
        return self.send_html("Signed in to the mock website", f"Page: {entry['path']}",
                              headers={"Set-Cookie": "authjs.session-token=mock-web-session; Path=/; HttpOnly",
                                       "Referrer-Policy": "no-referrer", "Cache-Control": "no-store"})

    def do_GET(self):
        self.route("GET")

    def do_POST(self):
        self.route("POST")

    def do_PATCH(self):
        self.route("PATCH")

    def do_DELETE(self):
        self.route("DELETE")


# ---------------------------------------------------------------- handlers

def get_orders(h, q):
    status = q.get("status")
    rows = [o for o in ORDERS if o["status"] != "DRAFT"]
    if q.get("storeId"):
        rows = [o for o in rows if o["storeId"] == q["storeId"]]
    totals = summary(rows)
    if status == "open":
        rows = [o for o in rows if o["status"] in OPEN]
    elif status:
        rows = [o for o in rows if o["status"] == status]
    h.ok(rows, summary=totals, range={"preset": q.get("range", "all"), "from": None, "to": None})


def find_order(oid):
    return next((o for o in ORDERS if o["id"] == oid), None)


def decide(new_status):
    def handler(h, q, oid):
        o = find_order(oid)
        if not o:
            return h.fail(404, "Order not found")
        if o["status"] not in ("AWAITING_PAYMENT", "PROOF_SUBMITTED", "EXPIRED"):
            return h.fail(409, "This order was already decided")
        body = h.json_body()
        o["status"] = new_status
        o["decidedAt"] = iso()
        o["updatedAt"] = iso()
        if new_status == "REJECTED":
            o["rejectReason"] = body.get("reason")
        for n in NOTIFICATIONS:
            if n["data"].get("orderId") == oid:
                n["read"] = True
        h.ok({"notified": True, "notifyError": None, "syncError": None})
    return handler


def cancel_order(h, q, oid):
    o = find_order(oid)
    if not o:
        return h.fail(404, "Order not found")
    if o["status"] != "AWAITING_PAYMENT":
        return h.fail(409, "This order can no longer be cancelled")
    o["status"] = "CANCELLED"
    o["updatedAt"] = iso()
    h.ok()


def sync_order(h, q, oid):
    o = find_order(oid)
    if o:
        o["externalSyncError"] = None
    h.ok()


def list_conversations(h, q):
    rows = [conversation_row(cid, c) for cid, c in CONVERSATIONS.items()]
    rows.sort(key=lambda r: (not r["needsAttention"], r["updatedAt"]), reverse=False)
    attention = [r for r in rows if r["needsAttention"]]
    rest = sorted([r for r in rows if not r["needsAttention"]], key=lambda r: r["updatedAt"], reverse=True)
    h.ok(attention + rest)


def get_conversation(h, q, cid):
    convo = CONVERSATIONS.get(cid)
    if not convo:
        return h.fail(404, "Conversation not found")
    row = conversation_row(cid, convo)
    row.pop("lastMessage")
    row["messages"] = convo["messages"]
    row["orders"] = [{"id": o["id"], "number": o["number"], "status": o["status"], "total": o["total"]} for o in ORDERS if o["conversationId"] == cid]
    h.ok(row)


def patch_conversation(h, q, cid):
    convo = CONVERSATIONS.get(cid)
    if not convo:
        return h.fail(404, "Conversation not found")
    body = h.json_body()
    if "aiPaused" in body:
        convo["aiPaused"] = bool(body["aiPaused"])
        if not convo["aiPaused"]:
            convo["needsAttention"] = False
    if body.get("needsAttention") is False:
        convo["needsAttention"] = False
    h.ok(conversation_row(cid, convo))


def send_message(h, q, cid):
    convo = CONVERSATIONS.get(cid)
    if not convo:
        return h.fail(404, "Conversation not found")
    text = (h.json_body().get("text") or "").strip()
    if not text or len(text) > 950:
        return h.fail(400, "Message must be 1 to 950 characters")
    convo["messages"].append(msg("OWNER", text, 0))
    convo["aiPaused"] = True
    convo["needsAttention"] = False
    h.ok()


def get_notifications(h, q):
    items = sorted(NOTIFICATIONS, key=lambda n: n["createdAt"], reverse=True)[:30]
    h.ok({"unreadCount": sum(1 for n in NOTIFICATIONS if not n["read"]), "badges": badges(), "items": items})


def read_notifications(h, q):
    ids = h.json_body().get("ids")
    for n in NOTIFICATIONS:
        if ids is None or n["id"] in ids:
            n["read"] = True
    h.ok()


def get_stores(h, q):
    h.ok([dict(STORE, _count={"products": len(PRODUCTS)})])


def get_store(h, q, sid):
    if sid != STORE["id"]:
        return h.fail(404, "Store not found")
    h.ok(STORE)


def patch_store(h, q, sid):
    body = h.json_body()
    for key, value in body.items():
        if key in STORE and key not in ("id", "instagramAccountId", "catalogSource"):
            STORE[key] = value
    STORE["updatedAt"] = iso()
    h.ok(STORE)


def telegram_connect(h, q, sid):
    token = h.json_body().get("botToken", "")
    if ":" not in token:
        return h.fail(422, "Telegram rejected this token. Copy it again from @BotFather.")
    STORE["telegramConfigured"] = True
    h.ok({"botUsername": "zebo_orders_bot"})


def telegram_disconnect(h, q, sid):
    STORE["telegramConfigured"] = False
    h.ok()


def telegram_test(h, q, sid):
    if not STORE["telegramConfigured"]:
        return h.fail(409, "Telegram is not connected")
    h.ok()


def get_products(h, q, sid):
    h.send_json({"success": True, "data": [product_view(p) for p in sorted(PRODUCTS, key=lambda p: p["position"])]})


def create_product(h, q, sid):
    body = h.json_body()
    product = {"id": new_id("p"), "storeId": sid, "name": body.get("name"), "description": body.get("description"),
               "price": body.get("price"), "minPrice": body.get("minPrice"), "stock": body.get("stock") or 0,
               "isActive": body.get("isActive", True), "position": len(PRODUCTS), "categoryId": body.get("categoryId"),
               "variants": [], "images": []}
    apply_variants(product, body.get("variants") or [])
    PRODUCTS.append(product)
    h.send_json({"success": True, "data": product_view(product)}, 201)


def apply_variants(product, variants):
    product["variants"] = [{
        "id": v.get("id") or new_id("v"), "productId": product["id"],
        "label": " / ".join(x for x in (v.get("color"), v.get("size")) if x), "size": v.get("size"), "color": v.get("color"),
        "colorHex": v.get("colorHex"), "imageId": v.get("imageId"), "stock": v.get("stock", 0),
        "priceOverride": v.get("priceOverride"), "position": index,
    } for index, v in enumerate(variants)]


def find_product(pid):
    return next((p for p in PRODUCTS if p["id"] == pid), None)


def patch_product(h, q, pid):
    product = find_product(pid)
    if not product:
        return h.fail(404, "Product not found")
    body = h.json_body()
    for key in ("name", "description", "price", "minPrice", "stock", "isActive", "categoryId"):
        if key in body:
            product[key] = body[key]
    if "variants" in body:
        apply_variants(product, body["variants"] or [])
    h.ok(product_view(product))


def delete_product(h, q, pid):
    PRODUCTS[:] = [p for p in PRODUCTS if p["id"] != pid]
    h.ok()


def upload_image(h, q, pid):
    product = find_product(pid)
    if not product:
        return h.fail(404, "Product not found")
    raw = h.body()
    start = raw.find(b"\r\n\r\n")
    end = raw.rfind(b"\r\n--")
    data = raw[start + 4:end] if start != -1 and end != -1 else b""
    if len(data) > 5_000_000:
        return h.fail(413, "File too large")
    img_id = new_id("img")
    IMAGES[img_id] = data
    product["images"].append(image(img_id, len(product["images"])))
    h.send_json({"success": True, "data": {"id": img_id}}, 201)


def reorder_images(h, q, pid):
    product = find_product(pid)
    order = h.json_body().get("order") or []
    by_id = {i["id"]: i for i in product["images"]}
    product["images"] = [dict(by_id[i], position=n) for n, i in enumerate(order) if i in by_id]
    h.ok()


def delete_image(h, q, pid, img_id):
    product = find_product(pid)
    product["images"] = [i for i in product["images"] if i["id"] != img_id]
    for v in product["variants"]:
        if v.get("imageId") == img_id:
            v["imageId"] = None
    h.ok()


def get_categories(h, q, sid):
    h.ok([dict(c, _count={"products": sum(1 for p in PRODUCTS if p.get("categoryId") == c["id"])}) for c in CATEGORIES])


def create_category(h, q, sid):
    name = (h.json_body().get("name") or "").strip()
    existing = next((c for c in CATEGORIES if c["name"].lower() == name.lower()), None)
    if existing:
        return h.ok(dict(existing, _count={"products": 0}))
    category = {"id": new_id("cat"), "name": name}
    CATEGORIES.append(category)
    h.send_json({"success": True, "data": dict(category, _count={"products": 0})}, 201)


def patch_category(h, q, cid):
    category = next((c for c in CATEGORIES if c["id"] == cid), None)
    category["name"] = h.json_body().get("name", category["name"])
    h.ok(dict(category, _count={"products": 0}))


def delete_category(h, q, cid):
    CATEGORIES[:] = [c for c in CATEGORIES if c["id"] != cid]
    for p in PRODUCTS:
        if p.get("categoryId") == cid:
            p["categoryId"] = None
    h.ok()


def get_team(h, q):
    if SETTINGS["soloOwner"]:
        return h.ok({**TEAM, "members": [m for m in TEAM["members"] if m["role"] == "OWNER"], "invitations": []})
    h.ok(TEAM)


def invite(h, q):
    body = h.json_body()
    TEAM["invitations"].append({"id": new_id("inv"), "email": body.get("email"), "role": body.get("role", "MEMBER"), "token": "t",
                                "expiresAt": iso(60 * 24 * 7), "createdAt": iso(), "inviteUrl": f"{BASE}/invite/t"})
    h.ok(TEAM)


def change_role(h, q):
    body = h.json_body()
    for m in TEAM["members"]:
        if m["id"] == body.get("memberId"):
            if m["role"] == "OWNER":
                return h.fail(400, "The owner's role cannot be changed")
            m["role"] = body.get("role")
    h.ok(TEAM)


def remove_member(h, q):
    body = h.json_body()
    if body.get("invitationId"):
        TEAM["invitations"] = [i for i in TEAM["invitations"] if i["id"] != body["invitationId"]]
    if body.get("memberId"):
        TEAM["members"] = [m for m in TEAM["members"] if m["id"] != body["memberId"] or m["role"] == "OWNER"]
    h.ok(TEAM)


def get_accounts(h, q):
    h.ok({"instagramAccounts": [{"id": "ig1", "username": "zebo.shoes", "instagramId": "1784", "name": "Zebo Shoes"}], "selectedInstagramAccountId": "ig1"})


def get_campaigns(h, q):
    h.ok(CAMPAIGNS)


def patch_campaign(h, q):
    campaign = next((c for c in CAMPAIGNS if c["id"] == q.get("id")), None)
    if not campaign:
        return h.fail(404, "Campaign not found")
    campaign.update({k: v for k, v in h.json_body().items() if k in campaign})
    h.ok(campaign)


def get_logs(h, q):
    rows = [l for l in LOGS if not q.get("status") or l["status"] == q["status"]]
    h.ok({"logs": rows, "pagination": {"page": 1, "limit": 30, "total": len(rows), "totalPages": 1}})


def delete_account(h, q):
    if h.json_body().get("confirm") is not True:
        return h.fail(400, 'Send {"confirm": true} to delete the account')
    time.sleep(1.5)
    if SETTINGS["failDelete"]:
        return h.fail(500, "Internal server error")
    SESSION_STATE["revoked"] = True
    KNOWN_EMAILS.clear()
    print("\n  Account deleted, session ended\n", flush=True)
    h.ok({"deletedWorkspaces": 1, "transferredWorkspaces": 0, "disconnectedInstagramAccounts": 1})


ROUTES = [
    (r"/api/account", {"DELETE": delete_account}),
    (r"/api/orders", {"GET": get_orders}),
    (r"/api/orders/([^/]+)/approve", {"POST": decide("APPROVED")}),
    (r"/api/orders/([^/]+)/reject", {"POST": decide("REJECTED")}),
    (r"/api/orders/([^/]+)/cancel", {"POST": cancel_order}),
    (r"/api/orders/([^/]+)/sync", {"POST": sync_order}),
    (r"/api/conversations", {"GET": list_conversations}),
    (r"/api/conversations/([^/]+)", {"GET": get_conversation, "PATCH": patch_conversation}),
    (r"/api/conversations/([^/]+)/messages", {"POST": send_message}),
    (r"/api/notifications", {"GET": get_notifications}),
    (r"/api/notifications/read", {"POST": read_notifications}),
    (r"/api/dashboard/stats", {"GET": lambda h, q: h.ok(dashboard(q.get("range", "month")))}),
    (r"/api/stores", {"GET": get_stores}),
    (r"/api/stores/([^/]+)", {"GET": get_store, "PATCH": patch_store}),
    (r"/api/stores/([^/]+)/telegram", {"POST": telegram_connect, "DELETE": telegram_disconnect}),
    (r"/api/stores/([^/]+)/telegram/test", {"POST": telegram_test}),
    (r"/api/stores/([^/]+)/products", {"GET": get_products, "POST": create_product}),
    (r"/api/stores/([^/]+)/categories", {"GET": get_categories, "POST": create_category}),
    (r"/api/products/([^/]+)", {"PATCH": patch_product, "DELETE": delete_product}),
    (r"/api/products/([^/]+)/images", {"POST": upload_image, "PATCH": reorder_images}),
    (r"/api/products/([^/]+)/images/([^/]+)", {"DELETE": delete_image}),
    (r"/api/categories/([^/]+)", {"PATCH": patch_category, "DELETE": delete_category}),
    (r"/api/workspace/members", {"GET": get_team, "POST": invite, "PATCH": change_role, "DELETE": remove_member}),
    (r"/api/instagram/accounts", {"GET": get_accounts}),
    (r"/api/automations", {"GET": get_campaigns, "PATCH": patch_campaign}),
    (r"/api/logs", {"GET": get_logs}),
]


def main():
    global BASE
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--port", type=int, default=8787)
    parser.add_argument("--fail-delete", action="store_true", help="answer 500 to DELETE /api/account")
    args = parser.parse_args()
    SETTINGS["failDelete"] = args.fail_delete
    BASE = f"http://localhost:{args.port}"
    print(f"Sotuvchi mock API on {BASE}")
    ThreadingHTTPServer(("127.0.0.1", args.port), Handler).serve_forever()


if __name__ == "__main__":
    main()
