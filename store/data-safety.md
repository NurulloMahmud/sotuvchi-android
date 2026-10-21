# Data safety form

Play Console > Policy and programs > App content > Data safety. The answers are mapped one to one from `ios/Store/privacy-labels.md`, so the two stores say the same thing, and they match the privacy policy at https://sotuvchi.ai/privacy.

## Overview questions

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | Yes |
| Is all of the user data collected by your app encrypted in transit? | Yes (HTTPS only; cleartext traffic is off in the release app) |
| Do you provide a way for users to request that their data is deleted? | Yes |
| Account creation methods | Email address (a 6-digit code sent to the email; the same code creates the account) |
| Delete account URL | https://sotuvchi.ai/data-deletion |
| Can users request deletion of some data without deleting the account? | No (deleting the account deletes the data; see account-deletion.md) |

The app has no advertising, analytics, crash-reporting or tracking tools and no third-party SDKs that collect data. Nothing is sold, and nothing is shared for advertising.

## Data types to declare

Every row: **collected = yes, shared = no**, processed by the app (not only on the device), **required** unless noted, purpose **App functionality** (plus **Account management** where noted). None is used for advertising, analytics, personalization, fraud prevention or any other purpose.

| Play category | Play data type | What it is here |
|---|---|---|
| Personal info | Email address | The owner's sign-in email. Purposes: App functionality, Account management |
| Personal info | Name | The owner's name, and the names of the shop's customers shown on orders and chats |
| Personal info | Phone number | Customers' phone numbers on orders |
| Personal info | Address | Customers' delivery addresses on orders, and the shop's pickup address |
| Personal info | User IDs | The account ID, and the customers' Instagram IDs. Purposes: App functionality, Account management |
| Financial info | User payment info | The card number and card holder name the owner types in so customers can pay them. The app does not process payments or touch any payment service |
| Photos and videos | Photos | Product photos the owner uploads (**optional**), and pictures customers send in chats such as payment receipts |
| Messages | Other in-app messages | Chat messages between the shop's customers and the AI seller or the owner |
| App activity | Other user-generated content | Product descriptions, store settings, the AI's instructions |

## Not collected

Approximate or precise location, contacts, calendar, audio, files and documents, health and fitness, web browsing history, search history, installed apps, app interactions and in-app search history, crash logs, diagnostics and performance data, device or other IDs (no advertising ID), purchase history, credit info.

## Notes for filling the form

- **Shared.** Play does not count it as "sharing" when data goes to a service provider that processes it for you under your instructions. Sotuvchi's servers are the only place the app sends data. If the server side uses an outside AI provider to read chats and receipts, that is a service provider; check that https://sotuvchi.ai/privacy names it, and keep "shared = no" only if that is true for your setup.
- **Customers' data.** Names, phones, addresses and messages belong to the shop's customers and reach the shop through Instagram. The owner sees them in the app, so they are declared.
- **On the device.** The app stores the sign-in session token (encrypted with an Android Keystore key) and the email and role. Nothing else is kept after sign-out, and cached pictures are only in memory.
- **Ephemeral processing** does not apply: everything above is stored on the server.
- **Advertising ID.** The app does not use it. In App content > Advertising ID answer "No". The manifest has no `AD_ID` permission.
- After saving, Play shows a preview of the "Data safety" section as users will see it. Compare it with this file.
