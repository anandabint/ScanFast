package com.blitar.scanfast.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import java.io.IOException
import java.io.OutputStream

/** Simpan PDF ke Documents/ScanFast/AKL {start}-{end}/{nomor}.pdf via MediaStore (tanpa izin storage). */
class MediaStoreOutput(context: Context) : PdfOutput {
    private val resolver = context.contentResolver
    private val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    /** Tulis PDF lewat [write]. File dengan nama sama (milik app) ditimpa. Return Uri file. */
    override fun savePdf(start: Int, end: Int, number: Int, write: (OutputStream) -> Unit): Uri {
        val relPath = relativePath(start, end)
        val name = "$number.pdf"
        val existing = find(relPath, name)
        val uri = existing ?: resolver.insert(collection, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }) ?: throw IOException("MediaStore insert gagal: $relPath$name")
        if (existing != null) setPending(uri, 1)
        try {
            val out = resolver.openOutputStream(uri, "wt")
                ?: throw IOException("openOutputStream null: $uri")
            out.use(write)
            setPending(uri, 0)
        } catch (e: Exception) {
            if (existing == null) resolver.delete(uri, null, null) else setPending(uri, 0)
            throw e
        }
        return uri
    }

    override fun delete(start: Int, end: Int, number: Int): Uri? {
        val uri = find(relativePath(start, end), "$number.pdf") ?: return null
        return try {
            if (resolver.delete(uri, null, null) > 0) uri else null
        } catch (e: SecurityException) {
            Log.e(TAG, "Tidak boleh menghapus $uri", e)
            null
        }
    }

    override fun deleteAll(start: Int, end: Int): Int {
        var deleted = 0
        val uris = ArrayList<Uri>()
        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.MIME_TYPE}=?",
            arrayOf(relativePath(start, end), "application/pdf"),
            null
        )?.use { c ->
            while (c.moveToNext()) uris += android.content.ContentUris.withAppendedId(collection, c.getLong(0))
        }
        for (uri in uris) {
            try {
                if (resolver.delete(uri, null, null) > 0) deleted++
            } catch (e: SecurityException) {
                Log.e(TAG, "Tidak boleh menghapus $uri", e)
            }
        }
        return deleted
    }

    override fun rename(start: Int, end: Int, from: Int, to: Int): Boolean {
        val relPath = relativePath(start, end)
        val source = find(relPath, "$from.pdf") ?: return false
        if (find(relPath, "$to.pdf") != null) throw IOException("$to.pdf sudah ada di $relPath")
        val rows = try {
            resolver.update(source, ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$to.pdf")
            }, null, null)
        } catch (e: RuntimeException) {
            throw IOException("Rename $from.pdf -> $to.pdf gagal: ${e.message}", e)
        }
        if (rows <= 0) throw IOException("Rename $from.pdf -> $to.pdf gagal (0 baris)")
        return true
    }

    override fun moveFolder(start: Int, oldEnd: Int, newEnd: Int): Int {
        if (oldEnd == newEnd) return 0
        val oldPath = relativePath(start, oldEnd)
        val newPath = relativePath(start, newEnd)
        val uris = ArrayList<Uri>()
        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.MIME_TYPE}=?",
            arrayOf(oldPath, "application/pdf"),
            null
        )?.use { c ->
            while (c.moveToNext()) uris += android.content.ContentUris.withAppendedId(collection, c.getLong(0))
        }
        val moved = ArrayList<Uri>()
        try {
            for (uri in uris) {
                val rows = resolver.update(uri, ContentValues().apply {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, newPath)
                }, null, null)
                if (rows <= 0) throw IOException("Pindah folder gagal untuk $uri")
                moved += uri
            }
        } catch (e: Exception) {
            for (uri in moved.asReversed()) {
                try {
                    resolver.update(uri, ContentValues().apply {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, oldPath)
                    }, null, null)
                } catch (r: Exception) {
                    Log.e(TAG, "Rollback pindah folder gagal untuk $uri", r)
                }
            }
            throw if (e is IOException) e else IOException("Pindah folder gagal: ${e.message}", e)
        }
        return moved.size
    }

    override fun removeFolderIfEmpty(start: Int, end: Int): Boolean {
        return try {
            var count = 0
            resolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.RELATIVE_PATH}=?",
                arrayOf(relativePath(start, end)),
                null
            )?.use { count = it.count }
            if (count > 0) return false
            // File.delete() pada direktori hanya berhasil kalau kosong, jadi file milik app lain
            // (tak terlihat oleh query MediaStore) tetap aman.
            val dir = java.io.File(
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS),
                "ScanFast/${folderName(start, end)}",
            )
            val removed = dir.isDirectory && dir.delete()
            Log.d(TAG, "Folder kosong ${dir.path}: dihapus=$removed")
            removed
        } catch (e: RuntimeException) {
            Log.w(TAG, "Bersihkan folder kosong gagal: ${e.message}")
            false
        }
    }

    private fun setPending(uri: Uri, value: Int) {
        resolver.update(uri, ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, value)
        }, null, null)
    }

    private fun find(relPath: String, name: String): Uri? {
        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?",
            arrayOf(relPath, name),
            null
        )?.use { c ->
            if (c.moveToFirst()) {
                return android.content.ContentUris.withAppendedId(collection, c.getLong(0))
            }
        }
        return null
    }

    companion object {
        private const val TAG = "MediaStoreOutput"

        fun folderName(start: Int, end: Int) = "AKL $start-$end"
        fun relativePath(start: Int, end: Int) = "Documents/ScanFast/${folderName(start, end)}/"
    }
}
