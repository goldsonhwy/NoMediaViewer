package com.nomedia.viewer

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import java.io.File

class FolderManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("folders_simple", Context.MODE_PRIVATE)

    data class ManagedFolder(
        val path: String,     // Actual file path like /storage/emulated/0/DCIM
        val uriStr: String,   // Original SAF URI for persistence
        val isEnabled: Boolean,
        val name: String
    )

    fun addFolder(uri: Uri) {
        val folders = getFoldersRaw().toMutableSet()
        folders.add(encodeFolder(uri.toString()))
        prefs.edit().putStringSet("folder_list", folders).apply()
        // Persist SAF permission
        try {
            context.contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {}
    }

    fun removeFolder(uriStr: String) {
        val folders = getFoldersRaw().toMutableSet()
        folders.remove(encodeFolder(uriStr))
        prefs.edit().putStringSet("folder_list", folders).apply()
    }

    fun setEnabled(uriStr: String, enabled: Boolean) {
        val key = "enabled_" + uriStr.hashCode()
        prefs.edit().putBoolean(key, enabled).apply()
    }

    fun isEnabled(uriStr: String): Boolean {
        return prefs.getBoolean("enabled_" + uriStr.hashCode(), true) // default enabled
    }

    fun getFolders(): List<ManagedFolder> {
        return getFoldersRaw().mapNotNull { encoded ->
            val uriStr = decodeFolder(encoded) ?: return@mapNotNull null
            val path = safUriToPath(uriStr) ?: return@mapNotNull null
            val file = File(path)
            ManagedFolder(
                path = path,
                uriStr = uriStr,
                isEnabled = isEnabled(uriStr),
                name = file.name.ifEmpty { path.substringAfterLast("/") }
            )
        }
    }

    fun hasFolders(): Boolean = getFoldersRaw().isNotEmpty()

    fun getEnabledPaths(): List<String> {
        return getFolders().filter { it.isEnabled }.map { it.path }
    }

    // Convert SAF content URI to real file path
    fun safUriToPath(uriStr: String): String? {
        try {
            val uri = Uri.parse(uriStr)
            // Format: content://com.android.externalstorage.documents/tree/primary%3APath%2FTo%2FFolder
            val docId = uri.lastPathSegment ?: return null
            val decoded = Uri.decode(docId)
            if (decoded.startsWith("primary:")) {
                val relPath = decoded.removePrefix("primary:")
                return "/storage/emulated/0/$relPath"
            }
            // Handle secondary storage (SD card)
            if (decoded.contains(":")) {
                val parts = decoded.split(":", limit = 2)
                return "/storage/${parts[0]}/${parts[1]}"
            }
            return "/storage/emulated/0/$decoded"
        } catch (_: Exception) { return null }
    }

    private fun getFoldersRaw(): Set<String> {
        return prefs.getStringSet("folder_list", emptySet()) ?: emptySet()
    }

    // ===== Preferences =====

    fun getColumnCount(): Int = prefs.getInt("column_count", 1).coerceIn(1, 2)
    fun setColumnCount(n: Int) { prefs.edit().putInt("column_count", n.coerceIn(1, 2)).apply() }

    private fun encodeFolder(uriStr: String): String {
        return uriStr.replace("|", "%7C")
    }

    private fun decodeFolder(encoded: String): String? {
        return try { Uri.decode(encoded) } catch (_: Exception) { null }
    }
}
