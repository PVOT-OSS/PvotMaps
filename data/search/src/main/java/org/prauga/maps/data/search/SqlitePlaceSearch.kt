/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.maps.data.search

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.cos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.prauga.maps.core.model.Coordinates
import org.prauga.maps.core.model.Place
import org.prauga.maps.core.model.PlaceKind
import org.prauga.maps.core.search.PlaceSearch
import org.prauga.maps.core.search.SearchText

// The database tools/search/build_index.py writes, installed per region on the device.
private const val DatabasePath = "search/places.db"

// Names that start with the query come first, then by rank (neighbourhoods before landmarks before
// shops before streets), then by distance; longitude is scaled by cos²(latitude) to keep it round.
private const val SearchSql =
    """
    SELECT p.id, p.name, p.local_name, p.category, p.kind, p.lat, p.lon, p.area, p.street,
        p.housenumber
    FROM places_fts JOIN places p ON p.id = places_fts.docid
    WHERE places_fts MATCH ?
    ORDER BY p.folded_name LIKE ? DESC, p.rank,
        (p.lat - ?) * (p.lat - ?) * ? + (p.lon - ?) * (p.lon - ?) * ?
    LIMIT ?
    """

@Singleton
internal class SqlitePlaceSearch
@Inject
constructor(@param:ApplicationContext private val context: Context) : PlaceSearch {

    private var database: SQLiteDatabase? = null

    override suspend fun isAvailable(): Boolean =
        withContext(Dispatchers.IO) { databaseFile().isFile }

    override suspend fun search(query: String, near: Coordinates?, limit: Int): List<Place> =
        withContext(Dispatchers.IO) {
            val words = SearchText.words(query)
            val db = open()
            if (words.isEmpty() || db == null) return@withContext emptyList()
            val lat = near?.latitude ?: 0.0
            val lon = near?.longitude ?: 0.0
            val latWeight = if (near == null) 0.0 else 1.0
            val lonWeight = if (near == null) 0.0 else cos(Math.toRadians(lat)).let { it * it }
            val args =
                arrayOf(
                    words.joinToString(" ") { "$it*" },
                    words.joinToString(" ") + "%",
                    lat.toString(),
                    lat.toString(),
                    latWeight.toString(),
                    lon.toString(),
                    lon.toString(),
                    lonWeight.toString(),
                    limit.toString(),
                )
            db.rawQuery(SearchSql, args).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.toPlace()) }
            }
        }

    @Synchronized
    private fun open(): SQLiteDatabase? {
        database?.let {
            return it
        }
        val file = databaseFile()
        if (!file.isFile) return null
        return SQLiteDatabase.openDatabase(
                file.path,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
            )
            .also { database = it }
    }

    private fun databaseFile() = File(context.filesDir, DatabasePath)

    private fun Cursor.toPlace() =
        Place(
            id = getLong(0),
            name = getString(1),
            localName = getStringOrNull(2),
            category = getString(3),
            kind = kindOf(getString(4)),
            coordinates = Coordinates(getDouble(5), getDouble(6)),
            area = getStringOrNull(7),
            street = getStringOrNull(8),
            houseNumber = getStringOrNull(9),
        )

    private fun Cursor.getStringOrNull(index: Int): String? =
        if (isNull(index)) null else getString(index)

    private fun kindOf(kind: String): PlaceKind =
        PlaceKind.entries.firstOrNull { it.name.equals(kind, ignoreCase = true) } ?: PlaceKind.Other
}
