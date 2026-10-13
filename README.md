# Binary-Clock

Eine Binäruhr für Android – als **Live Wallpaper** (Homescreen + Sperrbildschirm) und als **Homescreen-Widget**.

## Features

- **4 Stile**
  - *Klassisch* – BCD, eine Punktspalte pro Ziffer, Bernstein auf Schwarz
  - *Binär* – Stunde/Minute/Sekunde als echte Binärzahlen (3 Zeilen)
  - *LED* – eckige „Leuchtdioden“ im Gehäuse, mit Ziffern als Lernhilfe
  - *Papier* – heller Bauhaus-Look, Sekunden in Rot
- Akzentfarbe frei wählbar
- Sekunden, Lernhilfe (Zahlen/Stellenwerte) und Datum ein-/ausblendbar
- Live Wallpaper tickt sekündlich, aber nur solange es sichtbar ist (akkuschonend)
- Widget aktualisiert sich zu jeder vollen Minute (exakt, wenn die Berechtigung „Wecker & Erinnerungen“ erteilt ist)
- Deutsch und Englisch

## Architektur

| Paket | Inhalt |
|---|---|
| `clock` | `ClockModel` (Stile, Einstellungen, Bit-Zerlegung) und `ClockRenderer` – **ein** Canvas-Renderer für Wallpaper, Widget und App-Vorschau |
| `data` | `SettingsRepository` (SharedPreferences + `StateFlow`) |
| `wallpaper` | `BinaryClockWallpaperService` |
| `widget` | `BinaryClockWidget` + `WidgetUpdater` (Bitmap-Rendering, Minuten-Alarm) |
| `ui` | Compose-Oberfläche (Material 3) |

Stack: Kotlin, Jetpack Compose, Material 3, manuelle DI über `AppContainer`, minSdk 26, targetSdk 36.

## Bauen

```bash
./gradlew assembleDebug
```

Jeder Push auf `main` baut per GitHub Actions eine Debug-APK (Artefakt `binary-clock-debug`), führt Lint aus und prüft den Release-Build.

## Veröffentlichen

Siehe [RELEASE.md](RELEASE.md). Store-Texte und -Grafiken (DE/EN) liegen unter `fastlane/metadata/android/`.

---
© Beigel Apps
