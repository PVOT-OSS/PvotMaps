/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.maps.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.prauga.maps.core.regions.RegionRepository
import org.prauga.maps.core.regions.RegionState

@HiltViewModel
internal class RegionsViewModel @Inject constructor(private val repository: RegionRepository) :
    ViewModel() {

    val isConfigured: Boolean = repository.isConfigured

    val regions: StateFlow<List<RegionState>> = repository.regions

    fun refresh() {
        viewModelScope.launch { repository.refresh() }
    }

    fun download(id: String) = repository.download(id)

    fun delete(id: String) = repository.delete(id)
}
