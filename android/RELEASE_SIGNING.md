# Release signing

Google Play requires every release build to be signed. This project reads signing
credentials from `android/keystore.properties`, which is gitignored — never commit it
or the `.jks` file it points to.

## One-time setup

1. Generate an upload keystore (do this once, and **back it up somewhere safe outside this
   repo** — e.g. a password manager or encrypted drive. If you lose it before enrolling in
   Play App Signing, or lose access to Play App Signing itself, you cannot update the app
   under the same listing):

   ```
   keytool -genkeypair -v -keystore retailapp-upload.jks -alias retailapp -keyalg RSA -keysize 2048 -validity 10000
   ```

   Keep the resulting `retailapp-upload.jks` outside the repo (e.g. in your home directory),
   or if you keep it inside `android/`, it's covered by `.gitignore` already (`*.jks`).

2. Create `android/keystore.properties` (gitignored) with:

   ```properties
   storeFile=C:\\path\\to\\retailapp-upload.jks
   storePassword=your-store-password
   keyAlias=retailapp
   keyPassword=your-key-password
   ```

3. Build the release bundle Play Console expects:

   ```
   ./gradlew bundleRelease
   ```

   The `.aab` lands in `app/build/outputs/bundle/release/app-release.aab`.

Without `keystore.properties`, `bundleRelease`/`assembleRelease` still run but produce an
**unsigned** release artifact — fine for local shrinking/size checks, not for upload.

## Play App Signing

When you create the app in Play Console, opt into Play App Signing (the default) and
upload your `.jks`'s public certificate there, or let Play generate things around your
upload key — Google then re-signs your app for distribution with its own key, and your
upload key only has to authenticate you to the Console. If you ever lose the upload key,
Google has an account-recovery process to reset it; you are not permanently locked out
the way you would be under legacy (non-Play-App-Signing) signing.
