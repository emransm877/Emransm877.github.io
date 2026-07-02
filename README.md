# THE AGENCY · HQ

A single-page web app hosted on GitHub Pages.

**Live site:** https://emransm877.github.io

## Overview

The entire app lives in [`index.html`](index.html) — a self-contained page with inline CSS and JavaScript, no build step required. It features a sci-fi styled interface (Orbitron / Space Grotesk / Space Mono fonts) with a fixed header, search bar, and mobile-friendly bottom navigation.

Chat functionality is powered by a backend proxy hosted on [Railway](https://railway.app).

## Development

There's no build process — just edit `index.html` and open it in a browser:

```bash
# Serve locally
python3 -m http.server 8000
# then open http://localhost:8000
```

## Deployment

Pushing to the `master` branch automatically deploys the site via GitHub Pages.
