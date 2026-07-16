package com.moyavpn.app.vpn

import com.moyavpn.app.data.XrayParams
import org.json.JSONArray
import org.json.JSONObject

/**
 * Baut die XRay-Client-Config (JSON) aus den [XrayParams] des Servers.
 *
 * Bewusst so gesetzt, dass genau die Fehler vermieden werden, an denen der
 * AmneziaVPN-Client bei VLESS/Reality scheiterte:
 *  - queryStrategy = UseIPv4  → keine AAAA-Records → kein Haengen an toten IPv6-Zielen
 *  - freedom domainStrategy UseIPv4 → egress bevorzugt IPv4
 *  - udp/443 (QUIC) → blackhole → Browser faellt sofort auf TCP zurueck
 *  - flow leer (kein xtls-rprx-vision) → kompatibel mit Mux
 *  - sniffing an → Routing/DNS nach Domain statt nach (evtl. falscher) Client-IP
 *  - Port 53 → dedizierter dns-Outbound (1.1.1.1) → saubere Namensaufloesung
 *
 * [socksPort] ist der lokale SOCKS-Inbound, an den tun2socks die TUN-Pakete gibt.
 */
object XrayConfigBuilder {

    fun build(p: XrayParams, socksPort: Int): String {
        val realitySettings = JSONObject()
            .put("serverName", p.sni)
            .put("fingerprint", p.fingerprint)
            .put("publicKey", p.publicKey)
            .put("shortId", p.shortId)
            .put("spiderX", "")

        val user = JSONObject()
            .put("id", p.uuid)
            .put("encryption", "none")
        if (p.flow.isNotEmpty()) user.put("flow", p.flow)

        val vnext = JSONObject()
            .put("address", p.address)
            .put("port", p.port)
            .put("users", JSONArray().put(user))

        val proxyOut = JSONObject()
            .put("tag", "proxy")
            .put("protocol", "vless")
            .put("settings", JSONObject().put("vnext", JSONArray().put(vnext)))
            .put("streamSettings", JSONObject()
                .put("network", "tcp")
                .put("security", "reality")
                .put("realitySettings", realitySettings))

        val directOut = JSONObject()
            .put("tag", "direct")
            .put("protocol", "freedom")
            .put("settings", JSONObject().put("domainStrategy", "UseIPv4"))

        val blockOut = JSONObject()
            .put("tag", "block")
            .put("protocol", "blackhole")

        val dnsOut = JSONObject()
            .put("tag", "dns-out")
            .put("protocol", "dns")
            .put("settings", JSONObject().put("address", "1.1.1.1"))

        val socksIn = JSONObject()
            .put("tag", "socks-in")
            .put("port", socksPort)
            .put("listen", "127.0.0.1")
            .put("protocol", "socks")
            .put("settings", JSONObject().put("udp", true).put("auth", "noauth"))
            .put("sniffing", JSONObject()
                .put("enabled", true)
                .put("destOverride", JSONArray().put("http").put("tls").put("quic"))
                .put("routeOnly", false))

        val rules = JSONArray()
            .put(JSONObject().put("type", "field").put("port", "53").put("outboundTag", "dns-out"))
            .put(JSONObject().put("type", "field").put("port", "443").put("network", "udp").put("outboundTag", "block"))
            .put(JSONObject().put("type", "field").put("protocol", JSONArray().put("bittorrent")).put("outboundTag", "block"))
            .put(JSONObject().put("type", "field").put("ip", JSONArray().put("geoip:private")).put("outboundTag", "direct"))

        return JSONObject()
            .put("log", JSONObject().put("loglevel", "warning"))
            .put("dns", JSONObject()
                .put("servers", JSONArray().put("1.1.1.1").put("8.8.8.8"))
                .put("queryStrategy", "UseIPv4"))
            .put("inbounds", JSONArray().put(socksIn))
            // Reihenfolge wichtig: erster Outbound = Default fuer nicht gematchten Traffic.
            .put("outbounds", JSONArray().put(proxyOut).put(directOut).put(blockOut).put(dnsOut))
            .put("routing", JSONObject()
                .put("domainStrategy", "IPIfNonMatch")
                .put("rules", rules))
            .toString()
    }
}
