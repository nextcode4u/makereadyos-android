# MakeReadyOS for Android

The familiar MakeReadyOS mobile workspace, packaged for Android with camera and
file selection, resident PDF saving/sharing/printing, and a locally bundled
interface that does not disappear when a connection drops.

**Current beta: 0.1.0-beta.3.** Android 8.0 or newer and a compatible MakeReadyOS
server are required. This is a beta, not a claim that every device or workflow
has been certified.

Beta.3 adds optional Firebase Messaging SDK build support and setup documentation.
Native notification delivery is not implemented yet. The standard release APK
does not contain an operator's Firebase configuration. Install over a previous
signed beta without uninstalling to retain local data.

## Getting Started

1. Install the signed APK from this repository's Releases.
2. Enter your organization's HTTPS domain and choose Continue.
3. Sign in using your existing MakeReadyOS account.

After setup, the app opens your workspace directly. Valid sessions survive normal
app restarts; expiry, revocation and logout still require sign-in. The native
frame follows the workspace theme. English and Spanish setup text are included.
No organization's domain, credentials or property configuration ships in the app.

**Moving from the earlier test app:** this beta has a permanent application ID
and installs alongside the old test app. It does not transfer that app's local
data. Sync all pending work in the old app before removing it, then enter your
domain and sign in to the beta. Uninstalling deletes local drafts, queued uploads
and sessions. Later signed betas update this app in place.

## Connection and Offline Behavior

- Interface files ship in the APK; live records and permissions come from your server.
- Selected photo uploads and preparation saves use the shared web app's durable queues. Wait for the saved/pending confirmation before closing.
- Queued work retries while the app is open with the same authorized account. Native background sync is not implemented.
- Cold startup still verifies the session online. The optional encrypted My Work summary is read-only, lasts eight hours, and is not an offline login.
- Clearing app data or uninstalling deletes pending work. Always sync first.

The offline summary excludes residents, access codes, notes and photos. Its
passphrase has no recovery or lockout mechanism. Upload queues are separate and
are not encrypted by that passphrase. Use a secured, trusted device.

## Current Limits

For native notification project setup, see the
[Firebase preparation guide](https://github.com/nextcode4u/MakeReadyOS/blob/main/docs/FIREBASE_ANDROID.md).
Creating a Firebase project alone does not enable push in beta.3. Keep downloaded
client configuration in nonpublish/google-services.json; server service-account
keys must never be placed in the APK. Optional SDK build setup is documented in
[FIREBASE.md](FIREBASE.md). SDK inclusion alone does not enable work alerts.

Root-hosted HTTPS on port 443 only. External onboarding links, general downloads,
native push notifications and in-app server switching are not yet supported.
Final-walk PDFs have a dedicated native export flow. To change servers, sync all
work, sign out, then reset app storage through Android settings and reconnect.

The app respects normal server permissions and can change operational records.
Test new releases with synthetic accounts before using live workflows.

## Build

Use JDK 21, Android SDK 36, build tools 35.0.0 and the web project's Node/npm
toolchain. Android Studio is optional. Set ANDROID_HOME.

This beta bundles MakeReadyOS commit
`adecbb4bf10af6ec6fdccd5a0578391aae27385e` from
[MakeReadyOS](https://github.com/nextcode4u/MakeReadyOS).
Use that revision and install its dependencies for a matching build.

```sh
node scripts/bundle-web.mjs /path/to/MakeReadyOS
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
```

Generated web assets are ignored. Rebundle after Gradle clean. The bundle records
its source revision and dirty state and includes the web license notices.
Vite bundling is not a substitute for the web project's typecheck and tests.

See [RELEASING.md](RELEASING.md) for signing and versioning and
[TESTING.md](TESTING.md) for device checks.

## Privacy and Licensing

HTTPS verification cannot be bypassed. Third-party cookies, mixed content and
unrestricted JavaScript bridges are disabled. Narrow PDF/theme channels are
restricted to the selected origin and main frame. Screenshots and Android backups
are disabled. No additional analytics or crash-reporting service is included.
Reports exported by the user can contain sensitive information and cannot be
recalled by the app.

AGPL-3.0-only; see [LICENSE](LICENSE) and [NOTICE](NOTICE). Third-party components
retain their own licenses. The permanent application ID is
`com.makereadyos.android`; the installed app is named MakeReadyOS.
