package com.nomedia.viewer

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FolderTreeManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("folders_v2", Context.MODE_PRIVATE)

    // ===== Root folder management =====

    fun addRootFolder(uri: Uri): Boolean {
        val roots = getRootUris().toMutableSet()
        val uriStr = uri.toString()
        if (roots.add(uriStr)) {
            prefs.edit().putStringSet("root_folders", roots).apply()
            // Take persistable permission
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            // Auto-check the root folder
            setFolderChecked(uriStr, "", true)
            return true
        }
        return false
    }

    fun removeRootFolder(uri: Uri) {
        val uriStr = uri.toString()
        val roots = getRootUris().toMutableSet()
        roots.remove(uriStr)
        prefs.edit().putStringSet("root_folders", roots).apply()
        // Clean up checked entries for this root
        val allChecked = getRawCheckedMap().toMutableMap()
        allChecked.keys.removeAll { it.startsWith("$uriStr|") }
        allChecked.remove(uriStr)
        saveCheckedMap(allChecked)
    }

    fun getRootUris(): Set<String> {
        return prefs.getStringSet("root_folders", emptySet()) ?: emptySet()
    }

    fun getRootUriList(): List<Uri> {
        return getRootUris().map { Uri.parse(it) }
    }

    // ===== Checked folder management =====

    fun isFolderChecked(rootUri: String, relativePath: String = ""): Boolean {
        val key = if (relativePath.isEmpty()) rootUri else "$rootUri|$relativePath"
        return getRawCheckedMap()[key] == true
    }

    fun setFolderChecked(rootUri: String, relativePath: String, checked: Boolean) {
        val map = getRawCheckedMap().toMutableMap()
        val key = if (relativePath.isEmpty()) rootUri else "$rootUri|$relativePath"
        if (checked) {
            map[key] = true
            // Auto-check parent
            if (relativePath.isNotEmpty()) {
                val parentPath = relativePath.substringBeforeLast("/", "")
                setFolderChecked(rootUri, parentPath, true)
            }
        } else {
            map.remove(key)
            // Uncheck all children
            map.keys.removeAll { it.startsWith("$key/") }
        }
        saveCheckedMap(map)
    }

    fun getCheckedFolders(rootUri: String): Set<String> {
        val map = getRawCheckedMap()
        return map.filter { (k, v) ->
            v && (k == rootUri || k.startsWith("$rootUri|"))
        }.keys.map { k ->
            if (k == rootUri) "" else k.substringAfter("$rootUri|")
        }.toSet()
    }

    fun getCheckedRootUris(): List<String> {
        return getRootUris().filter { isFolderChecked(it) }
    }

    // ===== Folder tree data =====

    suspend fun getFolderTree(rootUri: Uri): FolderNode? = withContext(Dispatchers.IO) {
        try {
            val docFile = DocumentFile.fromTreeUri(context, rootUri) ?: return@withContext null
            buildTreeNode(rootUri.toString(), docFile, "", 0)
        } catch (_: Exception) { null }
    }

    private fun buildTreeNode(rootUri: String, docFile: DocumentFile, relativePath: String, depth: Int): FolderNode? {
        if (depth > 10) return null
        val name = docFile.name ?: return null
        val children = mutableListOf<FolderNode>()

        try {
            val files = docFile.listFiles() ?: return FolderNode(
                uri = docFile.uri.toString(),
                name = name,
                relativePath = relativePath,
                isChecked = isFolderChecked(rootUri, relativePath),
                children = emptyList(),
                depth = depth
            )
            for (file in files) {
                if (file.isDirectory && file.canRead()) {
                    val subPath = if (relativePath.isEmpty()) file.name ?: ""
                    else "$relativePath/${file.name}"
                    val child = buildTreeNode(rootUri, file, subPath, depth + 1)
                    if (child != null) children.add(child)
                }
            }
        } catch (_: Exception) {}

        return FolderNode(
            uri = docFile.uri.toString(),
            name = name,
            relativePath = relativePath,
            isChecked = isFolderChecked(rootUri, relativePath),
            children = children.sortedBy { it.name },
            depth = depth
        )
    }

    // ===== Preferences helpers =====

    private fun getRawCheckedMap(): Map<String, Boolean> {
        val raw = prefs.getStringSet("checked_folders", emptySet()) ?: emptySet()
        return raw.associateWith { true }
    }

    private fun saveCheckedMap(map: Map<String, Boolean>) {
        prefs.edit().putStringSet("checked_folders", map.keys).apply()
    }

    // ===== Preferences key for column count =====

    fun getColumnCount(): Int = prefs.getInt("column_count", 1).coerceIn(1, 2)
    fun setColumnCount(n: Int) { prefs.edit().putInt("column_count", n.coerceIn(1, 2)).apply() }
}

data class FolderNode(
    val uri: String,
    val name: String,
    val relativePath: String,
    val isChecked: Boolean,
    val children: List<FolderNode>,
    val depth: Int
)
