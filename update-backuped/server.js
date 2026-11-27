'use strict';

const crypto = require('node:crypto');
const fs = require('node:fs');
const http = require('node:http');
const https = require('node:https');
const path = require('node:path');

const PORT = Number(process.env.PORT || 8443);
const HOST = process.env.HOST || '0.0.0.0';
const RELEASES_FILE = process.env.RELEASES_FILE || path.join(__dirname, 'releases.json');
const ARTIFACTS_DIR = process.env.ARTIFACTS_DIR || path.join(__dirname, 'artifacts');
const PUBLIC_BASE_URL = (process.env.PUBLIC_BASE_URL || '').replace(/\/+$/, '');
const ADMIN_TOKEN = process.env.UPDATE_ADMIN_TOKEN || '';
const TLS_KEY_PATH = process.env.TLS_KEY_PATH || '';
const TLS_CERT_PATH = process.env.TLS_CERT_PATH || '';

if (!fs.existsSync(ARTIFACTS_DIR)) {
  fs.mkdirSync(ARTIFACTS_DIR, { recursive: true });
}

function setSecurityHeaders(res) {
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('X-Frame-Options', 'DENY');
  res.setHeader('Strict-Transport-Security', 'max-age=31536000; includeSubDomains');
  res.setHeader('Cache-Control', 'no-store');
}

function sendJson(res, statusCode, payload) {
  setSecurityHeaders(res);
  res.writeHead(statusCode, { 'Content-Type': 'application/json; charset=utf-8' });
  res.end(JSON.stringify(payload, null, 2));
}

function readReleaseManifest() {
  const raw = fs.readFileSync(RELEASES_FILE, 'utf8');
  return JSON.parse(raw);
}

function computeFileSha256(filePath) {
  return new Promise((resolve, reject) => {
    const hash = crypto.createHash('sha256');
    const stream = fs.createReadStream(filePath);
    stream.on('data', (chunk) => hash.update(chunk));
    stream.on('end', () => resolve(hash.digest('hex').toLowerCase()));
    stream.on('error', reject);
  });
}

function timingSafeBearerEquals(authHeader, expectedToken) {
  if (!expectedToken || typeof authHeader !== 'string' || !authHeader.startsWith('Bearer ')) {
    return false;
  }
  const provided = authHeader.slice('Bearer '.length).trim();
  const a = Buffer.from(provided, 'utf8');
  const b = Buffer.from(expectedToken, 'utf8');
  if (a.length !== b.length) return false;
  return crypto.timingSafeEqual(a, b);
}

function readJsonBody(req, maxBytes = 64 * 1024) {
  return new Promise((resolve, reject) => {
    let total = 0;
    const chunks = [];
    req.on('data', (chunk) => {
      total += chunk.length;
      if (total > maxBytes) {
        reject(new Error('Request body too large'));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on('end', () => {
      try {
        resolve(JSON.parse(Buffer.concat(chunks).toString('utf8')));
      } catch (err) {
        reject(new Error('Invalid JSON payload'));
      }
    });
    req.on('error', reject);
  });
}

function resolveDownloadUrl(manifest, req) {
  if (typeof manifest.apkDownloadUrl === 'string' && manifest.apkDownloadUrl.startsWith('https://')) {
    return manifest.apkDownloadUrl;
  }
  if (manifest.artifactFileName && PUBLIC_BASE_URL.startsWith('https://')) {
    return `${PUBLIC_BASE_URL}/api/v1/updates/download/${encodeURIComponent(manifest.artifactFileName)}`;
  }
  const host = req.headers.host;
  if (manifest.artifactFileName && host) {
    return `https://${host}/api/v1/updates/download/${encodeURIComponent(manifest.artifactFileName)}`;
  }
  return manifest.apkDownloadUrl;
}

function handleApkDownload(req, res, rawFileName) {
  const safeName = path.basename(decodeURIComponent(rawFileName));
  if (!safeName.endsWith('.apk')) {
    sendJson(res, 400, { error: 'Only signed .apk artifacts are served.' });
    return;
  }
  const resolvedPath = path.resolve(ARTIFACTS_DIR, safeName);
  if (!resolvedPath.startsWith(path.resolve(ARTIFACTS_DIR) + path.sep) || !fs.existsSync(resolvedPath)) {
    sendJson(res, 404, { error: 'APK artifact not found.' });
    return;
  }

  const stat = fs.statSync(resolvedPath);
  const fileSize = stat.size;
  const rangeHeader = req.headers.range;

  setSecurityHeaders(res);
  res.setHeader('Accept-Ranges', 'bytes');
  res.setHeader('Content-Type', 'application/vnd.android.package-archive');
  res.setHeader('Content-Disposition', `attachment; filename="${safeName}"`);

  if (rangeHeader) {
    const match = /^bytes=(\d+)-(\d*)$/.exec(rangeHeader.trim());
    if (!match) {
      res.writeHead(416, { 'Content-Range': `bytes */${fileSize}` });
      res.end();
      return;
    }
    const start = Number(match[1]);
    const end = match[2] ? Math.min(Number(match[2]), fileSize - 1) : fileSize - 1;

    if (Number.isNaN(start) || Number.isNaN(end) || start >= fileSize || start > end) {
      res.writeHead(416, { 'Content-Range': `bytes */${fileSize}` });
      res.end();
      return;
    }

    res.writeHead(206, {
      'Content-Range': `bytes ${start}-${end}/${fileSize}`,
      'Content-Length': end - start + 1,
    });
    fs.createReadStream(resolvedPath, { start, end }).pipe(res);
    return;
  }

  res.writeHead(200, { 'Content-Length': fileSize });
  fs.createReadStream(resolvedPath).pipe(res);
}

async function handlePublish(req, res) {
  if (!ADMIN_TOKEN) {
    sendJson(res, 503, { error: 'UPDATE_ADMIN_TOKEN is not configured on the server.' });
    return;
  }
  if (!timingSafeBearerEquals(req.headers.authorization, ADMIN_TOKEN)) {
    sendJson(res, 401, { error: 'Unauthorized release publisher.' });
    return;
  }

  let body;
  try {
    body = await readJsonBody(req);
  } catch (err) {
    sendJson(res, 400, { error: err.message });
    return;
  }

  const latestVersion = String(body.latestVersion || '').trim();
  const versionCode = Number(body.versionCode);
  const minSupportedVersion = Number(body.minSupportedVersion || 1);
  const updateType = String(body.updateType || 'OPTIONAL').toUpperCase() === 'CRITICAL' ? 'CRITICAL' : 'OPTIONAL';
  const releaseNotes = String(body.releaseNotes || '').trim();
  const releaseDate = String(body.releaseDate || new Date().toISOString()).trim();
  let apkDownloadUrl = String(body.apkDownloadUrl || '').trim();
  let sha256Checksum = String(body.sha256Checksum || '').trim().toLowerCase();

  if (!latestVersion || !Number.isInteger(versionCode) || versionCode <= 0) {
    sendJson(res, 400, { error: 'latestVersion and positive integer versionCode are required.' });
    return;
  }

  if (body.artifactFileName) {
    const safeArtifact = path.basename(String(body.artifactFileName));
    const artifactPath = path.resolve(ARTIFACTS_DIR, safeArtifact);
    if (!fs.existsSync(artifactPath)) {
      sendJson(res, 404, { error: `Artifact ${safeArtifact} not found in artifacts directory.` });
      return;
    }
    sha256Checksum = await computeFileSha256(artifactPath);
    if (!apkDownloadUrl && PUBLIC_BASE_URL.startsWith('https://')) {
      apkDownloadUrl = `${PUBLIC_BASE_URL}/api/v1/updates/download/${encodeURIComponent(safeArtifact)}`;
    }
  }

  if (!apkDownloadUrl.startsWith('https://')) {
    sendJson(res, 400, { error: 'apkDownloadUrl must use secure HTTPS.' });
    return;
  }

  if (!/^[a-f0-9]{64}$/.test(sha256Checksum)) {
    sendJson(res, 400, { error: 'sha256Checksum must be a valid 64-character SHA-256 hex digest.' });
    return;
  }

  const updated = {
    latestVersion,
    versionCode,
    apkDownloadUrl,
    releaseNotes: releaseNotes || `KIKO AI v${latestVersion} release`,
    minSupportedVersion: Number.isInteger(minSupportedVersion) && minSupportedVersion > 0 ? minSupportedVersion : 1,
    updateType,
    sha256Checksum,
    releaseDate,
  };

  const tmpPath = `${RELEASES_FILE}.tmp`;
  fs.writeFileSync(tmpPath, JSON.stringify(updated, null, 2) + '\n', 'utf8');
  fs.renameSync(tmpPath, RELEASES_FILE);

  sendJson(res, 200, { published: true, manifest: updated });
}

function createAppServer() {
  const handler = async (req, res) => {
    try {
      const url = new URL(req.url, 'https://localhost');
      if (req.method === 'GET' && url.pathname === '/health') {
        sendJson(res, 200, { status: 'ok', service: 'kiko-ai-update-backend' });
        return;
      }

      if (req.method === 'GET' && url.pathname === '/api/v1/updates/latest') {
        const manifest = readReleaseManifest();
        sendJson(res, 200, {
          ...manifest,
          apkDownloadUrl: resolveDownloadUrl(manifest, req),
        });
        return;
      }

      if (req.method === 'GET' && url.pathname.startsWith('/api/v1/updates/download/')) {
        const fileName = url.pathname.slice('/api/v1/updates/download/'.length);
        handleApkDownload(req, res, fileName);
        return;
      }

      if (req.method === 'POST' && url.pathname === '/api/v1/updates/publish') {
        await handlePublish(req, res);
        return;
      }

      sendJson(res, 404, { error: 'Endpoint not found.' });
    } catch (err) {
      sendJson(res, 500, { error: err.message || 'Internal server error' });
    }
  };

  if (TLS_KEY_PATH && TLS_CERT_PATH) {
    return https.createServer(
      {
        key: fs.readFileSync(TLS_KEY_PATH),
        cert: fs.readFileSync(TLS_CERT_PATH),
      },
      handler,
    );
  }
  return http.createServer(handler);
}

if (require.main === module) {
  const server = createAppServer();
  server.listen(PORT, HOST, () => {
    const mode = TLS_KEY_PATH && TLS_CERT_PATH ? 'HTTPS' : 'HTTP (place behind TLS reverse proxy in production)';
    console.log(`KIKO AI Update Backend listening on ${HOST}:${PORT} [${mode}]`);
  });
}

module.exports = {
  createAppServer,
  computeFileSha256,
};
