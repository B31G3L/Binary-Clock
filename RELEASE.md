# Veröffentlichen im Play Store

## 1. Upload-Schlüssel erzeugen (einmalig)

```bash
keytool -genkeypair -v \
  -keystore binary-clock-upload.jks \
  -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000
```

**Die Datei und beide Passwörter sicher aufbewahren** (z. B. Passwortmanager + Backup).
Mit Play App Signing ist das nur der *Upload*-Schlüssel – geht er verloren, kann Google ihn
zurücksetzen, aber das dauert ein paar Tage.

## 2. Secrets im GitHub-Repo anlegen

Repo → *Settings* → *Secrets and variables* → *Actions* → *New repository secret*:

| Name | Inhalt |
|---|---|
| `KEYSTORE_BASE64` | Ausgabe von `base64 -w0 binary-clock-upload.jks` (macOS: `base64 -i binary-clock-upload.jks`; Windows PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("binary-clock-upload.jks"))`) |
| `KEYSTORE_PASSWORD` | Keystore-Passwort |
| `KEY_ALIAS` | `upload` |
| `KEY_PASSWORD` | Schlüssel-Passwort |

## 3. Release bauen

- Version in `app/build.gradle.kts` erhöhen (`versionCode` +1, `versionName`)
- Committen, dann Tag pushen: `git tag v1.0.0 && git push --tags`
  (oder *Actions* → *Release* → *Run workflow*)
- Ergebnis: Artefakt `binary-clock-release-aab` mit `app-release.aab` und `mapping.txt`

## 4. Play Console

- Neue App anlegen, Paketname `com.beigel.binaryclock`, Play App Signing aktiv lassen
- Store-Eintrag: Texte und Grafiken liegen unter `fastlane/metadata/android/`
- Datensicherheit: keine Daten erhoben, keine Daten geteilt
- Altersfreigabe-Fragebogen ausfüllen (keine kritischen Inhalte)
- Datenschutzerklärung-URL eintragen
- AAB zuerst in einen Test-Track laden

Lokal bauen geht auch:

```bash
BC_KEYSTORE_FILE=/pfad/binary-clock-upload.jks \
BC_KEYSTORE_PASSWORD=... BC_KEY_ALIAS=upload BC_KEY_PASSWORD=... \
./gradlew bundleRelease
```
