package com.homesweetlove.instaytgrabber.core

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.util.UUID

object StoragePublisher {
    private lateinit var appContext: Context

    fun attach(context: Context) {
        appContext = context.applicationContext
    }

    fun tempDirectory(prefix: String): File {
        check(::appContext.isInitialized) { "StoragePublisher is not initialized" }
        val safe = prefix.replace(Regex("[^A-Za-z0-9._-]"), "_").take(60)
        return File(appContext.cacheDir, "grabber/" + safe + "_" + UUID.randomUUID())
            .apply { mkdirs() }
    }

    fun publishFiles(
        files: List<File>,
        relativeFolder: String
    ): List<File> {
        check(::appContext.isInitialized) { "StoragePublisher is not initialized" }
        val validFiles = files.filter { it.isFile && it.length() > 0L }
        require(validFiles.isNotEmpty()) { "저장할 파일이 없습니다." }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            validFiles.map { publishMediaStore(it, relativeFolder) }
        } else {
            validFiles.map { publishLegacy(it, relativeFolder) }
        }
    }

    private fun publishMediaStore(source: File, relativeFolder: String): File {
        val relative = Environment.DIRECTORY_DOWNLOADS +
            "/InstaYtGrabber/" + relativeFolder.trim('/')

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, source.name)
            put(MediaStore.Downloads.MIME_TYPE, mimeFor(source))
            put(MediaStore.Downloads.RELATIVE_PATH, relative)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val resolver = appContext.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Downloads 저장 공간을 만들지 못했습니다.")

        try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                source.inputStream().use { input ->
                    input.copyTo(output, 64 * 1024)
                }
            } ?: error("Downloads 파일을 열지 못했습니다.")

            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                null,
                null
            )

            return File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "InstaYtGrabber/" + relativeFolder.trim('/') + "/" + source.name
            )
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    @Suppress("DEPRECATION")
    private fun publishLegacy(source: File, relativeFolder: String): File {
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "InstaYtGrabber/" + relativeFolder.trim('/')
        ).apply { mkdirs() }

        val output = uniqueFile(directory, source.name)
        source.copyTo(output, overwrite = false)
        return output
    }

    private fun uniqueFile(directory: File, name: String): File {
        var output = File(directory, name)
        if (!output.exists()) return output

        val stem = name.substringBeforeLast('.', name)
        val extension = name.substringAfterLast('.', "")
        var index = 2
        while (output.exists()) {
            val suffix = if (extension.isBlank()) {
                stem + "_" + index
            } else {
                stem + "_" + index + "." + extension
            }
            output = File(directory, suffix)
            index++
        }
        return output
    }

    private fun mimeFor(file: File): String =
        when (file.extension.lowercase()) {
            "mp4", "m4v" -> "video/mp4"
            "webm" -> "video/webm"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "opus", "ogg" -> "audio/ogg"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "application/octet-stream"
        }
}
