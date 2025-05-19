package io.github.ktraum.pocketmic

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat

class AudioServerService : Service() {
    private val TAG = "AudioServerService"
    private val NOTIFICATION_CHANNEL_ID = "AudioServerChannel"
    private val NOTIFICATION_ID = 1

    private var audioCaptureService: AudioCaptureService? = null
    private var tcpAudioServer: TcpAudioServer? = null

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    companion object {
        const val ACTION_START_SERVER = "com.example.wifimicserver.ACTION_START_SERVER"
        const val ACTION_STOP_SERVER = "com.example.wifimicserver.ACTION_STOP_SERVER"
        const val EXTRA_PORT = "com.example.wifimicserver.EXTRA_PORT"
        var IS_SERVICE_RUNNING = false
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        audioCaptureService = AudioCaptureService()

        // Initialize WakeLock
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WifiMicServer::CpuWakeLock")
        wakeLock?.setReferenceCounted(false) // Important to manage release manually

        // Initialize WifiLock
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "WifiMicServer::WifiLock")
        wifiLock?.setReferenceCounted(false)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand: ${intent?.action}")

        when (intent?.action) {
            ACTION_START_SERVER -> {
                val port = intent.getIntExtra(EXTRA_PORT, 50005)
                startServer(port)
            }
            ACTION_STOP_SERVER -> {
                stopServer()
            }
        }
        return START_NOT_STICKY
    }

    private fun startServer(port: Int) {
        if (IS_SERVICE_RUNNING) {
            Log.w(TAG, "Server already running")
            return
        }

        Log.d(TAG, "Starting server on port $port")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Server Running on port $port"))

        wakeLock?.acquire(10*60*1000L /*10 minutes timeout*/)
        wifiLock?.acquire()

        audioCaptureService?.start()

        if (audioCaptureService == null) {
            Log.e(TAG, "AudioCaptureService is null, cannot start TCP server.")
            stopSelf()
            return
        }

        tcpAudioServer = TcpAudioServer(port, audioCaptureService!!)
        tcpAudioServer?.start()

        IS_SERVICE_RUNNING = true
        Log.d(TAG, "Server started and foreground service active.")
    }

    private fun stopServer() {
        Log.d(TAG, "Stopping server")
        tcpAudioServer?.stop()
        tcpAudioServer = null

        audioCaptureService?.stop()

        wifiLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock?.let {
            if (it.isHeld) it.release()
        }

        stopForeground(true)
        stopSelf()
        IS_SERVICE_RUNNING = false
        Log.d(TAG, "Server stopped.")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Audio Server Channel",
                NotificationManager.IMPORTANCE_LOW // Use LOW to avoid sound/vibration
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(contentText: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)

        val stopIntent = Intent(this, AudioServerService::class.java).apply {
            action = ACTION_STOP_SERVER
        }
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, pendingIntentFlags)

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("WiFi Mic Server")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_stop_placeholder, "Stop Server", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy")
        if (IS_SERVICE_RUNNING) {
            stopServer()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}