import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, mkdirSync, copyFileSync, writeFileSync } from 'node:fs';
import { resolve, dirname, basename } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
export const profiles = ['debug', 'firebase-debug', 'release', 'firebase-release'];
export function parseArgs(args) {
  const options = { profile: 'debug' };
  for (let i = 0; i < args.length; i++) {
    const arg = args[i];
    if (arg === '--help') { options.help = true; continue; }
    if (!['--profile', '--web-source', '--config'].includes(arg) || !args[i + 1] || args[i + 1].startsWith('--'))
      throw new Error('Use --profile, --web-source or --config with a value; see --help.');
    options[arg.slice(2)] = args[++i];
  }
  if (!profiles.includes(options.profile)) throw new Error('Unknown profile. Choose: ' + profiles.join(', '));
  return options;
}
export function gradleArgs(profile) {
  if (!profiles.includes(profile)) throw new Error('Unknown profile');
  const release = profile.endsWith('release');
  return ['--no-daemon', '--max-workers=2', '-PlocalEmulatorTls=false',
    '-PfirebaseNotifications=' + profile.startsWith('firebase-'), 'testDebugUnitTest',
    release ? 'lintRelease' : 'lintDebug', release ? 'assembleRelease' : 'assembleDebug'];
}
export function readConfig(path, explicit = false) {
  if (!existsSync(path)) {
    if (explicit) throw new Error('The requested build configuration file does not exist.');
    return {};
  }
  let config;
  try { config = JSON.parse(readFileSync(path, 'utf8')); }
  catch { throw new Error('Build configuration must be valid JSON.'); }
  const fields = ['webSource', 'javaHome', 'androidHome', 'keystore', 'keyAlias', 'storePasswordFile', 'keyPasswordFile'];
  if (!config || Array.isArray(config) || typeof config !== 'object'
      || Object.entries(config).some(([key, value]) => !fields.includes(key) || typeof value !== 'string' || !value.trim()))
    throw new Error('Invalid build configuration. Use the example fields; passwords belong in separate private files.');
  return config;
}
function run(command, args, cwd, env, label) {
  try { execFileSync(command, args, { cwd, env, stdio: 'inherit' }); }
  catch { throw new Error(label + ' failed. No APK was exported by this run.'); }
}
export function main(args = process.argv.slice(2)) {
  const options = parseArgs(args);
  if (options.help) {
    console.log('Usage: node scripts/build.mjs [--profile debug|firebase-debug|release|firebase-release]\n'
      + '  [--web-source /path/to/MakeReadyOS] [--config /path/to/private-build.json]\n'
      + 'Defaults: debug; optional .local/build.json; sibling ../MakeReadyOS checkout.\n'
      + 'Paths in configuration are relative to the Android repository, not the config file.\n'
      + 'Outputs: ignored release-artifacts/ (generic) or nonpublish/builds/ (Firebase).\n'
      + 'Does not install tools, publish, deploy, or generate signing keys.');
    return;
  }
  const config = readConfig(resolve(options.config || resolve(root, '.local/build.json')), Boolean(options.config));
  const source = options['web-source'] ? resolve(options['web-source']) : resolve(root, config.webSource || '../MakeReadyOS');
  const web = resolve(source, 'apps/web');
  const env = { ...process.env };
  if (config.javaHome) env.JAVA_HOME = resolve(root, config.javaHome);
  if (config.androidHome) env.ANDROID_HOME = resolve(root, config.androidHome);
  if (!env.ANDROID_HOME && env.ANDROID_SDK_ROOT) env.ANDROID_HOME = env.ANDROID_SDK_ROOT;
  if (!env.JAVA_HOME || !existsSync(resolve(env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java')))
    throw new Error('Set JAVA_HOME (JDK 21) or javaHome in .local/build.json.');
  if (!env.ANDROID_HOME || !existsSync(resolve(env.ANDROID_HOME, 'platforms/android-36/android.jar')))
    throw new Error('Set ANDROID_HOME or androidHome to an SDK with Android platform 36.');
  if (!existsSync(resolve(env.ANDROID_HOME, 'build-tools/35.0.0')))
    throw new Error('Install Android build-tools 35.0.0 before building.');
  for (const file of ['src/main.tsx', 'node_modules/vite/bin/vite.js', 'node_modules/typescript/bin/tsc'])
    if (!existsSync(resolve(web, file))) throw new Error('Provide a MakeReadyOS checkout with web dependencies installed: npm --prefix <checkout>/apps/web ci');
  const firebase = options.profile.startsWith('firebase-');
  const release = options.profile.endsWith('release');
  if (firebase && !existsSync(resolve(root, 'nonpublish/google-services.json')))
    throw new Error('Firebase builds require nonpublish/google-services.json. Never use a server service-account key.');
  if (release) {
    if (execFileSync('git', ['status', '--porcelain'], { cwd: source, encoding: 'utf8' }).trim())
      throw new Error('Release builds require a clean web source checkout. Commit reviewed changes first.');
    if (config.keystore) env.ANDROID_KEYSTORE = resolve(root, config.keystore);
    if (config.keyAlias) env.ANDROID_KEY_ALIAS = config.keyAlias;
    for (const [field, key] of [['storePasswordFile', 'ANDROID_STORE_PASSWORD'], ['keyPasswordFile', 'ANDROID_KEY_PASSWORD']]) {
      if (config[field]) {
        try { env[key] = readFileSync(resolve(root, config[field]), 'utf8').replace(/\r?\n$/, ''); }
        catch { throw new Error('Unable to read the private signing password file for ' + field); }
      }
    }
    for (const key of ['ANDROID_KEYSTORE', 'ANDROID_KEY_ALIAS', 'ANDROID_STORE_PASSWORD', 'ANDROID_KEY_PASSWORD'])
      if (!env[key]) throw new Error('Missing release signing input: ' + key + '. See BUILDING.md.');
    if (!existsSync(env.ANDROID_KEYSTORE)) throw new Error('Release keystore not found. Restore your existing key; do not replace it.');
  }
  console.log('Building ' + options.profile + '; private configuration values are not printed.');
  run(process.execPath, [resolve(web, 'node_modules/typescript/bin/tsc'), '-b'], web, env, 'Web typecheck');
  run(process.execPath, [resolve(root, 'scripts/bundle-web.mjs'), source], root, env, 'Web bundle');
  const gradle = gradleArgs(options.profile);
  if (process.platform === 'win32') {
    // Only fixed, validated preset arguments go through cmd; paths and secrets use cwd/env.
    run('cmd.exe', ['/d', '/s', '/c', 'gradlew.bat ' + gradle.join(' ')], root, env, 'Android checks/build');
  } else run(resolve(root, 'gradlew'), gradle, root, env, 'Android checks/build');
  const version = readFileSync(resolve(root, 'app/build.gradle'), 'utf8').match(/versionName\s+'([A-Za-z0-9._-]+)'/)?.[1];
  if (!version) throw new Error('Cannot determine APK version. No APK exported.');
  const variant = release ? 'release' : 'debug';
  const apk = resolve(root, `app/build/outputs/apk/${variant}/app-${variant}.apk`);
  run(resolve(env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java'),
    ['-jar', resolve(env.ANDROID_HOME, 'build-tools/35.0.0/lib/apksigner.jar'), 'verify', apk], root, env, 'APK signature verification');
  const output = resolve(root, firebase ? 'nonpublish/builds' : 'release-artifacts');
  mkdirSync(output, { recursive: true });
  const name = `makereadyos-android-${version}-${options.profile}.apk`;
  const destination = resolve(output, name);
  copyFileSync(apk, destination);
  const sha = createHash('sha256').update(readFileSync(destination)).digest('hex');
  writeFileSync(destination + '.sha256', `${sha}  ${basename(destination)}\n`);
  console.log(`APK and SHA-256 saved to ${firebase ? 'nonpublish/builds' : 'release-artifacts'}/${name}`);
  if (firebase) console.log('Operator-specific APK: keep private unless its distribution is explicitly approved. Server sender configuration and a live phone test are still required.');
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try { main(); }
  catch (error) { console.error(error.message); process.exitCode = 1; }
}
