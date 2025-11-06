package helium314.keyboard.settings.screens.gesturedata

import android.content.ClipData
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
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

    // share file
    TextButton(
        onClick = {
            createZipFile(ctx)
            if (zippedDataPath.isNotEmpty())
                ctx.startActivity(Intent.createChooser(shareFileIntent, "share it"))
        },
        enabled = dataFile.length() > 0
    ) {
        Text("share file to mail")
    }

    // send file via mail
    TextButton(
        onClick = {
            createZipFile(ctx)
            if (zippedDataPath.isNotEmpty())
                ctx.startActivity(sendMailIntent)
        },
        enabled = dataFile.length() > 0 && sendMailIntent.resolveActivity(ctx.packageManager) != null
    ) {
        Text("send file via mail") //Text(stringResource(R.string.gesture_data_send_mail))
    }

    // send file content via mail
    TextButton(
        onClick = {
            if (getGestureDataFile(ctx).length() > INTENT_SIZE_LIMIT) {
                // todo: tell user to do it manually
                return@TextButton
            }
            ctx.startActivity(sendMailPlaintextIntent(getGestureDataFile(ctx).readText()))
        },
        enabled = dataFile.length() > 0 && sendMailIntent.resolveActivity(ctx.packageManager) != null
    ) {
        Text("send data as text")
    }

    // send zipped base64 encoded file content via mail
    TextButton(
        onClick = {
            createZipFile(ctx)
            if (zippedDataPath.isEmpty()) return@TextButton
            if (File(zippedDataPath).length() > INTENT_SIZE_LIMIT) {
                // todo: tell user to do it manually
                return@TextButton
            }
            ctx.startActivity(sendMailPlaintextIntent(Base64.encode(File(zippedDataPath).readBytes())))
        },
        enabled = dataFile.length() > 0 && sendMailIntent.resolveActivity(ctx.packageManager) != null
    ) {
        Text("send data as text (compressed")
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

// works with FairEmail, thunderbird / k9 ignore the attached file
private val sendMailIntent = Intent(Intent.ACTION_SENDTO).apply {
    data = "mailto:".toUri()
    putExtra(Intent.EXTRA_EMAIL, arrayOf(MAIL_ADDRESS))
    putExtra(Intent.EXTRA_SUBJECT, MAIL_SUBJECT)
    putExtra(Intent.EXTRA_TEXT, MAIL_TEXT)
    putExtra(Intent.EXTRA_STREAM, MAIL_STREAM.toUri()) // todo: seems to be ignored by k9, whose fault is it?
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}

// works for FairEmail, thunderbird / k9 not available
private val shareFileIntent = Intent(Intent.ACTION_SEND).apply {
    type = "application/octet-stream" // todo: try different type?
    putExtra(Intent.EXTRA_EMAIL, arrayOf(MAIL_ADDRESS))
    putExtra(Intent.EXTRA_SUBJECT, MAIL_SUBJECT)
    putExtra(Intent.EXTRA_TEXT, MAIL_TEXT)
    putExtra(Intent.EXTRA_STREAM, MAIL_STREAM.toUri())
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}

// should work with all clients, but careful because android intents do have a size limit of 1 MB
// base64 encoded zip file is about 25% larger than normal zip, but still only 20% of plain text
private fun sendMailPlaintextIntent(text: String) = Intent(Intent.ACTION_SENDTO).apply {
    data = "mailto:".toUri()
    putExtra(Intent.EXTRA_EMAIL, arrayOf(MAIL_ADDRESS))
    putExtra(Intent.EXTRA_SUBJECT, MAIL_SUBJECT)
    putExtra(Intent.EXTRA_TEXT, MAIL_TEXT + "\n" + text)
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
        Log.d("GestureFileProvider", "query $uri, but we don't support it")
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
