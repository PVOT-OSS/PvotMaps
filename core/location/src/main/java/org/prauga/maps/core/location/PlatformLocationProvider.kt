/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.maps.core.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.prauga.maps.core.model.Coordinates

// Platform LocationManager only: no Google Play services.
internal class PlatformLocationProvider
@Inject
constructor(@param:ApplicationContext private val context: Context) : LocationProvider {

    private val locationManager = context.getSystemService(LocationManager::class.java)

    override fun isEnabled(): Boolean = bestProvider() != null

    // Callers ask for location permission first; getProviders(true) only lists providers it allows.
    @SuppressLint("MissingPermission")
    override fun lastKnownLocation(): Coordinates? =
        locationManager
            .getProviders(true)
            .mapNotNull { locationManager.getLastKnownLocation(it) }
            .maxByOrNull { it.elapsedRealtimeNanos }
            ?.let { Coordinates(it.latitude, it.longitude) }

    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(): Coordinates? {
        val provider = bestProvider() ?: return null
        val location =
            suspendCancellableCoroutine { continuation ->
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                LocationManagerCompat.getCurrentLocation(
                    locationManager,
                    provider,
                    signal,
                    ContextCompat.getMainExecutor(context),
                ) {
                    continuation.resume(it)
                }
            } ?: locationManager.getLastKnownLocation(provider)
        return location?.let { Coordinates(it.latitude, it.longitude) }
    }

    @SuppressLint("MissingPermission")
    override fun locationUpdates(): Flow<Coordinates> = callbackFlow {
        val provider = bestProvider()
        if (provider == null) {
            close()
            return@callbackFlow
        }
        val listener = LocationListenerCompat { trySend(Coordinates(it.latitude, it.longitude)) }
        val request =
            LocationRequestCompat.Builder(UpdateIntervalMillis)
                .setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY)
                .build()
        try {
            LocationManagerCompat.requestLocationUpdates(
                locationManager,
                provider,
                request,
                ContextCompat.getMainExecutor(context),
                listener,
            )
        } catch (_: SecurityException) {
            // Permission was taken away while the map was open.
            close()
        }
        awaitClose { LocationManagerCompat.removeUpdates(locationManager, listener) }
    }

    private fun bestProvider(): String? {
        val enabled = locationManager.getProviders(true)
        return ProviderPreference.firstOrNull { it in enabled }
    }

    private companion object {
        const val UpdateIntervalMillis = 1_000L

        // FUSED_PROVIDER is API 31; below that it's simply never among the enabled providers.
        @SuppressLint("InlinedApi")
        val ProviderPreference =
            listOf(
                LocationManager.FUSED_PROVIDER,
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
            )
    }
}
