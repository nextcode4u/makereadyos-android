import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

for (const activity of ['MainActivity', 'WebWorkspaceActivity']) {
  test(`${activity} permits screenshots for support and sharing`, () => {
    const source = readFileSync(new URL(`../app/src/main/java/com/makereadyos/android/${activity}.java`, import.meta.url), 'utf8');
    assert.doesNotMatch(source, /FLAG_SECURE|setRecentsScreenshotEnabled\(false\)/);
  });
}
