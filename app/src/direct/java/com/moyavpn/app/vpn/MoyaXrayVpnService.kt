package com.moyavpn.app.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.moyavpn.app.R
import kotlinx.coroutines.CompletableDeferred
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray

/**
 * VpnService fuer den XRay-Pfad (nur direct-Flavor).
 *
 * Architektur (wie v2rayNG/Hiddify):
 *  1. TUN via [VpnService.Builder] aufbauen.
 *  2. xray-core mit SOCKS-Inbound starten — startLoop(config, 0), fd=0, damit xray
 *     seinen experimentellen tun-Inbound NICHT nutzt.
 *  3. [TProxyService] (hev-socks5-tunnel) bruecke TUN↔SOCKS.
 *
 * Socket-Loop-Schutz: [VpnService.Builder.addDisallowedApplication] mit dem eigenen
 * Paket → xrays Outbound-Sockets (und hevs Loopback zum SOCKS) umgehen das TUN.
 */
class MoyaXrayVpnService : VpnService() {

    private var controller: CoreController? = null
    private var tun: ParcelFileDescriptor? = null

    companion object {
        private const val TAG = "MoyaXrayVpn"
        const val ACTION_START = "com.moyavpn.app.XRAY_START"
        const val ACTION_STOP  = "com.moyavpn.app.XRAY_STOP"
        const val EXTRA_CONFIG = "xray_config"   // fertiges Xray-JSON (XrayConfigBuilder)

        private const val CHANNEL_ID = "moyavpn_xray"
        private const val NOTIF_ID = 4711

        private const val MTU = 1500
        private const val TUN_IPV4 = "10.10.0.2"

        @Volatile var running = false
            private set

        /**
         * Startquittung: [XrayTunnelManager] legt sie vor dem startService an und wartet
         * darauf. Der Service erfuellt sie mit Erfolg — oder mit dem *echten* Fehler.
         */
        @Volatile private var startSignal: CompletableDeferred<Unit>? = null

        fun armStart(): CompletableDeferred<Unit> =
            CompletableDeferred<Unit>().also { startSignal = it }

        private fun signalOk() { startSignal?.complete(Unit); startSignal = null }
        private fun signalFail(msg: String) {
            startSignal?.completeExceptionally(IllegalStateException(msg)); startSignal = null
        }

        /**
         * Stoppquittung: der Aufrufer wartet, bis hev + xray + TUN wirklich abgebaut
         * sind, BEVOR ein anderer Tunnel (AWG) das TUN uebernimmt. Sonst liest hev
         * ein weggezogenes TUN-fd → nativer Absturz → App schliesst sich.
         */
        @Volatile private var stopSignal: CompletableDeferred<Unit>? = null

        fun armStop(): CompletableDeferred<Unit> =
            CompletableDeferred<Unit>().also { stopSignal = it }

        private fun signalStopped() { stopSignal?.complete(Unit); stopSignal = null }
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
        if (config.isNullOrEmpty()) {
            signalFail("keine XRay-Config uebergeben"); stopSelf(); return START_NOT_STICKY
        }

        try { goForeground() } catch (e: Exception) {
            Log.w(TAG, "startForeground fehlgeschlagen: ${e.message}")
        }

        return try {
            startVpn(config)
            signalOk()
            START_STICKY
        } catch (e: Exception) {
            Log.e(TAG, "startVpn fehlgeschlagen", e)
            stopVpn()
            signalFail(e.message ?: e.javaClass.simpleName)
            START_NOT_STICKY
        }
    }

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "VPN-Verbindung", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MoyaVPN")
            .setContentText("XRay-Verbindung aktiv")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(
            this, NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
    }

    /** Wirft bei Fehler — der Aufrufer meldet das ueber [signalFail] zurueck. */
    private fun startVpn(config: String) {
        if (running || controller != null) teardown()

        val builder = Builder()
            .setSession("MoyaVPN")
            .setMtu(MTU)
            .addAddress(TUN_IPV4, 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("1.1.1.1")
        // Eigene App aus dem Tunnel ausschliessen → xray-Outbound + hev-Loopback loopen nicht
        try { builder.addDisallowedApplication(packageName) } catch (_: Exception) {}

        val pfd = builder.establish() ?: error("VPN-Erlaubnis fehlt (establish() gab null)")
        tun = pfd

        // xray zuerst (SOCKS-Inbound), fd=0 → kein nativer tun-Inbound
        val c = Libv2ray.newCoreController(callback)
        c.startLoop(config, 0)
        controller = c

        // dann die TUN↔SOCKS-Bruecke
        TProxyService.start(filesDir, pfd.fd, MTU, TUN_IPV4)

        running = true
        Log.i(TAG, "XRay-Tunnel gestartet (xray socks + hev)")
    }

    /** Ressourcen freigeben, ohne den Service zu beenden. */
    private fun teardown() {
        running = false
        try { TProxyService.stop() } catch (e: Exception) { Log.w(TAG, "hev stop: ${e.message}") }
        try { controller?.stopLoop() } catch (e: Exception) { Log.w(TAG, "stopLoop: ${e.message}") }
        controller = null
        try { tun?.close() } catch (_: Exception) {}
        tun = null
    }

    private fun stopVpn() {
        teardown()
        try { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
        signalStopped()   // erst NACH vollstaendigem Abbau quittieren
        stopSelf()
    }

    /** Traffic-Statistik (rx,tx) in Bytes aus hev; (0,0) wenn n/a. */
    fun stats(): Pair<Long, Long> {
        val s = TProxyService.stats() ?: return 0L to 0L
        // hev liefert [tx, rx]
        val tx = s.getOrNull(0) ?: 0L
        val rx = s.getOrNull(1) ?: 0L
        return rx to tx
    }

    override fun onDestroy() { teardown(); super.onDestroy() }
    override fun onRevoke() { stopVpn(); super.onRevoke() }
}
