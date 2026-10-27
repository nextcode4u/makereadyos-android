import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, writeFileSync, rmSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const source = resolve(process.argv[2] || '../PIMP');
const web = resolve(source, 'apps/web');
if (!existsSync(resolve(web, 'src/main.tsx'))) throw new Error('Pass the MakeReadyOS source checkout path.');
if (!readFileSync(resolve(web, 'src/main.tsx'), 'utf8').includes('VITE_ANDROID_SHELL'))
  throw new Error('This source checkout lacks the bundled Android service-worker guard.');
const output = resolve(root, 'app/build/generated/webAssets/web');
mkdirSync(output, { recursive: true });
execFileSync(process.execPath, [resolve(web, 'node_modules/vite/bin/vite.js'), 'build', '--outDir', output, '--emptyOutDir'], {
  cwd: web, stdio: 'inherit', env: { ...process.env, VITE_API_BASE_URL: '/api', VITE_ANDROID_SHELL: 'true' }
});
// A packaged shell must never install the website's independent shell cache.
rmSync(resolve(output, 'sw.js'), { force: true });
for (const name of ['LICENSE', 'NOTICE']) {
  if (existsSync(resolve(source, name))) writeFileSync(resolve(output, name), readFileSync(resolve(source, name)));
}
const commit = execFileSync('git', ['rev-parse', 'HEAD'], { cwd: source, encoding: 'utf8' }).trim();
const dirty = execFileSync('git', ['status', '--porcelain'], { cwd: source, encoding: 'utf8' }).trim().length > 0;
writeFileSync(resolve(output, 'bundle-info.json'), JSON.stringify({ sourceCommit: commit, sourceDirty: dirty, bundled: true }, null, 2));
console.log('Web UI bundled. No deployment settings or source checkout path embedded.');
