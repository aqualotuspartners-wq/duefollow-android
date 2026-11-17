# DueFollow for Android

Package: `com.aqualotus.duefollow`. Invoice and client records stay on-device. PDF attachments use Android's document picker, with manual entry of invoice fields. Drafts open in a mail or WhatsApp app for the user to send. Enable notification permission on Android 13+. Android can defer inexact alarms.

## Build and sign

1. Extract the ZIP into the repository root and push all files, including `.github/workflows/android.yml`, to `main`. The initial push builds a debug APK and runs Android lint.
2. Use the existing upload key if this package already has one registered in Play Console. For a new package, create an upload key and keep a private backup:
   `keytool -genkeypair -v -keystore duefollow-upload.jks -alias duefollow -keyalg RSA -keysize 2048 -validity 10000`
3. Add repository Actions secrets `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_ALIAS` and `SIGNING_KEY_PASSWORD`. The first is the Base64 encoding of the complete keystore file. On Windows PowerShell, use `[Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path 'duefollow-upload.jks').Path))`. Never commit this output or the key.
4. In Actions → DueFollow Android → Run workflow, select `main` and enable `signed_release`. Download `duefollow-play` and extract `app-release.aab`.

## Internal testing

1. In Play Console open DueFollow → Internal testing → Create new release. Set up Play App Signing if prompted, upload the AAB, add release notes, and roll out to internal testers. Add the Google accounts to the tester list and use the opt-in link on the test phone.
2. In Play Console create subscription `duefollow_pro`. Add and activate auto-renewing base plans `monthly` (one month, USD 9.00) and `yearly` (one year, USD 79.00). Set region availability. The app displays the actual localized prices returned by Play.
3. Add the test account to Play Console license testing. Install through the internal testing link. Verify a test purchase, restoration after restart, and manage/cancel in Google Play. The app acknowledges purchased subscriptions and checks entitlement against the Play client.
4. Add a client, create an invoice with a PDF and a payment link, record a partial payment, and confirm both drafts show the remaining balance. Enable notifications, test a due invoice, tap its notification, then pause or mark paid and verify reminders stop. Check the three-open-invoice limit with a free account.
5. Before promoting to production, complete the store listing and declarations and add your public privacy-policy URL. Confirm any testing prerequisites shown in your Play Console.
