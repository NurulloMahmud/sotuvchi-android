# App access instructions for the reviewers

Play Console > Policy and programs > App content > App access. Choose **All or some functionality is restricted**, then **Add new instructions** and fill:

| Field | Value |
|---|---|
| Instruction name | Demo account |
| Username | reviewer@nahl-group.com |
| Password | `<REVIEWER_PASSWORD>` (you fill this in; do not commit it) |
| Any other information required to access your app | the text below |
| Do the instructions apply to all reviewers / any 2-step verification | No other verification is needed |

Text for "Any other information":

```
Sotuvchi is a business tool for owners of Instagram shops. Customers write to the shop's Instagram account, an AI seller answers them and takes orders, and the owner uses this app to check payment receipts, approve orders, read chats and manage products.

SIGN IN
The app normally signs in with a 6-digit code sent to the user's email. You cannot receive our email, so please use the demo account instead: on the sign-in screen tap "Sign in with a password" (bottom of the screen), then enter
Email: reviewer@nahl-group.com
Password: <REVIEWER_PASSWORD>
The app then opens the demo workspace with sample orders, chats and products.

WHAT TO TRY
Home (sales figures and chart), Orders (open an order, view the receipt, approve or reject), Chats (read a conversation, pause the AI, reply), Products (view or edit a product), More (Team, Campaigns, language switch).

ACCOUNT DELETION
More > Delete account (above Sign out) shows what will be deleted, asks for confirmation and deletes the account on the server. Deleting the demo account removes its demo data, so please try it last. We can provide a second throwaway account on request.

PURCHASES
The app has no purchases, prices or payment screens. The app only shows the status of the plan under More > Plan for customers who already have an account.

WEBSITE LINKS
Some buttons (for example "Open the web dashboard") open sotuvchi.ai in the browser. The owner's Instagram account is connected on the website through Meta's login, so connecting Instagram is not part of the app.

PERMISSIONS
No permission prompts. Product photos are picked with the system photo picker.
```

## Before you submit

The demo account is the Meta App Review account, `reviewer@nahl-group.com`; its password is `REVIEWER_PASSWORD` in `/home/sotuvchi/sotuvchi.env` on the server. Sign in as the reviewer on https://sotuvchi.ai/login?reviewer=1 and check that the workspace has what a reviewer needs to see, or the Orders, Chats and Products screens look empty and the review can fail:

- One store with a few products, with photos, prices and stock.
- A few orders in different states, including one waiting for review with a receipt picture.
- A few chats, including one that needs the owner.
- An active plan (`planExpiresAt` far in the future), so More > Plan does not say "No active plan".

Also try "Sign in with a password" in the Android app itself with the reviewer account before submitting.
