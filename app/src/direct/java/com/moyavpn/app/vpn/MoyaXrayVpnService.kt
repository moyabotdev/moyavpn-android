package com.moyavpn.app.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray

/**
 * VpnService für den XRay-Pfad (nur direct-Flavor).
 *
 * Ablauf: TUN via [VpnService.Builder] aufbauen → fd an [CoreController.startLoop]
 * geben. Die Lib (AndroidLibXrayLite / xray-core) bruecke TUN↔SOCKS-Inbound der
 * generierten Config selbst — kein separates tun2socks noetig.
 *
 * Socket-Loop-Schutz: [android.net.VpnService.Builder.addDisallowedApplication] mit dem
 * eigenen Paket → xrays Outbound-Sockets (im App-Prozess) umgehen das TUN.
 */
class MoyaXrayVpnService : VpnService() {

    private var controller: CoreController? = null
    private var tun: ParcelFileDescriptor? = null

    companion object {
        private const val TAG = "MoyaXrayVpn"
        const val ACTION_START = "com.moyavpn.app.XRAY_START"
        const val ACTION_STOP  = "com.moyavpn.app.XRAY_STOP"
        const val EXTRA_CONFIG = "xray_config"   // fertiges Xray-JSON (XrayConfigBuilder)
        @Volatile var running = false
            private set
    }

    private val callback = object : CoreCallbackHandler {
        override fun startup(): Long = 0
        override fun shutdown(): Long { stopVpn(); return 0 }
        override fun onEmitStatus(code: Long, msg: String?): Long {
            Log.i(TAG, "xray status $code: $msg"); return 0
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopVpn(); return START_NOT_STICKY }
        val config = intent?.getStringExtra(EXTRA_CONFIG)
        if (config.isNullOrEmpty()) { stopSelf(); return START_NOT_STICKY }
        return if (startVpn(config)) START_STICKY else START_NOT_STICKY
    }

    private fun startVpn(config: String): Boolean {
        try {
            val builder = Builder()
                .setSession("MoyaVPN")
                .setMtu(1500)
                .addAddress("10.10.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("1.1.1.1")
            // Eigene App aus dem Tunnel ausschliessen → xray-Outbound loopt nicht
            try { builder.addDisallowedApplication(packageName) } catch (_: Exception) {}
            val pfd = builder.establish() ?: run { Log.e(TAG, "establish() gab null"); return false }
            tun = pfd

            val c = Libv2ray.newCoreController(callback)
            c.startLoop(config, pfd.fd)
            controller = c
            running = true
            Log.i(TAG, "XRay-Tunnel gestartet")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "startVpn fehlgeschlagen", e)
            stopVpn()
            return false
        }
    }

    private fun stopVpn() {
        running = false
        try { controller?.stopLoop() } catch (e: Exception) { Log.w(TAG, "stopLoop: ${e.message}") }
        controller = null
        try { tun?.close() } catch (_: Exception) {}
        tun = null
        stopSelf()
    }

    /** Traffic-Statistik (rx,tx) des proxy-Outbounds in Bytes; (0,0) wenn n/a. */
    fun stats(): Pair<Long, Long> = try {
        val c = controller ?: return 0L to 0L
        c.queryStats("proxy", "uplink") to c.queryStats("proxy", "downlink")
    } catch (e: Exception) { 0L to 0L }

    override fun onDestroy() { stopVpn(); super.onDestroy() }
    override fun onRevoke() { stopVpn(); super.onRevoke() }
}
