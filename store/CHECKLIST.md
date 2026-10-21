# Google Play checklist

Everything that can be prepared in the repository is done. The steps below need your Play Console account, so they are yours. Package name `ai.sotuvchi.app`, version 1.0.0 (versionCode 1), free, no in-app purchases, only the INTERNET permission.

## Files in this folder

| File | What it is for |
|---|---|
| `listing.md` | Title, short and full description in English, Russian and Uzbek |
| `data-safety.md` | Answers for the Data safety form |
| `content-rating.md` | Content rating questionnaire, target audience, category, ads, permissions, other declarations |
| `app-access.md` | Reviewer login and instructions (put the password in yourself) |
| `account-deletion.md` | The in-app path and the web URL for deletion |
| `graphics/icon-512.png` | App icon, 512 x 512 |
| `graphics/feature-graphic-{en,ru,uz}.png` | Feature graphic, 1024 x 500 |
| `graphics/screenshots/{en,ru,uz}/*.png` | Eight phone screenshots per language, 1080 x 2160, from sample data |

## Before you start

1. **Back up the upload key.** Copy `~/.sotuvchi-signing/sotuvchi-upload.jks` and the four lines in `android/keystore.properties` (store password, key alias, key password) into your password manager. With Play App Signing a lost upload key can be reset through Play support, but it takes time and a form. Never put either in git or chat.
2. **Check which kind of developer account you have.** Play Console > Settings > Developer account. A **personal** account created after 13 November 2023 must run a closed test with at least 12 testers opted in for 14 days in a row before it can apply for production access. An **organization** account does not have that rule. The company is Nahl Group Inc., so it is probably an organization account (it also needs a D-U-N-S number and verification); check which one yours is, because it decides step 8.
3. **Check the reviewer workspace.** Follow "Before you submit" in `app-access.md`: sign in as `reviewer@nahl-group.com` and make sure it has a store, products, orders (one with a receipt), chats and an active plan. An empty demo workspace is the most common reason for a rejection.
4. If `make bundle` has not been run on this machine lately, run it from the `android` folder. It prints a list of checks and stops with an error if one fails.

## Steps

1. **Create the app.** Play Console > Create app. Name `Sotuvchi`, default language English (United States), App (not game), Free, accept the declarations.
2. **Upload the bundle to internal testing first.** Testing > Internal testing > Testers: create an email list with your own address. Create new release, upload `android/app/build/outputs/bundle/release/app-release.aab`, accept Play App Signing when asked, release name `1.0.0 (1)`, release notes `First release.`, save and roll out. Open the opt-in link on your phone and install the app from Play.
3. **Try it on your own phone** from internal testing: sign in with your email code, check Home, Orders, Chats, Products, More, the language switch, "Open the web dashboard" and the privacy policy link, and that Delete account shows its confirmation (do not confirm it on your real account). Also try "Sign in with a password" with the reviewer account.
4. **Main store listing.** Grow > Store presence > Main store listing: title, short and full description from `listing.md` (English), contact email `support@nahl-group.com`, website `https://sotuvchi.ai`. Upload `graphics/icon-512.png`, `graphics/feature-graphic-en.png` and the eight screenshots from `graphics/screenshots/en/` as phone screenshots. Then Manage translations (or Add a language) for Russian and Uzbek: paste their texts and upload their feature graphic and screenshots.
5. **Store settings.** Category Business, tags as in `listing.md`, privacy policy `https://sotuvchi.ai/privacy`.
6. **App content.** Policy and programs > App content, in this order (details in the files named):
   - Privacy policy: `https://sotuvchi.ai/privacy`
   - Ads: No (`content-rating.md`)
   - App access: restricted, with the reviewer login (`app-access.md`, replace `<REVIEWER_PASSWORD>`)
   - Content rating: the questionnaire (`content-rating.md`)
   - Target audience: 18 and over (`content-rating.md`)
   - Data safety: `data-safety.md`, with the delete account URL `https://sotuvchi.ai/data-deletion` (`account-deletion.md`)
   - Government apps, Financial features, Health, News, Advertising ID: the answers are in `content-rating.md`
7. **Countries and pricing.** Production > Countries / regions: choose where it is available. Free app.
8. **Production or closed testing.**
   - **Organization account:** Production > Create new release, add the same bundle (or promote the internal release), roll out, and send the app for review.
   - **Personal account (after Nov 2023):** Testing > Closed testing > create a track, add at least 12 testers (people with a Google account, by email list or Google Group), release the bundle to it, keep the 12 testers opted in for 14 days in a row, then apply for production access in the dashboard. Add 15 to be safe, because testers who leave reset the count.
9. **Publishing overview.** Check that every task on the dashboard is done, then send the changes for review. A first review of a new app usually takes a few days and can take up to about seven; Play may also send questions to the contact email.
10. **Pre-launch report.** Play runs the app on test devices after an upload. It signs in with the credentials from App access, so keep them current.

## Later releases

Raise `versionCode` in `android/app/build.gradle.kts` for every upload (Play rejects a repeated one) and `versionName` for a new version, run `make bundle`, and upload the new bundle to a release. Rebuild the pictures with `make screenshots` and `make graphics` when the design changes.

## Decisions and things to confirm

- **Title.** `Sotuvchi`; if Play says it is taken, use `Sotuvchi: AI Seller` (this is the same fallback as on the App Store).
- **Data safety "shared = no".** Correct as long as the only recipient is Sotuvchi's own servers and service providers that process data for you. If the privacy policy names an outside AI provider, that is a service provider and stays "not shared", but make sure the policy says it.
- **Reviewer password.** Not in the repository. `REVIEWER_PASSWORD` is in `/home/sotuvchi/sotuvchi.env` on the server.
