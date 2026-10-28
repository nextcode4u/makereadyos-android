# Beta Verification

Use synthetic accounts and an isolated server. Do not publish real resident data,
domains, credentials, access codes or local test logs.

## Automated Checks

Bundle the matching web source, then run:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

JVM tests cover domain validation, API compatibility/errors and cookie handling,
bounded file selection, PDF validation and theme choices. They do not certify
WebView interaction or device behavior.

## Device Checks

- First launch shows one domain field and Continue; invalid domains have actionable errors.
- Subsequent launches open the workspace without setup; Back backgrounds the app at its root.
- A valid session survives backgrounding and process restart. Logout and revocation do not.
- English/Spanish, dark/light themes, large text, TalkBack and keyboard insets remain usable.
- Slow networks and airplane mode preserve the local interface, without repeated reloads.
- An offline photo shows Pending upload, survives restart, uploads once and clears its pending indicator.
- Preparation checks and resident codes saved offline synchronize for the same account; conflicts stay visible.
- Gallery scroll and full-size photos work; camera/file-picker cancellation is harmless.
- Resident PDF blockers are visible. Successful PDFs offer Save, Open, Share and Print.
- Cancelled exports do not create downloads. Export errors remain visible.
- Account changes do not reveal another user's data or submit their pending edits.
- App upgrades preserve pending queues; uninstall/reset is explicitly treated as destructive.
- With a matching Firebase sender/relay and APK, trigger a synthetic assignment or
  final-walk correction. Background the app: the notification must name the event
  and property/unit, and expanded text must show the summary. Tap and verify the
  correct workspace opens. Repeat in the foreground: one descriptive toast, not a
  duplicate system alert. Verify private notes/access codes are absent. Repeat
  after logout/account switching to verify the old binding cannot display alerts.
- An older server without summary fields retains a generic fallback. This is
  compatibility behavior, not proof the new descriptive delivery is working.

Test the oldest supported Android version and a current physical device.
Record device, OS, app version and actual results privately. A passing build does
not mark these checks complete. Storage exhaustion, interrupted camera capture,
cloud file providers, revoked access and lost upload responses need explicit tests.

## Release Checks

- No debug flags, local CA, secrets, server details or operational fixtures in the APK or Git tree.
- Signature verifies against the stable release certificate.
- Version code increases; bundle metadata identifies a clean, retained web revision.
- License notices and corresponding source are available with the release.
- Installation and launch smoke tests pass on the signed artifact.
