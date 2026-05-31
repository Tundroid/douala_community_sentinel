package com.moleculesoft.dcs.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.ConnectionResult
import com.moleculesoft.dcs.R
import com.moleculesoft.dcs.MainActivity
import com.moleculesoft.dcs.data.DcsRepository
import com.moleculesoft.dcs.data.NoiseRecorder
import com.moleculesoft.dcs.data.SensorData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.sqrt

class SensorService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var noiseRecorder: NoiseRecorder
    private lateinit var repository: DcsRepository
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var lastSaveTime = 0L

    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private var variance = 0.0
    private var currentNeighborhood = "Unknown"

    private val CHANNEL_ID = "SensorServiceChannel"

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext)
        
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(this)
        if (resultCode != ConnectionResult.SUCCESS) {
            android.util.Log.e("SensorService", "Google Play Services not available: ${googleApiAvailability.getErrorString(resultCode)}")
        }

        noiseRecorder = NoiseRecorder(this)
        noiseRecorder.start(cacheDir)
        repository = DcsRepository()
        
        createNotificationChannel()
        startForeground(1, createNotification("Collecting urban data..."))

        setupLocationUpdates()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    private fun setupLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setMinUpdateIntervalMillis(5000)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    processData(location)
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            android.util.Log.e("SensorService", "Location permission missing: ${e.message}")
        }
    }

    private fun processData(location: Location) {
        val currentTime = System.currentTimeMillis()
        val dbLevel = noiseRecorder.getDb()
        
        currentNeighborhood = getNeighborhood(location.latitude, location.longitude)
        val roadQuality = classifyRoadQuality(variance)

        // Every 2 minutes, save data to repository
        if (currentTime - lastSaveTime > 120000) {
            serviceScope.launch {
                val data = SensorData(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accelerometerVariance = variance,
                    noiseLevelDb = dbLevel,
                    neighborhood = currentNeighborhood
                )
                repository.saveSensorData(data)
                lastSaveTime = currentTime
            }
        }

        val status = "Area: $currentNeighborhood | Road: $roadQuality | Noise: ${String.format("%.1f", dbLevel)} dB"
        updateNotification(status)
    }

    private fun getNeighborhood(lat: Double, lon: Double): String {
        return when {
            lat in 4.05..4.10 && lon in 9.60..9.68 -> "Bonaberi"
            lat in 4.04..4.06 && lon in 9.69..9.72 -> "Akwa"
            lat in 4.02..4.04 && lon in 9.71..9.75 -> "New Bell"
            else -> "Douala"
        }
    }

    private fun classifyRoadQuality(vibration: Double): String {
        return when {
            vibration < 1.0 -> "Good"
            vibration < 3.0 -> "Fair"
            vibration < 6.0 -> "Poor"
            else -> "Critical"
        }
    }

    private fun createNotification(content: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Douala Community Sentinel")
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(content: String) {
        val notification = createNotification(content)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Douala Sensor Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val deltaX = (x - lastX).absoluteValue
            val deltaY = (y - lastY).absoluteValue
            val deltaZ = (z - lastZ).absoluteValue

            // Simple variance/vibration score
            variance = sqrt(deltaX.pow(2) + deltaY.pow(2) + deltaZ.pow(2)).toDouble()

            lastX = x
            lastY = y
            lastZ = z
        }
    }

    private val Float.absoluteValue: Float
        get() = if (this < 0) -this else this

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        fusedLocationClient.removeLocationUpdates(locationCallback)
        noiseRecorder.stop()
    }
}
