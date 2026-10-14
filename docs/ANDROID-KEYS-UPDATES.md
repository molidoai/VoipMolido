# Android signing & update policy

1. Generate **one** release keystore on your own device and back it up securely. Never commit it.
2. Configure repository Actions secrets: `MOLIDO_KEYSTORE_BASE64`, `MOLIDO_KEYSTORE_PASSWORD`, `MOLIDO_KEY_ALIAS`, `MOLIDO_KEY_PASSWORD`.
3. CI builds a debug APK and, when all signing secrets exist, a release APK signed with the same key.
4. The Android app checks the **latest non-prerelease GitHub Release** on launch. If its tag is newer (strict `vX.Y.Z` or `X.Y.Z`) and it has an APK asset, it offers to open the official release page.
5. Installation is user-confirmed; Android does not permit silent replacement by an ordinary app. Only release APKs signed with the same key and same applicationId can upgrade each other. Debug builds cannot be upgraded with release builds.
6. Increment `versionCode` and `versionName` in `android/app/build.gradle` for each release. Upload the signed release APK to a GitHub Release with matching tag.
7. Do not share keystore, passwords, base64 or authentication codes in chat.

The APK is a WebView-based shell for the PWA preview, not a SIP calling client. Real device build and upgrade have not yet been verified.
