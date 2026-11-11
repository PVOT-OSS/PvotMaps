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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
private const val InstalledName = "regions/installed.json"
private const val DownloadDir = "regions/download"
private const val TimeoutMillis = 30_000
private const val BufferBytes = 1 shl 16
private const val SpareBytes = 50L shl 20

private data class RemoteFile(
    val url: String,
    val target: String,
    val size: Long,
    val sha256: String,
)

private data class RemoteRegion(val region: Region, val files: List<RemoteFile>)

private data class Installed(val id: String, val version: String, val targets: List<String>)

/**
 * Downloads regions from the static site [BuildConfig.REGIONS_URL] points at, checks every file
 * against its SHA-256, and only then moves them in place, so a failed download leaves the region
 * that was installed untouched. One region is installed at a time.
 */
@Singleton
internal class HttpRegionRepository
@Inject
constructor(@param:ApplicationContext private val context: Context) : RegionRepository {

    private val baseUrl = BuildConfig.REGIONS_URL.trimEnd('/')
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var remote: List<RemoteRegion> = emptyList()
    private val transient = MutableStateFlow<Map<String, RegionStatus>>(emptyMap())
    private val _regions = MutableStateFlow<List<RegionState>>(emptyList())
    private var job: Job? = null

    override val isConfigured: Boolean = baseUrl.isNotEmpty()

    override val regions: StateFlow<List<RegionState>> = _regions.asStateFlow()

    override suspend fun refresh() {
        if (!isConfigured) return
        withContext(Dispatchers.IO) {
            try {
                remote = parseManifest(fetchText("$baseUrl/$ManifestName"))
            } catch (_: Exception) {
                // Without the list, still show the region on the device so it can be deleted.
                if (remote.isEmpty()) {
                    transient.value = mapOf("" to RegionStatus.Failed(RegionFailure.Network))
                }
            }
            publish()
        }
    }

    override fun download(id: String) {
        val target = remote.firstOrNull { it.region.id == id } ?: return
        if (job?.isActive == true) return
        job =
            scope.launch {
                setStatus(id, RegionStatus.Downloading(0f))
                val failure = install(target)
                setStatus(id, failure?.let { RegionStatus.Failed(it) })
            }
    }

    override fun delete(id: String) {
        val installed = readInstalled() ?: return
        if (installed.id != id) return
        installed.targets.forEach { File(context.filesDir, it).delete() }
        File(context.filesDir, InstalledName).delete()
        publish()
    }

    private suspend fun install(remote: RemoteRegion): RegionFailure? {
        val dir =
            File(context.filesDir, DownloadDir).apply {
                deleteRecursively()
                mkdirs()
            }
        val total = remote.files.sumOf { it.size }
        if (allocatableBytes() < total + SpareBytes) return RegionFailure.Storage
        var done = 0L
        val downloaded = mutableListOf<Pair<File, RemoteFile>>()
        try {
            for (file in remote.files) {
                val part = File(dir, file.target.replace('/', '_'))
                val sha =
                    fetchFile("$baseUrl/${file.url}", part) { bytes ->
                        setStatus(
                            remote.region.id,
                            RegionStatus.Downloading((done + bytes).toFloat() / total),
                        )
                    }
                if (sha != file.sha256 || part.length() != file.size) return RegionFailure.Corrupt
                done += file.size
                downloaded += part to file
            }
        } catch (_: IOException) {
            return RegionFailure.Network
        } finally {
            if (downloaded.size != remote.files.size) dir.deleteRecursively()
        }
        readInstalled()?.targets?.forEach { File(context.filesDir, it).delete() }
        for ((part, file) in downloaded) {
            val target = File(context.filesDir, file.target)
            target.parentFile?.mkdirs()
            if (!part.renameTo(target)) return RegionFailure.Storage
        }
        dir.deleteRecursively()
        writeInstalled(
            Installed(remote.region.id, remote.region.version, remote.files.map { it.target })
        )
        return null
    }

    // Counts cached data the system would clear to make room, unlike the free space alone.
    private fun allocatableBytes(): Long {
        val storage = context.getSystemService(StorageManager::class.java)
        return try {
            storage.getAllocatableBytes(storage.getUuidForPath(context.filesDir))
        } catch (_: IOException) {
            0L
        }
    }

    private fun setStatus(id: String, status: RegionStatus?) {
        transient.value =
            (transient.value - "" - id).let { if (status == null) it else it + (id to status) }
        publish()
    }

    private fun publish() {
        val installed = readInstalled()
        val states =
            remote.map { (region, _) ->
                val status =
                    transient.value[region.id]
                        ?: if (installed?.id == region.id) {
                            RegionStatus.Installed(
                                installed.version,
                                installed.version < region.version,
                            )
                        } else {
                            RegionStatus.NotInstalled
                        }
                RegionState(region, status)
            }
        val offline =
            installed
                ?.takeIf { i -> remote.none { it.region.id == i.id } }
                ?.let {
                    RegionState(
                        Region(it.id, it.id, it.version, 0),
                        RegionStatus.Installed(it.version, false),
                    )
                }
        val failed =
            transient.value[""]?.let { listOf(RegionState(Region("", "", "", 0), it)) }.orEmpty()
        _regions.value = states + listOfNotNull(offline) + failed
    }

    private fun parseManifest(text: String): List<RemoteRegion> {
        val regions = JSONObject(text).getJSONArray("regions")
        return List(regions.length()) { i ->
            val json = regions.getJSONObject(i)
            val filesJson = json.getJSONArray("files")
            val files =
                List(filesJson.length()) { j ->
                    val f = filesJson.getJSONObject(j)
                    RemoteFile(
                        f.getString("url"),
                        f.getString("target"),
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

    private fun readInstalled(): Installed? {
        val file = File(context.filesDir, InstalledName)
        if (!file.isFile) return null
        return runCatching {
                val json = JSONObject(file.readText())
                val targets = json.getJSONArray("targets")
                Installed(
                    json.getString("id"),
                    json.getString("version"),
                    List(targets.length()) { targets.getString(it) },
                )
            }
            .getOrNull()
    }

    private fun writeInstalled(installed: Installed) {
        val file = File(context.filesDir, InstalledName)
        file.parentFile?.mkdirs()
        file.writeText(
            JSONObject()
                .put("id", installed.id)
                .put("version", installed.version)
                .put("targets", JSONArray(installed.targets))
                .toString()
        )
    }

    private companion object {
        const val ProgressStepBytes = 1L shl 20
    }
}
