import { chromium } from 'playwright';
import fs from 'node:fs';
import path from 'node:path';
const dir = path.dirname(new URL(import.meta.url).pathname);
fs.mkdirSync(path.join(dir, 'out'), { recursive: true });
const b = await chromium.launch(); const p = await b.newPage({ viewport: { width: 1200, height: 1000 } });
await p.goto('file://' + path.join(dir, 'icons.html'));
await p.locator('#w').screenshot({ path: path.join(dir, 'out', 'icon-entwuerfe.png') });
await b.close();
