/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.maps.engine.valhalla

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.prauga.maps.core.routing.RoutingEngine

@Module
@InstallIn(SingletonComponent::class)
internal abstract class RoutingModule {
    @Binds abstract fun bindRoutingEngine(engine: ValhallaRoutingEngine): RoutingEngine
}
