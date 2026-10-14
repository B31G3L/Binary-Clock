import { chromium } from 'playwright';
import fs from 'node:fs';
import path from 'node:path';

const dir = path.dirname(new URL(import.meta.url).pathname);
const b = await chromium.launch();
const p = await b.newPage({ viewport: { width: 1080, height: 1920 } });
p.on('pageerror', e => console.log('ERR', e.message));
await p.goto('file://' + path.join(dir, 'store.html'));
for (const lang of ['de', 'en']) {
  const n = await p.evaluate(l => window.renderLang(l), lang);
  await p.evaluate(() => document.fonts.ready);
  fs.mkdirSync(path.join(dir, 'out', 'store', lang), { recursive: true });
  const slides = await p.locator('.slide').all();
  for (let i = 0; i < n; i++) await slides[i].screenshot({ path: path.join(dir, 'out', 'store', lang, `${i + 1}.png`) });
  console.log(lang, n);
}
for (const lang of ['de', 'en']) {
  await p.evaluate(l => window.renderFeature(l), lang);
  await p.locator('.feat').screenshot({ path: path.join(dir, 'out', 'store', lang, 'featureGraphic.png') });
}
await b.close();
