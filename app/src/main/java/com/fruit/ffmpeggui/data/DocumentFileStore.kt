package com.fruit.ffmpeggui.data

import android.content.Context
import android.content.Intent
import android.content.res.AssetFileDescriptor
import android.net.Uri
import android.provider.OpenableColumns
import com.fruit.ffmpeggui.core.PreparedCommandInputs
import com.fruit.ffmpeggui.core.PreparedMediaFile
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.lang.SecurityException
import java.util.UUID

data class PreparedJobFiles(
    val workingDir: File,
    val commandInputs: PreparedCommandInputs,
    val outputFile: File?
)

class DocumentFileStore(private val context: Context) {
    fun describe(uri: Uri): SelectedMediaFile {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri)
        var name: String? = null
        var size: Long? = null
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex >= 0) name = cursor.getString(nameIndex)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
        return SelectedMediaFile(
            uri = uri,
            displayName = name ?: uri.lastPathSegment ?: "media",
            mimeType = mimeType,
            sizeBytes = size
        )
    }

    fun persistReadPermission(uri: Uri) {
        persistPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun persistWritePermission(uri: Uri) {
        persistPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }

    fun prepareJob(
        inputs: List<SelectedMediaFile>,
        outputExtension: String?,
        subtitle: SelectedMediaFile?,
        watermark: SelectedMediaFile?
    ): PreparedJobFiles {
        val workingDir = createWorkingDir()
        val preparedInputs = inputs.mapIndexed { index, file ->
            copyToWorkingFile(file, workingDir, "input_${index + 1}")
        }
        val preparedSubtitle = subtitle?.let { copyToWorkingFile(it, workingDir, "subtitle") }
        val preparedWatermark = watermark?.let { copyToWorkingFile(it, workingDir, "watermark") }
        val outputFile = outputExtension?.let {
            File(workingDir, "output.${it.trimStart('.')}")
        }

        return PreparedJobFiles(
            workingDir = workingDir,
            commandInputs = PreparedCommandInputs(
                inputs = preparedInputs,
                outputPath = outputFile?.absolutePath,
                subtitle = preparedSubtitle,
                watermark = preparedWatermark
            ),
            outputFile = outputFile
        )
    }

    fun prepareProbeJob(inputs: List<SelectedMediaFile>): PreparedJobFiles {
        val workingDir = createWorkingDir()
        val preparedInputs = inputs.mapIndexed { index, file ->
            copyToWorkingFile(file, workingDir, "input_${index + 1}")
        }

        return PreparedJobFiles(
            workingDir = workingDir,
            commandInputs = PreparedCommandInputs(inputs = preparedInputs),
            outputFile = null
        )
    }

    fun copyOutputToUri(outputFile: File, target: Uri) {
        context.contentResolver.openOutputStream(target, "w")?.use { output ->
            outputFile.inputStream().use { input -> input.copyTo(output) }
        } ?: error("Cannot open output document for writing.")
    }

    fun clean(workingDir: File) {
        workingDir.deleteRecursively()
    }

    private fun copyToWorkingFile(
        source: SelectedMediaFile,
        workingDir: File,
        prefix: String
    ): PreparedMediaFile {
        val safeName = source.displayName.sanitizeFileName().ifBlank { prefix }
        val target = runCatching {
            File.createTempFile("${prefix}_", "_$safeName", workingDir)
        }.getOrElse { throwable ->
            error("Cannot create workspace file for ${source.displayName}: ${throwable.message}")
        }
        val inputStream = openInputStreamForRead(source)
        inputStream?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Cannot open ${source.displayName} for reading. Uri=${source.uri}")
        return PreparedMediaFile(path = target.absolutePath, displayName = source.displayName)
    }

    private fun openInputStreamForRead(source: SelectedMediaFile): InputStream? {
        val failures = mutableListOf<String>()
        openUriStream(source.uri, "original openInputStream", failures)?.let { return it }
        openAssetFileStream(source.uri, "original openAssetFileDescriptor", failures)?.let { return it }
        openTypedAssetFileStream(source.uri, "original openTypedAssetFileDescriptor", failures)?.let { return it }
        openFileDescriptorStream(source.uri, "original openFileDescriptor", failures)?.let { return it }

        throw IllegalStateException(
            "Cannot open ${source.displayName} for reading. Uri=${source.uri}" +
                ". Attempts: ${failures.joinToString(" | ")}"
        )
    }

    private fun openUriStream(
        uri: Uri,
        label: String,
        failures: MutableList<String>
    ): InputStream? =
        try {
            context.contentResolver.openInputStream(uri)
        } catch (throwable: FileNotFoundException) {
            failures += "$label failed: ${throwable.message ?: throwable::class.java.simpleName}"
            null
        } catch (throwable: SecurityException) {
            failures += "$label denied: ${throwable.message ?: throwable::class.java.simpleName}"
            null
        }

    private fun openAssetFileStream(
        uri: Uri,
        label: String,
        failures: MutableList<String>
    ): InputStream? =
        try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.toInputStream()
        } catch (throwable: FileNotFoundException) {
            failures += "$label failed: ${throwable.message ?: throwable::class.java.simpleName}"
            null
        } catch (throwable: SecurityException) {
            failures += "$label denied: ${throwable.message ?: throwable::class.java.simpleName}"
            null
        }

    private fun openTypedAssetFileStream(
        uri: Uri,
        label: String,
        failures: MutableList<String>
    ): InputStream? =
        try {
            context.contentResolver.openTypedAssetFileDescriptor(uri, "*/*", null)?.toInputStream()
        } catch (throwable: FileNotFoundException) {
            failures += "$label failed: ${throwable.message ?: throwable::class.java.simpleName}"
            null
        } catch (throwable: SecurityException) {
            failures += "$label denied: ${throwable.message ?: throwable::class.java.simpleName}"
            null
        } catch (throwable: IllegalArgumentException) {
            failures += "$label unsupported: ${throwable.message ?: throwable::class.java.simpleName}"
            null
        }

    private fun openFileDescriptorStream(
        uri: Uri,
        label: String,
        failures: MutableList<String>
    ): InputStream? =
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.let { descriptor ->
                FileInputStream(descriptor.fileDescriptor)
            }
        } catch (throwable: FileNotFoundException) {
            failures += "$label failed: ${throwable.message ?: throwable::class.java.simpleName}"
            null
        } catch (throwable: SecurityException) {
            failures += "$label denied: ${throwable.message ?: throwable::class.java.simpleName}"
            null
        }

    private fun AssetFileDescriptor.toInputStream(): InputStream =
        createInputStream()

    private fun createWorkingDir(): File {
        val root = File(context.cacheDir, "ffmpeg_jobs")
        if (root.exists() && !root.isDirectory) {
            root.delete()
        }
        if (!root.exists() && !root.mkdirs()) {
            error("Cannot create workspace directory: ${root.absolutePath}")
        }
        return File(root, UUID.randomUUID().toString()).apply {
            if (!mkdirs()) {
                error("Cannot create workspace directory: $absolutePath")
            }
        }
    }

    private fun persistPermission(uri: Uri, flags: Int) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        }
    }

    private fun String.sanitizeFileName(): String =
        replace(Regex("[^A-Za-z0-9._-]"), "_").take(96)
}
