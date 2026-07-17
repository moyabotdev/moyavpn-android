package com.moyavpn.app.vpn

import com.moyavpn.app.data.XrayParams
import org.json.JSONArray
import org.json.JSONObject

/**
 * Baut die XRay-Client-Config (JSON) aus den [XrayParams] des Servers.
 *
 * Architektur wie v2rayNG/Hiddify: xray laeuft mit einem **SOCKS-Inbound** auf
 * 127.0.0.1:[SOCKS_PORT]; die Bruecke TUN↔SOCKS macht hev-socks5-tunnel
 * ([TProxyService]). Der native `tun`-Inbound von xray-core wird NICHT benutzt —
 * er ist laut xray-Doku experimentell ("results nothing, or infinite loop") und
 * war die Ursache fuer "verbunden, aber kein Durchsatz".
 *
 * Bewusste Details (vermeiden die Fehler, an denen der AmneziaVPN-Client scheiterte):
 *  - queryStrategy = UseIPv4 → keine AAAA → kein Haengen an toten IPv6-Zielen
 *  - freedom domainStrategy UseIPv4 → egress bevorzugt IPv4
 *  - udp/443 (QUIC) → blackhole → Browser faellt sofort auf TCP zurueck
 *  - flow leer (kein xtls-rprx-vision) → kompatibel mit Mux
 *  - sniffing an → Routing nach Domain statt nach (evtl. falscher) Client-IP
 *  - DNS laeuft durch den Proxy (kein Leak, keine toten Client-DNS)
 */
object XrayConfigBuilder {

    /** SOCKS-Inbound-Port, an den hev-socks5-tunnel den TUN-Traffic weiterreicht. */
    const val SOCKS_PORT = 10808
    const val SOCKS_ADDR = "127.0.0.1"

    /**
     * Private/nicht-routbare Netze, ausgeschrieben. Bewusst NICHT "geoip:private":
     * das setzt die Datei geoip.dat voraus, die es in der eingebetteten Lib nicht gibt
     * (xray bricht sonst schon beim Parsen ab: "failed to open geoip.dat").
     */
    private val PRIVATE_CIDRS = listOf(
        "0.0.0.0/8", "10.0.0.0/8", "100.64.0.0/10", "127.0.0.0/8", "169.254.0.0/16",
        "172.16.0.0/12", "192.0.0.0/24", "192.0.2.0/24", "192.88.99.0/24",
        "192.168.0.0/16", "198.18.0.0/15", "198.51.100.0/24", "203.0.113.0/24",
        "224.0.0.0/4", "240.0.0.0/4", "255.255.255.255/32",
        "::1/128", "fc00::/7", "fe80::/10",
    )

    fun build(p: XrayParams): String {
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

        // SOCKS-Inbound: hev-socks5-tunnel reicht hier den gesamten TUN-Traffic hinein.
        val socksIn = JSONObject()
            .put("tag", "socks-in")
            .put("protocol", "socks")
            .put("listen", SOCKS_ADDR)
            .put("port", SOCKS_PORT)
            .put("settings", JSONObject()
                .put("auth", "noauth")
                .put("udp", true)
                .put("address", SOCKS_ADDR))
            .put("sniffing", JSONObject()
                .put("enabled", true)
                .put("destOverride", JSONArray().put("http").put("tls").put("quic"))
                .put("routeOnly", false))

        val privateNets = JSONArray()
        PRIVATE_CIDRS.forEach { privateNets.put(it) }

        val rules = JSONArray()
            .put(JSONObject().put("type", "field").put("port", "443").put("network", "udp").put("outboundTag", "block"))
            .put(JSONObject().put("type", "field").put("protocol", JSONArray().put("bittorrent")).put("outboundTag", "block"))
            .put(JSONObject().put("type", "field").put("ip", privateNets).put("outboundTag", "direct"))

        return JSONObject()
            .put("log", JSONObject().put("loglevel", "warning"))
            .put("dns", JSONObject()
                .put("servers", JSONArray().put("1.1.1.1").put("8.8.8.8"))
                .put("queryStrategy", "UseIPv4"))
            .put("inbounds", JSONArray().put(socksIn))
            // Reihenfolge wichtig: erster Outbound = Default fuer nicht gematchten Traffic.
            .put("outbounds", JSONArray().put(proxyOut).put(directOut).put(blockOut))
            .put("routing", JSONObject()
                .put("domainStrategy", "IPIfNonMatch")
                .put("rules", rules))
            .toString()
    }
}
