package com.office.locationtool

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

class MockLocationService : Service() {

    companion object {
        const val ACTION_STOP = "stop"
        const val EXTRA_LAT = "lat"
        const val EXTRA_LNG = "lng"
        const val CHANNEL_ID = "mock_loc"
        var running = false
        var lastError: String? = null
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var lm: LocationManager
    private val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
    private var lat = 0.0
    private var lng = 0.0

    private val tick = object : Runnable {
        override fun run() {
            push()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { shutdown(); return START_NOT_STICKY }
        lat = intent?.getDoubleExtra(EXTRA_LAT, lat) ?: lat
        lng = intent?.getDoubleExtra(EXTRA_LNG, lng) ?: lng
        lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        goForeground()
        try {
            providers.forEach {
                try { lm.removeTestProvider(it) } catch (_: Exception) {}
                lm.addTestProvider(it, false, false, false, false, true, true, true,
                    Criteria.POWER_LOW, Criteria.ACCURACY_FINE)
                lm.setTestProviderEnabled(it, true)
            }
            lastError = null
            running = true
            handler.removeCallbacks(tick)
            handler.post(tick)
        } catch (e: SecurityException) {
            lastError = "NOT_MOCK_APP"
            shutdown()
        }
        return START_STICKY
    }

    private fun push() {
        providers.forEach {
            val loc = Location(it).apply {
                latitude = lat; longitude = lng
                accuracy = 5f; altitude = 0.0; speed = 0f
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            }
            try { lm.setTestProviderLocation(it, loc) } catch (_: Exception) {}
        }
    }

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26)
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Location", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Custom location active")
            .setContentText("%.5f, %.5f".format(lat, lng))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(open).setOngoing(true).build()
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(this, 1, n, type)
    }

    private fun shutdown() {
        handler.removeCallbacks(tick)
        if (::lm.isInitialized) providers.forEach { try { lm.removeTestProvider(it) } catch (_: Exception) {} }
        running = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() { shutdown(); super.onDestroy() }
}
