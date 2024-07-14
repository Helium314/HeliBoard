package helium314.keyboard.keyboard.clipboard

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import helium314.keyboard.latin.ClipboardHistoryEntry
import helium314.keyboard.latin.common.FileUtils
import helium314.keyboard.latin.utils.Log
import java.io.File

class ClipboardImageProvider(private val context: Context) {
    fun saveClipboardImage(imageUri: Uri, fileName: String): Uri? {
        try {
            val imagesDir = File(context.filesDir, IMAGES_DIR_NAME)
            val imageFile = File(imagesDir, fileName)
            FileUtils.copyContentUriToNewFile(imageUri, context, imageFile)
            return FileProvider.getUriForFile(context, "${context.packageName}.provider", imageFile)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Could not get URI for file: $fileName, ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Could not save clipboard image: $fileName, ${e.message}", e)
        }
        return null
    }

    fun deleteClipboardImage(imageUri: Uri) {
        val fileName = imageUri.lastPathSegment ?: run {
            Log.w(TAG, "Invalid URI: $imageUri")
            return
        }
        try {
            val imagesDir = File(context.filesDir, IMAGES_DIR_NAME)
            val imageFile = File(imagesDir, fileName)
            if (imageFile.exists()) {
                context.revokeUriPermission(imageUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (!imageFile.delete()) {
                    Log.w(TAG, "Failed to delete clipboard image: $fileName")
                }
            } else {
                Log.w(TAG, "File does not exist: $fileName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not delete clipboard image: $fileName, ${e.message}", e)
        }
    }

    fun removeOrphanedImages(historyEntries: List<ClipboardHistoryEntry>) {
        val imageFiles = try {
            File(context.filesDir, IMAGES_DIR_NAME).listFiles() ?: return
        } catch (e: Exception) {
            Log.e(TAG, "Could not get image files: ${e.message}", e)
            return
        }
        val historyUris = historyEntries.mapNotNull { it.imageUri }.toSet()
        val imagesToDelete = mutableListOf<Uri>()
        for (imageFile in imageFiles) {
            try {
                val imageUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", imageFile)
                if (imageUri !in historyUris) {
                    imagesToDelete.add(imageUri)
                }
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Could not get URI for file: ${imageFile.name}, ${e.message}", e)
            }
        }
        imagesToDelete.forEach { image -> deleteClipboardImage(image) }
    }

    companion object {
        private val TAG = ClipboardImageProvider::class.java.simpleName
        private const val IMAGES_DIR_NAME = "clipboard_images"
    }
}