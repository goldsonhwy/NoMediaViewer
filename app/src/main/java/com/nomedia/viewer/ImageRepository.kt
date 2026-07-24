package com.nomedia.viewer

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class ImageFile(
    val path: String,
    val uri: String = "",
    val name: String,
    val size: Long,
    val lastModified: Long
)

class ImageRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("nomedia_viewer", Context.MODE_PRIVATE)

    // ===== Scan only checked folders =====

    suspend fun scanCheckedFolders(folderManager: FolderTreeManager): List<ImageFile> = withContext(Dispatchers.IO) {
        val images = mutableListOf<ImageFile>()
        val extensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")

        for (rootUriStr in folderManager.getRootUris()) {
            try {
                val rootUri = Uri.parse(rootUriStr)
                val rootDoc = DocumentFile.fromTreeUri(context, rootUri) ?: continue
                val checkedPaths = folderManager.getCheckedFolders(rootUriStr)

                for (checkedPath in checkedPaths) {
                    val targetDoc = if (checkedPath.isEmpty()) {
                        rootDoc
                    } else {
                        navigateToPath(rootDoc, checkedPath)
                    }
                    if (targetDoc != null) {
                        scanDocumentFiles(targetDoc, extensions, images)
                    }
                }
            } catch (_: Exception) {}
        }

        images.sortedByDescending { it.lastModified }
    }

    private fun navigateToPath(root: DocumentFile, path: String): DocumentFile? {
        var current = root
        val parts = path.split("/")
        for (part in parts) {
            if (part.isEmpty()) continue
            val children = current.listFiles() ?: return null
            current = children.find { it.isDirectory && it.name == part } ?: return null
        }
        return current
    }

    private fun scanDocumentFiles(dir: DocumentFile, extensions: Set<String>, result: MutableList<ImageFile>, depth: Int = 0) {
        if (depth > 10) return
        try {
            val files = dir.listFiles() ?: return
            for (file in files) {
                if (file.isDirectory) {
                    scanDocumentFiles(file, extensions, result, depth + 1)
                } else if (file.isFile) {
                    val name = file.name ?: continue
                    val ext = name.substringAfterLast(".", "").lowercase()
                    if (ext in extensions) {
                        result.add(ImageFile(
                            path = file.uri.toString(),
                            uri = file.uri.toString(),
                            name = name,
                            size = file.length(),
                            lastModified = file.lastModified()
                        ))
                    }
                }
            }
        } catch (_: Exception) {}
    }

    // ===== Favorites =====

    fun getFavorites(): Set<String> {
        return prefs.getStringSet("favorites", emptySet()) ?: emptySet()
    }

    fun toggleFavorite(uri: String): Boolean {
        val favs = getFavorites().toMutableSet()
        val added = if (uri in favs) { favs.remove(uri); false } else { favs.add(uri); true }
        prefs.edit().putStringSet("favorites", favs).apply()
        return added
    }

    fun isFavorite(uri: String): Boolean = uri in getFavorites()

    fun getFavoriteImages(): List<ImageFile> {
        return getFavorites().mapNotNull { uriStr ->
            try {
                val uri = Uri.parse(uriStr)
                val docFile = DocumentFile.fromSingleUri(context, uri)
                    ?: DocumentFile.fromFile(File(uriStr))
                if (docFile != null && docFile.exists()) {
                    ImageFile(path = uriStr, uri = uriStr, name = docFile.name ?: "Unknown", size = docFile.length(), lastModified = docFile.lastModified())
                } else null
            } catch (_: Exception) { null }
        }
    }

    // ===== Viewing History =====

    fun getViewedImages(): Set<String> = prefs.getStringSet("viewed", emptySet()) ?: emptySet()
    fun markAsViewed(uri: String) {
        val viewed = getViewedImages().toMutableSet()
        viewed.add(uri)
        prefs.edit().putStringSet("viewed", viewed).apply()
    }
    fun isViewed(uri: String): Boolean = uri in getViewedImages()
    fun resetHistory() { prefs.edit().remove("viewed").apply() }
}
