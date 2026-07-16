# XRay/Reality-Integration in die MoyaVPN-App

Ziel: VLESS+Reality als **zweites Protokoll** neben AmneziaWG — nur im **`direct`-Flavor**
(Play-Version bleibt AWG-only, kein Policy-Risiko, kein Xray-Lib im Store-Build).
Nutzer merken keinen Unterschied: gleiche Login-/Server-Listen-/Verbinden-UX, XRay-Server
erscheinen automatisch in der Liste (API-getrieben).

Warum eigener Client statt AmneziaVPN: AmneziaVPN Android routet VLESS/Reality-Browser-Traffic
nicht zuverlaessig (getestet 2026-07-16 — Chat/DNS liefen, Browser nicht). Hiddify lief sofort.
Wir bauen den Client selbst und kontrollieren die Config → die Amnezia-Fehler entfallen.

## Produktvision (Richtungsentscheid 2026-07-16)
- **Zwei Modi, eine Engine:** (a) *Managed* = MoyaVPN-Account → `/app/v1/account`; (b) *BYO* =
  Nutzer importieren eigene Server (`vless://`/`ss://`/`trojan://`/WG/**Subscription-URL**/QR).
  → App wird ein **generischer Multiprotokoll-Client** (Hiddify/v2rayNG-Klasse) mit besserer UX;
  Managed-Server als Premium-Default. Freemium: BYO gratis, MoyaVPN-Server bezahlt.
- **Play-freundlicher** als ein reines Umgehungstool → langfristig auch XRay in Play (per Flag).
- Braucht zusaetzlich: URI-Parser (Standard-Linkformate → Param-Objekte), Subscription-Abruf, Import-UI.

## Monetarisierung (Richtungsentscheid 2026-07-16, spaeter umzusetzen)
Freemium mit **Tagesvolumen** statt 4h-Trial. Bestaetigte Entscheidungen:
- **Free-Server fuer alle**, gedeckelt ~**100 MB/Tag/Nutzer** (Startwert, per Monitoring justieren).
- **MVP-Protokoll = AmneziaWG** (die Live-App kann heute nur AWG; XRay-Free-Tier kommt mit 2.0).
- **Limit-Verhalten: Trennen + Upsell** — Peer bei 100 MB sperren → VPN trennt, Meldung
  „Tageslimit erreicht — unbegrenzt mit MoyaVPN-Zugang".
- **Zugang nur mit Telefon-Verifizierung** (sms.ru: +7 Anruf / sonst SMS) — ein Free-Kontingent
  pro verifizierter Nummer = Missbrauchsschutz.
- **Verifizierung nativ in der App** (kein Web-Umweg): recycelt das live Backend `verify/start→check→trial`
  ([[project_moyabot_webtrial]]). Android-UX: Phone-Number-Hint + SMS-Retriever → fast ein Tap
  (nur +7-Anruf-Methode braucht manuelle 4-Ziffern-Eingabe). Neue App-Endpunkte `/app/v1/free/verify/*`
  spiegeln die Web-Endpunkte.
- Deckel **serverseitig**: wg-easy rx/tx pro Peer, Job misst (kumulativ − Mitternachts-Baseline),
  sperrt bei Limit, reaktiviert+reset um Mitternacht. `/app/v1/account` liefert `daily_used`/`daily_limit`.
- Bezahlte Server unlimitiert; BYO (eigene Server) gratis. Ersetzt den 4h-Trial.
- Sofort-Variante ohne App-Umbau: **Bot-Button „🆓 Gratis (100 MB/Tag)"** mit derselben Verifizierung.

## Play-Zukunftsfaehigkeit (nicht hart an `direct` koppeln)
- Xray-core = MPL-2.0, 16-KB-aligned → Play-konform. sing-box (GPLv3) bewusst gemieden.
- **`ENABLE_XRAY`-BuildConfig pro Flavor** (jetzt direct=true, play=false; spaeter umlegbar).
- Protokoll-Abstraktion (`VpnBackend`-Interface); nur die core-anbindende Klasse ist flavor-spezifisch.
- Native-Lib via `directImplementation(...)` → spaeter zusaetzlich `playImplementation(...)`.
- Serverliste backend-getrieben → Play-Clients bekommen XRay-Server erst, wenn Backend sie ausliefert.

## Protokoll-Lineup
Phase 1 (alles ueber Xray-core, MPL, Play-tauglich — nur Config-Varianten + Parser) + AWG:
- **VLESS+Reality** (Flaggschiff RU) · **AmneziaWG** (GoBackend) · **Shadowsocks/SS-2022** ·
  **Trojan** · **VMess/VLESS + WS/gRPC + TLS** (CDN/Cloudflare).
Phase 2 (zweiter Core, Lizenz zuerst pruefen): **Hysteria2 / TUIC** (QUIC) — sing-box waere GPLv3
(Play-Problem) → permissive Hysteria-Lib evaluieren.
Model: `Connection.protocol` ∈ {awg, xray, shadowsocks, trojan, vmess, …}; xray|shadowsocks|trojan|vmess
teilen sich den Xray-core-Pfad (nur XrayConfigBuilder-Variante unterscheidet sich).

## Lizenz
- **Xray-core: MPL-2.0** (file-level Copyleft) → in closed-source App einbettbar; nur Xray-cores
  eigene Dateien bleiben MPL, App-Quellen bleiben proprietaer.
- Wrapper **2dust/AndroidLibXrayLite** (gomobile-Binding, nutzt xray-core 26.x) — Wrapper-Lizenz
  vor Release final pruefen; alternativ eigenes duennes gomobile-Binding um xray-core.

## Architektur
```
Connection.protocol == "awg"  → GoBackend (org.amnezia.awg)           [wie bisher]
Connection.protocol == "xray" → MoyaXrayVpnService + libv2ray + tun2socks   [neu, direct-only]
```
- Server-Liste bleibt `GET /app/v1/account` → `connections[]`; jeder Eintrag traegt `protocol`.
- XRay-Pfad: eigener `VpnService` baut das TUN, `libv2ray` startet xray mit generierter Config
  (SOCKS-Inbound 127.0.0.1:PORT), `tun2socks` (hev-socks5-tunnel) bruecke TUN↔SOCKS.

## Status
- [x] `Connection.protocol` + `XrayParams` im Model (`data/Models.kt`) — rueckwaertskompatibel.
- [x] `XrayConfigBuilder` (`src/direct/.../vpn/XrayConfigBuilder.kt`) — Client-Config mit den
      Anti-Amnezia-Fixes: queryStrategy UseIPv4, QUIC(udp443)→block, kein Vision (Mux-safe),
      sniffing an, Port53→dns-out(1.1.1.1), freedom UseIPv4.
- [ ] CI-Job: `xray-tunnel.aar` bauen (siehe unten).
- [ ] `MoyaXrayVpnService` + tun2socks + `XrayTunnelManager`.
- [ ] Protokoll-Weiche in `TunnelManager`/`VpnConnector` + UI-Badge; `play` no-op.
- [ ] Backend `/app/v1/account`: XRay-Standorte mit `protocol:"xray"` + `xray`-Params liefern.
- [ ] CI-Build + On-Device-Test gegen FI (<deko-domain>-Reality).

## CI: xray-tunnel.aar bauen (Mirror des awg-Jobs in android.yml)
Nur fuer `direct` noetig. Schritte (GitHub Actions, ubuntu):
```yaml
- name: Checkout AndroidLibXrayLite
  uses: actions/checkout@v4
  with: { repository: 2dust/AndroidLibXrayLite, path: xray-src, fetch-depth: 0 }
- name: Set up Go
  uses: actions/setup-go@v5
  with: { go-version: '1.26' }
- name: Set up Android NDK  # NDK via setup-android; ANDROID_NDK_HOME exportieren
  run: echo "ANDROID_NDK_HOME=$ANDROID_NDK_ROOT" >> $GITHUB_ENV
- name: Build libv2ray.aar (gomobile)
  run: |
    cd xray-src
    go install golang.org/x/mobile/cmd/gomobile@latest
    export PATH=$PATH:$(go env GOPATH)/bin
    gomobile init
    # 16-KB-Page-Size wie beim awg-Build: -ldflags Linker-Flags
    gomobile bind -v -androidapi 21 -target=android/arm64,android/amd64 \
      -ldflags="-extldflags=-Wl,-z,max-page-size=16384,-z,common-page-size=16384" \
      -o "$GITHUB_WORKSPACE/app/libs/xray-tunnel.aar" .
- name: Verify 16 KB alignment  # gleiche readelf-Pruefung wie awg
```
tun2socks: `hev-socks5-tunnel` als vorgebaute `.so` mitliefern ODER als Submodule bauen
(v2rayNG bindet es als prebuilt libtun2socks.so ein). Alignment ebenfalls 16 KB.

## MoyaXrayVpnService (Skizze)
1. `VpnService.Builder`: addAddress(10.x.x.x/32), addDnsServer(1.1.1.1),
   addRoute(0.0.0.0/0), setMtu(1500), establish() → TUN-fd.
2. `libv2ray` starten mit `XrayConfigBuilder.build(params, socksPort)`.
3. `tun2socks` starten: TUN-fd → SOCKS 127.0.0.1:socksPort (udp on).
4. Statistik/Reconnect analog TunnelManager; sauberes Teardown (tun2socks stop, xray stop, fd close).
Die genauen `libv2ray`-API-Aufrufe (CoreController/V2RayPoint.startLoop) gegen das gebaute AAR final.

## Backend-Aenderung (Bot, /app/v1/account)
Im App-Account-Endpoint pro XRay-Standort ein `connections`-Element mit:
```json
{ "server_id":"fx", "server_name":"Finnland · XRay", "flag":"🇫🇮",
  "status":"active", "expires_at":"...", "protocol":"xray",
  "xray": { "address":"<server-ip>","port":443,"uuid":"<per-user>",
            "public_key":"<public_key>","short_id":"<short_id>","sni":"<deko-domain>",
            "fingerprint":"chrome","flow":"" } }
```
UUID pro Nutzer via 3x-ui-API (schon implementiert: `xray_create_client`). AWG-Eintraege bleiben unveraendert.
```
```
