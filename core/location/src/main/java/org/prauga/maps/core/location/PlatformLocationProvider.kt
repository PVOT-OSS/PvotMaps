package org.prauga.maps.core.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import org.prauga.maps.core.model.Coordinates
import javax.inject.Inject
import kotlin.coroutines.resume

// Platform LocationManager only: no Google Play services.
internal class PlatformLocationProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : LocationProvider {

    private val locationManager = context.getSystemService(LocationManager::class.java)

    // Callers ask for location permission first; getProviders(true) only lists providers it allows.
    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(): Coordinates? {
        val enabled = locationManager.getProviders(true)
        val provider = ProviderPreference.firstOrNull { it in enabled } ?: return null
        val location = suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            LocationManagerCompat.getCurrentLocation(
                locationManager,
                provider,
                signal,
                ContextCompat.getMainExecutor(context),
            ) { continuation.resume(it) }
        } ?: locationManager.getLastKnownLocation(provider)
        return location?.let { Coordinates(it.latitude, it.longitude) }
    }

    private companion object {
        // FUSED_PROVIDER is API 31; below that it's simply never among the enabled providers.
        @SuppressLint("InlinedApi")
        val ProviderPreference = listOf(
            LocationManager.FUSED_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
        )
    }
}
