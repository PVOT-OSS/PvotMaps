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

// One database per installed region, as tools/search/build_index.py writes them.
private const val SearchDir = "search"
private const val DatabaseSuffix = ".db"

// Names that start with the query come first, then by rank (neighbourhoods before landmarks before
// shops before streets), then by distance; longitude is scaled by cos²(latitude) to keep it round.
// The same order is applied again when merging the regions' results.
private const val SearchSql =
    """
    SELECT p.id, p.name, p.local_name, p.category, p.kind, p.lat, p.lon, p.area, p.street,
        p.housenumber, p.folded_name LIKE ? AS starts, p.rank,
        (p.lat - ?) * (p.lat - ?) * ? + (p.lon - ?) * (p.lon - ?) * ? AS distance
    FROM places_fts JOIN places p ON p.id = places_fts.docid
    WHERE places_fts MATCH ?
    ORDER BY starts DESC, p.rank, distance
    LIMIT ?
    """

private class Ranked(val place: Place, val starts: Boolean, val rank: Int, val distance: Double)

@Singleton
internal class SqlitePlaceSearch
@Inject
constructor(@param:ApplicationContext private val context: Context) : PlaceSearch {

    private class Opened(val database: SQLiteDatabase, val modified: Long)

    private val opened = mutableMapOf<String, Opened>()

    override suspend fun isAvailable(): Boolean =
        withContext(Dispatchers.IO) { databaseFiles().isNotEmpty() }

    override suspend fun search(query: String, near: Coordinates?, limit: Int): List<Place> =
        withContext(Dispatchers.IO) {
            val words = SearchText.words(query)
            if (words.isEmpty()) return@withContext emptyList()
            val lat = near?.latitude ?: 0.0
            val lon = near?.longitude ?: 0.0
            val latWeight = if (near == null) 0.0 else 1.0
            val lonWeight = if (near == null) 0.0 else cos(Math.toRadians(lat)).let { it * it }
            val args =
                arrayOf(
                    words.joinToString(" ") + "%",
                    lat.toString(),
                    lat.toString(),
                    latWeight.toString(),
                    lon.toString(),
                    lon.toString(),
                    lonWeight.toString(),
                    words.joinToString(" ") { "$it*" },
                    limit.toString(),
                )
            open()
                .flatMap { (name, db) ->
                    db.rawQuery(SearchSql, args).use { cursor ->
                        buildList { while (cursor.moveToNext()) add(cursor.toRanked(name)) }
                    }
                }
                .sortedWith(
                    compareByDescending<Ranked> { it.starts }
                        .thenBy { it.rank }
                        .thenBy { it.distance }
                )
                // Regions overlap at their edges; a place there is in both of their databases.
                .distinctBy {
                    Triple(
                        SearchText.fold(it.place.name),
                        it.place.coordinates.latitude,
                        it.place.coordinates.longitude,
                    )
                }
                .take(limit)
                .map { it.place }
        }

    // Opens every region's database, closing those deleted and reopening those replaced.
    @Synchronized
    private fun open(): List<Pair<String, SQLiteDatabase>> {
        val files = databaseFiles().associateBy { it.name }
        opened.keys.toList().forEach { name ->
            val file = files[name]
            if (file == null || file.lastModified() != opened.getValue(name).modified) {
                opened.remove(name)?.database?.close()
            }
        }
        files.forEach { (name, file) ->
            if (name !in opened) {
                val db =
                    SQLiteDatabase.openDatabase(
                        file.path,
                        null,
                        SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
                    )
                opened[name] = Opened(db, file.lastModified())
            }
        }
        return opened.map { (name, it) -> name to it.database }
    }

    private fun databaseFiles(): List<File> =
        File(context.filesDir, SearchDir)
            .listFiles { file -> file.isFile && file.name.endsWith(DatabaseSuffix) }
            ?.toList()
            .orEmpty()

    // Row ids repeat across regions' databases; mix in which database the row came from.
    private fun Cursor.toRanked(database: String) =
        Ranked(
            place = toPlace(database.hashCode().toLong() shl 32 or getLong(0)),
            starts = getInt(10) != 0,
            rank = getInt(11),
            distance = getDouble(12),
        )

    private fun Cursor.toPlace(id: Long) =
        Place(
            id = id,
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
