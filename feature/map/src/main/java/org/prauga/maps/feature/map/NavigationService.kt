/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.maps.feature.map

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.prauga.maps.core.model.ManeuverKind

/**
 * Keeps guidance running with the screen off or another app in front, as a location foreground
 * service, and shows the next turn in its notification. It stops itself when guidance ends.
 */
@AndroidEntryPoint
internal class NavigationService : Service() {

    @Inject lateinit var session: NavigationSession

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationManagerCompat.from(this)
            .createNotificationChannel(
                NotificationChannelCompat.Builder(Channel, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName(getString(R.string.map_nav_channel))
                    .build()
            )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ActionEnd) {
            session.stop()
            stopSelf()
            return START_NOT_STICKY
        }
        val navigation = session.navigation.value
        if (navigation == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        ServiceCompat.startForeground(
            this,
            NotificationId,
            notification(navigation),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
        scope.launch {
            session.navigation.collect { current ->
                if (current == null) {
                    ServiceCompat.stopForeground(
                        this@NavigationService,
                        ServiceCompat.STOP_FOREGROUND_REMOVE,
                    )
                    stopSelf()
                } else if (
                    NotificationManagerCompat.from(this@NavigationService).areNotificationsEnabled()
                ) {
                    NotificationManagerCompat.from(this@NavigationService)
                        .notify(NotificationId, notification(current))
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(navigation: Navigation): Notification {
        val progress = navigation.progress
        val maneuver = progress?.nextManeuver ?: navigation.route.maneuvers.firstOrNull()
        val title =
            when {
                navigation.rerouting -> getString(R.string.map_nav_rerouting)
                progress?.arrived == true -> getString(R.string.map_nav_arrived)
                progress != null && maneuver != null ->
                    formatDistance(resources, progress.distanceToNextManeuverMeters)
                else -> navigation.to.name
            }
        val open =
            packageManager.getLaunchIntentForPackage(packageName)?.let {
                PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
            }
        val end =
            PendingIntent.getService(
                this,
                1,
                Intent(this, NavigationService::class.java).setAction(ActionEnd),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val kind = if (progress?.arrived == true) ManeuverKind.Arrive else maneuver?.kind
        return NotificationCompat.Builder(this, Channel)
            .setSmallIcon(kind?.icon ?: R.drawable.ic_turn_straight)
            .setContentTitle(title)
            .setContentText(maneuver?.instruction)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .addAction(0, getString(R.string.map_nav_end), end)
            .build()
    }

    companion object {
        private const val Channel = "navigation"
        private const val NotificationId = 1
        private const val ActionEnd = "org.prauga.maps.action.END_NAVIGATION"

        fun intent(context: Context) = Intent(context, NavigationService::class.java)
    }
}
