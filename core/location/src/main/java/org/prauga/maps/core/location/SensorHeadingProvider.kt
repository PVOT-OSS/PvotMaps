/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.maps.core.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow

// Smaller turns than this are sensor noise and not worth redrawing the map for.
private const val MinTurnDegrees = 2f

internal class SensorHeadingProvider
@Inject
constructor(@param:ApplicationContext private val context: Context) : HeadingProvider {

    private val sensorManager = context.getSystemService(SensorManager::class.java)

    override fun heading(): Flow<Float> {
        val sensor =
            sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) ?: return emptyFlow()
        return callbackFlow {
                val rotation = FloatArray(9)
                val remapped = FloatArray(9)
                val orientation = FloatArray(3)
                val listener =
                    object : SensorEventListener {
                        override fun onSensorChanged(event: SensorEvent) {
                            SensorManager.getRotationMatrixFromVector(rotation, event.values)
                            remapForDisplay(rotation, remapped)
                            SensorManager.getOrientation(remapped, orientation)
                            trySend(
                                (Math.toDegrees(orientation[0].toDouble()).toFloat() + 360f) % 360f
                            )
                        }

                        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
                    }
                sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
                awaitClose { sensorManager.unregisterListener(listener) }
            }
            .distinctUntilChanged { old, new -> turn(old, new) < MinTurnDegrees }
    }

    // The sensor reports in the device's natural orientation; the heading follows the screen.
    private fun remapForDisplay(rotation: FloatArray, out: FloatArray) {
        val (x, y) =
            when (ContextCompat.getDisplayOrDefault(context).rotation) {
                Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
            }
        SensorManager.remapCoordinateSystem(rotation, x, y, out)
    }

    private fun turn(from: Float, to: Float): Float {
        val delta = abs(from - to) % 360f
        return if (delta > 180f) 360f - delta else delta
    }
}
