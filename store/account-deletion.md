# Account deletion

Google Play requires an in-app way to delete the account and a web page for the same request. Both exist.

## In the app

**More > Delete account** (the Account section, above Sign out). The screen says what will be deleted, asks for confirmation, and deletes the account on the server. The user is signed out and returned to the sign-in screen with the message "Your account was deleted."; the saved email is cleared.

What the screen tells the user:

- A workspace the user owns alone is deleted with everything in it: stores, products, orders, receipts, chats and campaigns, and the connected Instagram accounts are disconnected and Sotuvchi deletes its access to them.
- If other people are members of the workspace, it is kept for them and the longest-standing member becomes its owner.
- The sign-in email, memberships and every session are removed. Signing in again with the same email starts a new, empty account.

If the server fails, the user stays signed in and gets a Retry button, so deletion is never half done on the phone.

## On the web

https://sotuvchi.ai/data-deletion explains every way to remove data (the app, Settings on the website, removing the app from Instagram, asking Instagram, or emailing support@nahl-group.com). Requests are completed within 30 days and usually much sooner; only what the law requires is kept (such as records to prevent fraud or settle a dispute) and deleted when that need ends.

## Where to enter it in Play Console

- Data safety > "Delete account URL": `https://sotuvchi.ai/data-deletion`
- Data safety > "Do you provide a way for users to request that their data is deleted": Yes
- The same page covers a customer of a shop who wants their messages deleted (they email support with their Instagram username).
