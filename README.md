# MoyaVPN — Android

Gebrandete VPN-App für MoyaBot. Der Nutzer meldet sich mit einem **App-Zugangscode**
(im MoyaBot generiert) an, sieht alle seine Verbindungen (DE/NL/CH/AT) und schaltet
den Tunnel mit einem Tipp an/aus.

> **Status:** Tunnel läuft über den **AmneziaWG-Core** (`org.amnezia.awg`,
> DPI-resistent — passend zu euren Servern). Das tunnel-AAR wird in CI aus
> `amnezia-vpn/amneziawg-android` v2.0.1 gebaut (nativer Go/NDK-Build).

---

## Schnellstart

### APK ohne lokale Einrichtung bauen (empfohlen)
1. Repo zu GitHub pushen (siehe unten).
2. GitHub baut automatisch (Actions → **Build MoyaVPN APK**).
3. APK herunterladen: Actions-Run → **Artifacts → MoyaVPN-debug-apk**.
4. Auf dem Android-Gerät installieren (unbekannte Quellen erlauben).

### Lokal bauen (Android Studio)
- Android Studio öffnen → Projekt importieren → „Run".
- Oder Kommandozeile: `./gradlew assembleDebug` → `app/build/outputs/apk/debug/`.

---

## Architektur

| Schicht | Datei | Aufgabe |
|---|---|---|
| UI | `ui/MainScreen.kt` | Login-Feld + Verbindungsliste (Compose) |
| State | `ui/MainViewModel.kt` | Login, Account laden, Verbinden/Trennen, Traffic |
| Netzwerk | `data/MoyaApi.kt`, `data/Models.kt` | Retrofit-Client + JSON-Modelle |
| Token | `data/TokenStore.kt` | App-Token persistent (DataStore) |
| Tunnel | `vpn/TunnelManager.kt` | WireGuard-GoBackend, Config parsen, an/aus |

Der VPN-Dienst selbst kommt aus der WireGuard-Library und wird automatisch ins
Manifest gemerged — kein eigener `VpnService` nötig.

---

## API-Contract (Server-Endpoint)

Die App ruft **einen** Endpoint auf. Den bauen wir in den MoyaBot (`register.moyabot.ru`).

```
GET /app/v1/account
Header: Authorization: Bearer <APP_TOKEN>
```

**Antwort 200** (`application/json`):
```json
{
  "user": { "name": "Max", "expires_at": "2026-08-01" },
  "connections": [
    {
      "server_id": "de",
      "server_name": "Deutschland",
      "flag": "🇩🇪",
      "status": "active",
      "expires_at": "2026-08-01",
      "config": "[Interface]\nPrivateKey=...\nAddress=10.9.0.5/32\nDNS=10.9.0.1\n\n[Peer]\nPublicKey=...\nPresharedKey=...\nEndpoint=de.moyabot.ru:443\nAllowedIPs=0.0.0.0/0, ::/0\nPersistentKeepalive=25\n",
      "awg": { "jc": 4, "jmin": 40, "jmax": 70, "s1": 0, "s2": 0, "h1": 1, "h2": 2, "h3": 3, "h4": 4 }
    }
  ]
}
```
- **401** bei ungültigem/abgelaufenem Token.
- `awg` ist optional und wird vom WireGuard-MVP ignoriert (nur für AmneziaWG relevant).

> **Offene Frage für den Server-Teil:** Die `config` braucht den **privaten Schlüssel**
> des Clients. wg-easy liefert den nur **bei Erstellung** zurück — der Bot muss ihn also
> ab jetzt mitspeichern, sonst kann die App keine fertige Config ausliefern.
> Das klären wir beim Bau des Endpoints.

Basis-URL liegt in `app/build.gradle.kts` → `API_BASE_URL`.

---

## AmneziaWG-Umstellung

Für DPI-Resistenz (Russland) den WireGuard-Core gegen AmneziaWG tauschen:

1. In `app/build.gradle.kts` die Tunnel-Dependency ersetzen:
   ```kotlin
   // implementation("com.wireguard.android:tunnel:1.0.20230706")
   implementation("com.github.amnezia-vpn:amneziawg-android:<version>")
   ```
   (JitPack ist in `settings.gradle.kts` schon eingetragen.)
2. In `vpn/TunnelManager.kt` die `com.wireguard.*`-Imports auf `org.amnezia.awg.*`
   umstellen (API ist nahezu identisch).
3. Die `awg`-Parameter aus der Server-Antwort in die Config einsetzen
   (`Jc`, `Jmin`, `Jmax`, `S1`, `S2`, `H1`–`H4`).

---

## Zu GitHub pushen

```bash
cd moyavpn-android
git init && git add -A && git commit -m "MoyaVPN Android MVP"
git branch -M main
git remote add origin git@github.com:moyabotdev/moyavpn-android.git
git push -u origin main
```
Danach baut GitHub die APK automatisch.

---

## Roadmap
- [ ] Server-Endpoint `/app/v1/account` im Bot + App-Token-Verwaltung
- [ ] Privaten Client-Key beim Peer-Erstellen mitspeichern
- [ ] AmneziaWG-Core (DPI-resistent)
- [ ] Auto-Reconnect + „immer an"
- [ ] iOS (separates Projekt)
- [ ] App-Token auf einer Webseite anzeigen/generieren
