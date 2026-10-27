# Firebase Messaging Build Setup

Status: optional SDK integration only. Device opt-in, account/session binding,
server sending and end-to-end notification tests are still required. The published
beta.3 has no native push delivery. SDK installation is not working push.

## Private Configuration

Register com.makereadyos.android in Firebase, then place its downloaded
google-services.json in nonpublish/ at the repository root.
The whole folder is ignored. The build reads it in place; no tracked copy is needed.
Client identifiers will be compiled into a Firebase-enabled APK and are not
server credentials. Never place a service-account key in Android resources.

## Build

Normal builds omit the Messaging SDK and project resources. Explicit opt-in
adds Firebase Messaging through the Firebase BoM and Google services plugin:

```sh
node scripts/bundle-web.mjs /path/to/MakeReadyOS
./gradlew -PfirebaseNotifications=true testDebugUnitTest lintDebug assembleDebug
```

Missing, malformed, wrong-package or service-account configuration fails the build.
Automatic messaging initialization and Analytics collection are disabled in the
manifest. No Analytics SDK, Hosting, Firestore or Firebase Authentication is added.
Do not enable automatic token registration until the signed-in user's explicit
notification choice, server ownership checks and logout cleanup are implemented.

Generated resources stay in app/build. Do not publish generated resource XML,
verbose build logs or local configuration. Debug builds are not release artifacts.
The same opt-in will be needed for a future notification-enabled signed release.
Preserve the existing release keystore and increment versionCode.

Test delivery on a device with Google Play services. The plain AOSP emulator
cannot establish that FCM delivery works.

## Server Setup

The API does not yet consume FCM credentials or send native messages. Existing
VAPID variables remain for browser Web Push. Do not put a service-account key in
the client repository or add undocumented server variables.

See the [provider guide](https://github.com/nextcode4u/MakeReadyOS/blob/main/docs/FIREBASE_ANDROID.md)
for project ownership, least privilege and acceptance gates.

References: [Google services plugin](https://firebase.google.com/docs/android/google-services-plugin-and-file),
[FCM Android setup](https://firebase.google.com/docs/cloud-messaging/android/get-started).
