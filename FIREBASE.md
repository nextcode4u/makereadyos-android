# Firebase Messaging Build Setup

Beta.4 includes device opt-in, session binding, generic native
alerts and foreground toasts. It requires the matching updated MakeReadyOS API.
The previously published beta.3 remains SDK-only. Live end-to-end delivery must be
verified with a configured sender and a Google Play services device before release.

## Private Configuration

Register com.makereadyos.android in Firebase, then place its downloaded
google-services.json in nonpublish/ at the repository root.
The whole folder is ignored. The build reads it in place; no tracked copy is needed.
Client identifiers will be compiled into a Firebase-enabled APK and are not
server credentials. Never place a service-account key in Android resources.

## Build

Recommended: `node scripts/build.mjs --profile firebase-debug --web-source /path/to/MakeReadyOS`.
Use `firebase-release` for a signed build. See [BUILDING.md](BUILDING.md) to save
private settings once and reuse the preset without retyping paths.

Normal builds omit the Messaging SDK and project resources. Explicit opt-in
adds Firebase Messaging through the Firebase BoM and Google services plugin:

```sh
node scripts/bundle-web.mjs /path/to/MakeReadyOS
./gradlew -PfirebaseNotifications=true testDebugUnitTest lintDebug assembleDebug
```

Missing, malformed, wrong-package or service-account configuration fails the build.
Automatic messaging initialization and Analytics collection are disabled in the
manifest. No Analytics SDK, Hosting, Firestore or Firebase Authentication is added.
Token acquisition is explicit after authenticated opt-in. On later launches and
foreground resumes, opted-in devices refresh their token binding without prompts.
Signing out clears the binding and posted alerts; old-binding messages are ignored.

Generated resources stay in app/build. Do not publish generated resource XML,
verbose build logs or local configuration. Debug builds are not release artifacts.
The same opt-in will be needed for a future notification-enabled signed release.
Preserve the existing release keystore and increment versionCode.

Test delivery on a device with Google Play services. The plain AOSP emulator
cannot establish that FCM delivery works.

## Server Setup

### Shared Publisher App

The source now supports optional shared-relay registration. A publisher can ship
one Firebase-enabled app; unrelated servers receive separate relay credentials
instead of the Firebase sender key. No per-user compilation or Firebase project
is needed. Relay hosting/enrollment and physical-device acceptance testing must
be completed before advertising this as an available public service.

The app verifies an expiring, single-use FCM challenge against its current server
origin, pending nonce and account binding. Verification is silent, not a user
notification. An unverified token is not sufficient to enroll a device. Existing
direct-Firebase registration remains supported. Older released APKs need updating
to use this exchange; source changes alone do not upgrade installed apps.

See the [relay operator and server guide](https://github.com/nextcode4u/MakeReadyOS/blob/main/docs/PUSH_RELAY.md).
Publisher client identifiers are included in the APK; service-account keys and
server relay credentials are not. The generic non-Firebase build remains available.

### Direct Firebase

The updated API accepts `FCM_PROJECT_ID` and `FCM_SERVICE_ACCOUNT_FILE` (a server-only
credential path). Apply its Android-push migration and mount the credential privately
read-only. Existing VAPID variables remain for browser Web Push. Never put a sender
key in this checkout or APK. Open Alerts after signing in to enable, disable or test.

See the [provider guide](https://github.com/nextcode4u/MakeReadyOS/blob/main/docs/FIREBASE_ANDROID.md)
for project ownership, least privilege and acceptance gates.

References: [Google services plugin](https://firebase.google.com/docs/android/google-services-plugin-and-file),
[FCM Android setup](https://firebase.google.com/docs/cloud-messaging/android/get-started).
