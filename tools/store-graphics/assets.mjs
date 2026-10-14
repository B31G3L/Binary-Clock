import { chromium } from 'playwright';
import fs from 'node:fs';
import path from 'node:path';

const dir = path.dirname(new URL(import.meta.url).pathname);
const out = path.join(dir, 'out');
const browser = await chromium.launch();
const page = await browser.newPage();
await page.goto('file://' + path.join(dir, 'assets.html'));
const files = await page.evaluate(() => window.outputs());
for (const [name, url] of Object.entries(files)) {
  const p = path.join(out, name);
  fs.mkdirSync(path.dirname(p), { recursive: true });
  fs.writeFileSync(p, Buffer.from(url.split(',')[1], 'base64'));
  console.log(name);
}
await browser.close();
