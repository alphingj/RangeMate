package com.rangemate.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeedProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _speedKmh = MutableStateFlow(0f)
    val speedKmh: StateFlow<Float> = _speedKmh.asStateFlow()

    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private var locationListener: LocationListener? = null

    fun start() {
        if (hasLocationPermission()) {
            _isEnabled.value = true
            requestLocationUpdates()
        } else {
            _isEnabled.value = false
        }
    }

    fun stop() {
        _isEnabled.value = false
        locationListener?.let { listener ->
            try {
                locationManager.removeUpdates(listener)
            } catch (e: Exception) {
                // Ignore
            }
        }
        locationListener = null
        _speedKmh.value = 0f
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdates() {
        locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val speedMs = location.speed
                if (speedMs >= 0f) {
                    _speedKmh.value = speedMs * 3.6f  // m/s to km/h
                }
            }

            override fun onStatusChanged(provider: String, status: Int, extras: android.os.Bundle) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {
                _speedKmh.value = 0f
            }
        }

        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000, // 1 second min time
                1f,   // 1 meter min distance
                locationListener!!,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            _isEnabled.value = false
        } catch (e: IllegalArgumentException) {
            // GPS provider not available
            _isEnabled.value = false
        }
    }
}