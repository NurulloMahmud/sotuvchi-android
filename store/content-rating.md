# Content rating, audience, category, ads, permissions

Play Console > Policy and programs > App content.

## Content rating questionnaire (IARC)

Start the questionnaire under App content > Content rating. Email: support@nahl-group.com. Category: **Utility, Productivity, Communication or Other** (not a game, not social media, not news).

Answer **No** to everything that is about content in the app: violence, blood, sexual content, nudity, profanity or crude humor, drug or alcohol or tobacco references or use, gambling or simulated gambling, fear or horror, discrimination or hate, user-generated content that is shared publicly. The app has none of this. The customers' messages arrive from Instagram and are shown only to the shop's owner.

Questions where the honest answer is not simply "no":

| Question | Answer | Why |
|---|---|---|
| Can users interact or exchange content with other users? | Yes | The owner can reply to customers in the chats. Expect an "Users interact" note on the rating, which does not raise the age much |
| Does the app share the user's location with other users? | No | No location is used |
| Does the app allow users to purchase digital goods? | No | Nothing is sold in the app |
| Does the app provide unrestricted web access? | No | Website buttons open the browser (Custom Tab) for sotuvchi.ai only |
| Does the app have a social feature or public posts? | No | |

The expected result is a low rating (for example Everyone / PEGI 3 with the interaction notice). If the questionnaire gives something else, check the answers again before accepting it.

## Target audience and content

- Target age group: **18 and over** only. Do not tick any child age group.
- Appeal to children: **No**. The app is a business tool for shop owners.
- Not part of the Designed for Families program.
- Teacher Approved: not applicable.

## App category

**Business** (App, not game). Free, no in-app purchases.

## Ads

**No ads.** App content > Ads > "My app does not contain ads".

## Permissions

The release app requests only `android.permission.INTERNET`. No runtime permission prompt exists: product photos are chosen with the system photo picker, and links open in Chrome Custom Tabs. `make bundle` checks this on every build.

## Other App content declarations

| Declaration | Answer |
|---|---|
| Advertising ID | No (not used, no `AD_ID` permission) |
| Government app | No |
| Financial features | The app provides no financial features. It does not lend, pay, transfer or process money; the card number in a store's settings is only text shown to the shop's customers by the AI seller |
| Health apps | No |
| News app | No |
| Data safety | See data-safety.md |
| App access | See app-access.md |
| Target API level | The app targets API 37 |
