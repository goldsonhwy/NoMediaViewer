package com.nomedia.viewer

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class ImageFile(
    val path: String,
    val uri: String = "",
    val name: String,
    val size: Long,
    val lastModified: Long
)

data class FolderGroup(
    val path: String,
    val name: String,
    val images: List<ImageFile>,
    val thumbnailPath: String? // first image path
)

class ImageRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("nomedia_viewer", Context.MODE_PRIVATE)

    // ===== Scan & Group by Folder =====

    suspend fun scanAndGroup(enabledPaths: List<String>): List<FolderGroup> = withContext(Dispatchers.IO) {
        val extensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")
        val folderMap = mutableMapOf<String, MutableList<ImageFile>>()

        for (basePath in enabledPaths) {
            val baseDir = File(basePath)
            if (!baseDir.exists() || !baseDir.canRead()) continue
            scanAndCollect(baseDir, basePath, extensions, folderMap, depth = 0)
        }

        folderMap.map { (path, images) ->
            val sorted = images.sortedByDescending { it.lastModified }
            FolderGroup(
                path = path,
                name = path.substringAfterLast("/"),
                images = sorted,
                thumbnailPath = sorted.firstOrNull()?.path
            )
        }.filter { it.images.isNotEmpty() }
         .sortedBy { it.name.lowercase() }
    }

    private fun scanAndCollect(
        dir: File,
        rootPath: String,
        extensions: Set<String>,
        result: MutableMap<String, MutableList<ImageFile>>,
        depth: Int
    ) {
        if (depth > 12) return
        try {
            val files = dir.listFiles() ?: return
            var hasImages = false
            for (file in files) {
                if (file.isDirectory) {
                    scanAndCollect(file, rootPath, extensions, result, depth + 1)
                } else if (file.isFile) {
                    val ext = file.extension.lowercase()
                    if (ext in extensions) {
                        val img = ImageFile(
                            path = file.absolutePath,
                            uri = file.absolutePath,
                            name = file.name,
                            size = file.length(),
                            lastModified = file.lastModified()
                        )
                        result.getOrPut(dir.absolutePath) { mutableListOf() }.add(img)
                        hasImages = true
                    }
                }
            }
        } catch (_: SecurityException) {}
    }

    // ===== Scan flat (for browse mode) =====

    suspend fun scanPaths(paths: List<String>): List<ImageFile> = withContext(Dispatchers.IO) {
        val images = mutableListOf<ImageFile>()
        val extensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")
        for (path in paths) {
            val dir = File(path)
            if (!dir.exists() || !dir.canRead()) continue
            scanRecursive(dir, extensions, images, depth = 0)
        }
        images.sortedByDescending { it.lastModified }
    }

    suspend fun scanSingleFolder(folderPath: String): List<ImageFile> = withContext(Dispatchers.IO) {
        val images = mutableListOf<ImageFile>()
        val extensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")
        val dir = File(folderPath)
        if (dir.exists() && dir.canRead()) {
            scanRecursive(dir, extensions, images, depth = 0)
        }
        images.sortedByDescending { it.lastModified }
    }

    private fun scanRecursive(dir: File, extensions: Set<String>, result: MutableList<ImageFile>, depth: Int) {
        if (depth > 12) return
        try {
            val files = dir.listFiles() ?: return
            for (file in files) {
                if (file.isDirectory) scanRecursive(file, extensions, result, depth + 1)
                else if (file.isFile) {
                    val ext = file.extension.lowercase()
                    if (ext in extensions) {
                        result.add(ImageFile(file.absolutePath, file.absolutePath, file.name, file.length(), file.lastModified()))
                    }
                }
            }
        } catch (_: SecurityException) {}
    }

    // ===== Favorites =====

    fun getFavorites(): Set<String> = prefs.getStringSet("favorites", emptySet()) ?: emptySet()
    fun toggleFavorite(uri: String): Boolean {
        val favs = getFavorites().toMutableSet()
        val added = if (uri in favs) { favs.remove(uri); false } else { favs.add(uri); true }
        prefs.edit().putStringSet("favorites", favs).apply()
        return added
    }
    fun isFavorite(uri: String): Boolean = uri in getFavorites()
    fun getFavoriteImages(): List<ImageFile> = getFavorites().mapNotNull { path ->
        try { val f = File(path); if (f.exists()) ImageFile(path, path, f.name, f.length(), f.lastModified()) else null } catch (_: Exception) { null }
    }
    fun getViewedImages(): Set<String> = prefs.getStringSet("viewed", emptySet()) ?: emptySet()
    fun markAsViewed(uri: String) {
        val viewed = getViewedImages().toMutableSet(); viewed.add(uri); prefs.edit().putStringSet("viewed", viewed).apply()
    }
    fun resetHistory() { prefs.edit().remove("viewed").apply() }
}
