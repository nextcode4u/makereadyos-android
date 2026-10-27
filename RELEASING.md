# Releases

The current release is **0.1.0-beta.3** (Android version code 5).
Earlier beta.1 and beta.2 used codes 3 and 4. Use beta.4, beta.5, etc. for subsequent iterations.
After stabilization, use 0.1.x for fixes and 0.2.0 for a significant feature set.
Every published APK must have a strictly increasing version code, regardless of
the displayed version name.

## Signed Build

Keep the release keystore and passwords outside Git. Back them up securely:
losing the signing key prevents normal updates to existing installations.
Never sign a release with a debug key or include local emulator certificates.

Set these environment variables in a private shell:

- ANDROID_KEYSTORE: absolute keystore path
- ANDROID_STORE_PASSWORD: keystore password
- ANDROID_KEY_ALIAS: signing alias
- ANDROID_KEY_PASSWORD: key password

From a clean web checkout at the documented revision:

```sh
node scripts/bundle-web.mjs /path/to/MakeReadyOS
./gradlew --no-daemon testDebugUnitTest lintRelease assembleRelease
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
sha256sum app/build/outputs/apk/release/app-release.apk
```

Do not enable localEmulatorTls for distributed builds. That opt-in debug overlay
exists only for private emulator fixtures and is excluded from release variants.

Run the device checks in TESTING.md, review the staged Git tree, commit and tag
the version. Publish betas as GitHub prereleases with the signed APK, checksum and
matching bundled-web source archive. GitHub also provides this repository's tagged
source. Include upgrade cautions and known limits in release notes.

Release APKs and signing material are not source files; keep them out of Git.
Publish only the generic APK as the standard release asset. Keep operator-specific
Firebase-configured APKs private unless their distribution is explicitly approved.
SDK-enabled builds in beta.3 do not yet deliver work notifications.
Do not publish internal plans, operational notes, private screenshots or test
fixtures. A repository's visibility must not be changed as part of releasing.
