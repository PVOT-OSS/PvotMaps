/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.maps.data.regions

import java.io.File
import java.io.IOException
import java.io.InputStream

private const val Block = 512

/**
 * Unpacks the regular files of a ustar archive, such as tools/regions/package.py writes, into
 * [into], and returns their paths relative to it. Refuses any path that would land outside [into].
 */
internal fun unpackTar(input: InputStream, into: File): List<String> {
    val header = ByteArray(Block)
    val unpacked = mutableListOf<String>()
    val root = into.canonicalFile
    while (true) {
        if (!readFully(input, header)) break
        if (header.all { it == 0.toByte() }) break
        val name = header.text(0, 100)
        val prefix = header.text(345, 155)
        val path = if (prefix.isEmpty()) name else "$prefix/$name"
        val size = header.text(124, 12).trim().ifEmpty { "0" }.toLong(8)
        val type = header[156].toInt().toChar()
        if (type == '0' || type == '\u0000') {
            val target = File(root, path).canonicalFile
            if (!target.path.startsWith(root.path + File.separator)) {
                throw IOException("unsafe path in archive: $path")
            }
            target.parentFile?.mkdirs()
            target.outputStream().use { output -> copy(input, output::write, size) }
            unpacked += path
        } else {
            copy(input, { _, _, _ -> }, size)
        }
        val padding = (Block - size % Block) % Block
        copy(input, { _, _, _ -> }, padding)
    }
    return unpacked
}

private fun ByteArray.text(offset: Int, length: Int): String {
    var end = offset
    while (end < offset + length && this[end] != 0.toByte()) end++
    return String(this, offset, end - offset, Charsets.UTF_8)
}

private fun readFully(input: InputStream, buffer: ByteArray): Boolean {
    var read = 0
    while (read < buffer.size) {
        val n = input.read(buffer, read, buffer.size - read)
        if (n < 0) {
            if (read == 0) return false
            throw IOException("archive ends inside a header")
        }
        read += n
    }
    return true
}

private fun copy(input: InputStream, write: (ByteArray, Int, Int) -> Unit, count: Long) {
    val buffer = ByteArray(1 shl 16)
    var left = count
    while (left > 0) {
        val n = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
        if (n < 0) throw IOException("archive ends inside a file")
        write(buffer, 0, n)
        left -= n
    }
}
