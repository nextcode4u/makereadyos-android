# Build Presets

These scripts are safe to publish. They contain no operator domains, Firebase
configuration, passwords or signing keys. They do not publish or deploy anything.

## Prerequisites

Use Node 24, Git, JDK 21 (`JAVA_HOME`), Android platform 36 and build-tools 35.0.0
(`ANDROID_HOME`). Android Studio is optional. Install the web dependencies with
`npm --prefix /path/to/MakeReadyOS/apps/web ci`. The wrapper does not download or
install tooling; Gradle can download its dependencies on first use.

Run from the Android checkout on Linux, macOS or Windows (PowerShell):

```sh
node scripts/build.mjs --profile debug --web-source /path/to/MakeReadyOS
node scripts/build.mjs --profile firebase-debug --web-source /path/to/MakeReadyOS
node scripts/build.mjs --profile release --web-source /path/to/MakeReadyOS
node scripts/build.mjs --profile firebase-release --web-source /path/to/MakeReadyOS
```

Omitting `--profile` selects generic debug, even if Firebase configuration exists.
Quote paths containing spaces. Each preset typechecks and bundles the web UI,
runs Android unit tests and variant lint, builds the APK and verifies its signature.
The scripts use two
Gradle workers, no persistent daemon and no emulator-specific trust certificates.
Generic outputs and SHA-256 files go to ignored `release-artifacts/`; Firebase
outputs go to ignored `nonpublish/builds/`. Filenames distinguish the presets.
Repeated builds of the same version/preset replace that local artifact.

## Remember Private Settings

Copy `scripts/build-config.example.json` to `.local/build.json`, which is ignored.
Adjust the web path and remove signing fields if you only need debug builds.
Optional `javaHome` and `androidHome` override environment settings. Paths in this
JSON are relative to the Android repository; absolute paths also work. A command-line
`--web-source` overrides the saved web path and is relative to your current directory.
Use `--config /private/build.json` for another private configuration file.

After that, the usual command is simply:

```sh
node scripts/build.mjs --profile firebase-release
```

Release presets require a clean web checkout and your existing release signing key.
Passwords are read from private files or the existing `ANDROID_STORE_PASSWORD` and
`ANDROID_KEY_PASSWORD` environment variables, never command-line arguments.
`ANDROID_KEYSTORE` and `ANDROID_KEY_ALIAS` remain supported. Saved configuration
overrides these environment values. A single trailing newline is removed from a
password file. Protect the files with owner-only permissions and back them up securely.
No signing key is generated automatically. Never replace an established release key.
Increment the app version before distribution; see [RELEASING.md](RELEASING.md).

Firebase presets additionally require `nonpublish/google-services.json`; Gradle
validates its package and rejects server credentials. This client configuration is
embedded in the resulting APK. Keep operator-specific APKs private unless distribution
is approved. A successful build does not prove delivery: configure the matching
server sender and perform the [Firebase checks](FIREBASE.md) on a real phone.

## Script Checks

```sh
node scripts/build.mjs --help
node --test scripts/build.test.mjs
```

These checks need only Node and do not access private settings or run a build.
Public CI uses the generic debug preset, not a Firebase project or release key.
