# Sotuvchi for Android

The store owner's app for [Sotuvchi](https://sotuvchi.ai): an AI seller that answers Instagram DMs, negotiates within the owner's limits, takes the order and asks for a payment receipt. The owner checks the receipt and approves, takes over any chat by hand, and keeps the catalogue up to date. This app puts that daily job on the phone.

It is the Kotlin twin of the iOS app (`sotuvchi-ios`): same screens, same behavior, same strings, same design tokens, against the same API.

## Features

| Area | What the owner can do |
|---|---|
| Home | Approved sales for a period with the change against the previous period, orders, open orders, receipts to review (equal-height tiles), daily revenue chart, top products, comment-campaign figures, a "Needs you" card, a notice in the plan's last week, a welcome card for new accounts |
| Orders | Tabs for To review, Open, Approved, Rejected and All; date and store filters; search by number, customer, phone or product; receipt viewer with pinch to zoom; the amount the AI read from the receipt compared with the order total; approve, reject with a reason, cancel, retry the warehouse sync |
| Chats | Conversations that need the owner first; AI status per chat; take over or resume the AI; Instagram's 24-hour reply window with a countdown; linked orders; pictures, videos, voice messages and shared links |
| Notifications | Bell feed with unread count, tab badges, in-app banners for new orders and receipts (polled every 30 seconds while the app is open; there are no push notifications) |
| Products | Manual catalogue: up to 10 photos (Photo Picker, compressed on the phone), price and the secret lowest price for bargaining, stock, categories, variants with size, color, swatch, own price and picture. MoySklad catalogues are shown read-only |
| Stores | Store name and description, AI on or off and its instructions, payment card, stock hold time, pickup address, email alerts and their language, Telegram order alerts (connect, test, disconnect) |
| Team | Members and roles, invite by email, share the invite link, change roles, remove members, revoke invites |
| Campaigns | Comment-to-DM campaign results, pause and resume, DM log |
| Account | Plan status (active until, ended or none), delete account, sign out |
| App | Uzbek, Russian and English interface with an in-app switch |

Owners and admins get every action. Members get a read-only app: manage actions are hidden, the card number is masked and the lowest price is not shown, exactly as the API enforces.

## Requirements

- Android Studio (current stable) with its bundled JDK, and an SDK with platform 37 and build-tools 36
- An emulator image (API 26 or newer) or a device
- minSdk 26, compile and target SDK 37, Kotlin 2.4, Android Gradle Plugin 9.4, Gradle 9.6 (wrapper committed)
- Python 3 for the mock server and the translation check (standard library only)

Dependencies, all current stable and few: Compose (BOM) with Material 3 as a base, Navigation Compose, lifecycle, activity, kotlinx.serialization and coroutines, OkHttp, Coil, androidx.browser (Custom Tabs), DataStore and ExifInterface. There is no Hilt: `AppContainer` wires things by hand.

## Getting started

```bash
make build        # debug APK
make test         # translation check + JVM unit tests
make ui-test      # Compose UI tests against the mock API, on the running emulator
make ui-test-qa   # the same suite against the shrunk (R8) QA build
make run          # build, install and launch against the local mock API (starts it)
make run-prod     # the live server, after typing "yes"
make mock         # the mock API on http://localhost:8787, in the foreground
make bundle       # signed release app bundle for Google Play, then checked
make screenshots  # Play Store phone screenshots (en, ru, uz) from the mock's sample data
make graphics     # Play Store icon and feature graphics (needs Pillow)
make clean
```

The Makefile uses Android Studio's JDK (`/Applications/Android Studio.app/Contents/jbr/Contents/Home`; set `JAVA_HOME` to change it) and the SDK in `~/Library/Android/sdk` (`ANDROID_HOME`). Gradle also needs `local.properties` with `sdk.dir=...`; Android Studio writes it, and it is git-ignored.

### Toolchain on a new Mac

1. Install Android Studio (`brew install --cask android-studio`), open it once and let the setup wizard install the SDK, the platform tools and an emulator image. Accept the SDK licenses yourself.
2. Create an emulator in Device Manager (a Pixel with a Play Store image works, and `adb` is in `~/Library/Android/sdk/platform-tools`).
3. The password sign-in and the "Open the web dashboard" test open Chrome. On a fresh emulator, start Chrome once and finish its first-run screen ("Stay signed out"), or the Custom Tab stops at that screen.

### Working without the backend

`Tools/mock-server/mock_server.py` is a standard-library Python stand-in for the API with a sample store, orders, chats, products and team. It also implements the email-code sign-in and prints each code instead of emailing it.

```bash
make run         # starts the mock if needed, builds, installs and launches (make run-mock is the same)
```

Sign in with `owner@zebo.uz` (an existing account) or any other email (a new account), then type the code the mock prints. Deleting the account in the app ends the mock session and forgets every email, so the next sign-in is a new account. Start the mock with `--fail-delete` to see the retry path. `GET /login` stands in for the website's password login (it sets the session cookie), so "Sign in with a password" can be tried too.

The emulator reaches the Mac's mock through `adb reverse tcp:8787 tcp:8787` (the scripts run it), so the app talks to `http://localhost:8787/api`. The debug build allows cleartext HTTP to `localhost`, `127.0.0.1` and `10.0.2.2` only, through a debug-only network security config; release builds allow no cleartext at all.

## Configuration

| Setting | Where | Default |
|---|---|---|
| API URL | `API_URL` BuildConfig field in `app/build.gradle.kts`. Endpoint paths are relative to it, so moving to `/api/v1` is a one-line change. Web pages and Auth.js use the same host's root. | `https://sotuvchi.ai/api` |
| Debug override | `adb shell am start -n ai.sotuvchi.app/.MainActivity --es apiURL <url>` (debug builds only). The choice is kept until another is given; an empty value goes back to the default. | none |
| Release signing | `keystore.properties` in this folder (git-ignored): `storeFile`, `storePassword`, `keyAlias`, `keyPassword`. Without it the release build stays unsigned. | unsigned |

The debug build keeps the API address it was launched with. Debug and QA builds show a strip across the status bar that names the server (amber "TEST BUILD - MOCK localhost:8787", red "TEST BUILD - LIVE SERVER sotuvchi.ai"), so a test build on production cannot go unnoticed; release builds have neither the banner nor the override. `make run` always uses the mock, and `make run-prod` asks you to type "yes" first. The UI tests uninstall the app, which wipes its saved address; `make ui-test` writes the address of the last `make run` to `Tools/out/last-api-url` and installs the debug app again with it afterwards. A debug build launched by hand with no `apiURL` extra talks to production, and says so in the banner.

## How sign-in works

The app signs in with a 6-digit code sent by email (`/api/mobile/auth/*`). The same flow signs in existing owners and registers new ones; the website keeps using magic links, and both share the same accounts.

1. The owner enters their email and the app calls `POST /api/mobile/auth/request-code`. The code is valid for 10 minutes; a new one can be requested after 60 seconds, at most 5 per hour.
2. The owner types or pastes the code; at 6 digits it is submitted to `POST /api/mobile/auth/verify-code`, which returns a session token.
3. The token is encrypted with an Android Keystore AES-GCM key (the key never leaves the Keystore) and the ciphertext is kept in DataStore. It is sent as `Authorization: Bearer <token>` on every request. Any `401` clears it and returns to sign-in.
4. Signing out clears the token first, then calls `POST /api/mobile/auth/logout`.

Every error has its own message: wrong code, expired code, too many attempts (5), resend cooldowns (with a countdown on the resend button), unreachable mail service, blocked email. A brand-new account lands on a welcome card.

"Sign in with a password" is for the store-review account. It opens the web login (`/login?reviewer=1`) in a WebView with a fresh cookie store, takes the `__Secure-authjs.session-token` cookie once the server sets it, and uses it as the Bearer token.

### Opening the website signed in

Buttons that hand off to the website ("Open the web dashboard", "Set up on the web", store and campaign setup) open a Custom Tab already signed in. The app asks `POST /api/mobile/auth/web-handoff` (8-second timeout) for a one-time link, opens it only if its host is our site, and falls back to the plain page otherwise. The link works once, for about a minute. The session token never goes into a URL. Privacy policy and terms open as plain public pages.

## Account deletion and the plan

Google Play requires an app with sign-up to let people delete their account inside the app. More > Account > Delete account explains what happens for this user's case and calls `DELETE /api/account` with `{ "confirm": true }`:

- An owner alone in the workspace loses the workspace with its stores, products, orders, receipts, chats, campaigns and Instagram connection.
- An owner with a team keeps the workspace for the team, and the longest-standing member becomes its owner.
- Admins and members leave the workspace; its data stays.

On success the app clears the token, the saved email and role, and the image caches, then shows sign-in with a toast. On failure the user stays signed in and gets a retry button.

The plan (Sotuvchi Pro) is shown, not sold. More shows whether it is active and until when, read from `workspace.planExpiresAt` on `GET /api/dashboard/stats`, and Home shows a notice in its last week. It is read again when the app comes back to the foreground. There is no pay or renew button and no link to the web payment page: Google Play's payments policy generally requires Play Billing for subscriptions sold inside an app. The backend has Payme and Stripe checkout (`POST /api/billing/checkout`); adding a button is a product decision to make first.

## Tests

- `make test` runs the translation check and the JVM unit tests (`app/src/test`): sample payloads from `mobile-api.md`, money and date formatting, the reply window, order rules, notification text, chat text, initials, plan status, sign-in error mapping, request building (Bearer, the delete body, web handoff against a local server), store and product form logic, chart scale, image sizing and the translation files.
- `make ui-test` runs Compose UI tests (`app/src/androidTest`) against the mock on the running emulator. It starts the mock, sets up `adb reverse`, runs the tests and stops the mock. It covers:
  - the server fails the first delete: the user stays signed in and gets Retry, the retry deletes the account, the app returns to sign-in, and the same email then starts a new account;
  - an owner alone in the workspace is told the workspace, stores, orders, chats and Instagram connection go too;
  - "Open the web dashboard" asks for a one-time link and the browser opens it (checked through the mock's `/mock/handoffs`);
  - a tour of every screen (Home, orders and a receipt, chats and a thread, products and the editor, stores, team, campaigns, Instagram accounts, notifications) that checks real content from each response shows up.
- `make ui-test-qa` runs the same suite against the `qa` build type: release-like (R8 code and resource shrinking), debug-signed, with the API override and the banner on. It is what proves the shrunk code decodes JSON and draws every screen. The test APK reuses some library classes from the app, so `app/proguard-qa-rules.pro` keeps androidx and Kotlin whole in that build only. `python3 Tools/qa-smoke.py` tries the QA app at full strength instead (no such keeps) by signing in through the mock and opening every tab with plain adb taps.

The tests drive the mock through its `/mock/*` hooks (reset, make deletes fail, read the last sign-in code, see which web links a browser used) and launch the app with the `uiTesting` extra, which keeps the test session under separate storage keys (`uitest.*`) so a real sign-in on the same device is never touched.

## Design

The interface follows the [Intercom DESIGN.md](https://github.com/VoltAgent/awesome-design-md/tree/main/design-md/intercom) from VoltAgent/awesome-design-md, like the iOS app.

- Warm cream canvas (`#f5f1ec`) with white cards lifted by a 1px hairline (`#d3cec6`). No shadows.
- Charcoal (`#111111`) is the system primary for text and the main buttons.
- One chromatic accent, reserved for AI moments: the brand blue (`#2563eb`) for the AI sparkle, AI message bubbles, "Resume AI", attention markers and the unread dot. The notification bell itself is ink-colored.
- Buttons have 8dp corners, cards 12dp, large tiles 16dp. CTAs are never pill-shaped; pill toggles are used for tabs.
- The system font, medium for display and regular for body, with negative tracking on large sizes.
- A dark palette derived from the same rules.

Material 3 is only a base. All tokens live in `design/Theme.kt` and the shared components (Card, PillTabs, Badge, AIMark, Avatar, EmptyState, ErrorBanner, Notice, NavRow, StatTile, buttons, fields, toast) in `design/`. The revenue chart is drawn on a Compose Canvas. The launcher icon is adaptive, made from the Sotuvchi icon.

## Translations

English is the source language, as on the web. `app/src/main/assets/uz.json` and `ru.json` map English text to Uzbek and Russian and are the same files as in the iOS app (a few keys were added for this app). Write UI text as `L("English text")`, `L("Order #{number}", mapOf("number" to n))` or, for counts, `L(one = "{count} product", other = "{count} products", count = n)`, then run:

```bash
make l10n-check  # fails when a string has no Uzbek or Russian translation
```

Money is whole UZS integers grouped with non-breaking spaces (`450 000 so'm`, `450 000 сум`, `450 000 UZS`); day filters use Tashkent time. Signed S3 image URLs expire, so images are cached in memory only; images on our own host (the MoySklad proxy) are fetched with the Bearer header.

## Project layout

```
app/src/main/java/ai/sotuvchi/app/
  MainActivity.kt, SotuvchiApp.kt   one activity; language set-up
  app/                              AppContainer (manual DI), AppModel (session, badges, polling), routes, tab shell, nav host
  core/
    auth/                           email-code sign-in
    network/                        API client, endpoints, errors, JSON helpers
    model/                          API models (orders, chats, notifications, stores, catalogue, team, campaigns)
    l10n/                           L() and the language setting
    format/                         money (whole UZS) and dates
    store/                          DataStore and the Keystore-backed token store
  design/                           tokens and shared components
  features/                         one folder per screen group (auth, home, orders, chats, notifications, catalog, stores, team, campaigns, more)
app/src/main/assets/                uz.json, ru.json
app/src/test/                       JVM unit tests
app/src/androidTest/                Compose UI tests against the mock API
app/src/debug/                      debug-only network security config (cleartext to localhost)
Tools/                              mock API, translation check, run, UI test, bundle check and store picture scripts
store/                              Google Play listing, data safety, checklist and graphics
```

## Release

Everything for Google Play is prepared in this repository; what needs your Play Console account is listed step by step in [store/CHECKLIST.md](store/CHECKLIST.md).

**Version.** `versionName 1.0.0`, `versionCode 1` in `app/build.gradle.kts`. Raise `versionCode` for every upload (Play rejects a repeated one) and `versionName` for a new version.

**Signing.** The app is signed with an upload key. Play App Signing keeps the real app signing key, so a lost upload key can be reset through Play support, but that is slow; back it up. The key is `~/.sotuvchi-signing/sotuvchi-upload.jks` (outside the repository), and `keystore.properties` in this folder (git-ignored, mode 600) holds its path and passwords. To create a new one:

```bash
keytool -genkeypair -v -keystore ~/.sotuvchi-signing/sotuvchi-upload.jks -storetype PKCS12 -alias sotuvchi-upload \
  -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Sotuvchi, OU=Mobile, O=Nahl Group Inc., C=US"
```

then write `storeFile`, `storePassword`, `keyAlias`, `keyPassword` into `keystore.properties`. Never commit either file (`.gitignore` covers `*.jks`, `*.keystore` and `keystore.properties`).

**Bundle.** `make bundle` builds the release app bundle, `app/build/outputs/bundle/release/app-release.aab`, with R8 and resource shrinking, signed with the upload key, and then runs `Tools/check-bundle.py`, which fails the build unless:

- `jarsigner` verifies the signature and the signer is the Sotuvchi upload certificate, not the debug key;
- the dex has no Compose tooling classes, no API override, no banner switch and no banner text (the debug-only code is compiled out);
- the manifest is not debuggable, the version is 1.0.0 and the only permission is `INTERNET`;
- every native library (DataStore and Compose graphics, 8 files for 4 ABIs) has 16 KB aligned segments, which Play requires for apps that target Android 15 or newer.

**Store texts and pictures.** `store/` holds the listing in English, Russian and Uzbek, the data safety answers, the content rating guidance, the reviewer access text, the account deletion text and `graphics/` (icon, feature graphics, phone screenshots). `make screenshots` and `make graphics` rebuild the pictures.

## Backend work that would improve the app

From `mobile-api.md`, in order of impact: push notifications (a device-token endpoint hooked into `notifyOwners`; until then the app polls in the foreground and points owners to Telegram alerts), `GET /api/orders/{id}` and pagination for orders, chats and messages, a versioned API path (`/api/v1`) before the app ships, App Review copy on the password login page, and Play Billing if the plan should ever be sold inside the app.
