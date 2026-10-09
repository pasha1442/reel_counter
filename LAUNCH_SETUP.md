# Launch setup

The code for payments, sign-in, crash reports, legal pages and release signing is done. Each
piece switches on when its key or file is added; until then the app runs with that piece off
(debug builds show a labelled test mode instead). These steps need your own accounts.

## 1. Release signing (done, back it up)

- Upload key: `~/.reelcounter-keys/upload-keystore.jks`
- Passwords: `keystore.properties` in the project root (a copy is in `~/.reelcounter-keys/`).
- Both are ignored by git. **Copy the whole `~/.reelcounter-keys` folder to a password manager or
  an encrypted drive.** If it is lost, Google can reset it through Play App Signing, but that
  takes days.
- Build for Play: `./gradlew :app:bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`

## 2. Legal pages (needed before release)

1. Fill in `docs/config.js`: your legal name, email, address, privacy contact person and
   courts city. Every page reads from there; empty values show as "[to fill in]".
2. Have a lawyer read `docs/privacy.html` and `docs/terms.html`. They match what the app does
   today; change them whenever the app changes what it collects.
3. Publish `docs/` on the web (e.g. GitHub Pages: repository Settings → Pages → folder `/docs`).
4. Put the address in `gradle.properties`: `reelcounter.legalBaseUrl=https://…` and your
   support email in `reelcounter.supportEmail=`.
5. In Play Console use: Privacy policy URL = `…/privacy.html`, account deletion URL =
   `…/delete-account.html`.

The app shows the same pages from inside itself, so they work offline.

## 3. Firebase: sign-in, crash reports, usage stats

1. console.firebase.google.com → Add project → add an Android app with package
   `com.reelcounter.android`. Add a second Android app `com.reelcounter.android.debug` for testing.
2. Add the SHA fingerprints of both keys (Project settings → Your apps → Add fingerprint):
   - Upload key: `keytool -list -v -keystore ~/.reelcounter-keys/upload-keystore.jks -alias upload`
   - Debug key: SHA-1 `21:48:0D:E1:F6:BD:D3:F3:1C:1B:92:42:A7:18:27:73:B6:61:F4:F6`
   - After the first Play upload, also add the **app signing key** SHA-1 from Play Console →
     Setup → App signing. Without it, Google sign-in fails for people who install from Play.
3. Authentication → Sign-in method: enable **Google** and **Email/Password**.
4. Download `google-services.json` and put it in `app/`. Rebuild. Sign-in, Crashlytics and
   Analytics switch on automatically; test mode disappears.
5. Crash reports and usage stats still send nothing until each user says yes in the app.

## 4. Payments: Google Play Billing through RevenueCat

1. Play Console → your app → Monetize → Subscriptions → create a subscription with two base
   plans: yearly (with a 7-day free trial offer) and monthly. Set rupee prices.
2. app.revenuecat.com → new project → add the Play Store app (needs a Play service-account
   JSON; RevenueCat's guide walks through it) and turn on real-time developer notifications.
3. In RevenueCat: create the entitlement **`pro`**, attach both products, and put them in the
   **current** offering as the Annual and Monthly packages.
4. Copy the public Google API key (starts with `goog_`) into `local.properties`:
   `revenuecat.apiKey=goog_…` Rebuild.
5. Test with license-tester accounts (Play Console → Settings → License testing).

The paywall then shows real prices from Google Play, the "Test prices" note disappears, and
Restore, Manage subscription and the trial reminder use the real subscription.

## 5. Play Console

- Create the app, upload the AAB to a **closed test**, and keep 12 testers opted in for 14 days.
- App content: Privacy policy, Data safety (see below), Ads: none, Target audience 13+,
  content rating, **Accessibility declaration** with a video of the consent screen
  ("How counting works"), and **App access** instructions so reviewers can reach Pro features.
- Data safety, as the app works today: account email and name (optional, for accounts),
  purchase history (Pro), crash logs and app interactions (optional, opt-in). Nothing shared for
  ads; data encrypted in transit; users can request deletion.

## What each build does without these keys

| Piece | Debug build | Release build |
|---|---|---|
| Sign-in | Test mode, labelled, nothing sent | Hidden ("No account yet") |
| Pro | "Pretend to be Pro" switch | Pro screen says payments aren't available yet |
| Crash reports / usage stats | Toggles shown, send nothing | Hidden |
