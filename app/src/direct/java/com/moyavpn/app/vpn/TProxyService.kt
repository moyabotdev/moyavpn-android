package com.moyavpn.app.vpn

import android.util.Log
import java.io.File

/**
 * JNI-Bruecke zu hev-socks5-tunnel (libhev-socks5-tunnel.so).
 *
 * hev liest den TUN-fd des [MoyaXrayVpnService] direkt und leitet den gesamten
 * L3-Traffic an xrays SOCKS-Inbound (127.0.0.1:[XrayConfigBuilder.SOCKS_PORT]).
 * Das ist die bewaehrte Architektur von v2rayNG/Hiddify — nicht der experimentelle
 * native tun-Inbound von xray-core.
 *
 * WICHTIG (JNI): Die native Lib registriert TProxyStart/Stop/GetStats fest auf die
 * Klasse com/moyavpn/app/vpn/TProxyService (PKGNAME beim NDK-Build gesetzt). Paket
 * und Klassenname duerfen daher NICHT geaendert werden, sonst findet JNI_OnLoad die
 * Klasse nicht.
 */
object TProxyService {

    private const val TAG = "MoyaXrayHev"

    @JvmStatic
    @Suppress("FunctionName")
    private external fun TProxyStartService(configPath: String, fd: Int)

    @JvmStatic
    @Suppress("FunctionName")
    private external fun TProxyStopService()

    @JvmStatic
    @Suppress("FunctionName")
    private external fun TProxyGetStats(): LongArray?

    @Volatile private var running = false

    init {
        System.loadLibrary("hev-socks5-tunnel")
    }

    /**
     * Startet die TUN↔SOCKS-Bruecke. [tunFd] = fd des etablierten VpnService-TUN,
     * [filesDir] = App-Verzeichnis fuer die YAML-Config, [mtu]/[tunIpv4] muessen mit
     * dem VpnService.Builder uebereinstimmen.
     */
    fun start(filesDir: File, tunFd: Int, mtu: Int, tunIpv4: String) {
        val yaml = buildString {
            appendLine("tunnel:")
            appendLine("  mtu: $mtu")
            appendLine("  ipv4: $tunIpv4")
            appendLine("socks5:")
            appendLine("  port: ${XrayConfigBuilder.SOCKS_PORT}")
            appendLine("  address: ${XrayConfigBuilder.SOCKS_ADDR}")
            appendLine("  udp: 'udp'")
            appendLine("misc:")
            appendLine("  tcp-read-write-timeout: 300000")
            appendLine("  udp-read-write-timeout: 60000")
            appendLine("  log-level: warn")
        }
        val cfg = File(filesDir, "hev-socks5-tunnel.yaml").apply { writeText(yaml) }
        TProxyStartService(cfg.absolutePath, tunFd)
        running = true
        Log.i(TAG, "hev-socks5-tunnel gestartet (fd=$tunFd, socks=${XrayConfigBuilder.SOCKS_PORT})")
    }

    fun stop() {
        if (!running) return
        running = false
        try { TProxyStopService() } catch (e: Exception) { Log.w(TAG, "TProxyStopService: ${e.message}") }
    }

    /** (tx,rx) in Bytes oder null. */
    fun stats(): LongArray? = try { TProxyGetStats() } catch (e: Exception) { null }
}
