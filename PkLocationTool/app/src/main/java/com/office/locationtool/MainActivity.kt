package com.office.locationtool

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.provider.Settings
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var coords: TextView
    private var marker: Marker? = null
    private var selected: GeoPoint? = null

    // Pakistan bounding box
    private val minLat = 23.6; private val maxLat = 37.1
    private val minLng = 60.8; private val maxLng = 77.9

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
        Configuration.getInstance().userAgentValue = packageName
        setContentView(R.layout.activity_main)

        map = findViewById(R.id.map)
        coords = findViewById(R.id.coords)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(5.5)
        map.controller.setCenter(GeoPoint(30.3753, 69.3451))

        map.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean { select(p); return true }
            override fun longPressHelper(p: GeoPoint): Boolean = false
        }))

        val box = findViewById<EditText>(R.id.searchBox)
        findViewById<Button>(R.id.searchBtn).setOnClickListener { search(box.text.toString()) }
        box.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_SEARCH) { search(box.text.toString()); true } else false
        }
        findViewById<Button>(R.id.setBtn).setOnClickListener { applyLocation() }
        findViewById<Button>(R.id.stopBtn).setOnClickListener {
            startService(Intent(this, MockLocationService::class.java).setAction(MockLocationService.ACTION_STOP))
            toast("Stopped. Real location restored.")
        }
        askPermissions()
    }

    private fun askPermissions() {
        val need = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) need.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = need.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1)
    }

    private fun inPakistan(p: GeoPoint) = p.latitude in minLat..maxLat && p.longitude in minLng..maxLng

    private fun select(p: GeoPoint) {
        if (!inPakistan(p)) { toast("Please choose a location inside Pakistan"); return }
        selected = p
        marker?.let { map.overlays.remove(it) }
        marker = Marker(map).apply { position = p; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM) }
        map.overlays.add(marker)
        map.invalidate()
        coords.text = "Selected: %.6f, %.6f".format(p.latitude, p.longitude)
    }

    private fun search(q: String) {
        if (q.isBlank()) return
        thread {
            try {
                val url = "https://nominatim.openstreetmap.org/search?format=json&limit=1&countrycodes=pk&q=" +
                        URLEncoder.encode(q, "UTF-8")
                val c = URL(url).openConnection() as HttpURLConnection
                c.setRequestProperty("User-Agent", "PkLocationTool/1.0")
                val arr = JSONArray(c.inputStream.bufferedReader().readText())
                runOnUiThread {
                    if (arr.length() == 0) { toast("Place not found"); return@runOnUiThread }
                    val o = arr.getJSONObject(0)
                    val p = GeoPoint(o.getDouble("lat"), o.getDouble("lon"))
                    map.controller.animateTo(p); map.controller.setZoom(15.0)
                    select(p)
                }
            } catch (e: Exception) { runOnUiThread { toast("Search failed. Check internet.") } }
        }
    }

    private fun applyLocation() {
        val p = selected ?: return toast("Select a location first")
        startForegroundService(Intent(this, MockLocationService::class.java)
            .putExtra(MockLocationService.EXTRA_LAT, p.latitude)
            .putExtra(MockLocationService.EXTRA_LNG, p.longitude))
        coords.postDelayed({
            if (MockLocationService.lastError == "NOT_MOCK_APP") {
                AlertDialog.Builder(this)
                    .setTitle("One-time setup needed")
                    .setMessage("Open Developer options > 'Select mock location app' and choose PK Location Tool.\n\n" +
                            "(Enable Developer options first: Settings > About phone > tap Build number 7 times.)")
                    .setPositiveButton("Open Developer options") { _, _ ->
                        try { startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
                        catch (e: Exception) { startActivity(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)) }
                    }
                    .setNegativeButton("Cancel", null).show()
            } else toast("Location set to %.5f, %.5f".format(p.latitude, p.longitude))
        }, 800)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    override fun onResume() { super.onResume(); map.onResume() }
    override fun onPause() { super.onPause(); map.onPause() }
}
