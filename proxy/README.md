# Agency HQ proxy

This proxy forwards the site's chat requests to the Anthropic API. Unlike a
minimal proxy, it **forwards the `tools` field**, which is required for the
agents' real tools (code execution, web search, page reading) and works with
smart routing.

## Deploy on Railway

1. Push this `proxy/` folder to a repo (or a Railway service).
2. In Railway: **New Project → Deploy from repo** (or empty service).
3. Set the environment variable **`ANTHROPIC_API_KEY`** to your key.
4. (Optional) Set **`ALLOW_ORIGIN`** to `https://emransm877.github.io` to lock
   CORS to your site.
5. Start command: `node server.js` (or `npm start`).

Railway gives you a public URL like
`https://agency-proxy-production.up.railway.app`. The site already points at
`/api/chat` on that host — if your URL differs, update `PROXY` at the top of
the `<script>` in `index.html`.

## How to know it worked

Open the site, keep **🔧 TOOLS** on, and ask an agent to *"calculate 12345 × 6789
by running code"*. If you see a `🔧 running code` chip and the exact number
`83810205`, the proxy is forwarding tools correctly. If instead you see
`⚠ tools not supported by this proxy`, the proxy is still stripping the field —
redeploy with this `server.js`.

## Image generation (optional)

The agents' `generate_image` tool posts to `/api/image` on this proxy, which
calls OpenAI's image model. To enable it:

1. Get an **OpenAI API key** from the OpenAI **API platform**
   (platform.openai.com → API keys). **Important:** this is a separate,
   pay-as-you-go API product. A **ChatGPT / ChatGPT Enterprise** subscription
   does **not** include it and cannot be used here — only an API key works.
2. Set the env var **`OPENAI_API_KEY`** on this proxy.
3. (Optional) `IMAGE_MODEL` — defaults to `gpt-image-1`.

Until a key is set, `/api/image` returns HTTP 501 and the app shows a friendly
"image generation not set up" message instead of failing. Vector graphics via
`create_file` (SVG) keep working with no key.

## Notes

- Node 18+ (uses built-in `fetch`). No dependencies.
- Streams responses when the client requests it.
- Accepts request bodies up to ~12 MB (for image/PDF uploads).
