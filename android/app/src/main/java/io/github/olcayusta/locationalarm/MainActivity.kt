package io.github.olcayusta.locationalarm

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Polygon

private const val MIN_RADIUS_METERS = 20
private val DEFAULT_CENTER = GeoPoint(41.0082, 28.9784)

class MainActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var searchInput: EditText
    private lateinit var radiusLabel: TextView
    private lateinit var radiusSeekBar: SeekBar
    private lateinit var targetLabel: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var statusText: TextView
    private lateinit var backgroundLocationCard: LinearLayout
    private lateinit var approximateLocationWarning: TextView
    private lateinit var fullScreenIntentWarning: TextView
    private lateinit var batteryOptimizationCard: LinearLayout

    private lateinit var prefs: PrefsRepository
    private lateinit var geofenceManager: GeofenceManager

    private var targetMarker: Marker? = null
    private var radiusCircle: Polygon? = null
    private var currentTarget: AlarmTarget? = null

    private val foregroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onForegroundLocationResult()
    }

    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        refreshPermissionUi()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        refreshPermissionUi()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = PrefsRepository(this)
        geofenceManager = GeofenceManager(this)

        bindViews()
        setupMap()
        setupRadiusSeekBar()
        setupSearch()
        setupButtons()

        restoreSavedTarget()
        refreshPermissionUi()
    }

    private fun bindViews() {
        mapView = findViewById(R.id.mapView)
        searchInput = findViewById(R.id.searchInput)
        radiusLabel = findViewById(R.id.radiusLabel)
        radiusSeekBar = findViewById(R.id.radiusSeekBar)
        targetLabel = findViewById(R.id.targetLabel)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        statusText = findViewById(R.id.statusText)
        backgroundLocationCard = findViewById(R.id.backgroundLocationCard)
        approximateLocationWarning = findViewById(R.id.approximateLocationWarning)
        fullScreenIntentWarning = findViewById(R.id.fullScreenIntentWarning)
        batteryOptimizationCard = findViewById(R.id.batteryOptimizationCard)

        findViewById<Button>(R.id.openLocationSettingsButton).setOnClickListener {
            openAppSettings()
        }
        findViewById<Button>(R.id.disableBatteryOptimizationButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(13.0)
        mapView.controller.setCenter(DEFAULT_CENTER)

        val eventsReceiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                setTarget(p.latitude, p.longitude)
                return true
            }

            override fun longPressHelper(p: GeoPoint): Boolean = false
        }
        mapView.overlays.add(MapEventsOverlay(eventsReceiver))
    }

    private fun setupRadiusSeekBar() {
        updateRadiusLabel(radiusSeekBar.progress + MIN_RADIUS_METERS)
        radiusSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val radius = progress + MIN_RADIUS_METERS
                updateRadiusLabel(radius)
                currentTarget?.let { setTarget(it.latitude, it.longitude) }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun updateRadiusLabel(radius: Int) {
        radiusLabel.text = getString(R.string.radius_label, radius)
    }

    private fun setupSearch() {
        findViewById<Button>(R.id.searchButton).setOnClickListener { performSearch() }
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }
    }

    private fun performSearch() {
        val query = searchInput.text.toString().trim()
        if (query.isEmpty()) return

        statusText.text = "Aranıyor…"
        lifecycleScope.launch {
            val result = try {
                NominatimClient.search(query)
            } catch (e: Exception) {
                null
            }

            if (result == null) {
                statusText.text = "Sonuç bulunamadı."
                return@launch
            }

            mapView.controller.setCenter(GeoPoint(result.latitude, result.longitude))
            mapView.controller.setZoom(15.0)
            setTarget(result.latitude, result.longitude)
            statusText.text = "Hedef adres bulundu ve işaretlendi."
        }
    }

    private fun setupButtons() {
        startButton.setOnClickListener { onStartClicked() }
        stopButton.setOnClickListener { onStopClicked() }
    }

    private fun setTarget(latitude: Double, longitude: Double, persist: Boolean = true) {
        val radiusMeters = (radiusSeekBar.progress + MIN_RADIUS_METERS).toFloat()
        val target = AlarmTarget(latitude, longitude, radiusMeters)
        currentTarget = target

        val point = GeoPoint(latitude, longitude)

        if (targetMarker == null) {
            targetMarker = Marker(mapView).also { mapView.overlays.add(it) }
        }
        targetMarker?.position = point

        if (radiusCircle == null) {
            radiusCircle = Polygon(mapView).apply {
                fillPaint.color = 0x293B82F6
                outlinePaint.color = 0xFF3B82F6.toInt()
            }
            mapView.overlays.add(radiusCircle)
        }
        radiusCircle?.points = Polygon.pointsAsCircle(point, radiusMeters.toDouble())

        mapView.invalidate()

        targetLabel.text = getString(R.string.target_set, latitude, longitude)
        startButton.isEnabled = true

        if (persist) {
            prefs.saveTarget(target)
        }
    }

    private fun restoreSavedTarget() {
        val saved = prefs.loadTarget() ?: return
        radiusSeekBar.progress = (saved.radiusMeters.toInt() - MIN_RADIUS_METERS).coerceIn(0, radiusSeekBar.max)
        updateRadiusLabel(saved.radiusMeters.toInt())
        setTarget(saved.latitude, saved.longitude, persist = false)
        mapView.controller.setCenter(GeoPoint(saved.latitude, saved.longitude))
        mapView.controller.setZoom(15.0)

        if (prefs.isTracking) {
            startButton.isEnabled = false
            stopButton.isEnabled = true
            statusText.text = getString(R.string.status_tracking)
        }
    }

    private fun onStartClicked() {
        val target = currentTarget ?: return

        if (!hasForegroundLocationPermission()) {
            foregroundLocationLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
            return
        }

        geofenceManager.addGeofence(
            target,
            onSuccess = {
                prefs.isTracking = true
                startButton.isEnabled = false
                stopButton.isEnabled = true
                statusText.text = getString(R.string.status_tracking)
            },
            onFailure = {
                Toast.makeText(this, it.message ?: "Hata", Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun onStopClicked() {
        geofenceManager.removeGeofence()
        prefs.isTracking = false
        startButton.isEnabled = true
        stopButton.isEnabled = false
        statusText.text = getString(R.string.status_stopped)
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        refreshPermissionUi()
    }

    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }

    // --- Permissions -----------------------------------------------------

    private fun hasForegroundLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun onForegroundLocationResult() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            requestNotificationPermissionIfNeeded()
        }

        refreshPermissionUi()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun refreshPermissionUi() {
        val fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        approximateLocationWarning.visibility =
            if (coarseGranted && !fineGranted) android.view.View.VISIBLE else android.view.View.GONE

        val backgroundGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        backgroundLocationCard.visibility =
            if (fineGranted && !backgroundGranted) android.view.View.VISIBLE else android.view.View.GONE

        val canUseFullScreen = if (Build.VERSION.SDK_INT >= 34) {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).canUseFullScreenIntent()
        } else {
            true
        }
        fullScreenIntentWarning.visibility = if (canUseFullScreen) android.view.View.GONE else android.view.View.VISIBLE
        fullScreenIntentWarning.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 34) {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName"))
                )
            }
        }

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        val ignoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(packageName)
        batteryOptimizationCard.visibility =
            if (ignoringBatteryOptimizations) android.view.View.GONE else android.view.View.VISIBLE

        if (fineGranted) {
            requestNotificationPermissionIfNeeded()
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }
}
