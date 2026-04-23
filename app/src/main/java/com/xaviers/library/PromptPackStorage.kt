package com.xaviers.library

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

enum class PromptPackAsset(
    val storageFileName: String,
    val assetFileName: String,
    val lastImportedNameKey: String
) {
    PROMPT_PACK(
        storageFileName = "prompt_pack.json",
        assetFileName = "prompt_pack.json",
        lastImportedNameKey = "prompt_pack_last_imported_name"
    ),
    VISUAL_DICTIONARY(
        storageFileName = "visual_dictionary.json",
        assetFileName = "visual_dictionary.json",
        lastImportedNameKey = "visual_dictionary_last_imported_name"
    )
}

data class PromptPackImportResult(
    val asset: PromptPackAsset,
    val displayName: String,
    val targetFile: File
)

class PromptPackStorage(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val runtimeDirectory = File(appContext.filesDir, RUNTIME_DIRECTORY).apply { mkdirs() }

    fun fileFor(asset: PromptPackAsset): File {
        return File(runtimeDirectory, asset.storageFileName)
    }

    fun importDocument(asset: PromptPackAsset, uri: Uri): PromptPackImportResult {
        val targetFile = fileFor(asset)
        val tempFile = File(runtimeDirectory, "${asset.storageFileName}.tmp")
        val displayName = queryDisplayName(uri) ?: asset.storageFileName

        appContext.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalArgumentException("Unable to open the selected file.")

        if (tempFile.length() == 0L) {
            tempFile.delete()
            throw IllegalArgumentException("The selected file is empty.")
        }

        if (targetFile.exists() && !targetFile.delete()) {
            tempFile.delete()
            throw IllegalStateException("Unable to replace the existing imported file.")
        }
        if (!tempFile.renameTo(targetFile)) {
            tempFile.copyTo(targetFile, overwrite = true)
            tempFile.delete()
        }

        prefs.edit()
            .putString(asset.lastImportedNameKey, displayName)
            .apply()

        return PromptPackImportResult(
            asset = asset,
            displayName = displayName,
            targetFile = targetFile
        )
    }

    fun lastImportedName(asset: PromptPackAsset): String? {
        return prefs.getString(asset.lastImportedNameKey, null)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun queryDisplayName(uri: Uri): String? {
        appContext.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                return cursor.getString(nameIndex)?.trim()?.takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    companion object {
        private const val PREFS_NAME = "prompt_pack_storage"
        private const val RUNTIME_DIRECTORY = "prompt_pack"
    }
}
