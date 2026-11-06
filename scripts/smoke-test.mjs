import assert from 'node:assert/strict';

const base = 'http://127.0.0.1:4173';
const server = await import('node:child_process').then(({ spawn }) => spawn('npm', ['run', 'preview', '--', '--host', '127.0.0.1', '--port', '4173', '--strictPort'], { stdio: 'ignore' }));
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

try {
  let response;
  for (let attempt = 0; attempt < 40; attempt += 1) {
    try { response = await fetch(base); if (response.ok) break; } catch {}
    await sleep(500);
  }
  assert.ok(response?.ok, 'Vite preview server should respond successfully');
  const html = await response.text();
  assert.match(html, /RykoCollection/, 'homepage should contain the app title');
  assert.match(html, /site\.webmanifest/, 'homepage should advertise its PWA manifest');

  const manifestResponse = await fetch(base + '/site.webmanifest');
  assert.equal(manifestResponse.status, 200, 'PWA manifest should be served');
  const manifest = await manifestResponse.json();
  assert.equal(manifest.name, 'RykoCollection');
  assert.ok(manifest.icons?.some(icon => icon.src === '/logo.svg'), 'manifest should include the app icon');

  const iconResponse = await fetch(base + '/logo.svg');
  assert.equal(iconResponse.status, 200, 'PWA icon should be served');

  const entry = html.match(/src="([^"]+\.js)"/);
  assert.ok(entry, 'built HTML should include a JavaScript entry point');
  const scriptResponse = await fetch(new URL(entry[1], base));
  assert.equal(scriptResponse.status, 200, 'built JavaScript entry point should be served');

  console.log('Runtime smoke tests passed: homepage, manifest, icon and JavaScript entry.');
} finally {
  server.kill('SIGTERM');
}
