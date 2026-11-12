/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.maps.data.regions

import android.content.Context
import android.os.storage.StorageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.prauga.maps.core.regions.Region
import org.prauga.maps.core.regions.RegionFailure
import org.prauga.maps.core.regions.RegionRepository
import org.prauga.maps.core.regions.RegionState
import org.prauga.maps.core.regions.RegionStatus

private const val ManifestName = "regions.json"
private const val ManifestVersion = 2
private const val InstalledName = "regions/installed.json"
private const val DownloadDir = "regions/download"
private const val TilesDir = "routing/tiles"
private const val TilesStamp = "routing/tiles.stamp"
private const val TilesKind = "tiles"
private const val TimeoutMillis = 30_000
private const val BufferBytes = 1 shl 16
private const val ProgressStepBytes = 1L shl 20
private const val SpareBytes = 50L shl 20

// What a single-region install of an earlier version left behind.
private val LegacyFiles =
    listOf(
        "search/places.db",
        "routing/tiles.tar",
        "routing/valhalla.json",
        "routing/valhalla.device.json",
    )

private data class RemoteFile(
    val url: String,
    val target: String,
    val kind: String,
    val size: Long,
    val sha256: String,
)

private data class RemoteRegion(val region: Region, val files: List<RemoteFile>)

/** An installed region and every path, relative to the files dir, it put on the device. */
private data class Installed(val id: String, val version: String, val paths: Set<String>)

/**
 * Downloads regions from the static site [BuildConfig.REGIONS_URL] points at. Every file is checked
 * against its SHA-256 before anything is moved in place, so a failed download leaves the installed
 * regions untouched. Routing tiles from all regions share one folder; a path is only deleted once
 * no installed region still lists it, since neighbouring regions share their border tiles.
 */
@Singleton
internal class HttpRegionRepository
@Inject
constructor(@param:ApplicationContext private val context: Context) : RegionRepository {

    private val baseUrl = BuildConfig.REGIONS_URL.trimEnd('/')
    private val files = context.filesDir
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<String>(Channel.UNLIMITED)
    private val lock = Any()
    private var remote: List<RemoteRegion> = emptyList()
    private var unreachable = false
    private val transient = MutableStateFlow<Map<String, RegionStatus>>(emptyMap())
    private val _regions = MutableStateFlow<List<RegionState>>(emptyList())

    override val isConfigured: Boolean = baseUrl.isNotEmpty()

    override val regions: StateFlow<List<RegionState>> = _regions.asStateFlow()

    init {
        scope.launch {
            dropLegacyInstall()
            for (id in queue) install(id)
        }
    }

    override suspend fun refresh() {
        if (!isConfigured) return
        withContext(Dispatchers.IO) {
            try {
                remote = parseManifest(fetchText("$baseUrl/$ManifestName"))
                unreachable = false
            } catch (_: Exception) {
                // Without the list, still show the regions on the device so they can be deleted.
                unreachable = remote.isEmpty()
            }
            publish()
        }
    }

    override fun download(id: String) {
        if (remote.none { it.region.id == id }) return
        val installed = readInstalled()
        val outdated =
            installed.filter { i ->
                remote.any { it.region.id == i.id && it.region.version != i.version }
            }
        for (next in listOf(id) + outdated.map { it.id }) {
            if (transient.value[next] != null) continue
            setStatus(next, RegionStatus.Queued)
            queue.trySend(next)
        }
    }

    override fun delete(id: String) {
        synchronized(lock) {
            val installed = readInstalled()
            val gone = installed.firstOrNull { it.id == id } ?: return
            val kept = installed - gone
            removeUnused(gone.paths, kept)
            writeInstalled(kept)
            touchTiles()
        }
        publish()
    }

    private suspend fun install(id: String) {
        val target = remote.firstOrNull { it.region.id == id }
        val failure = if (target == null) RegionFailure.Network else installOrFail(target)
        setStatus(id, failure?.let { RegionStatus.Failed(it) })
    }

    private suspend fun installOrFail(remote: RemoteRegion): RegionFailure? {
        val id = remote.region.id
        setStatus(id, RegionStatus.Downloading(0f))
        val staging =
            File(files, "$DownloadDir/$id").apply {
                deleteRecursively()
                mkdirs()
            }
        try {
            val total = remote.files.sumOf { it.size }
            if (allocatableBytes() < total * 2 + SpareBytes) return RegionFailure.Storage
            var done = 0L
            val paths = mutableSetOf<String>()
            val moves = mutableListOf<Pair<File, String>>()
            for (file in remote.files) {
                val part = File(staging, file.target.replace('/', '_') + ".part")
                val sha =
                    fetchFile("$baseUrl/${file.url}", part) { bytes ->
                        setStatus(id, RegionStatus.Downloading((done + bytes).toFloat() / total))
                    }
                if (sha != file.sha256 || part.length() != file.size) return RegionFailure.Corrupt
                done += file.size
                if (file.kind == TilesKind) {
                    val unpacked = File(staging, "tiles")
                    part
                        .inputStream()
                        .buffered()
                        .use { unpackTar(it, unpacked) }
                        .forEach {
                            moves += File(unpacked, it) to "$TilesDir/$it"
                            paths += "$TilesDir/$it"
                        }
                    part.delete()
                } else {
                    moves += part to file.target
                    paths += file.target
                }
            }
            synchronized(lock) {
                val installed = readInstalled()
                val previous = installed.firstOrNull { it.id == id }
                // A region the server no longer lists can't be updated; if it is from another
                // build its tiles would mix with this one's in the shared folder, so it goes.
                val listed = this.remote.map { it.region.id }.toSet()
                val orphans =
                    installed.filter {
                        it.id != id && it.id !in listed && it.version != remote.region.version
                    }
                val others = installed.filter { it.id != id && it !in orphans }
                orphans.forEach { removeUnused(it.paths, others + listOfNotNull(previous)) }
                for ((from, to) in moves) {
                    val dest = File(files, to)
                    dest.parentFile?.mkdirs()
                    if (!from.renameTo(dest)) return RegionFailure.Storage
                }
                previous?.let { removeUnused(it.paths - paths, others) }
                writeInstalled(others + Installed(id, remote.region.version, paths))
                touchTiles()
            }
            return null
        } catch (_: IOException) {
            return RegionFailure.Network
        } finally {
            staging.deleteRecursively()
        }
    }

    // Deletes each of [paths] that none of [kept] still uses.
    private fun removeUnused(paths: Set<String>, kept: List<Installed>) {
        val used = kept.flatMap { it.paths }.toSet()
        (paths - used).forEach { File(files, it).delete() }
    }

    private fun touchTiles() {
        val stamp = File(files, TilesStamp)
        stamp.parentFile?.mkdirs()
        stamp.writeText(System.currentTimeMillis().toString())
    }

    // Counts cached data the system would clear to make room, unlike the free space alone.
    private fun allocatableBytes(): Long {
        val storage = context.getSystemService(StorageManager::class.java)
        return try {
            storage.getAllocatableBytes(storage.getUuidForPath(files))
        } catch (_: IOException) {
            0L
        }
    }

    private fun setStatus(id: String, status: RegionStatus?) {
        transient.value =
            (transient.value - id).let { if (status == null) it else it + (id to status) }
        publish()
    }

    private fun publish() {
        val installed = readInstalled().associateBy { it.id }
        val states =
            remote.map { (region, _) ->
                val mine = installed[region.id]
                val status =
                    transient.value[region.id]
                        ?: if (mine != null) {
                            RegionStatus.Installed(mine.version, mine.version != region.version)
                        } else {
                            RegionStatus.NotInstalled
                        }
                RegionState(region, status)
            }
        val offline =
            installed.values
                .filter { i -> remote.none { it.region.id == i.id } }
                .map {
                    RegionState(
                        Region(it.id, it.id, it.version, 0),
                        RegionStatus.Installed(it.version, false),
                    )
                }
        val failed =
            if (unreachable)
                listOf(
                    RegionState(Region("", "", "", 0), RegionStatus.Failed(RegionFailure.Network))
                )
            else emptyList()
        _regions.value = states + offline + failed
    }

    private fun parseManifest(text: String): List<RemoteRegion> {
        val manifest = JSONObject(text)
        if (manifest.optInt("version") != ManifestVersion)
            throw IOException("unsupported regions.json")
        val regions = manifest.getJSONArray("regions")
        return List(regions.length()) { i ->
            val json = regions.getJSONObject(i)
            val filesJson = json.getJSONArray("files")
            val files =
                List(filesJson.length()) { j ->
                    val f = filesJson.getJSONObject(j)
                    RemoteFile(
                        f.getString("url"),
                        f.getString("target"),
                        f.optString("kind", "file"),
                        f.getLong("size"),
                        f.getString("sha256"),
                    )
                }
            RemoteRegion(
                Region(
                    json.getString("id"),
                    json.getString("name"),
                    json.getString("version"),
                    files.sumOf { it.size },
                ),
                files,
            )
        }
    }

    private fun connect(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TimeoutMillis
            readTimeout = TimeoutMillis
            if (responseCode != HttpURLConnection.HTTP_OK)
                throw IOException("HTTP $responseCode for $url")
        }

    private fun fetchText(url: String): String {
        val connection = connect(url)
        return try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun fetchFile(url: String, to: File, onProgress: (Long) -> Unit): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val connection = connect(url)
        try {
            connection.inputStream.use { input ->
                to.outputStream().use { output ->
                    val buffer = ByteArray(BufferBytes)
                    var read = 0L
                    var last = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        currentCoroutineContext().ensureActive()
                        output.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        read += n
                        if (read - last > ProgressStepBytes) {
                            onProgress(read)
                            last = read
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    // A single-region install from before regions could share the phone can't be told apart from
    // a newer build's border tiles; drop it and let the user download the region again.
    private fun dropLegacyInstall() {
        val record = File(files, InstalledName)
        val legacy =
            record.isFile &&
                runCatching { JSONObject(record.readText()).has("id") }.getOrDefault(true)
        if (!legacy) return
        LegacyFiles.forEach { File(files, it).delete() }
        record.delete()
        publish()
    }

    private fun readInstalled(): List<Installed> {
        val file = File(files, InstalledName)
        if (!file.isFile) return emptyList()
        return runCatching {
                val regions = JSONObject(file.readText()).getJSONArray("regions")
                List(regions.length()) { i ->
                    val json = regions.getJSONObject(i)
                    val paths = json.getJSONArray("paths")
                    Installed(
                        json.getString("id"),
                        json.getString("version"),
                        List(paths.length()) { paths.getString(it) }.toSet(),
                    )
                }
            }
            .getOrDefault(emptyList())
    }

    private fun writeInstalled(installed: List<Installed>) {
        val file = File(files, InstalledName)
        file.parentFile?.mkdirs()
        val regions =
            JSONArray(
                installed.map {
                    JSONObject()
                        .put("id", it.id)
                        .put("version", it.version)
                        .put("paths", JSONArray(it.paths.sorted()))
                }
            )
        val temp = File(file.path + ".tmp")
        temp.writeText(JSONObject().put("regions", regions).toString())
        temp.renameTo(file)
    }
}
