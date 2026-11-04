/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.maps.core.location

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class LocationModule {
    @Binds abstract fun bindLocationProvider(provider: PlatformLocationProvider): LocationProvider

    @Binds abstract fun bindHeadingProvider(provider: SensorHeadingProvider): HeadingProvider
}
