/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.maps.core.regions

import kotlinx.coroutines.flow.StateFlow

/** A region whose offline data (place search and routing) can be downloaded. */
data class Region(
    val id: String,
    val name: String,
    /** The OpenStreetMap date the data was built from, such as "2026-11-10". */
    val version: String,
    val sizeBytes: Long,
)

sealed interface RegionStatus {
    data object NotInstalled : RegionStatus

    data class Installed(val version: String, val updateAvailable: Boolean) : RegionStatus

    /** Waiting for the download before it to finish. */
    data object Queued : RegionStatus

    /** [progress] from 0 to 1. */
    data class Downloading(val progress: Float) : RegionStatus

    data class Failed(val reason: RegionFailure) : RegionStatus
}

enum class RegionFailure {
    /** The list of regions or a file couldn't be fetched. */
    Network,

    /** A file arrived but didn't match its checksum. */
    Corrupt,

    /** Not enough free space on the device. */
    Storage,
}

data class RegionState(val region: Region, val status: RegionStatus)

/**
 * Offline data for regions: what can be downloaded, what is on the device, and getting it there.
 * Any number of regions can be installed; routes run across neighbouring ones.
 */
interface RegionRepository {
    /** Whether this build knows where to download regions from. */
    val isConfigured: Boolean

    /** Every region the server offers, with how it stands on this device. */
    val regions: StateFlow<List<RegionState>>

    /** Fetches the list of regions again. */
    suspend fun refresh()

    /**
     * Downloads [id], or updates it, after any download already under way; it carries on in the
     * background. Installed regions on older data are updated with it, so all stay on one build.
     */
    fun download(id: String)

    fun delete(id: String)
}
