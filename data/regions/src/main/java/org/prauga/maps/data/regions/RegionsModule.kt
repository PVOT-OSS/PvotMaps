/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.maps.data.regions

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.prauga.maps.core.regions.RegionRepository

@Module
@InstallIn(SingletonComponent::class)
internal abstract class RegionsModule {
    @Binds abstract fun bindRegionRepository(repository: HttpRegionRepository): RegionRepository
}
