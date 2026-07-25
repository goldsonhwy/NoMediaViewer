package com.nomedia.viewer

import android.content.Context
import java.io.File

class ImageIndexStore(context: Context) {
    private val file = File(context.filesDir, "image_index.tsv")

    data class Entry(
        val path: String,
        val parent: String,
        val name: String,
        val size: Long,
        val lastModified: Long,
        val width: Int,
        val height: Int
    ) {
        fun toImageFile(): ImageFile = ImageFile(path, name, parent, size, lastModified, width, height)
    }

    fun load(): MutableMap<String, Entry> {
        if (!file.exists()) return mutableMapOf()
        return runCatching {
            file.readLines().mapNotNull { line ->
                val p = line.split('\t')
                if (p.size < 7) null else {
                    val e = Entry(
                        path = p[0],
                        parent = p[1],
                        name = p[2],
                        size = p[3].toLongOrNull() ?: 0L,
                        lastModified = p[4].toLongOrNull() ?: 0L,
                        width = p[5].toIntOrNull() ?: 0,
                        height = p[6].toIntOrNull() ?: 0
                    )
                    e.path to e
                }
            }.toMap().toMutableMap()
        }.getOrDefault(mutableMapOf())
    }

    fun save(entries: Collection<Entry>) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(entries.joinToString("\n") { e ->
            listOf(e.path, e.parent, e.name, e.size, e.lastModified, e.width, e.height)
                .joinToString("\t") { it.toString().replace("\t", " ").replace("\n", " ") }
        })
        tmp.renameTo(file)
    }

    fun clear() { runCatching { file.delete() } }
}
