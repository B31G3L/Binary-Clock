# Store-Grafiken

Erzeugt alle Grafiken für den Play Store und die App aus HTML/Canvas per Playwright.
`render.js` ist ein JavaScript-Nachbau von `ClockRenderer.kt` (gleiche Maße und Farben) –
**bei Änderungen am Renderer bitte hier mitziehen**, sonst passen die Bilder nicht mehr zur App.

## Einrichten

```bash
cd tools/store-graphics
npm install
npx playwright install chromium
```

## Erzeugen

| Befehl | Ergebnis in `out/` | Ziel im Projekt |
|---|---|---|
| `npm run store` | `store/<de|en>/1–7.png` (1080×1920), `featureGraphic.png` (1024×500) | `fastlane/metadata/android/<de-DE|en-US>/images/phoneScreenshots/` bzw. `images/` |
| `npm run assets` | `icon.png` (512×512), `widget_preview.png`, `wallpaper_thumbnail.png` | `images/icon.png` bzw. `app/src/main/res/drawable-nodpi/` |
| `npm run icons` | `icon-entwuerfe.png` – Vergleich der Icon-Ideen | `design/` |
| `npm run grid` | `stile-hell-dunkel.png` – alle Stile in Hell und Dunkel | `design/` |

## Dateien

- `render.js` – Uhr-Renderer (JS-Port von `ClockRenderer.kt`)
- `store.html` / `store.mjs` – Screenshots mit Überschrift, Hintergrund und Handy-Rahmen, Feature-Grafik.
  Texte stehen in `SLIDES` in `store.html`.
- `assets.html` / `assets.mjs` – Store-Icon, Widget-Vorschau, Wallpaper-Vorschaubild
  (die älteren, schlichten Screenshots darin werden nicht mehr verwendet)
- `icons.html` / `icons.mjs` – Icon-Entwürfe A–F
- `grid.html` / `grid.mjs` – Übersicht aller Stile
