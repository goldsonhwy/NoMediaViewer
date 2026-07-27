package com.nomedia.viewer

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File

class ImageIndexDb(context: Context) : SQLiteOpenHelper(context, "yellow_gallery_index.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE folders(
                path TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                coverPath TEXT NOT NULL,
                count INTEGER NOT NULL,
                latestModified INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE images(
                path TEXT PRIMARY KEY,
                parentPath TEXT NOT NULL,
                name TEXT NOT NULL,
                size INTEGER NOT NULL,
                lastModified INTEGER NOT NULL,
                width INTEGER NOT NULL,
                height INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_images_parent ON images(parentPath)")
        db.execSQL("CREATE INDEX idx_folders_latest ON folders(latestModified)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS images")
        db.execSQL("DROP TABLE IF EXISTS folders")
        onCreate(db)
    }

    fun loadFolders(): List<FolderAlbum> {
        val out = mutableListOf<FolderAlbum>()
        readableDatabase.rawQuery("SELECT path,name,coverPath,count,latestModified FROM folders ORDER BY latestModified DESC, lower(name) ASC", null).use { c ->
            while (c.moveToNext()) {
                val path = c.getString(0)
                out += FolderAlbum(
                    path = path,
                    name = c.getString(1),
                    coverPath = c.getString(2),
                    count = c.getInt(3),
                    latestModified = c.getLong(4),
                    imagePaths = emptyList()
                )
            }
        }
        return out
    }

    fun loadImages(parentPaths: List<String>): List<ImageFile> {
        if (parentPaths.isEmpty()) return emptyList()
        val db = readableDatabase
        val out = mutableListOf<ImageFile>()
        parentPaths.distinct().forEach { parent ->
            db.rawQuery("SELECT path,name,parentPath,size,lastModified,width,height FROM images WHERE parentPath=? ORDER BY lastModified DESC", arrayOf(parent)).use { c ->
                while (c.moveToNext()) {
                    out += ImageFile(c.getString(0), c.getString(1), c.getString(2), c.getLong(3), c.getLong(4), c.getInt(5), c.getInt(6))
                }
            }
        }
        return out
    }

    fun loadImageEntry(path: String): ImageFile? {
        readableDatabase.rawQuery("SELECT path,name,parentPath,size,lastModified,width,height FROM images WHERE path=?", arrayOf(path)).use { c ->
            if (!c.moveToFirst()) return null
            return ImageFile(c.getString(0), c.getString(1), c.getString(2), c.getLong(3), c.getLong(4), c.getInt(5), c.getInt(6))
        }
    }

    fun replaceAll(images: List<ImageFile>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("images", null, null)
            db.delete("folders", null, null)
            val imgCv = ContentValues()
            images.forEach { img ->
                imgCv.clear()
                imgCv.put("path", img.path)
                imgCv.put("parentPath", img.parentPath)
                imgCv.put("name", img.name)
                imgCv.put("size", img.size)
                imgCv.put("lastModified", img.lastModified)
                imgCv.put("width", img.width)
                imgCv.put("height", img.height)
                db.insertWithOnConflict("images", null, imgCv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            val folderCv = ContentValues()
            images.groupBy { it.parentPath }.values.forEach { list ->
                val sorted = list.sortedByDescending { it.lastModified }
                val first = sorted.firstOrNull() ?: return@forEach
                folderCv.clear()
                folderCv.put("path", first.parentPath)
                folderCv.put("name", File(first.parentPath).name.ifBlank { first.parentPath })
                folderCv.put("coverPath", first.path)
                folderCv.put("count", list.size)
                folderCv.put("latestModified", first.lastModified)
                db.insertWithOnConflict("folders", null, folderCv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
