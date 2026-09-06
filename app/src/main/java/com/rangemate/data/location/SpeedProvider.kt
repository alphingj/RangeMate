package com.rangemate.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Vehicle speed via the Fused Location Provider (GPS + network + sensors),
 * which keeps working where raw GPS alone drops out (urban canyons, tree cover).
 */
@Singleton
class SpeedProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

    private val _speedKmh = MutableStateFlow(0f)
    val speedKmh: StateFlow<Float> = _speedKmh.asStateFlow()

    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private var locationCallback: LocationCallback? = null
    private var started = false

    fun start() {
        if (started) return
        if (!hasLocationPermission()) {
            _isEnabled.value = false
            return
        }
        started = true
        _isEnabled.value = true
        requestLocationUpdates()
    }

    fun stop() {
        started = false
        _isEnabled.value = false
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
        locationCallback = null
        _speedKmh.value = 0f
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateDistanceMeters(1f)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                if (location.hasSpeed() && location.speed >= 0f) {
                    _speedKmh.value = location.speed * 3.6f // m/s to km/h
                }
            }

            override fun onLocationAvailability(available: com.google.android.gms.location.LocationAvailability) {
                if (!available.isLocationAvailable) {
                    _speedKmh.value = 0f
                }
            }
        }

        try {
            fusedClient.requestLocationUpdates(
                request,
                locationCallback!!,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            _isEnabled.value = false
            started = false
        }
    }
}
