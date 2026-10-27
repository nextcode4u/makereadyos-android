import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { parseArgs, profiles, gradleArgs, readConfig } from './build.mjs';

test('build defaults are generic and every preset explicitly sets Firebase and TLS policy', () => {
  assert.equal(parseArgs([]).profile, 'debug');
  for (const profile of profiles) {
    const args = gradleArgs(profile);
    assert.ok(args.includes('-PlocalEmulatorTls=false'));
    assert.ok(args.includes('-PfirebaseNotifications=' + profile.startsWith('firebase-')));
    assert.ok(args.includes('testDebugUnitTest'));
    assert.ok(args.includes(profile.endsWith('release') ? 'lintRelease' : 'lintDebug'));
    assert.ok(args.includes(profile.endsWith('release') ? 'assembleRelease' : 'assembleDebug'));
  }
});
test('arguments reject unknown presets, missing values and shell/Gradle injection', () => {
  for (const args of [['--profile', 'release;echo'], ['--profile'], ['--password', 'secret'], ['--web-source', '--help']])
    assert.throws(() => parseArgs(args));
  assert.equal(parseArgs(['--web-source', 'path with spaces', '--profile', 'firebase-release'])['web-source'], 'path with spaces');
  assert.equal(parseArgs(['--help']).help, true);
});
test('configuration rejects inline passwords and malformed data without exposing contents', () => {
  const dir = mkdtempSync(join(tmpdir(), 'mros-build-'));
  const file = join(dir, 'config.json');
  try {
    assert.deepEqual(readConfig(file), {});
    assert.throws(() => readConfig(file, true));
    for (const contents of ['{"password":"DO-NOT-PRINT"}', 'DO-NOT-PRINT', 'null', '[]', '{"webSource":42}']) {
      writeFileSync(file, contents);
      assert.throws(() => readConfig(file), error => !error.message.includes('DO-NOT-PRINT'));
    }
    writeFileSync(file, '{"webSource":"../MakeReadyOS","storePasswordFile":".local/password"}');
    assert.equal(readConfig(file).storePasswordFile, '.local/password');
  } finally { rmSync(dir, { recursive: true, force: true }); }
});
