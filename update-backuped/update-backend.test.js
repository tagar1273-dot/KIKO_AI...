'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { after, before, test } = require('node:test');

const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'kiko-update-test-'));
const releasesPath = path.join(tmpDir, 'releases.json');
const artifactsDir = path.join(tmpDir, 'artifacts');
fs.mkdirSync(artifactsDir, { recursive: true });

const sampleApkBytes = Buffer.from('PK\x03\x04KIKO-TEST-APK-PAYLOAD-0123456789ABCDEF', 'utf8');
const sampleApkName = 'kiko-ai-v2.0.0.apk';
fs.writeFileSync(path.join(artifactsDir, sampleApkName), sampleApkBytes);

fs.writeFileSync(
  releasesPath,
  JSON.stringify({
    latestVersion: '1.0.0',
    versionCode: 1,
    apkDownloadUrl: 'https://updates.example.com/api/v1/updates/download/kiko-ai-v1.0.0.apk',
    releaseNotes: 'Initial release',
    minSupportedVersion: 1,
    updateType: 'OPTIONAL',
    sha256Checksum: '0'.repeat(64),
    releaseDate: '2026-11-26T00:00:00Z',
  }),
);

process.env.RELEASES_FILE = releasesPath;
process.env.ARTIFACTS_DIR = artifactsDir;
process.env.PUBLIC_BASE_URL = 'https://updates.example.com';
process.env.UPDATE_ADMIN_TOKEN = 'test-secret-admin-token';

const { createAppServer, computeFileSha256 } = require('./server');

let server;
let baseUrl;

before(async () => {
  server = createAppServer();
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const addr = server.address();
  baseUrl = `http://127.0.0.1:${addr.port}`;
});

after(async () => {
  await new Promise((resolve) => server.close(resolve));
  fs.rmSync(tmpDir, { recursive: true, force: true });
});

test('GET /api/v1/updates/latest returns release manifest', async () => {
  const res = await fetch(`${baseUrl}/api/v1/updates/latest`);
  assert.equal(res.status, 200);
  const json = await res.json();
  assert.equal(json.latestVersion, '1.0.0');
  assert.equal(json.versionCode, 1);
  assert.equal(json.updateType, 'OPTIONAL');
});

test('POST /api/v1/updates/publish enforces auth and computes SHA-256 for artifact', async () => {
  const unauth = await fetch(`${baseUrl}/api/v1/updates/publish`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ latestVersion: '2.0.0', versionCode: 2 }),
  });
  assert.equal(unauth.status, 401);

  const publishRes = await fetch(`${baseUrl}/api/v1/updates/publish`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: 'Bearer test-secret-admin-token',
    },
    body: JSON.stringify({
      latestVersion: '2.0.0',
      versionCode: 2,
      artifactFileName: sampleApkName,
      releaseNotes: 'Critical security & holographic update.',
      minSupportedVersion: 2,
      updateType: 'CRITICAL',
    }),
  });
  assert.equal(publishRes.status, 200);
  const body = await publishRes.json();
  assert.equal(body.published, true);
  assert.equal(body.manifest.updateType, 'CRITICAL');
  const expectedSha = await computeFileSha256(path.join(artifactsDir, sampleApkName));
  assert.equal(body.manifest.sha256Checksum, expectedSha);
});

test('GET /api/v1/updates/download supports HTTP Range resumable chunks', async () => {
  const firstHalf = await fetch(`${baseUrl}/api/v1/updates/download/${sampleApkName}`, {
    headers: { Range: 'bytes=0-15' },
  });
  assert.equal(firstHalf.status, 206);
  const buf1 = Buffer.from(await firstHalf.arrayBuffer());
  assert.equal(buf1.length, 16);

  const secondHalf = await fetch(`${baseUrl}/api/v1/updates/download/${sampleApkName}`, {
    headers: { Range: 'bytes=16-' },
  });
  assert.equal(secondHalf.status, 206);
  const buf2 = Buffer.from(await secondHalf.arrayBuffer());

  const combined = Buffer.concat([buf1, buf2]);
  assert.deepEqual(combined, sampleApkBytes);
});
