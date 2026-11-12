/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.maps.feature.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prauga.pvot.designsystem.components.button.PvotButton
import com.prauga.pvot.designsystem.components.button.PvotButtonStyle
import com.prauga.pvot.designsystem.components.progress.PvotLinearProgress
import com.prauga.pvot.designsystem.theme.PvotSpacing
import kotlin.math.roundToInt
import org.prauga.maps.core.regions.RegionFailure
import org.prauga.maps.core.regions.RegionState
import org.prauga.maps.core.regions.RegionStatus

private const val BytesPerMegabyte = 1024.0 * 1024.0

/** Regions whose search and routing data can be kept on the phone. */
@Composable
internal fun OfflineRegions(viewModel: RegionsViewModel = hiltViewModel()) {
    val regions by viewModel.regions.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.refresh() }
    Column(
        modifier = Modifier.padding(top = PvotSpacing.L),
        verticalArrangement = Arrangement.spacedBy(PvotSpacing.M),
    ) {
        Text(
            text = stringResource(R.string.map_offline_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        when {
            !viewModel.isConfigured -> Note(stringResource(R.string.map_offline_not_set_up))
            regions.isEmpty() -> Note(stringResource(R.string.map_offline_loading))
            else ->
                regions.forEach { state ->
                    if (state.region.id.isEmpty()) {
                        Note(stringResource(R.string.map_offline_failed_network))
                    } else {
                        RegionRow(
                            state,
                            onDownload = { viewModel.download(state.region.id) },
                            onDelete = { viewModel.delete(state.region.id) },
                        )
                    }
                }
        }
    }
}

@Composable
private fun RegionRow(state: RegionState, onDownload: () -> Unit, onDelete: () -> Unit) {
    val region = state.region
    val status = state.status
    val megabytes = (region.sizeBytes / BytesPerMegabyte).roundToInt()
    Column(verticalArrangement = Arrangement.spacedBy(PvotSpacing.S)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = region.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text =
                        when (status) {
                            is RegionStatus.Installed ->
                                if (status.updateAvailable) {
                                    stringResource(R.string.map_offline_update_available, megabytes)
                                } else {
                                    stringResource(R.string.map_offline_installed, status.version)
                                }
                            is RegionStatus.Downloading ->
                                stringResource(
                                    R.string.map_offline_downloading,
                                    (status.progress * 100).roundToInt(),
                                )
                            is RegionStatus.Failed -> stringResource(status.reason.message)
                            RegionStatus.Queued -> stringResource(R.string.map_offline_queued)
                            RegionStatus.NotInstalled ->
                                stringResource(R.string.map_offline_size, megabytes)
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (status) {
                is RegionStatus.Installed ->
                    if (status.updateAvailable) {
                        PvotButton(
                            stringResource(R.string.map_offline_update),
                            onClick = onDownload,
                        )
                    } else {
                        PvotButton(
                            stringResource(R.string.map_offline_delete),
                            onClick = onDelete,
                            style = PvotButtonStyle.Text,
                        )
                    }
                is RegionStatus.Downloading,
                RegionStatus.Queued -> Unit
                is RegionStatus.Failed,
                RegionStatus.NotInstalled ->
                    PvotButton(stringResource(R.string.map_offline_download), onClick = onDownload)
            }
        }
        if (status is RegionStatus.Downloading) {
            PvotLinearProgress(progress = { status.progress }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private val RegionFailure.message: Int
    get() =
        when (this) {
            RegionFailure.Network -> R.string.map_offline_failed_network
            RegionFailure.Corrupt -> R.string.map_offline_failed_corrupt
            RegionFailure.Storage -> R.string.map_offline_failed_storage
        }
