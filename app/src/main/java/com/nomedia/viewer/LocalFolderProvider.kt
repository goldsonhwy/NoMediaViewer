package com.nomedia.viewer

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

class LocalFolderProvider(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("folders", Context.MODE_PRIVATE)

    fun addFolder(uri: Uri): Boolean {
        val uriStr = uri.toString()
        val folders = getFolders().toMutableSet()
        if (folders.add(uriStr)) {
            prefs.edit().putStringSet("selected_folders", folders).apply()
            // Take persistable permission
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            return true
        }
        return false
    }

    fun removeFolder(uri: Uri) {
        val folders = getFolders().toMutableSet()
        folders.remove(uri.toString())
        prefs.edit().putStringSet("selected_folders", folders).apply()
    }

    fun getFolders(): Set<String> {
        return prefs.getStringSet("selected_folders", emptySet()) ?: emptySet()
    }

    fun getFolderUriList(): List<Uri> {
        return getFolders().map { Uri.parse(it) }
    }

    fun getFolderNames(): List<String> {
        return getFolderUriList().mapNotNull { uri ->
            val docFile = DocumentFile.fromTreeUri(context, uri)
            docFile?.name ?: uri.lastPathSegment ?: "Unknown"
        }
    }

    fun hasFolders(): Boolean {
        return getFolders().isNotEmpty()
    }
}
