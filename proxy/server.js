// Agency HQ proxy — forwards chat requests to the Anthropic API.
//
// Why this file exists: the site's agents can use tools (code execution,
// web search, page reading) and smart routing. For that to work, the proxy
// MUST forward the `tools` field (and stream/tool params) through to
// Anthropic — a proxy that only passes {model,max_tokens,system,messages}
// silently strips tools and the agents fall back to plain chat.
//
// Deploy on Railway (or any Node host):
//   1. Put this file + package.json in a repo/service.
//   2. Set env var ANTHROPIC_API_KEY.
//   3. Start command: node server.js
//
// Node 18+ (built-in fetch). No dependencies.

const API_URL = 'https://api.anthropic.com/v1/messages';
const API_KEY = process.env.ANTHROPIC_API_KEY;
// Optional: enables the /api/image route. Get this from the OpenAI API
// platform (platform.openai.com) — it is NOT the same as a ChatGPT
// subscription. Billed per image on your OpenAI API account.
const OPENAI_API_KEY = process.env.OPENAI_API_KEY;
const IMAGE_MODEL = process.env.IMAGE_MODEL || 'gpt-image-1';
const PORT = process.env.PORT || 3000;

// Lock this to your site in production instead of '*'
const ALLOW_ORIGIN = process.env.ALLOW_ORIGIN || '*';

const CORS = {
  'Access-Control-Allow-Origin': ALLOW_ORIGIN,
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type',
};

// Only these fields are forwarded — note `tools` and `tool_choice` are included.
const PASS = ['model', 'max_tokens', 'system', 'messages', 'tools', 'tool_choice', 'temperature', 'top_p', 'stop_sequences', 'stream'];

const J = (res, status, obj) => { res.writeHead(status, { ...CORS, 'Content-Type': 'application/json' }); res.end(JSON.stringify(obj)); };

// POST /api/image  { prompt, size? } -> OpenAI image model -> { data:[{b64_json}] }
async function handleImage(res, payload) {
  if (!OPENAI_API_KEY) return J(res, 501, { error: { message: 'Image generation not configured: add OPENAI_API_KEY (from the OpenAI API platform) to this proxy.' } });
  const prompt = String(payload.prompt || '').slice(0, 4000);
  if (!prompt) return J(res, 400, { error: { message: 'Missing prompt' } });
  const size = ['1024x1024', '1024x1536', '1536x1024', 'auto'].includes(payload.size) ? payload.size : '1024x1024';
  try {
    const up = await fetch('https://api.openai.com/v1/images/generations', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + OPENAI_API_KEY },
      body: JSON.stringify({ model: IMAGE_MODEL, prompt, size, n: 1 }),
    });
    const text = await up.text();
    res.writeHead(up.status, { ...CORS, 'Content-Type': 'application/json' });
    res.end(text);
  } catch (err) {
    J(res, 502, { error: { message: 'Image upstream error: ' + (err && err.message || err) } });
  }
}

require('http').createServer(async (req, res) => {
  if (req.method === 'OPTIONS') { res.writeHead(204, CORS); return res.end(); }
  const isChat = req.url.startsWith('/api/chat');
  const isImage = req.url.startsWith('/api/image');
  if (req.method !== 'POST' || (!isChat && !isImage)) {
    res.writeHead(404, CORS); return res.end('Not found');
  }
  if (isChat && !API_KEY) return J(res, 500, { error: { message: 'Server missing ANTHROPIC_API_KEY' } });

  let body = '';
  req.on('data', c => { body += c; if (body.length > 12e6) req.destroy(); });
  req.on('end', async () => {
    let payload;
    try { payload = JSON.parse(body); } catch (e) {
      return J(res, 400, { error: { message: 'Invalid JSON' } });
    }
    if (isImage) return handleImage(res, payload);
    const fwd = {};
    for (const k of PASS) if (payload[k] !== undefined) fwd[k] = payload[k];
    if (!fwd.max_tokens) fwd.max_tokens = 4096;

    try {
      const upstream = await fetch(API_URL, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'x-api-key': API_KEY,
          'anthropic-version': '2023-06-01',
        },
        body: JSON.stringify(fwd),
      });

      // Stream passthrough (SSE) when the client asked for it
      if (fwd.stream && upstream.body) {
        res.writeHead(upstream.status, { ...CORS, 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache' });
        const reader = upstream.body.getReader();
        for (;;) {
          const { done, value } = await reader.read();
          if (done) break;
          res.write(Buffer.from(value));
        }
        return res.end();
      }

      const text = await upstream.text();
      res.writeHead(upstream.status, { ...CORS, 'Content-Type': 'application/json' });
      res.end(text);
    } catch (err) {
      res.writeHead(502, { ...CORS, 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: { message: 'Upstream error: ' + (err && err.message || err) } }));
    }
  });
}).listen(PORT, () => console.log('Agency proxy on :' + PORT));
