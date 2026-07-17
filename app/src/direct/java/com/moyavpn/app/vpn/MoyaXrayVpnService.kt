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
import kotlinx.coroutines.CompletableDeferred
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray

/**
 * VpnService für den XRay-Pfad (nur direct-Flavor).
 *
 * Ablauf: TUN via [VpnService.Builder] aufbauen → fd an [CoreController.startLoop].
 * Die Lib setzt daraus die Env-Var `xray.tun.fd`, die der `tun`-Inbound der Config
 * (siehe [XrayConfigBuilder]) ausliest — kein separates tun2socks noetig.
 *
 * Socket-Loop-Schutz: [VpnService.Builder.addDisallowedApplication] mit dem eigenen
 * Paket → xrays Outbound-Sockets (im App-Prozess) umgehen das TUN.
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

        @Volatile var running = false
            private set

        /**
         * Startquittung: [XrayTunnelManager] legt sie vor dem startService an und wartet
         * darauf. Der Service erfuellt sie mit Erfolg — oder mit dem *echten* Fehler.
         * Ohne das koennte der Aufrufer nur direkt nach dem (asynchronen) startService
         * auf [running] pruefen und laege dabei immer daneben.
         */
        @Volatile private var startSignal: CompletableDeferred<Unit>? = null

        fun armStart(): CompletableDeferred<Unit> =
            CompletableDeferred<Unit>().also { startSignal = it }

        private fun signalOk() { startSignal?.complete(Unit); startSignal = null }
        private fun signalFail(msg: String) {
            startSignal?.completeExceptionally(IllegalStateException(msg)); startSignal = null
        }
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

        // Muss unmittelbar nach dem Start passieren, sonst killt Android den Dienst.
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
        val notif: Notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("MoyaVPN")
            .setContentText("XRay-Verbindung aktiv")
            .setSmallIcon(android.R.drawable.stat_sys_vpn_ic)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    /** Wirft bei Fehler — der Aufrufer meldet das ueber [signalFail] zurueck. */
    private fun startVpn(config: String) {
        // Erneuter Start bei laufendem Tunnel: erst sauber abbauen (sonst zweiter
        // establish()/startLoop auf altem State → Absturz).
        if (running || controller != null) teardown()

        val builder = Builder()
            .setSession("MoyaVPN")
            .setMtu(1500)
            .addAddress("10.10.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("1.1.1.1")
        // Eigene App aus dem Tunnel ausschliessen → xray-Outbound loopt nicht
        try { builder.addDisallowedApplication(packageName) } catch (_: Exception) {}

        val pfd = builder.establish() ?: error("VPN-Erlaubnis fehlt (establish() gab null)")
        tun = pfd

        val c = Libv2ray.newCoreController(callback)
        c.startLoop(config, pfd.fd)   // wirft, wenn xray die Config nicht akzeptiert
        controller = c
        running = true
        Log.i(TAG, "XRay-Tunnel gestartet")
    }

    /** Ressourcen freigeben, ohne den Service zu beenden. */
    private fun teardown() {
        running = false
        try { controller?.stopLoop() } catch (e: Exception) { Log.w(TAG, "stopLoop: ${e.message}") }
        controller = null
        try { tun?.close() } catch (_: Exception) {}
        tun = null
    }

    private fun stopVpn() {
        teardown()
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
        stopSelf()
    }

    /** Traffic-Statistik (rx,tx) des proxy-Outbounds in Bytes; (0,0) wenn n/a. */
    fun stats(): Pair<Long, Long> = try {
        val c = controller ?: return 0L to 0L
        c.queryStats("proxy", "uplink") to c.queryStats("proxy", "downlink")
    } catch (e: Exception) { 0L to 0L }

    override fun onDestroy() { teardown(); super.onDestroy() }
    override fun onRevoke() { stopVpn(); super.onRevoke() }
}
