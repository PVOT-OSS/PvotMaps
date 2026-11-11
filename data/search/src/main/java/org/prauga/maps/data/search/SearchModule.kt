/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.maps.data.search

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.prauga.maps.core.search.PlaceSearch
import org.prauga.maps.core.search.RecentPlaces

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SearchModule {
    @Binds abstract fun bindPlaceSearch(search: SqlitePlaceSearch): PlaceSearch

    @Binds abstract fun bindRecentPlaces(recents: DataStoreRecentPlaces): RecentPlaces
}
