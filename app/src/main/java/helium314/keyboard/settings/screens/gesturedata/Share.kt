package helium314.keyboard.settings.screens.gesturedata

import android.content.ClipData
import android.content.ComponentName
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.AbstractCursor
import android.database.Cursor
import android.database.CursorWrapper
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.compose.material.icons.materialIcon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.settings.filePicker
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.encoding.Base64

// todo: find "best" ways, maybe 2 or 3 that work everywhere
@Composable
fun ShareGestureData() {
    val ctx = LocalContext.current
    val dataFile = getGestureDataFile(ctx)
    val getDataPicker = getData()

    // get file
    TextButton({ getDataPicker.launch(getDataIntent) }, enabled = dataFile.length() > 0) {
        Text(stringResource(R.string.gesture_data_get_data))
    }

    // share file, but only to mail apps
    TextButton(
        onClick = {
            createZipFile(ctx)
            if (zippedDataPath.isNotEmpty())
                ctx.startActivity(createSendIntentChooserThatTargetsOnlyMailApps(ctx))
        },
        enabled = dataFile.length() > 0 && Intent(Intent.ACTION_SENDTO)
            .apply { data = "mailto:".toUri() }.resolveActivity(ctx.packageManager) != null
    ) {
        Text("share file to mail")
    }

    // copy mail address to clipboard, in case user doesn't use the mail intent
    val clip = LocalClipboard.current
    val scope = rememberCoroutineScope()
    TextButton({ scope.launch { clip.setClipEntry(ClipEntry(ClipData.newPlainText("mail address", MAIL_ADDRESS))) } }) {
        Text("copy mail address")
    }

    // delete data (not sharing, but afterwards)
    TextButton({ dataFile.delete() }, enabled = dataFile.length() > 0) { Text(stringResource(R.string.gesture_data_delete_data)) }
}

private val getDataIntent = Intent(Intent.ACTION_CREATE_DOCUMENT)
    .addCategory(Intent.CATEGORY_OPENABLE)
    .putExtra(Intent.EXTRA_TITLE, "gesture_data_${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().time)}.zip")
    .setType("application/zip")

// the straightforward way would be to just put the extras in the sendMailIntent and return that,
// but this doesn't attach the file in K9 and Proton
private fun createSendIntentChooserThatTargetsOnlyMailApps(context: Context): Intent {
    // targets the correct apps
    val sendMailIntent = Intent(Intent.ACTION_SENDTO).apply { data = "mailto:".toUri() }
    val mailTargets = context.packageManager.queryIntentActivities(sendMailIntent, 0)
    // targets all apps that accept this kind of intent
    val shareFileIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_EMAIL, arrayOf(MAIL_ADDRESS))
        putExtra(Intent.EXTRA_SUBJECT, MAIL_SUBJECT)
        putExtra(Intent.EXTRA_TEXT, MAIL_TEXT)
        putExtra(Intent.EXTRA_STREAM, MAIL_STREAM.toUri())
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val shareTargets = context.packageManager.queryIntentActivities(shareFileIntent, 0)

    // so now we get the shareTargets that are not mailTargets and create ComponentNames...
    val mailTargetPackages = mailTargets.map { it.activityInfo.packageName }
    val nonMailShareTargets = shareTargets.filterNot { it.activityInfo.packageName in mailTargetPackages }
    val nonMailShareTargetComponentNames = nonMailShareTargets.map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
    // ... because the chooser only allows to exclude components, but has no multi-component include functionality
    val chooser = Intent.createChooser(shareFileIntent, "share it")
    chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, nonMailShareTargetComponentNames.toTypedArray())
    return chooser
}

@Composable
private fun getData(): ManagedActivityResultLauncher<Intent, ActivityResult> {
    val ctx = LocalContext.current
    // check if file exists and not size 0, otherwise don't even offer the button
    return filePicker { uri ->
        val file = getGestureDataFile(ctx)
        ctx.getActivity()?.contentResolver?.openOutputStream(uri)?.use { os ->
            val zipStream = ZipOutputStream(os)
            zipStream.setLevel(9)
            val fileStream = FileInputStream(file).buffered()
            zipStream.putNextEntry(ZipEntry(file.name))
            fileStream.copyTo(zipStream, 1024)
            fileStream.close()
            zipStream.closeEntry()
            zipStream.close()
        }
    }
}

private fun createZipFile(context: Context) {
    zippedDataPath = ""
    val dataFile = getGestureDataFile(context)
    val filename = "gesture_data.zip"
    val zipfile = File(context.filesDir, filename)
    zipfile.delete()
    zipfile.outputStream().use { os ->
        val zipStream = ZipOutputStream(os)
        zipStream.setLevel(9)
        val fileStream = FileInputStream(dataFile).buffered()
        zipStream.putNextEntry(ZipEntry(dataFile.name))
        fileStream.copyTo(zipStream, 1024)
        fileStream.close()
        zipStream.closeEntry()
        zipStream.close()
    }
    zippedDataPath = zipfile.absolutePath
}

// necessary for giving the mail app access to an internal file
class GestureFileProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        Log.d("GestureFileProvider", "onCreate")
        return true
    }

    @Throws(FileNotFoundException::class)
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        Log.d("GestureFileProvider", "openFile $uri, mode $mode")
        // todo: not called by K9 when using sendMailIntent, maybe "their fault"? works with FairEmail
        // todo: remove file when we're back in this activity?
        if (uri.toString() != MAIL_STREAM)
            throw FileNotFoundException("Unsupported uri: $uri")
        return ParcelFileDescriptor.open(File(zippedDataPath), ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun update(uri: Uri, contentvalues: ContentValues?, s: String?, `as`: Array<String>?): Int {
        Log.d("GestureFileProvider", "update $uri, but we don't support it")
        return 0
    }
    override fun delete(uri: Uri, s: String?, `as`: Array<String>?): Int {
        Log.d("GestureFileProvider", "delete $uri, but we don't support it")
        return 0
    }
    override fun insert(uri: Uri, contentvalues: ContentValues?): Uri? {
        Log.d("GestureFileProvider", "insert $uri, but we don't support it")
        return null
    }
    override fun getType(uri: Uri): String? {
        Log.d("GestureFileProvider", "getType $uri")
        return null
    }
    override fun query(uri: Uri, projection: Array<String>?, s: String?, as1: Array<String>?, s1: String?): Cursor? {
        // apps query information about the file
        // FairEmail queries _display_name, mime_type, _size separately, K9 both at the same time
        // but just returning null is actually fine, only difference seems to be K9 not showing a file size
        return null
        Log.d("GestureFileProvider", "query $uri, ${projection?.toList()}, $s, ${as1.toString()}, $s1, but we don't support it")

        // looks like the only effect of not returning null is different names, mime types and K9 showing file size
        return object : AbstractCursor() {
            override fun getCount(): Int {
                Log.d("GestureFileProvider", "get count")
                return 1
            }

            override fun getColumnNames(): Array<out String?>? {
                Log.d("GestureFileProvider", "get col names")
                return projection
            }

            override fun getString(p0: Int): String? {
                Log.d("GestureFileProvider", "get string at $p0")
                return when (projection?.getOrNull(p0)) {
                    "_display_name" -> "disp name"
                    "_size" -> "1234567"
                    "mime_type" -> "application/zipp"
                    else -> null
                }
            }

            override fun getShort(p0: Int): Short {
                TODO("Not yet implemented")
            }

            override fun getInt(p0: Int): Int {
                Log.d("GestureFileProvider", "get int at $p0")
                return when (projection?.getOrNull(p0)) {
                    "_size" -> 123456
                    else -> TODO("Not yet implemented")
                }
            }

            override fun getLong(p0: Int): Long {
                TODO("Not yet implemented")
            }

            override fun getFloat(p0: Int): Float {
                TODO("Not yet implemented")
            }

            override fun getDouble(p0: Int): Double {
                TODO("Not yet implemented")
            }

            override fun isNull(p0: Int): Boolean {
                TODO("Not yet implemented")
            }

        }
        return null
    }
}

private var zippedDataPath = "" // set after writing the file

private const val INTENT_SIZE_LIMIT = 500000 // is it 500 kB? 1 MB?
private const val MAIL_ADDRESS = "insert mail here"
private const val MAIL_SUBJECT = "Heliboard ${BuildConfig.VERSION_NAME} gesture data"
private const val MAIL_TEXT = "here is gesture data"
private const val GESTURE_PROVIDER_AUTHORITY = BuildConfig.APPLICATION_ID + ".provider"
private const val MAIL_STREAM = "content://$GESTURE_PROVIDER_AUTHORITY/gesture_data.zip"
